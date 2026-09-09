package com.thelightphone.soccerfootball

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/**
 * The competitions this tool tracks. IDs are API-Football's own numeric league IDs (a documented,
 * stable API — unlike the ESPN/FotMob variants of this tool, so these aren't reverse-engineered).
 * [hasStandings] is false for single-elimination domestic cups, which have no league table —
 * API-Football's `/standings` legitimately returns nothing for them, so they're excluded from the
 * Standings and My Team league pickers (see `SoccerViewModel.followedTableCompetitions`) while
 * still showing up normally in Scores/Fixtures.
 *
 * Verification status, three tiers (see the module README's "Data source" section for the full
 * writeup) — a wrong ID here isn't silently dangerous either way: the proxy's own league whitelist
 * (`soccer-pro-proxy`'s `app/config.py`) has to list the same ID before any request for it
 * succeeds at all, and the first real request against a wrong ID either errors or comes back as
 * an obviously different competition's real teams:
 *
 * - **Curl-confirmed against a real response**, this project's own history: 39 (Premier League),
 *   2 (UEFA Champions League).
 * - **Corroborated by two independent sources** (general knowledge plus a public GitHub reference
 *   listing API-Football's commonly-used IDs) but not curl-tested against a live response: 140
 *   (La Liga), 135 (Serie A), 78 (Bundesliga), 61 (Ligue 1), 3 (UEFA Europa League), 45 (FA Cup),
 *   143 (Copa del Rey), 137 (Coppa Italia), 81 (DFB-Pokal), 66 (Coupe de France).
 * - **Recalled from general knowledge only, no independent source found** — the riskiest two left,
 *   worth checking first once live: 40 (Championship), 48 (EFL Cup). MLS (253) used to sit in this
 *   same tier; dropped from [TRACKED_COMPETITIONS] entirely on request rather than re-verified —
 *   see that val's doc comment.
 */
data class Competition(
    val id: Int,
    val name: String,
    // Defaults to the full name — only overridden below for names long enough to be worth
    // shortening. Backs the per-match league label on the merged Scores/schedule list's second
    // line (see MatchRow's font-size-audit entry on the day-card redesign) — plain well-known
    // abbreviations (EPL, UCL, UEL), not confirmed against how this app's own font actually
    // measures them since there's no compiler/emulator in this sandbox to render-test against;
    // worth a real look once built.
    val shortName: String = name,
    val hasStandings: Boolean = true,
    // False only for the two UEFA competitions below. Backs [competitionIsDomestic] — used to hide
    // My Team's league-rank line for a team followed from a continental context (see
    // MyTeamHeaderRow's doc comment in SoccerHomeScreen.kt): a UCL/UEL group-stage position reads
    // as noise next to the domestic-table rank the request actually wants there.
    val isDomestic: Boolean = true,
)

// MLS (id 253) was tracked here through an earlier round, in the "recalled from general knowledge
// only" (least-verified) ID tier above, and ran into a real standings-parsing bug live (see
// SoccerViewModel.apiErrorMessage's doc comment on "the MLS standings bug"). Dropped entirely on
// request rather than re-verified or fixed. Safe to remove outright: SoccerViewModel.selectedIds
// is read from persisted prefs and filtered against this list (`TRACKED_COMPETITIONS.filter { it.id
// in selectedIds }`), so a device that had MLS selected before this change just silently stops
// seeing it — no migration needed, nothing left dangling.
val TRACKED_COMPETITIONS: List<Competition> = listOf(
    // England
    Competition(id = 39, name = "Premier League", shortName = "EPL"),
    Competition(id = 40, name = "Championship"),
    Competition(id = 45, name = "FA Cup", hasStandings = false),
    Competition(id = 48, name = "EFL Cup", hasStandings = false),
    // Italy
    Competition(id = 135, name = "Serie A"),
    Competition(id = 137, name = "Coppa Italia", hasStandings = false),
    // Spain
    Competition(id = 140, name = "La Liga"),
    Competition(id = 143, name = "Copa del Rey", hasStandings = false),
    // Germany
    Competition(id = 78, name = "Bundesliga"),
    Competition(id = 81, name = "DFB-Pokal", hasStandings = false),
    // France
    Competition(id = 61, name = "Ligue 1"),
    Competition(id = 66, name = "Coupe de France", shortName = "Coupe Fr."),
    // Europe
    Competition(id = 2, name = "UEFA Champions League", shortName = "UCL", isDomestic = false),
    Competition(id = 3, name = "UEFA Europa League", shortName = "UEL", isDomestic = false),
)

/**
 * Competitions whose `/standings` response splits into multiple group arrays instead of one flat
 * table (confirmed for id 2 / UEFA Champions League this session — 8 groups of 4 — see
 * [ApiFootballStandingsLeagueDto.standings]'s doc comment). Id 3 / Europa League is assumed to
 * share UEFA's group-stage format. Not independently confirmed —
 * [toStandingsRows] doesn't actually need this set (it detects grouping directly from the response
 * shape, i.e. `standings.size > 1`), so a wrong assumption here costs nothing; this is just
 * documentation of what's expected going in. The knockout cups in [TRACKED_COMPETITIONS] aren't
 * here at all — they have no standings response to be grouped or flat in the first place (see
 * [Competition.hasStandings]).
 */
val GROUP_STAGE_COMPETITION_IDS: Set<Int> = setOf(2, 3)

// Used as a tiebreaker for same-kickoff-time matches in [groupedByDate] below, and — on request,
// after Scores went back to grouping each day's matches by league within the day card (see
// ScheduleDayCard's doc comment in SoccerHomeScreen.kt) — as the order those per-day league groups
// render in, via [competitionDisplayOrder].
private val COMPETITION_DISPLAY_ORDER: Map<Int, Int> =
    TRACKED_COMPETITIONS.mapIndexed { index, c -> c.id to index }.toMap()
private val COMPETITION_NAMES: Map<Int, String> = TRACKED_COMPETITIONS.associate { it.id to it.name }
private val COMPETITION_SHORT_NAMES: Map<Int, String> = TRACKED_COMPETITIONS.associate { it.id to it.shortName }

fun competitionName(id: Int): String = COMPETITION_NAMES[id] ?: "League $id"

/** [Competition.shortName] by id, falling back to the full name for a league id this app doesn't
 * track (shouldn't happen in practice — every [Fixture.leagueId] on screen came from a followed,
 * therefore tracked, competition — but matches [competitionName]'s own fallback rather than risking
 * a blank label). */
fun competitionShortName(id: Int): String = COMPETITION_SHORT_NAMES[id] ?: competitionName(id)

/** Whether [id] has a real league table — false for single-elimination cups (see [Competition.hasStandings]).
 * Used to gate the Scores screen's league-logo-tap navigation so tapping a cup's logo doesn't try
 * to open a standings table that doesn't exist. */
fun competitionHasStandings(id: Int): Boolean = TRACKED_COMPETITIONS.firstOrNull { it.id == id }?.hasStandings ?: false

/** Whether [id] is a domestic league rather than a UEFA continental competition (see
 * [Competition.isDomestic]). Defaults true for an untracked id, same fallback reasoning as
 * [competitionName]/[competitionShortName] — every real [MyTeamSummary.leagueId] on screen came
 * from [TRACKED_COMPETITIONS], so this only matters if that ever stops being true. */
fun competitionIsDomestic(id: Int): Boolean = TRACKED_COMPETITIONS.firstOrNull { it.id == id }?.isDomestic ?: true

/** [TRACKED_COMPETITIONS]' own declared order for league id — the same fixed preference order this
 * app has used since before the Scores/Fixtures merge (see [COMPETITION_DISPLAY_ORDER]'s doc
 * comment), exposed here for `ScheduleDayCard`'s per-day league-group ordering (SoccerHomeScreen.kt).
 * A followed league not found in [TRACKED_COMPETITIONS] (shouldn't happen — see
 * [competitionShortName]'s same fallback reasoning) sorts last rather than crashing. */
fun competitionDisplayOrder(id: Int): Int = COMPETITION_DISPLAY_ORDER[id] ?: Int.MAX_VALUE

// Shorter display names for teams likely to truncate on Scores' combined "Home - Away" line (see
// ScheduleMatchRow's doc comment in SoccerHomeScreen.kt), also used by the Standings table (see
// StandingsTableRow in SoccerHomeScreen.kt).
//
// Rebuilt from scratch this round on a completely different, more reliable process than the
// original version of this map: rather than guessing keys from Wikipedia's official club names
// (which turned out to frequently NOT be the string API-Football actually sends — "Swansea", not
// "Swansea City"; "Tottenham", not "Tottenham Hotspur"; "Athletic Club", not "Athletic Bilbao"; and
// dozens more like it, discovered piecemeal over several rounds of this map shipping broken), every
// key below is copied verbatim from a real `GET /standings` response — the user ran a small curl
// script against this app's own proxy (soccer-proxy.ravisolter.com) for all 8 tracked competitions
// (Premier League, Championship, Serie A, La Liga, Bundesliga, Ligue 1, Champions League, Europa
// League) and shared the raw JSON. All 159 distinct team names that came back were compiled into a
// CSV (existing map status + a suggested short name for each), the user reviewed and edited it, and
// this map is built directly from their approved suggestions. Four suggestions were declined
// outright (Tottenham, Celta Vigo, Galatasaray stay full-length; Deportivo La Coruna's suggested
// short name became "Deportivo" instead of "Depor") — see that CSV for the full picture, including
// every team that's ALREADY short enough on its own and so has no entry here (most of them: 97 of
// the 159 real names needed no shortening at all). Juventus, Barcelona, and Marseille were declined
// in an earlier round on the same basis and are still deliberately absent.
//
// Because every key here is a real, confirmed string, this map should be genuinely reliable now —
// not just "hopefully right" the way the Wikipedia-sourced version was. The tradeoff: it only covers
// teams that were actually in one of those 8 competitions' live tables at fetch time (2026-09-08).
// A team previously mapped here that didn't appear in that fetch (Hellas Verona, Leicester City,
// Ipswich Town, Oxford United, VfL Wolfsburg, FC St. Pauli, 1. FC Heidenheim, AFC Ajax, BSC Young
// Boys, FC Basel, Crvena Zvezda, and a few more) has been dropped rather than carried forward
// unconfirmed — same reasoning as declining to guess in the first place: an unconfirmed key is
// worse than no key, since a wrong one silently never fires while looking like it should. Any of
// those teams just render unshortened (safe, same fallback as always) until they show up in a
// future re-pull of this same process and get a real, confirmed entry.
//
// Domestic cup fixtures (FA Cup, EFL Cup, Copa del Rey, Coppa Italia, DFB-Pokal, Coupe de France) can
// pull in lower-league and non-league clubs entirely outside this map — the original "Carshalton
// Athletic" truncation case this whole effort traces back to — and that's still out of scope by
// design: a live FA Cup fixtures fetch confirmed that pool is qualifying-round non-league football
// (Aveley vs Cheshunt, Ossett United vs Pontefract Collieries, 150+ more like them), far too large
// and short-lived a season to hand-cover, and every name in it was already short on its own anyway.
// A cup fixture between two clubs this map DOES cover (e.g. two Championship sides) still gets
// shortened today, for free — the lookup is by team name, not by which competition it's in.
private val TEAM_SHORT_NAMES: Map<String, String> = mapOf(
    // Premier League
    "Aston Villa" to "Villa",
    "Crystal Palace" to "Palace",
    "Hull City" to "Hull",
    "Manchester City" to "Man City",
    "Manchester United" to "Man Utd",
    "Nottingham Forest" to "Nott'm Forest",
    // Championship
    "Middlesbrough" to "Boro",
    "Sheffield Utd" to "Sheff Utd",
    "Stoke City" to "Stoke",
    // Serie A
    "AC Milan" to "Milan",
    "AS Roma" to "Roma",
    // La Liga
    "Athletic Club" to "Athletic",
    "Atletico Madrid" to "Atletico",
    "Deportivo La Coruna" to "Deportivo",
    "Racing Santander" to "Racing",
    "Rayo Vallecano" to "Rayo",
    "Real Betis" to "Betis",
    "Real Sociedad" to "Sociedad",
    // Bundesliga
    "1. FC Köln" to "Köln",
    "1899 Hoffenheim" to "Hoffenheim",
    "Bayer Leverkusen" to "Leverkusen",
    "Bayern München" to "Bayern",
    "Borussia Dortmund" to "Dortmund",
    "Borussia Mönchengladbach" to "Gladbach",
    "Eintracht Frankfurt" to "Frankfurt",
    "FC Augsburg" to "Augsburg",
    "FC Schalke 04" to "Schalke",
    "FSV Mainz 05" to "Mainz",
    "Hamburger SV" to "HSV",
    "RB Leipzig" to "Leipzig",
    "SC Freiburg" to "Freiburg",
    "SC Paderborn 07" to "Paderborn",
    "SV Elversberg" to "Elversberg",
    "VfB Stuttgart" to "Stuttgart",
    "Werder Bremen" to "Werder",
    // Ligue 1
    "Estac Troyes" to "Troyes",
    "Paris Saint Germain" to "PSG",
    "Stade Brestois 29" to "Brest",
    // UEFA Champions League
    "AEK Athens FC" to "AEK Athens",
    "Club Brugge KV" to "Club Brugge",
    "FC Porto" to "Porto",
    "Fenerbahçe" to "Fenerbahce",
    "Lask Linz" to "LASK",
    "PSV Eindhoven" to "PSV",
    "Shakhtar Donetsk" to "Shakhtar",
    "Slavia Praha" to "Slavia",
    "Slovan Bratislava" to "Slovan",
    "Sporting CP" to "Sporting",
    // UEFA Europa League
    "AZ Alkmaar" to "AZ",
    "Ararat-Armenia" to "Ararat",
    "Beşiktaş" to "Besiktas",
    "Ferencvarosi TC" to "Ferencvaros",
    "Hapoel Beer Sheva" to "Beer Sheva",
    "Jagiellonia" to "Jaga",
    "Lech Poznan" to "Lech",
    "Levski Sofia" to "Levski",
    "NEC Nijmegen" to "NEC",
    "Olympiakos Piraeus" to "Olympiakos",
    "Omonia Nicosia" to "Omonia",
    "Red Bull Salzburg" to "Salzburg",
    "Sparta Praha" to "Sparta",
    "Union St. Gilloise" to "Union SG",
)

/** Shorter display name for [fullName], if this app has one — otherwise [fullName] itself,
 * unshortened. See [TEAM_SHORT_NAMES]'s doc comment for how this list was built (real API-Football
 * strings this time, not guessed from Wikipedia) and why domestic-cup non-league clubs aren't
 * covered. */
fun teamShortName(fullName: String): String = TEAM_SHORT_NAMES[fullName] ?: fullName

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
    /** Hosted PNG league badge — curl-verified present on a real `/fixtures` response this session
     * (`GET /fixtures?league=39&season=2026&from=2026-08-15&to=2026-09-06`). The same response also
     * has a `flag` field (a country flag, not the league badge) that's deliberately NOT modeled here:
     * it's an `.svg` URL, and this app has no SVG decode path — [BitmapFactory] only handles raster
     * formats, and pulling in an SVG-rendering library isn't an option given the Light SDK's
     * dependency allow-list. That's a real format limitation, not an oversight. */
    val logo: String = "",
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
    leagueLogo = league.logo,
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

// Curated display order, on request ("reorder the stats to resemble the screenshots provided") —
// the screenshots given were from a different, richer provider (Sofascore-style: xG, xGOT, "big
// chances", "duels", an attacking-zones map) whose stat set this API doesn't return at all, so this
// isn't a literal copy of those rows. It's this app's best approximation of the same overall
// reading order those screenshots showed — an attacking/shots summary first, then passing, then
// fouls/set-pieces, then goalkeeping, discipline last — built against API-Football's own documented
// `type` strings for `/fixtures/statistics`, of which only "Yellow Cards" and "expected_goals" have
// actually been seen in a real response this session (see this file's wire-format doc comment
// above). The rest (Ball Possession, Total/on-Goal/off-Goal/Blocked/insidebox/outsidebox Shots,
// Total passes/Passes accurate/Passes %, Fouls, Corner Kicks, Offsides, Goalkeeper Saves, Red Cards)
// are recalled from API-Football's public docs, not independently curl-verified — worth checking
// once this can actually be built and run against a live match. A `type` not in this map (including
// any spelled differently than guessed here) still renders, just after everything that IS mapped,
// in whatever order the API sent it — this never drops a real stat over an unrecognized label.
private val STAT_DISPLAY_ORDER: Map<String, Int> = listOf(
    "Ball Possession",
    "expected_goals",
    "Total Shots",
    "Shots on Goal",
    "Shots off Goal",
    "Blocked Shots",
    "Shots insidebox",
    "Shots outsidebox",
    "Total passes",
    "Passes accurate",
    "Passes %",
    "Fouls",
    "Corner Kicks",
    "Offsides",
    "Goalkeeper Saves",
    "Yellow Cards",
    "Red Cards",
).withIndex().associate { (index, type) -> type to index }

internal fun List<ApiFootballTeamStatisticsDto>.toMatchStatRows(homeTeamId: Int, awayTeamId: Int): List<MatchStatRow> {
    val home = firstOrNull { it.team.id == homeTeamId }?.statistics.orEmpty()
    val away = firstOrNull { it.team.id == awayTeamId }?.statistics.orEmpty()
    val awayByType = away.associateBy { it.type }
    return home.mapNotNull { h ->
        val a = awayByType[h.type] ?: return@mapNotNull null
        MatchStatRow(label = h.type, homeValue = h.value.toStatDisplay(), awayValue = a.value.toStatDisplay())
    }
        // sortedBy is stable, so any type missing from STAT_DISPLAY_ORDER (rank Int.MAX_VALUE) keeps
        // its original relative position among other unmapped types, at the end of the list.
        .sortedBy { STAT_DISPLAY_ORDER[it.label] ?: Int.MAX_VALUE }
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

/** `photo` confirmed present on a real `GET /fixtures/lineups?fixture=1035037` response this
 * session (curl-verified, at the user's request) as a ready-to-use full URL
 * (`https://media.api-sports.io/football/coachs/{id}.png`), not something this app needs to
 * construct from [id] itself. Kit `colors` was checked too, more than once: an initial single-fixture
 * curl came back `colors: null`, and a follow-up sweep across 6 genuinely finished fixtures
 * (2 each in Premier League, Serie A, and Champions League) came back `colors: null` for all 12
 * team entries. This proxy/API tier simply doesn't populate that field for the leagues this app
 * tracks, so it isn't modeled here; this app's crest-sampled team color (see
 * extractCrestAccentColor in SoccerHomeScreen.kt) stays the only source for that, not a real one
 * that just wasn't wired up. */
@Serializable
internal data class ApiFootballCoachDto(val id: Int? = null, val name: String? = null, val photo: String? = null)

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
    coachPhotoUrl = coach?.photo?.takeIf { it.isNotBlank() },
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
    /** Same hosted PNG league badge as [ApiFootballFixtureLeagueDto.logo] — curl-verified present
     * on a real `/standings` response this session too (`GET /standings?league=39&season=2026`),
     * not just assumed to carry over from the fixtures shape. Same `flag`-is-SVG caveat applies;
     * see that field's doc comment for why `flag` isn't modeled here either. */
    val logo: String = "",
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
    val goals: ApiFootballStandingsGoalsDto = ApiFootballStandingsGoalsDto(),
)

/** Standings' own `goals` shape — `{"for": N, "against": N}`, a season total — which is NOT the
 * same shape as [ApiFootballGoalsDto] (`{"home": N, "away": N}`, one fixture's final score). This
 * row's `all.goals` was wrongly typed as [ApiFootballGoalsDto] previously: `ignoreUnknownKeys`
 * silently accepted the response instead of failing to parse, and `home`/`away` both stayed null
 * (defaulting to 0) since neither key exists in a standings goals object — which is why GF/GA
 * showed 0 for every team rather than a genuine API limitation. "for" is a Kotlin keyword, hence
 * the [SerialName] mapping rather than a backticked property name. */
@Serializable
internal data class ApiFootballStandingsGoalsDto(
    @SerialName("for") val goalsFor: Int = 0,
    val against: Int = 0,
)

internal fun ApiFootballStandingsLeagueDto.toStandingsRows(): List<StandingsRow> {
    val isGrouped = standings.size > 1
    return standings.flatten().map { it.toStandingsRow(isGrouped) }
}

/** Bundles a standings table with its league badge URL, so [ApiFootballApi.fetchStandings]'s one
 * call can hand both back to the Standings screen without a second request just for the logo —
 * see [ApiFootballStandingsLeagueDto.logo]'s doc comment for where that URL comes from. */
internal data class StandingsFetchResult(val rows: List<StandingsRow>, val leagueLogoUrl: String = "")

internal fun ApiFootballStandingsLeagueDto.toStandingsFetchResult(): StandingsFetchResult =
    StandingsFetchResult(rows = toStandingsRows(), leagueLogoUrl = logo)

private fun ApiFootballStandingsRowDto.toStandingsRow(isGrouped: Boolean): StandingsRow = StandingsRow(
    position = rank,
    teamId = team.id,
    teamName = team.name,
    played = all.played,
    win = all.win,
    draw = all.draw,
    lose = all.lose,
    goalsFor = all.goals.goalsFor,
    goalsAgainst = all.goals.against,
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
    val leagueLogo: String = "",
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

/** Win/draw/loss outcome of a finished [Fixture] relative to a given team — used for My Team's
 * colored result badges (see ResultBadge in SoccerHomeScreen.kt), mirroring the W/D/L blocks
 * fotmob shows next to a team's recent results. Derived entirely from fields this app already
 * fetches; no new API call needed. */
enum class MatchResult { WIN, DRAW, LOSS }

/** Null when the match has no final score yet, or [teamId] isn't one of the two sides — callers
 * (My Team's "RECENT RESULTS" list) only ever pass a team that is actually in the fixture, but this
 * stays defensive rather than throwing. */
fun Fixture.resultFor(teamId: Int): MatchResult? {
    if (!hasScore) return null
    val home = homeGoals ?: return null
    val away = awayGoals ?: return null
    val isHome = when (teamId) {
        homeTeamId -> true
        awayTeamId -> false
        else -> return null
    }
    if (home == away) return MatchResult.DRAW
    val teamWon = if (isHome) home > away else away > home
    return if (teamWon) MatchResult.WIN else MatchResult.LOSS
}

/** One calendar day's fixtures across every followed league — on request, replaces the old
 * two-level grouping (`CompetitionGroup`/`groupedForDisplay` for Scores, `FixtureDayGroup`/
 * `groupedByDateThenLeague` for the standalone Fixtures screen, both removed) now that Scores has
 * absorbed Fixtures into one continuous list spanning [SoccerViewModel]'s schedule window. [matches]
 * itself stays flat and chronological here — sorted by kickoff time ([Fixture.utcDate]) with
 * [COMPETITION_DISPLAY_ORDER] only as a tiebreaker for same-time kickoffs — but isn't rendered flat
 * any more: `ScheduleDayCard` (SoccerHomeScreen.kt) re-groups it by league for display, on request,
 * back into per-league sections within the day card (each with its own centered header, via
 * [competitionDisplayOrder] for the header order) rather than a per-row league label. Kept flat here
 * rather than pre-grouped, since [SoccerViewModel]'s stale-guard equality check and the schedule
 * window math both only care about the day's matches as a set, not how they're presented. */
data class FixtureDay(val date: LocalDate, val dateLabel: String, val matches: List<Fixture>)

fun List<Fixture>.groupedByDate(): List<FixtureDay> = this
    .mapNotNull { match -> match.localDate()?.let { it to match } }
    .groupBy({ it.first }) { it.second }
    .map { (date, matches) ->
        FixtureDay(
            date = date,
            dateLabel = formatFixtureDateHeader(date),
            matches = matches.sortedWith(compareBy({ it.utcDate }, { COMPETITION_DISPLAY_ORDER[it.leagueId] ?: Int.MAX_VALUE })),
        )
    }
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
    /** Ready-to-fetch headshot URL — see the doc comment on [ApiFootballCoachDto.photo]. Null if
     * the coach object was missing or sent no photo. */
    val coachPhotoUrl: String? = null,
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
 * team followed from a non-domestic-league context, or a standings fetch failure). No longer
 * rendered directly (the standings block under the crest was dropped on request), but kept on the
 * model since it's already fetched for free (see [ApiFootballApi.fetchMyTeamSummary]'s doc
 * comment) and may be surfaced again later. */
data class MyTeamSummary(
    val teamId: Int,
    val teamName: String,
    val leagueId: Int,
    val leagueName: String,
    val standingsRow: StandingsRow?,
    /** The team's match for today, if one exists among [upcomingFixtures]/[recentFixtures]'s
     * unfiltered source list; otherwise its next upcoming fixture, or null if neither exists (e.g.
     * the season's already over for this team). Shown in the header row's "today/next match"
     * placeholder next to the crest — see [ApiFootballApi.fetchMyTeamSummary] for exactly how
     * "today" is resolved and why [upcomingFixtures]/[recentFixtures] deliberately exclude whatever
     * this ends up pointing at. */
    val featuredFixture: Fixture?,
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
    /** Same idea as [teamLogoBytes], but for [featuredFixture]'s *other* side — the small opponent
     * badge shown next to the today/next match placeholder. Null if there's no [featuredFixture],
     * its logo URL was blank, or the fetch failed. */
    val featuredOpponentLogoBytes: ByteArray?,
)
