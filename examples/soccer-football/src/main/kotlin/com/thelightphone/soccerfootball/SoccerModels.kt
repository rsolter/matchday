package com.thelightphone.soccerfootball

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The competitions this tool tracks. IDs are API-Football's own numeric league IDs (a documented,
 * stable API — unlike the ESPN/FotMob variants of this tool, so these aren't reverse-engineered).
 *
 * 39 (Premier League) and 2 (UEFA Champions League) were confirmed this session against real
 * `/leagues` and `/standings` responses. 135 (Serie A) and 3 (UEFA Europa League) are
 * API-Football's commonly published IDs for those competitions but were **not** independently
 * curl-verified against a real response in this session — worth a quick
 * `GET /leagues?id=135` / `GET /leagues?id=3` check before relying on them, same way 39 and 2 were
 * confirmed (see the module README's "Data source" section for the verification trail).
 */
data class Competition(val id: Int, val name: String)

val TRACKED_COMPETITIONS: List<Competition> = listOf(
    Competition(id = 39, name = "Premier League"),
    Competition(id = 135, name = "Serie A"),
    Competition(id = 2, name = "UEFA Champions League"),
    Competition(id = 3, name = "UEFA Europa League"),
)

/**
 * Competitions whose `/standings` response splits into multiple group arrays instead of one flat
 * table (confirmed for id 2 / UEFA Champions League this session — 8 groups of 4 — see
 * [ApiFootballStandingsLeagueDto.standings]'s doc comment). Id 3 / Europa League is assumed to
 * share UEFA's group-stage format but wasn't independently confirmed — [toStandingsRows] doesn't
 * actually need this set (it detects grouping directly from the response shape, i.e.
 * `standings.size > 1`), so a wrong assumption here costs nothing; this is just documentation of
 * what's expected going in.
 */
val GROUP_STAGE_COMPETITION_IDS: Set<Int> = setOf(2, 3)

private val COMPETITION_DISPLAY_ORDER: Map<Int, Int> =
    TRACKED_COMPETITIONS.mapIndexed { index, c -> c.id to index }.toMap()
private val COMPETITION_NAMES: Map<Int, String> = TRACKED_COMPETITIONS.associate { it.id to it.name }

fun competitionName(id: Int): String = COMPETITION_NAMES[id] ?: "League $id"

// --- Wire format (API-Football /fixtures response) ----------------------------
//
// Confirmed against a real `GET /fixtures?league=39&season=2023&from=2023-08-01&to=2023-08-31`
// response (29 fixtures, all finished) — see the module README for the full verification trail.
// Only the `FT` status code has actually been seen in a real response this session; the rest of
// [MatchStatus]'s mapping below comes from API-Football's published status-code table, not
// independent curl verification — worth re-checking now that Phase 3 has live data via the proxy.

@Serializable
internal data class ApiFootballFixturesResponse(
    val response: List<ApiFootballFixtureDto> = emptyList(),
)

@Serializable
internal data class ApiFootballFixtureDto(
    val fixture: ApiFootballFixtureInfoDto,
    val league: ApiFootballFixtureLeagueDto,
    val teams: ApiFootballFixtureTeamsDto,
    val goals: ApiFootballGoalsDto = ApiFootballGoalsDto(),
)

@Serializable
internal data class ApiFootballFixtureInfoDto(
    val id: Int,
    val date: String,
    val status: ApiFootballFixtureStatusDto,
)

@Serializable
internal data class ApiFootballFixtureStatusDto(
    val long: String = "",
    val short: String = "",
    val elapsed: Int? = null,
    val extra: Int? = null,
)

@Serializable
internal data class ApiFootballFixtureLeagueDto(
    val id: Int,
    val name: String = "",
    val round: String = "",
)

@Serializable
internal data class ApiFootballFixtureTeamsDto(
    val home: ApiFootballFixtureTeamDto,
    val away: ApiFootballFixtureTeamDto,
)

@Serializable
internal data class ApiFootballFixtureTeamDto(
    val id: Int,
    val name: String = "",
    val winner: Boolean? = null,
    /** Hosted PNG crest URL — confirmed present on a real `/fixtures` team object this session
     * (curl-verified, not assumed from API-Football's docs). Not modeled on [ApiFootballEventTeamDto]
     * (standings/statistics/injuries' shared team stub) since only the fixtures response was
     * actually checked for it. */
    val logo: String = "",
)

@Serializable
internal data class ApiFootballGoalsDto(
    val home: Int? = null,
    val away: Int? = null,
)

internal fun ApiFootballFixtureDto.toFixture(): Fixture = Fixture(
    id = fixture.id,
    utcDate = fixture.date,
    statusShort = fixture.status.short,
    statusLong = fixture.status.long,
    elapsed = fixture.status.elapsed,
    extraMinutes = fixture.status.extra,
    leagueId = league.id,
    leagueName = league.name,
    round = league.round,
    homeTeamId = teams.home.id,
    homeTeamName = teams.home.name,
    homeTeamLogo = teams.home.logo,
    awayTeamId = teams.away.id,
    awayTeamName = teams.away.name,
    awayTeamLogo = teams.away.logo,
    homeGoals = goals.home,
    awayGoals = goals.away,
)

// --- Wire format (API-Football /fixtures/events response) ---------------------
//
// Confirmed against a real `GET /fixtures/events?fixture=1035037` response — 15 real events
// covering all four `type` values seen this session: "Goal", "subst", "Card", "Var".
//
// The one non-obvious gotcha: for a "subst" event, `player`/`assist` do NOT mean scorer/assist
// the way they do for a "Goal" event — they mean player-off/player-on respectively. [toMatchEvent]
// below branches on `type` specifically so this doesn't get flattened into one generic shape.

@Serializable
internal data class ApiFootballEventsResponse(
    val response: List<ApiFootballEventDto> = emptyList(),
)

@Serializable
internal data class ApiFootballEventDto(
    val time: ApiFootballEventTimeDto,
    val team: ApiFootballEventTeamDto,
    val player: ApiFootballEventPersonDto? = null,
    val assist: ApiFootballEventPersonDto? = null,
    val type: String = "",
    val detail: String? = null,
    val comments: String? = null,
)

@Serializable
internal data class ApiFootballEventTimeDto(val elapsed: Int = 0, val extra: Int? = null)

@Serializable
internal data class ApiFootballEventTeamDto(val id: Int, val name: String = "")

@Serializable
internal data class ApiFootballEventPersonDto(val id: Int? = null, val name: String? = null)

internal fun ApiFootballEventDto.toMatchEvent(): MatchEvent {
    val minuteLabel = buildMinuteLabel(time.elapsed, time.extra)
    val playerName = player?.name?.takeIf { it.isNotBlank() }
    val assistName = assist?.name?.takeIf { it.isNotBlank() }
    return when (type) {
        // Unlike the "Card" case below, a real "Goal" event's `headline` was never actually
        // prefixed with "Goal" — it just showed the bare scorer name, which reads fine in
        // isolation but doesn't scan as a distinct event type next to "Yellow Card — X" rows in
        // the Timeline. Fixed to match the Card row's "<label> — <player>" shape.
        "Goal" -> {
            val scorer = playerName ?: detail?.takeIf { it.isNotBlank() } ?: "Goal"
            // API-Football's documented `detail` values for Goal events include "Penalty" and
            // "Own Goal" alongside the common "Normal Goal" — only "Normal Goal"-shaped events
            // were seen in this session's real curl responses, so treating anything other than
            // "Normal Goal" as a qualifier is a defensive mapping, not one independently verified
            // against a real penalty/own-goal response.
            val qualifier = detail?.takeIf { it.isNotBlank() && !it.equals("Normal Goal", ignoreCase = true) }
            MatchEvent(
                minuteLabel = minuteLabel,
                type = MatchEventType.GOAL,
                teamName = team.name,
                headline = if (qualifier != null) "Goal ($qualifier) — $scorer" else "Goal — $scorer",
                subtext = assistName?.let { "Assist: $it" },
                scorer = scorer,
                scorerQualifier = qualifier,
            )
        }
        // player = player going OFF, assist = player coming ON — a real inversion of the "Goal"
        // case's field meanings, confirmed against real events (not documentation).
        "subst" -> MatchEvent(
            minuteLabel = minuteLabel,
            type = MatchEventType.SUBSTITUTION,
            teamName = team.name,
            headline = assistName?.let { "$it on" } ?: "Substitution",
            subtext = playerName?.let { "$it off" },
        )
        "Card" -> MatchEvent(
            minuteLabel = minuteLabel,
            type = MatchEventType.CARD,
            teamName = team.name,
            headline = listOfNotNull(detail, playerName).joinToString(" — ").ifBlank { "Card" },
            subtext = comments,
        )
        "Var" -> MatchEvent(
            minuteLabel = minuteLabel,
            type = MatchEventType.VAR,
            teamName = team.name,
            headline = detail ?: "VAR Review",
            subtext = comments,
        )
        else -> MatchEvent(
            minuteLabel = minuteLabel,
            type = MatchEventType.OTHER,
            teamName = team.name,
            headline = detail ?: type.ifBlank { "Event" },
            subtext = comments,
        )
    }
}

private fun buildMinuteLabel(elapsed: Int, extra: Int?): String =
    if (extra != null && extra > 0) "$elapsed+$extra'" else "$elapsed'"

// --- Wire format (API-Football /fixtures/statistics response) -----------------
//
// Confirmed against a real `GET /fixtures/statistics?fixture=1035037` response: two team objects,
// each `{team, statistics:[{type,value}]}`. `value` is genuinely heterogeneous — plain int,
// percentage string ("66%"), decimal string ("2.08" for expected_goals), and `null` (confirmed
// meaning zero: both teams showed null for "Yellow Cards" in a match with no cards). Modeled as
// raw [JsonElement] rather than a fixed type so decoding can't throw on whichever shape shows up
// for a given `type` — [toStatDisplay] handles the null-as-zero case, [JsonPrimitive.content]
// handles the rest uniformly whether the primitive is a quoted string or a bare number.
//
// This deliberately doesn't hardcode a curated allow-list/order of stat `type` names (the way the
// ESPN variant of this tool does for its own stat keys) — only "Yellow Cards" and "expected_goals"
// were actually seen in a real response this session, and guessing the rest of API-Football's
// stat-type vocabulary from memory risks silently dropping real stats a live match sends that
// weren't in the guess. [toMatchStatRows] instead renders whatever `type`s are present, in the
// order the API returns them, keyed by matching `type` string across the two team blocks. Revisit
// once Phase 3's real live-match calls show the full set — a curated order (like the ESPN
// version's `BOXSCORE_STAT_ORDER`) is a reasonable follow-up once that's known instead of assumed.

@Serializable
internal data class ApiFootballStatisticsResponse(
    val response: List<ApiFootballTeamStatisticsDto> = emptyList(),
)

@Serializable
internal data class ApiFootballTeamStatisticsDto(
    val team: ApiFootballEventTeamDto,
    val statistics: List<ApiFootballStatDto> = emptyList(),
)

@Serializable
internal data class ApiFootballStatDto(
    val type: String = "",
    val value: JsonElement = JsonNull,
)

internal fun JsonElement.toStatDisplay(): String =
    if (this is JsonNull) "0" else (this as? JsonPrimitive)?.content ?: "0"

internal fun List<ApiFootballTeamStatisticsDto>.toMatchStatRows(homeTeamId: Int, awayTeamId: Int): List<MatchStatRow> {
    val home = firstOrNull { it.team.id == homeTeamId }?.statistics.orEmpty()
    val away = firstOrNull { it.team.id == awayTeamId }?.statistics.orEmpty()
    val awayByType = away.associateBy { it.type }
    return home.mapNotNull { h ->
        val a = awayByType[h.type] ?: return@mapNotNull null
        MatchStatRow(label = h.type, homeValue = h.value.toStatDisplay(), awayValue = a.value.toStatDisplay())
    }
}

// --- Wire format (API-Football /fixtures/lineups response) --------------------
//
// Confirmed against a real `GET /fixtures/lineups?fixture=1035037` response: two team objects with
// `formation`, `startXI`/`substitutes` (each `{player: {id,name,number,pos,grid}}`), `coach`, kit
// colors (not modeled here — LightOS has no image/color rendering for this). `grid` is a real
// `"row:col"` pitch position from API-Football itself (row 1 = goalkeeper, increasing toward
// attack) — unlike the ESPN variant of this tool, which had to *infer* a formation from bare
// position codes because ESPN sends no such field. `grid` is null for substitutes.

@Serializable
internal data class ApiFootballLineupsResponse(
    val response: List<ApiFootballTeamLineupDto> = emptyList(),
)

@Serializable
internal data class ApiFootballTeamLineupDto(
    val team: ApiFootballEventTeamDto,
    val formation: String? = null,
    val startXI: List<ApiFootballLineupSlotDto> = emptyList(),
    val substitutes: List<ApiFootballLineupSlotDto> = emptyList(),
    val coach: ApiFootballCoachDto? = null,
)

@Serializable
internal data class ApiFootballLineupSlotDto(val player: ApiFootballLineupPlayerDto)

@Serializable
internal data class ApiFootballLineupPlayerDto(
    val id: Int? = null,
    val name: String = "",
    val number: Int? = null,
    val pos: String? = null,
    val grid: String? = null,
)

@Serializable
internal data class ApiFootballCoachDto(val name: String? = null)

internal fun ApiFootballLineupsResponse.toMatchLineups(homeTeamId: Int, awayTeamId: Int): MatchLineups = MatchLineups(
    home = response.firstOrNull { it.team.id == homeTeamId }?.toTeamLineup(),
    away = response.firstOrNull { it.team.id == awayTeamId }?.toTeamLineup(),
)

private fun ApiFootballTeamLineupDto.toTeamLineup(): TeamLineup = TeamLineup(
    teamName = team.name,
    formation = formation,
    startXI = startXI.map { it.player.toLineupPlayer() },
    substitutes = substitutes.map { it.player.toLineupPlayer() },
    coachName = coach?.name?.takeIf { it.isNotBlank() },
)

private fun ApiFootballLineupPlayerDto.toLineupPlayer(): LineupPlayer = LineupPlayer(
    name = name,
    number = number,
    position = pos,
    grid = grid,
)

// --- Wire format (API-Football /standings response) ---------------------------
//
// Confirmed against two real responses this session: `GET /standings?league=39&season=2023`
// (Premier League — `league.standings` is a single 20-row array) and
// `GET /standings?league=2&season=2023` (UEFA Champions League — `league.standings` is 8 separate
// 4-row arrays, one per group). [toStandingsRows] flattens either shape into one row list, tagging
// each row's [StandingsRow.group] only when the source had more than one inner array — a flat
// domestic league's rows all come out with `group = null`.

@Serializable
internal data class ApiFootballStandingsResponse(
    val response: List<ApiFootballStandingsLeagueWrapperDto> = emptyList(),
)

@Serializable
internal data class ApiFootballStandingsLeagueWrapperDto(val league: ApiFootballStandingsLeagueDto)

@Serializable
internal data class ApiFootballStandingsLeagueDto(
    val id: Int,
    val name: String = "",
    val standings: List<List<ApiFootballStandingsRowDto>> = emptyList(),
)

@Serializable
internal data class ApiFootballStandingsRowDto(
    val rank: Int,
    val team: ApiFootballEventTeamDto,
    val points: Int = 0,
    val goalsDiff: Int = 0,
    val group: String? = null,
    val form: String? = null,
    val all: ApiFootballStandingsStatsDto = ApiFootballStandingsStatsDto(),
)

@Serializable
internal data class ApiFootballStandingsStatsDto(
    val played: Int = 0,
    val win: Int = 0,
    val draw: Int = 0,
    val lose: Int = 0,
    val goals: ApiFootballGoalsDto = ApiFootballGoalsDto(),
)

internal fun ApiFootballStandingsLeagueDto.toStandingsRows(): List<StandingsRow> {
    val isGrouped = standings.size > 1
    return standings.flatten().map { it.toStandingsRow(isGrouped) }
}

private fun ApiFootballStandingsRowDto.toStandingsRow(isGrouped: Boolean): StandingsRow = StandingsRow(
    position = rank,
    teamId = team.id,
    teamName = team.name,
    played = all.played,
    win = all.win,
    draw = all.draw,
    lose = all.lose,
    goalsFor = all.goals.home ?: 0,
    goalsAgainst = all.goals.away ?: 0,
    goalDifference = goalsDiff,
    points = points,
    form = form?.takeIf { it.isNotBlank() },
    group = if (isGrouped) group else null,
)

// --- Wire format (API-Football /injuries response) ----------------------------
//
// Confirmed against two real responses: `GET /injuries?fixture=1035037` (1 row) and
// `GET /injuries?league=39&season=2023&team=50` (74 rows). Two gotchas that shape everything
// downstream of this DTO:
//
// 1. This is a season-long log, not a "current state" snapshot — a team+season query returns one
//    row per fixture that player was unavailable for across the whole season, not "who's out right
//    now". There's no API-Football call that answers "current" directly; getting that requires
//    filtering this log down to rows tied to one specific fixture (typically the team's next
//    upcoming one) — see `ApiFootballApi.fetchUnavailableForFixture`.
// 2. Despite the endpoint's name, this is really an "unavailability" log, not strictly an injury
//    one: `reason` includes real injuries ("Thigh Injury", "Illness") but also suspension causes
//    ("Red Card", "Yellow Cards"). [toUnavailablePlayer] splits on that rather than labeling
//    everything here "injured".

@Serializable
internal data class ApiFootballInjuriesResponse(
    val response: List<ApiFootballInjuryDto> = emptyList(),
)

@Serializable
internal data class ApiFootballInjuryDto(
    val player: ApiFootballInjuryPlayerDto,
    val team: ApiFootballEventTeamDto,
    val fixture: ApiFootballInjuryFixtureDto,
)

@Serializable
internal data class ApiFootballInjuryPlayerDto(
    val id: Int? = null,
    val name: String = "",
    /** "Missing Fixture" or "Questionable" — not a boolean, see [UnavailablePlayer.isOut]. */
    val type: String = "",
    val reason: String = "",
)

@Serializable
internal data class ApiFootballInjuryFixtureDto(
    val id: Int,
    val date: String = "",
    val timestamp: Long = 0,
)

private val SUSPENSION_REASON_MARKERS = listOf("card", "suspen")

internal fun ApiFootballInjuryDto.toUnavailablePlayer(): UnavailablePlayer {
    val reasonLower = player.reason.lowercase()
    val kind = if (SUSPENSION_REASON_MARKERS.any { it in reasonLower }) {
        UnavailabilityKind.SUSPENDED
    } else {
        UnavailabilityKind.INJURED
    }
    return UnavailablePlayer(
        playerName = player.name,
        kind = kind,
        reason = player.reason.ifBlank { "Unavailable" },
        isOut = player.type == "Missing Fixture",
    )
}

// --- Domain model ---------------------------------------------------------------

enum class MatchStatus {
    SCHEDULED,
    IN_PLAY,
    HALFTIME,
    FINISHED,
    POSTPONED,
    CANCELLED,
    SUSPENDED,
    UNKNOWN;

    val isLive: Boolean get() = this == IN_PLAY || this == HALFTIME
}

private val LIVE_STATUS_CODES = setOf("1H", "2H", "ET", "P", "BT", "LIVE")

@Serializable
data class Fixture(
    val id: Int,
    val utcDate: String,
    /** API-Football's own status.short, e.g. "FT", "NS", "1H" — the source [status] derives from.
     * Only "FT" has been seen in a real response this session (see [MatchStatus]'s doc comment). */
    val statusShort: String,
    val statusLong: String,
    val elapsed: Int?,
    val extraMinutes: Int?,
    val leagueId: Int,
    val leagueName: String,
    val round: String,
    val homeTeamId: Int,
    val homeTeamName: String,
    val homeTeamLogo: String = "",
    val awayTeamId: Int,
    val awayTeamName: String,
    val awayTeamLogo: String = "",
    val homeGoals: Int? = null,
    val awayGoals: Int? = null,
) {
    val status: MatchStatus
        get() = when (statusShort) {
            "NS", "TBD" -> MatchStatus.SCHEDULED
            "HT" -> MatchStatus.HALFTIME
            in LIVE_STATUS_CODES -> MatchStatus.IN_PLAY
            "FT", "AET", "PEN" -> MatchStatus.FINISHED
            "PST" -> MatchStatus.POSTPONED
            "CANC", "ABD" -> MatchStatus.CANCELLED
            "SUSP", "INT" -> MatchStatus.SUSPENDED
            else -> MatchStatus.UNKNOWN
        }

    val hasScore: Boolean get() = statusShort != "NS" && statusShort != "TBD"
}

data class CompetitionGroup(val leagueId: Int, val leagueName: String, val matches: List<Fixture>)

fun List<Fixture>.groupedForDisplay(): List<CompetitionGroup> = this
    .groupBy { it.leagueId to it.leagueName }
    .map { (key, matches) -> CompetitionGroup(key.first, key.second, matches.sortedBy { it.utcDate }) }
    .sortedBy { COMPETITION_DISPLAY_ORDER[it.leagueId] ?: Int.MAX_VALUE }

data class FixtureDateGroup(val date: LocalDate, val dateLabel: String, val matches: List<Fixture>)

fun List<Fixture>.groupedByDate(): List<FixtureDateGroup> = this
    .mapNotNull { match -> match.localDate()?.let { it to match } }
    .sortedBy { it.second.utcDate }
    .groupBy { it.first }
    .map { (date, pairs) -> FixtureDateGroup(date, formatFixtureDateHeader(date), pairs.map { it.second }) }
    .sortedBy { it.date }

/**
 * One team's row in a league table (flat) or one group's table (UCL/Europa League) — [group] is
 * non-null only for the latter, see [ApiFootballStandingsLeagueDto.toStandingsRows].
 */
data class StandingsRow(
    val position: Int,
    val teamId: Int,
    val teamName: String,
    val played: Int,
    val win: Int,
    val draw: Int,
    val lose: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val goalDifference: Int,
    val points: Int,
    /** Recent-form string, e.g. "WWDLW" — most recent last, per API-Football's own ordering. */
    val form: String?,
    val group: String?,
)

enum class MatchEventType { GOAL, SUBSTITUTION, CARD, VAR, OTHER }

/** One row of the match detail screen's Event Timeline tab — see [ApiFootballEventDto.toMatchEvent]
 * for how each raw event `type` maps to [headline]/[subtext]. [scorer]/[scorerQualifier] are set
 * only for [MatchEventType.GOAL] events, for the compact goalscorer strip shown under the score
 * (see [MatchDetailHeader]) — kept separate from [headline] since that strip wants just the
 * player's name, not the fuller "Goal — Name" text the Timeline tab shows. */
data class MatchEvent(
    val minuteLabel: String,
    val type: MatchEventType,
    val teamName: String,
    val headline: String,
    val subtext: String?,
    val scorer: String? = null,
    val scorerQualifier: String? = null,
)

data class MatchStatRow(val label: String, val homeValue: String, val awayValue: String)

data class LineupPlayer(
    val name: String,
    val number: Int?,
    /** Broad position code API-Football sends, e.g. "G", "D", "M", "F". */
    val position: String?,
    /** "row:col" pitch position — row 1 is the goalkeeper, increasing rows move toward attack.
     * Null for substitutes (API-Football sends no grid for the bench). */
    val grid: String?,
)

data class TeamLineup(
    val teamName: String,
    val formation: String?,
    val startXI: List<LineupPlayer>,
    val substitutes: List<LineupPlayer>,
    val coachName: String?,
)

data class MatchLineups(val home: TeamLineup?, val away: TeamLineup?)

/** Groups [TeamLineup.startXI] by [LineupPlayer.grid] row, sorted within each row by column, and
 * returns rows ordered goalkeeper-first (row 1) to most advanced last — reverse this list when
 * rendering a "forwards at the top" pitch view, same convention the ESPN variant of this tool
 * uses. Players with no grid (shouldn't happen for a real startXI row) are dropped rather than
 * risking a garbage row. */
fun List<LineupPlayer>.groupedByPitchRow(): List<List<LineupPlayer>> = this
    .filter { it.grid != null }
    .groupBy { it.grid!!.substringBefore(':').toIntOrNull() ?: 0 }
    .toSortedMap()
    .map { (_, players) -> players.sortedBy { it.grid!!.substringAfter(':').toIntOrNull() ?: 0 } }

/** The match detail screen's four tabs' worth of content — Stats, Event Timeline, Home Lineup,
 * Away Lineup all fold into this one bundle since they're fetched together (three separate
 * API-Football calls: events, statistics, lineups). Any section can come back empty for a match
 * that hasn't kicked off — the screen omits an empty section/tab-content rather than showing it
 * blank. */
data class MatchDetail(
    val stats: List<MatchStatRow>,
    val events: List<MatchEvent>,
    val lineups: MatchLineups,
)

enum class UnavailabilityKind { INJURED, SUSPENDED }

/** One row of My Team's "Unavailable" section — split into [UnavailabilityKind.INJURED] and
 * [UnavailabilityKind.SUSPENDED] buckets rather than one flat "injuries" list, since
 * API-Football's /injuries endpoint conflates both under one `reason` field (see the doc comment
 * on [ApiFootballInjuryDto]). */
data class UnavailablePlayer(
    val playerName: String,
    val kind: UnavailabilityKind,
    val reason: String,
    /** true = "Missing Fixture" (ruled out), false = "Questionable" (doubtful). */
    val isOut: Boolean,
)

/** My Team's screen content for one followed team: its league position, its next few and most
 * recent fixtures, and who's unavailable for its next match. [standingsRow] is null if the
 * followed league's standings haven't loaded or the team isn't in this competition's table (a
 * team followed from a non-domestic-league context, or a standings fetch failure). */
data class MyTeamSummary(
    val teamId: Int,
    val teamName: String,
    val leagueId: Int,
    val leagueName: String,
    val standingsRow: StandingsRow?,
    val upcomingFixtures: List<Fixture>,
    val recentFixtures: List<Fixture>,
    val unavailable: List<UnavailablePlayer>,
    /** Raw crest image bytes for [teamId], already fetched — the Light SDK's dependency allow-list
     * blocks every third-party image-loading library (confirmed against a real build failure:
     * `LightSdkPlugin` rejected `io.coil-kt.coil3:*` outright), so there's no `AsyncImage`-style
     * lazy loader available here. The crest URL itself comes from whichever of
     * [upcomingFixtures]/[recentFixtures] mentions [teamId] first (no dedicated "team" endpoint call
     * — see [ApiFootballApi.fetchMyTeamSummary]), fetched eagerly with the same Ktor client
     * everything else in this file uses. Null if no logo URL was found, or the fetch failed. */
    val teamLogoBytes: ByteArray?,
)
