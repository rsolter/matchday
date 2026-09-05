package com.thelightphone.soccerfootball

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.number
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

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

/** The calendar date (device-local timezone) a fixture's kickoff falls on, or null if
 * [Fixture.utcDate] couldn't be parsed. API-Football sends full ISO-8601 with an explicit offset
 * (e.g. "2023-08-11T19:00:00+00:00"), so unlike the ESPN variant of this tool, no padding/repair
 * is needed before parsing. */
fun Fixture.localDate(): LocalDate? =
    parseIso(utcDate)?.toLocalDateTime(TimeZone.currentSystemDefault())?.date

/** Short, uppercase date header for grouping fixtures by day, e.g. "SAT, AUG 19" — or "TODAY" for
 * the real device-local date ([todayLocalDate]). */
fun formatFixtureDateHeader(date: LocalDate): String =
    if (date == todayLocalDate()) {
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
