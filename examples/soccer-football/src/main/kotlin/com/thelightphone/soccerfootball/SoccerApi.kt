package com.thelightphone.soccerfootball

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsBytes
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

private const val API_BASE = "https://soccer-proxy.ravisolter.com"
private const val REQUEST_TIMEOUT_MS = 15_000L

/**
 * Client for this app's own caching proxy (`https://soccer-proxy.ravisolter.com`), not
 * API-Football directly — Phase 3 onward, the proxy holds the real API-Football key server-side
 * and absorbs the request-rate/plan cost, so this class sends no auth header of its own (confirmed
 * by reading the proxy's own source: none of its routes check for a client credential, only an
 * IP-based rate limiter and a league allow-list).
 *
 * A subtlety that mattered when this class talked to API-Football directly, and is left in place
 * as defense-in-depth even though the proxy now intercepts it server-side: API-Football itself
 * returns real errors (plan restrictions among them) as an HTTP 200 with a populated `errors`
 * field, not a non-2xx status. [getChecked] still checks for that shape via
 * [apiFootballErrorMessage]. What actually reaches this class now, per the proxy's own
 * `app/main.py`, are real HTTP statuses: 429 (rate limited), 402 (plan restricted), 502 (network
 * failure talking to API-Football), 403 (league not on the whitelist), 400 (bad params) — [get]'s
 * status handling below maps these to real [ApiFootballApiException.Kind]s.
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

    /** [leagueIds]' matches within [dateFrom]..[dateTo] (both yyyy-MM-dd, inclusive), one request
     * per league fanned out concurrently and tolerant of partial failure — mirrors the ESPN
     * variant's own fan-out for the same reason: one league's request failing shouldn't blank out
     * the others. Backs both [fetchTodaysMatches] (today..today, for Scores) and the Fixtures
     * screen's all-followed-leagues view (a wider window — see [SoccerViewModel.openFixtures]).
     * Uses `from`/`to` rather than API-Football's `last` parameter, which was Pro-only on the free
     * tier (confirmed: `last=5` returned
     * `{"errors":{"plan":"Free plans do not have access to the Last parameter."}}`). */
    suspend fun fetchFixturesForLeagues(
        leagueIds: List<Int>,
        dateFrom: String,
        dateTo: String,
    ): Result<List<Fixture>> = coroutineScope {
        if (leagueIds.isEmpty()) return@coroutineScope Result.success(emptyList())
        val results = leagueIds.map { id ->
            async { runCatching { fetchFixturesInternal(leagueId = id, dateFrom = dateFrom, dateTo = dateTo) } }
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

    /** Today's ([todayLocalDate]) matches across [leagueIds] — a thin wrapper over
     * [fetchFixturesForLeagues] with both ends of the range pinned to today. */
    suspend fun fetchTodaysMatches(leagueIds: List<Int>): Result<List<Fixture>> {
        val date = todayLocalDate().toString()
        return fetchFixturesForLeagues(leagueIds, date, date)
    }

    /** A single team's matches within [dateFrom]..[dateTo] — My Team's upcoming/recent fixtures.
     * Uses API-Football's `team` filter the same way [fetchFixturesForLeagues] uses `league`; this
     * specific combination (`team` + `season` + `from`/`to`, no `league`) was **not**
     * independently curl-verified this session (only `league`-scoped fixture queries were run) —
     * worth a quick real-request check before trusting it against a live match, same as the
     * league IDs flagged in SoccerModels.kt. */
    suspend fun fetchFixturesForTeam(
        teamId: Int,
        dateFrom: String,
        dateTo: String,
    ): Result<List<Fixture>> = runCatching {
        val body: ApiFootballFixturesResponse = getChecked("$API_BASE/fixtures") {
            parameter("team", teamId)
            parameter("season", currentSeason())
            parameter("from", dateFrom)
            parameter("to", dateTo)
        }
        body.response.map { it.toFixture() }
    }

    private suspend fun fetchFixturesInternal(leagueId: Int, dateFrom: String, dateTo: String): List<Fixture> {
        val body: ApiFootballFixturesResponse = getChecked("$API_BASE/fixtures") {
            parameter("league", leagueId)
            parameter("season", currentSeason())
            parameter("from", dateFrom)
            parameter("to", dateTo)
        }
        return body.response.map { it.toFixture() }
    }

    // --- Standings -------------------------------------------------------------

    /** Current-season (i.e. [currentSeason]) table for one competition — flattened from
     * API-Football's possibly-grouped shape, see [ApiFootballStandingsLeagueDto.toStandingsRows] —
     * bundled with the league badge URL from the same response, see [StandingsFetchResult]. */
    suspend fun fetchStandings(leagueId: Int): Result<StandingsFetchResult> = runCatching {
        val body: ApiFootballStandingsResponse = getChecked("$API_BASE/standings") {
            parameter("league", leagueId)
            parameter("season", currentSeason())
        }
        body.response.firstOrNull()?.league?.toStandingsFetchResult() ?: StandingsFetchResult(rows = emptyList())
    }

    // --- Match detail (events, statistics, lineups) -----------------------------

    /** All three match-detail sections for one fixture, fetched concurrently. Each section is
     * independently tolerant of failure (via [runCatching] per-call rather than one combined
     * try/catch) — a match that hasn't kicked off yet legitimately has no stats/lineups yet, and
     * that shouldn't take down the whole screen the way it would if one failed call threw for all
     * three. */
    suspend fun fetchMatchDetail(
        fixtureId: Int,
        homeTeamId: Int,
        awayTeamId: Int,
    ): Result<MatchDetail> = coroutineScope {
        val eventsDeferred = async { runCatching { fetchEventsInternal(fixtureId) } }
        val statsDeferred = async { runCatching { fetchStatisticsInternal(fixtureId, homeTeamId, awayTeamId) } }
        val lineupsDeferred = async { runCatching { fetchLineupsInternal(fixtureId, homeTeamId, awayTeamId) } }

        val events = eventsDeferred.await().getOrDefault(emptyList())
        val stats = statsDeferred.await().getOrDefault(emptyList())
        val lineups = lineupsDeferred.await().getOrDefault(MatchLineups(home = null, away = null))

        Result.success(MatchDetail(stats = stats, events = events, lineups = lineups))
    }

    private suspend fun fetchEventsInternal(fixtureId: Int): List<MatchEvent> {
        val body: ApiFootballEventsResponse = getChecked("$API_BASE/fixtures/events") {
            parameter("fixture", fixtureId)
        }
        return body.response.map { it.toMatchEvent() }
    }

    private suspend fun fetchStatisticsInternal(
        fixtureId: Int,
        homeTeamId: Int,
        awayTeamId: Int,
    ): List<MatchStatRow> {
        val body: ApiFootballStatisticsResponse = getChecked("$API_BASE/fixtures/statistics") {
            parameter("fixture", fixtureId)
        }
        return body.response.toMatchStatRows(homeTeamId, awayTeamId)
    }

    private suspend fun fetchLineupsInternal(
        fixtureId: Int,
        homeTeamId: Int,
        awayTeamId: Int,
    ): MatchLineups {
        val body: ApiFootballLineupsResponse = getChecked("$API_BASE/fixtures/lineups") {
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
    suspend fun fetchUnavailableForFixture(fixtureId: Int): Result<List<UnavailablePlayer>> = runCatching {
        val body: ApiFootballInjuriesResponse = getChecked("$API_BASE/injuries") {
            parameter("fixture", fixtureId)
        }
        body.response.map { it.toUnavailablePlayer() }
    }

    // --- My Team -----------------------------------------------------------------

    /** Assembles one followed team's My Team screen: league position, a handful of upcoming and
     * recent fixtures, and who's unavailable for its next match. [standings] is passed in rather
     * than re-fetched — the caller (SoccerViewModel) already has it cached from the Standings
     * screen for any league the user follows, and re-fetching here would just burn another call
     * against the proxy's shared daily budget. */
    suspend fun fetchMyTeamSummary(
        teamId: Int,
        teamName: String,
        leagueId: Int,
        leagueName: String,
        standings: List<StandingsRow>,
    ): Result<MyTeamSummary> = runCatching {
        val today = todayLocalDate()
        val windowStart = today.minus(MY_TEAM_WINDOW_PAST_DAYS, DateTimeUnit.DAY)
        val windowEnd = today.plus(MY_TEAM_WINDOW_FUTURE_DAYS, DateTimeUnit.DAY)
        val fixtures = fetchFixturesForTeam(teamId, windowStart.toString(), windowEnd.toString())
            .getOrElse { emptyList() }
            .sortedBy { it.utcDate }

        // Today's fixture (if any) is pulled out separately for the header's "today/next match"
        // placeholder — it used to fall into `upcoming` below (d >= today), which is exactly what
        // let a live-in-progress or already-finished-today match show up in the "UPCOMING" card, a
        // bug flagged from a real screenshot. `upcoming`/`recent` now both deliberately exclude it
        // (d > today / d < today), on request, so today's match only ever appears in the featured
        // placeholder, never duplicated into either list.
        val todaysFixture = fixtures.firstOrNull { it.localDate() == today }
        val upcoming = fixtures.filter { it.localDate()?.let { d -> d > today } == true }
        val recent = fixtures.filter { it.localDate()?.let { d -> d < today } == true }.reversed()
        val featuredFixture = todaysFixture ?: upcoming.firstOrNull()

        val referenceFixtureId = featuredFixture?.id ?: recent.firstOrNull()?.id
        // distinctBy: a real /injuries response has been observed repeating the same player row
        // (confirmed by a user report of duplicated "Unavailable" entries) — API-Football's own
        // docs don't explain why, so this dedupes defensively by player name rather than assuming
        // a specific cause. Keeps whichever row for that name came first.
        val unavailable = referenceFixtureId
            ?.let { fetchUnavailableForFixture(it).getOrElse { emptyList() } }
            ?.distinctBy { it.playerName }
            ?: emptyList()

        // No standalone "team" endpoint call for this — the crest URL rides along on every fixture's
        // team object (see ApiFootballFixtureTeamDto.logo), so the first fixture that actually
        // mentions teamId (home or away side) is enough; costs zero extra requests against the proxy
        // (the crest fetch below hits a separate image host directly, not the proxy, so it doesn't
        // touch that budget either — see [fetchImageBytes]'s doc comment).
        val teamLogoUrl = fixtures.firstNotNullOfOrNull { f ->
            when (teamId) {
                f.homeTeamId -> f.homeTeamLogo.takeIf { it.isNotBlank() }
                f.awayTeamId -> f.awayTeamLogo.takeIf { it.isNotBlank() }
                else -> null
            }
        }
        val teamLogoBytes = teamLogoUrl?.let { fetchImageBytes(it).getOrNull() }

        // Same idea, but for the featured fixture's *other* side — the small opponent badge next to
        // the today/next match placeholder. Same "hits the image CDN directly, not the proxy" cost
        // profile as teamLogoBytes above.
        val featuredOpponentLogoUrl = featuredFixture?.let { f ->
            when (teamId) {
                f.homeTeamId -> f.awayTeamLogo.takeIf { it.isNotBlank() }
                f.awayTeamId -> f.homeTeamLogo.takeIf { it.isNotBlank() }
                else -> null
            }
        }
        val featuredOpponentLogoBytes = featuredOpponentLogoUrl?.let { fetchImageBytes(it).getOrNull() }

        MyTeamSummary(
            teamId = teamId,
            teamName = teamName,
            leagueId = leagueId,
            leagueName = leagueName,
            standingsRow = standings.firstOrNull { it.teamId == teamId },
            featuredFixture = featuredFixture,
            upcomingFixtures = upcoming.take(MY_TEAM_FIXTURE_LIMIT),
            recentFixtures = recent.take(MY_TEAM_FIXTURE_LIMIT),
            unavailable = unavailable,
            teamLogoBytes = teamLogoBytes,
            featuredOpponentLogoBytes = featuredOpponentLogoBytes,
        )
    }

    /** Home/away crest bytes for the match detail header's team icons. Sourced from the tapped
     * [Fixture]'s own [Fixture.homeTeamLogo]/[Fixture.awayTeamLogo] — already present on every
     * fixture response (see [ApiFootballFixtureTeamDto.logo]), so this needs no extra "team"
     * lookup, just the two image fetches themselves, run concurrently the same way
     * [fetchMatchDetail]'s three sections are. Either side is null if its URL was blank or the
     * fetch failed — the header simply omits that crest rather than showing a broken image. */
    suspend fun fetchMatchCrests(homeLogoUrl: String, awayLogoUrl: String): Pair<ByteArray?, ByteArray?> = coroutineScope {
        val homeDeferred = async { homeLogoUrl.takeIf { it.isNotBlank() }?.let { fetchImageBytes(it).getOrNull() } }
        val awayDeferred = async { awayLogoUrl.takeIf { it.isNotBlank() }?.let { fetchImageBytes(it).getOrNull() } }
        homeDeferred.await() to awayDeferred.await()
    }

    /** Home/away coach headshot bytes for the lineup tab — see [TeamLineup.coachPhotoUrl]'s doc
     * comment for where that URL comes from. Unlike [fetchMatchCrests], the URLs here aren't known
     * until [fetchMatchDetail]'s lineups section has already resolved (a coach's photo URL only
     * exists once the lineups response itself has arrived), so this takes nullable URLs rather than
     * being kicked off alongside the initial fixture tap. Either side is null if that team had no
     * lineup/coach/photo, or the fetch failed. */
    suspend fun fetchCoachPhotos(homePhotoUrl: String?, awayPhotoUrl: String?): Pair<ByteArray?, ByteArray?> = coroutineScope {
        val homeDeferred = async { homePhotoUrl?.takeIf { it.isNotBlank() }?.let { fetchImageBytes(it).getOrNull() } }
        val awayDeferred = async { awayPhotoUrl?.takeIf { it.isNotBlank() }?.let { fetchImageBytes(it).getOrNull() } }
        homeDeferred.await() to awayDeferred.await()
    }

    /** League badge bytes for one or more league logo URLs (see [ApiFootballFixtureLeagueDto.logo] /
     * [ApiFootballStandingsLeagueDto.logo]), fetched concurrently and keyed by URL so a caller with
     * several distinct leagues on screen at once (Scores' competition groups) can look each one up
     * by the same URL string it already has, rather than this function needing to know about league
     * IDs at all. A blank or duplicate URL is fetched at most once; a URL whose fetch fails is simply
     * absent from the result map — same "omit rather than show broken" convention as the other image
     * fetches on this class. */
    suspend fun fetchLeagueLogos(urls: Collection<String>): Map<String, ByteArray> = coroutineScope {
        urls.filter { it.isNotBlank() }.distinct()
            .map { url -> url to async { fetchImageBytes(url).getOrNull() } }
            .mapNotNull { (url, deferred) -> deferred.await()?.let { url to it } }
            .toMap()
    }

    /** Team crest bytes for one or more [Fixture.homeTeamLogo]/[Fixture.awayTeamLogo] URLs, keyed
     * by URL — used by Scores and Results & Fixtures' combined team-crest/score cell (see
     * MatchTeamsAndScoreCell in SoccerHomeScreen.kt). Mechanically identical to [fetchLeagueLogos]
     * (fetch each distinct URL once, keyed by URL, omit on failure) — this is a separate name
     * purely so call sites read as "team crests", not because the fetch itself differs at all. */
    suspend fun fetchTeamLogos(urls: Collection<String>): Map<String, ByteArray> = fetchLeagueLogos(urls)

    /** Fetches a hosted image as raw bytes — used for team crest URLs off [ApiFootballFixtureTeamDto.logo].
     * Deliberately bypasses [get]/[getChecked] below: there's no JSON body here to run through
     * [apiFootballErrorMessage]'s success/failure check, and the crest URL points at whatever CDN
     * actually serves the image (not this app's proxy), so it's a plain unauthenticated GET either
     * way. Note this means crest fetches don't go through the proxy at all — they bypass its
     * caching, its request budget, and its rate limiter, hitting the image CDN directly every time.
     * Reuses this class's [client] (same timeouts) rather than standing up a second HTTP client
     * just for images. */
    private suspend fun fetchImageBytes(url: String): Result<ByteArray> = runCatching {
        val response = try {
            client.get(url)
        } catch (e: Exception) {
            throw ApiFootballApiException(e.message ?: "Image fetch failed.", ApiFootballApiException.Kind.NETWORK)
        }
        if (response.status.value !in 200..299) {
            throw ApiFootballApiException(
                "Image fetch returned HTTP ${response.status.value}",
                ApiFootballApiException.Kind.NETWORK,
            )
        }
        response.bodyAsBytes()
    }

    // --- HTTP plumbing -------------------------------------------------------------

    private suspend inline fun <reified T> getChecked(
        url: String,
        noinline block: HttpRequestBuilder.() -> Unit = {},
    ): T {
        val response = get(url, block)
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
        url: String,
        block: HttpRequestBuilder.() -> Unit,
    ): HttpResponse {
        val response = try {
            client.get(url) {
                block()
            }
        } catch (e: HttpRequestTimeoutException) {
            throw ApiFootballApiException("Request to the proxy timed out.", ApiFootballApiException.Kind.NETWORK)
        } catch (e: Exception) {
            throw ApiFootballApiException(e.message ?: "Network error.", ApiFootballApiException.Kind.NETWORK)
        }

        when (response.status.value) {
            429 -> throw ApiFootballApiException(
                "Too many requests — try again in a minute.",
                ApiFootballApiException.Kind.RATE_LIMITED,
            )
            402 -> throw ApiFootballApiException(
                "This data isn't available on the current plan.",
                ApiFootballApiException.Kind.PLAN_RESTRICTED,
            )
            403 -> throw ApiFootballApiException(
                "This league isn't turned on for Soccer Pro yet — try again once the proxy is updated.",
                ApiFootballApiException.Kind.UNKNOWN,
            )
            502 -> throw ApiFootballApiException(
                "The proxy couldn't reach API-Football — try again shortly.",
                ApiFootballApiException.Kind.NETWORK,
            )
            in 200..299 -> Unit
            else -> throw ApiFootballApiException(
                "The proxy returned HTTP ${response.status.value}: ${response.bodyAsText().take(200)}",
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
 * — also confirmed directly. Returns the joined error message(s), or null if there's no error.
 * Left in place as defense-in-depth: the proxy already intercepts this shape server-side and turns
 * it into a real HTTP status (see this file's class doc comment), so in practice this should never
 * fire once behind the proxy — but it costs nothing to keep checking. */
private fun apiFootballErrorMessage(element: JsonElement): String? {
    val errors = (element as? JsonObject)?.get("errors") ?: return null
    return when (errors) {
        is JsonArray -> errors.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString("; ").takeIf { it.isNotBlank() }
        is JsonObject -> errors.values.mapNotNull { (it as? JsonPrimitive)?.content }.joinToString("; ").takeIf { it.isNotBlank() }
        else -> null
    }
}

private const val MY_TEAM_FIXTURE_LIMIT = 5

/** How far back/forward of [todayLocalDate] to search for a followed team's fixtures — wide enough
 * that a team without a match in the immediate past/future week still turns up something on
 * both sides, without pulling a whole season. [MY_TEAM_WINDOW_PAST_DAYS] was 30 until a real
 * screenshot showed only 4 "RECENT RESULTS" for a team that plays roughly weekly — MY_TEAM_
 * FIXTURE_LIMIT below was already 5, so the cap wasn't the bottleneck, the fetch window was: 30
 * days of a ~weekly domestic schedule (plus the odd cup week off) lands on 4 played matches about
 * as often as 5. Bumped to 45 for enough buffer that a normal schedule reliably clears 5, without
 * fetching a whole season's worth of history. [MY_TEAM_WINDOW_FUTURE_DAYS] gets the identical fix
 * here, on request, after a real screenshot showed only 3 "UPCOMING" fixtures for a team — same
 * root cause as the past-days fix above (MY_TEAM_FIXTURE_LIMIT was already 5, so again the fetch
 * window, not the cap, was the bottleneck), diagnosed by direct analogy rather than confirmed
 * against real fixture data for that specific team, since this sandbox has no way to hit the live
 * API and see the actual schedule. Matched to MY_TEAM_WINDOW_PAST_DAYS's 45 rather than picked
 * independently, since nothing about "upcoming" vs. "recent" suggests they need different widths. */
private const val MY_TEAM_WINDOW_PAST_DAYS = 45
private const val MY_TEAM_WINDOW_FUTURE_DAYS = 45
