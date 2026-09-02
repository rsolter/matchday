package com.thelightphone.soccerfootball

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Phase 1's stand-in for "today" — see [PHASE1_SEASON]'s doc comment in SoccerModels.kt for why
 * this build can't use the device's real current date. August 19, 2023 was picked because it's a
 * real Premier League matchday confirmed against a real response this session (fixture 1035052,
 * Man City vs. Sheffield United among others), not an arbitrary guess.
 *
 * Every place in this module that means "today" for fixture/season purposes — the Scores screen's
 * default view, the Fixtures window's center — reads [phase1Today] instead of [todayLocalDate].
 * [todayLocalDate] itself stays wired to the real device clock, since it's still correct for
 * things that are genuinely about *now* rather than season data (e.g. "last updated 2 minutes
 * ago"). Phase 3 deletes [phase1Today] and switches every one of its call sites back to
 * [todayLocalDate] — grep for `phase1Today` when doing that swap.
 */
private val PHASE1_REFERENCE_DATE = LocalDate(2023, 8, 19)

fun phase1Today(): LocalDate = PHASE1_REFERENCE_DATE

/**
 * Short status/score text shown next to a match, e.g. "2 - 1 · 37'", "HT", "FT", "7:30 PM".
 * [Fixture.elapsed]/[Fixture.extraMinutes] build the live minute directly (API-Football sends
 * these as plain numbers, not a pre-formatted string the way ESPN's `displayClock` does).
 */
fun Fixture.statusLabel(): String = when (status) {
    MatchStatus.IN_PLAY -> elapsed?.let { e ->
        val extra = extraMinutes?.takeIf { it > 0 }
        if (extra != null) "$e+$extra'" else "$e'"
    } ?: "LIVE"
    MatchStatus.HALFTIME -> "HT"
    MatchStatus.FINISHED -> "FT"
    MatchStatus.POSTPONED -> "Postponed"
    MatchStatus.CANCELLED -> "Cancelled"
    MatchStatus.SUSPENDED -> "Suspended"
    MatchStatus.SCHEDULED -> formatKickoffTime(utcDate)
    MatchStatus.UNKNOWN -> statusLong.ifBlank { "—" }
}

/** "2 - 1" if the match has started/finished, otherwise "vs" for matches that haven't kicked off. */
fun Fixture.scoreLabel(): String =
    if (hasScore) "${homeGoals ?: 0} - ${awayGoals ?: 0}" else "vs"

fun formatKickoffTime(isoDate: String): String =
    parseIso(isoDate)?.toLocalDateTime(TimeZone.currentSystemDefault())?.toAmPm() ?: "--:--"

fun formatUpdatedAt(instant: Instant): String =
    instant.toLocalDateTime(TimeZone.currentSystemDefault()).toAmPm()

/** Today's date in the device's local timezone — used for "how fresh is this fetch" purposes only,
 * not for anything season/fixture-related (see [phase1Today]'s doc comment). */
fun todayLocalDate(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

fun todayLocalDateString(): String = todayLocalDate().toString()

/** The calendar date (device-local timezone) a fixture's kickoff falls on, or null if
 * [Fixture.utcDate] couldn't be parsed. API-Football sends full ISO-8601 with an explicit offset
 * (e.g. "2023-08-11T19:00:00+00:00"), so unlike the ESPN variant of this tool, no padding/repair
 * is needed before parsing. */
fun Fixture.localDate(): LocalDate? =
    parseIso(utcDate)?.toLocalDateTime(TimeZone.currentSystemDefault())?.date

/** Short, uppercase date header for grouping fixtures by day, e.g. "SAT, AUG 19" — or "TODAY" for
 * [phase1Today] specifically (not the real device date, in this Phase 1 build). */
fun formatFixtureDateHeader(date: LocalDate): String =
    if (date == phase1Today()) {
        "TODAY"
    } else {
        "${date.dayOfWeek.name.take(3)}, ${date.month.name.take(3)} ${date.dayOfMonth}"
    }

private fun parseIso(isoDate: String): Instant? = runCatching { Instant.parse(isoDate) }.getOrNull()

private fun LocalDateTime.toAmPm(): String {
    val period = if (hour < 12) "AM" else "PM"
    val twelveHour = hour % 12
    val displayHour = if (twelveHour == 0) 12 else twelveHour
    val minuteStr = minute.toString().padStart(2, '0')
    return "$displayHour:$minuteStr $period"
}
