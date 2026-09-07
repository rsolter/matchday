package com.thelightphone.soccerfootball

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Short status/score text shown next to a match, e.g. "2 - 1 · 37'", "HT", "FT", "19:30".
 * [Fixture.elapsed]/[Fixture.extraMinutes] build the live minute directly (API-Football sends
 * these as plain numbers, not a pre-formatted string the way ESPN's `displayClock` does).
 *
 * [lineupsAvailable] (default false, so every existing call site is unaffected) swaps the plain
 * kickoff time for "Lineups" once [SoccerViewModel]'s near-kickoff check has confirmed
 * API-Football has posted one for this fixture — see that class's lineup-availability doc comment
 * and [ApiFootballApi.fetchLineupAvailability]. Only meaningful for [MatchStatus.SCHEDULED]; every
 * other branch ignores it.
 */
fun Fixture.statusLabel(lineupsAvailable: Boolean = false): String = when (status) {
    MatchStatus.IN_PLAY -> elapsed?.let { e ->
        val extra = extraMinutes?.takeIf { it > 0 }
        if (extra != null) "$e+$extra'" else "$e'"
    } ?: "LIVE"
    MatchStatus.HALFTIME -> "HT"
    MatchStatus.FINISHED -> "FT"
    MatchStatus.POSTPONED -> "Postponed"
    MatchStatus.CANCELLED -> "Cancelled"
    MatchStatus.SUSPENDED -> "Suspended"
    MatchStatus.SCHEDULED -> if (lineupsAvailable) "Lineups" else formatKickoffTime(utcDate)
    MatchStatus.UNKNOWN -> statusLong.ifBlank { "—" }
}

/** "2 - 1" if the match has started/finished, otherwise "vs" for matches that haven't kicked off. */
fun Fixture.scoreLabel(): String =
    if (hasScore) "${homeGoals ?: 0} - ${awayGoals ?: 0}" else "vs"

fun formatKickoffTime(isoDate: String): String =
    parseIso(isoDate)?.toLocalDateTime(TimeZone.currentSystemDefault())?.to24HourTime() ?: "--:--"

fun formatUpdatedAt(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).to24HourTime()

/** Today's date in the device's local timezone. Phase 3 onward, this is used for fixture/season
 * purposes too (see [currentSeason]) — Phase 1's separate frozen-date stand-in (`phase1Today`) is
 * gone now that the proxy backing this app has real current-season access. */
fun todayLocalDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

fun todayLocalDateString(): String = todayLocalDate().toString()

/**
 * API-Football's `season` query parameter is the year the (northern-hemisphere) season *starts*,
 * not the calendar year — confirmed empirically twice in this project: Phase 1's frozen
 * `season=2023` paired with an Aug 19, 2023 reference date returned real fixtures, and this
 * session's live proxy check (`GET /standings?league=39&season=2026` on Sept 5, 2026) returned
 * real, in-progress 2026/27 Premier League standings.
 *
 * The July cutover below (season flips over on July 1st) is a reasonable assumption based on when
 * European domestic leagues actually kick off preseason/transfer business, but it hasn't been
 * independently verified against a real response near that boundary — worth a real check next
 * time this code runs in June/July.
 */
fun currentSeason(referenceDate: LocalDate = todayLocalDate()): Int =
    if (referenceDate.month.number >= 7) referenceDate.year else referenceDate.year - 1

/** Kickoff date + time for contexts that aren't already grouped by day, e.g. My Team's flat
 * "UPCOMING" list (unlike Scores/Fixtures, which group matches under a per-day header first, so a
 * bare time there already reads unambiguously). Renders as e.g. "9/25 19:45" — same time format
 * [formatKickoffTime] already uses, just prefixed with the short numeric month/day. */
fun formatKickoffDateAndTime(isoDate: String): String {
    val dateTime = parseIso(isoDate)?.toLocalDateTime(TimeZone.currentSystemDefault()) ?: return "--/-- --:--"
    return "${dateTime.date.monthNumber}/${dateTime.date.dayOfMonth} ${dateTime.to24HourTime()}"
}

/** The calendar date (device-local timezone) a fixture's kickoff falls on, or null if
 * [Fixture.utcDate] couldn't be parsed. API-Football sends full ISO-8601 with an explicit offset
 * (e.g. "2023-08-11T19:00:00+00:00"), so unlike the ESPN variant of this tool, no padding/repair
 * is needed before parsing. */
fun Fixture.localDate(): LocalDate? =
    parseIso(utcDate)?.toLocalDateTime(TimeZone.currentSystemDefault())?.date

/** Parsed kickoff [Instant], or null if [Fixture.utcDate] couldn't be parsed — a public counterpart
 * of the private [parseIso] this file's other Fixture helpers already use internally, added for
 * [SoccerViewModel]'s near-kickoff lineup-availability check, which needs a real time delta rather
 * than just the local calendar date [localDate] gives. */
fun Fixture.kickoffInstant(): Instant? = parseIso(utcDate)

/** Short, uppercase date header for grouping fixtures by day, e.g. "SAT, AUG 19" — or "TODAY" for
 * the real device-local date ([todayLocalDate]). */
fun formatFixtureDateHeader(date: LocalDate): String =
    if (date == todayLocalDate()) {
        "TODAY"
    } else {
        "${date.dayOfWeek.name.take(3)}, ${date.month.name.take(3)} ${date.dayOfMonth}"
    }

private fun parseIso(isoDate: String): Instant? = runCatching { Instant.parse(isoDate) }.getOrNull()

// 24-hour ("military"/international) format, e.g. "09:05" or "19:45" — no AM/PM suffix, so this
// is also a character or two shorter than the old 12-hour format in every slot that shows a
// kickoff time (Scores, Fixtures, My Team's "UPCOMING"), which was the point of switching: on
// request, to claw back a bit more of the tight left-hand slot those labels share (see
// LEFT_SLOT_WIDTH/LEFT_SLOT_WIDTH_DATED and allowKickoffLabelWrap in SoccerHomeScreen.kt).
private fun LocalDateTime.to24HourTime(): String {
    val hourStr = hour.toString().padStart(2, '0')
    val minuteStr = minute.toString().padStart(2, '0')
    return "$hourStr:$minuteStr"
}
