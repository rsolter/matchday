package com.thelightphone.soccerfootball

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement

private const val API_BASE = "https://v3.football.api-sports.io"
private const val REQUEST_TIMEOUT_MS = 15_000L

/**
 * API-Football / API-Sports REST client (`https://v3.football.api-sports.io`), authenticated via
 * the `x-apisports-key` header. Unlike the ESPN variant of this tool, this is a documented,
 * stable, official API — but it has real, undocumented free-tier restrictions found only by
 * testing against a real key this session (see the module README's "Data source" section):
 * seasons are capped to 2022-2024, and the `last` query parameter is Pro-only.
 *
 * A subtlety that matters for every method below: API-Football returns real errors (including
 * both restrictions above) as an HTTP 200 with a populated `errors` field, not as a non-2xx status
 * — confirmed against real responses (`{"errors":{"plan":"..."}}`) alongside real *successful*
 * responses that carry `"errors":[]` (an empty array, not absent). [getChecked] handles both
 * shapes so a plan restriction surfaces as a real error message instead of silently decoding into
 * an empty `response` list and looking like "no fixtures found".
 */
internal class ApiFootballApiException(
    message: String,
    val kind: Kind,
) : Exception(message) {
    enum class Kind { RATE_LIMITED, PLAN_RESTRICTED, NETWORK, UNKNOWN }
}

internal class ApiFootballApi {
    private val json = Json { ignoreUnknownKeys = true }

    private val client = HttpClient(OkHttp) {
        install(HttpTimeout) {
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = REQUEST_TIMEOUT_MS
            socketTimeoutMillis = REQUEST_TIMEOUT_MS
        }
    }

    // --- Fixtures ------------------------------------------------------------

    /** Today's ([phase1Today], not the device's real date — see its doc comment) matches across
     * [leagueIds]. One request per league, fanned out concurrently and tolerant of partial
     * failure — mirrors the ESPN variant's [SoccerApi.fetchTodaysMatches] fan-out for the same
     * reason: one league's request failing shouldn't blank out the others. */
    suspend fun fetchTodaysMatches(apiKey: String, leagueIds: List<Int>): Result<List<Fixture>> = coroutineScope {
        if (leagueIds.isEmpty()) return@coroutineScope Result.success(emptyList())
        val date = phase1Today().toString()
        val results = leagueIds.map { id ->
            async { runCatching { fetchFixturesInternal(apiKey, leagueId = id, dateFrom = date, dateTo = date) } }
        }.map { it.await() }

        val succeeded = results.mapNotNull { it.getOrNull() }
        if (succeeded.isEmpty()) {
            val firstError = results.firstNotNullOfOrNull { it.exceptionOrNull() }
                ?: ApiFootballApiException("Unknown error", ApiFootballApiException.Kind.UNKNOWN)
            Result.failure(firstError)
        } else {
            Result.success(succeeded.flatten())
        }
    }

    /** A single competition's matches within [dateFrom]..[dateTo] (both yyyy-MM-dd, inclusive) —
     * the Fixtures mode's by-date list. Uses `from`/`to` rather than API-Football's `last`
     * parameter, which is Pro-only on the free tier (confirmed: `last=5` returned
     * `{"errors":{"plan":"Free plans do not have access to the Last parameter."}}`). */
    suspend fun fetchFixturesForLeague(
        apiKey: String,
        leagueId: Int,
        dateFrom: String,
        dateTo: String,
    ): Result<List<Fixture>> = runCatching { fetchFixturesInternal(apiKey, leagueId = leagueId, dateFrom = dateFrom, dateTo = dateTo) }

    /** A single team's matches within [dateFrom]..[dateTo] — My Team's upcoming/recent fixtures.
     * Uses API-Football's `team` filter the same way [fetchFixturesForLeague] uses `league`; this
     * specific combination (`team` + `season` + `from`/`to`, no `league`) was **not**
     * independently curl-verified this session (only `league`-scoped fixture queries were run) —
     * worth a quick real-request check before trusting it against a live match, same as the
     * league IDs flagged in SoccerModels.kt. */
    suspend fun fetchFixturesForTeam(
        apiKey: String,
        teamId: Int,
        dateFrom: String,
        dateTo: String,
    ): Result<List<Fixture>> = runCatching {
        val body: ApiFootballFixturesResponse = getChecked(apiKey, "$API_BASE/fixtures") {
            parameter("team", teamId)
            parameter("season", PHASE1_SEASON)
            parameter("from", dateFrom)
            parameter("to", dateTo)
        }
        body.response.map { it.toFixture() }
    }

    private suspend fun fetchFixturesInternal(apiKey: String, leagueId: Int, dateFrom: String, dateTo: String): List<Fixture> {
        val body: ApiFootballFixturesResponse = getChecked(apiKey, "$API_BASE/fixtures") {
            parameter("league", leagueId)
            parameter("season", PHASE1_SEASON)
            parameter("from", dateFrom)
            parameter("to", dateTo)
        }
        return body.response.map { it.toFixture() }
    }

    // --- Standings -------------------------------------------------------------

    /** Current-season (i.e. [PHASE1_SEASON]) table for one competition — flattened from
     * API-Football's possibly-grouped shape, see [ApiFootballStandingsLeagueDto.toStandingsRows]. */
    suspend fun fetchStandings(apiKey: String, leagueId: Int): Result<List<StandingsRow>> = runCatching {
        val body: ApiFootballStandingsResponse = getChecked(apiKey, "$API_BASE/standings") {
            parameter("league", leagueId)
            parameter("season", PHASE1_SEASON)
        }
        body.response.firstOrNull()?.league?.toStandingsRows() ?: emptyList()
    }

    // --- Match detail (events, statistics, lineups) -----------------------------

    /** All three match-detail sections for one fixture, fetched concurrently. Each section is
     * independently tolerant of failure (via [runCatching] per-call rather than one combined
     * try/catch) — a match that hasn't kicked off yet legitimately has no stats/lineups yet, and
     * that shouldn't take down the whole screen the way it would if one failed call threw for all
     * three. */
    suspend fun fetchMatchDetail(
        apiKey: String,
        fixtureId: Int,
        homeTeamId: Int,
        awayTeamId: Int,
    ): Result<MatchDetail> = coroutineScope {
        val eventsDeferred = async { runCatching { fetchEventsInternal(apiKey, fixtureId) } }
        val statsDeferred = async { runCatching { fetchStatisticsInternal(apiKey, fixtureId, homeTeamId, awayTeamId) } }
        val lineupsDeferred = async { runCatching { fetchLineupsInternal(apiKey, fixtureId, homeTeamId, awayTeamId) } }

        val events = eventsDeferred.await().getOrDefault(emptyList())
        val stats = statsDeferred.await().getOrDefault(emptyList())
        val lineups = lineupsDeferred.await().getOrDefault(MatchLineups(home = null, away = null))

        Result.success(MatchDetail(stats = stats, events = events, lineups = lineups))
    }

    private suspend fun fetchEventsInternal(apiKey: String, fixtureId: Int): List<MatchEvent> {
        val body: ApiFootballEventsResponse = getChecked(apiKey, "$API_BASE/fixtures/events") {
            parameter("fixture", fixtureId)
        }
        return body.response.map { it.toMatchEvent() }
    }

    private suspend fun fetchStatisticsInternal(
        apiKey: String,
        fixtureId: Int,
        homeTeamId: Int,
        awayTeamId: Int,
    ): List<MatchStatRow> {
        val body: ApiFootballStatisticsResponse = getChecked(apiKey, "$API_BASE/fixtures/statistics") {
            parameter("fixture", fixtureId)
        }
        return body.response.toMatchStatRows(homeTeamId, awayTeamId)
    }

    private suspend fun fetchLineupsInternal(
        apiKey: String,
        fixtureId: Int,
        homeTeamId: Int,
        awayTeamId: Int,
    ): MatchLineups {
        val body: ApiFootballLineupsResponse = getChecked(apiKey, "$API_BASE/fixtures/lineups") {
            parameter("fixture", fixtureId)
        }
        return body.toMatchLineups(homeTeamId, awayTeamId)
    }

    // --- Injuries / unavailability (My Team) ------------------------------------

    /** Who's unavailable for one specific fixture — confirmed against a real
     * `GET /injuries?fixture=1035037` response (1 row). This is the fixture-scoped call, not the
     * team+season "whole log" one (`GET /injuries?league=...&season=...&team=...`, confirmed
     * separately but returns one row per fixture across the *entire* season — see the gotcha
     * documented on [ApiFootballInjuryDto]) — My Team picks one reference fixture (its team's next
     * upcoming match, or most recent if none upcoming) and calls this instead of pulling the whole
     * season log and filtering client-side, since the fixture-scoped call already does exactly
     * that filtering server-side. */
    suspend fun fetchUnavailableForFixture(apiKey: String, fixtureId: Int): Result<List<UnavailablePlayer>> = runCatching {
        val body: ApiFootballInjuriesResponse = getChecked(apiKey, "$API_BASE/injuries") {
            parameter("fixture", fixtureId)
        }
        body.response.map { it.toUnavailablePlayer() }
    }

    // --- My Team -----------------------------------------------------------------

    /** Assembles one followed team's My Team screen: league position, a handful of upcoming and
     * recent fixtures, and who's unavailable for its next match. [standings] is passed in rather
     * than re-fetched — the caller (SoccerViewModel) already has it cached from the Standings
     * screen for any league the user follows, and re-fetching here would just burn another call
     * against a free tier that's already capped at 100/day. */
    suspend fun fetchMyTeamSummary(
        apiKey: String,
        teamId: Int,
        teamName: String,
        leagueId: Int,
        leagueName: String,
        standings: List<StandingsRow>,
    ): Result<MyTeamSummary> = runCatching {
        val today = phase1Today()
        val windowStart = today.minus(MY_TEAM_WINDOW_PAST_DAYS, DateTimeUnit.DAY)
        val windowEnd = today.plus(MY_TEAM_WINDOW_FUTURE_DAYS, DateTimeUnit.DAY)
        val fixtures = fetchFixturesForTeam(apiKey, teamId, windowStart.toString(), windowEnd.toString())
            .getOrElse { emptyList() }
            .sortedBy { it.utcDate }

        val upcoming = fixtures.filter { it.localDate()?.let { d -> d >= today } == true }
        val recent = fixtures.filter { it.localDate()?.let { d -> d < today } == true }.reversed()

        val referenceFixtureId = upcoming.firstOrNull()?.id ?: recent.firstOrNull()?.id
        val unavailable = referenceFixtureId
            ?.let { fetchUnavailableForFixture(apiKey, it).getOrElse { emptyList() } }
            ?: emptyList()

        // No standalone "team" endpoint call for this — the crest URL rides along on every fixture's
        // team object (see ApiFootballFixtureTeamDto.logo), so the first fixture that actually
        // mentions teamId (home or away side) is enough; costs zero extra requests.
        val teamLogoUrl = fixtures.firstNotNullOfOrNull { f ->
            when (teamId) {
                f.homeTeamId -> f.homeTeamLogo.takeIf { it.isNotBlank() }
                f.awayTeamId -> f.awayTeamLogo.takeIf { it.isNotBlank() }
                else -> null
            }
        }

        MyTeamSummary(
            teamId = teamId,
            teamName = teamName,
            leagueId = leagueId,
            leagueName = leagueName,
            standingsRow = standings.firstOrNull { it.teamId == teamId },
            upcomingFixtures = upcoming.take(MY_TEAM_FIXTURE_LIMIT),
            recentFixtures = recent.take(MY_TEAM_FIXTURE_LIMIT),
            unavailable = unavailable,
            teamLogoUrl = teamLogoUrl,
        )
    }

    // --- HTTP plumbing -------------------------------------------------------------

    private suspend inline fun <reified T> getChecked(
        apiKey: String,
        url: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response = get(apiKey, url, block)
        val text = response.bodyAsText()
        val element = try {
            json.parseToJsonElement(text)
        } catch (e: Exception) {
            throw ApiFootballApiException("API-Football returned an unreadable response.", ApiFootballApiException.Kind.UNKNOWN)
        }
        apiFootballErrorMessage(element)?.let { message ->
            throw ApiFootballApiException(message, ApiFootballApiException.Kind.PLAN_RESTRICTED)
        }
        return json.decodeFromJsonElement(element)
    }

    private suspend fun get(
        apiKey: String,
        url: String,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val response = try {
            client.get(url) {
                header("x-apisports-key", apiKey)
                block()
            }
        } catch (e: HttpRequestTimeoutException) {
            throw ApiFootballApiException("Request to API-Football timed out.", ApiFootballApiException.Kind.NETWORK)
        } catch (e: Exception) {
            throw ApiFootballApiException(e.message ?: "Network error.", ApiFootballApiException.Kind.NETWORK)
        }

        when (response.status.value) {
            429 -> throw ApiFootballApiException(
                "Too many requests — try again in a minute.",
                ApiFootballApiException.Kind.RATE_LIMITED,
            )
            in 200..299 -> Unit
            else -> throw ApiFootballApiException(
                "API-Football returned HTTP ${response.status.value}: ${response.bodyAsText().take(200)}",
                ApiFootballApiException.Kind.UNKNOWN,
            )
        }

        return response
    }

    fun close() = client.close()
}

/** API-Football's `errors` field is `[]` (empty array) on a real success — confirmed against every
 * successful response gathered this session — and a populated *object* (not array) on a real
 * failure, e.g. `{"plan": "Free plans do not have access to this season, try from 2022 to 2024."}`
 * — also confirmed directly. Returns the joined error message(s), or null if there's no error. */
private fun apiFootballErrorMessage(element: JsonElement): String? {
    val errors = (element as? JsonObject)?.get("errors") ?: return null
    return when (errors) {
        is JsonArray -> errors.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString("; ").takeIf { it.isNotBlank() }
        is JsonObject -> errors.values.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString("; ").takeIf { it.isNotBlank() }
        else -> null
    }
}

private const val MY_TEAM_FIXTURE_LIMIT = 5

/** How far back/forward of [phase1Today] to search for a followed team's fixtures — wide enough
 * that a team without a match in the immediate past/future week still turns up something on
 * both sides, without pulling a whole season. */
private const val MY_TEAM_WINDOW_PAST_DAYS = 30
private const val MY_TEAM_WINDOW_FUTURE_DAYS = 30
