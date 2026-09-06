package com.thelightphone.soccerfootball

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextVariant
import com.thelightphone.sdk.ui.LightTheme
import com.thelightphone.sdk.ui.LightThemeController
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.LightTopBar
import com.thelightphone.sdk.ui.LightTopBarCenter
import com.thelightphone.sdk.ui.gridUnitsAsDp
import com.thelightphone.sdk.ui.lightClickable
import com.thelightphone.sdk.ui.scrollBarGutterUnits

@InitialScreen
class SoccerHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SoccerViewModel>(sealedActivity) {

    override val viewModelClass: Class<SoccerViewModel>
        get() = SoccerViewModel::class.java

    override fun createViewModel(): SoccerViewModel = SoccerViewModel(lightContext.dataStore)

    @Composable
    override fun Content() {
        val themeColors by LightThemeController.colors.collectAsState()
        val state by viewModel.uiState.collectAsState()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val mode = state.mode) {
                    is ScoreScreenMode.Loading -> {
                        LoadingContent(title = "Soccer Pro", message = mode.message)
                    }

                    is ScoreScreenMode.Scores -> {
                        ScoresContent(
                            groups = mode.groups,
                            leagueLogos = mode.leagueLogos,
                            onOpenSettings = viewModel::openSettings,
                            onOpenMyTeam = viewModel::openMyTeam,
                            onOpenStandingsTable = viewModel::openStandingsTable,
                            onOpenFixtures = viewModel::openFixtures,
                            onMatchClick = viewModel::openMatchDetail,
                        )
                    }

                    is ScoreScreenMode.Settings -> {
                        SettingsContent(
                            selectedLeagueNames = mode.selectedLeagueNames,
                            myTeamName = mode.myTeamName,
                            onBack = viewModel::closeSettings,
                            onOpenAttribution = viewModel::openAttribution,
                            onOpenLeagueSelection = viewModel::openLeagueSelection,
                            onOpenMyTeamSetup = viewModel::openMyTeamSetup,
                            onClearMyTeam = viewModel::clearMyTeam,
                            onManualRefresh = viewModel::manualRefresh,
                        )
                    }

                    is ScoreScreenMode.Attribution -> {
                        AttributionContent(onBack = viewModel::closeAttribution)
                    }

                    is ScoreScreenMode.LeagueSelection -> {
                        LeagueSelectionContent(
                            rows = mode.rows,
                            onToggle = viewModel::toggleLeague,
                            onBack = viewModel::closeLeagueSelection,
                        )
                    }

                    is ScoreScreenMode.Standings -> {
                        StandingsTableContent(
                            leagueId = mode.leagueId,
                            leagueName = mode.leagueName,
                            leagueLogoBytes = mode.leagueLogoBytes,
                            rows = mode.rows,
                            isLoading = mode.isLoading,
                            onBack = viewModel::backFromStandingsTable,
                        )
                    }

                    is ScoreScreenMode.Fixtures -> {
                        FixturesContent(
                            groups = mode.groups,
                            isLoading = mode.isLoading,
                            onBack = viewModel::backFromFixturesTable,
                            onMatchClick = viewModel::openMatchDetail,
                        )
                    }

                    is ScoreScreenMode.MyTeamLeaguePicker -> {
                        CompetitionPickerContent(
                            title = "My Team",
                            leagues = mode.leagues,
                            onSelect = viewModel::selectMyTeamLeague,
                            onBack = viewModel::backFromMyTeamLeaguePicker,
                        )
                    }

                    is ScoreScreenMode.MyTeamTeamPicker -> {
                        MyTeamTeamPickerContent(
                            leagueName = mode.leagueName,
                            teams = mode.teams,
                            isLoading = mode.isLoading,
                            onSelect = { teamId, teamName -> viewModel.selectMyTeamTeam(teamId, teamName, mode.leagueId) },
                            onBack = viewModel::backFromMyTeamTeamPicker,
                        )
                    }

                    is ScoreScreenMode.MyTeam -> {
                        MyTeamContent(
                            summary = mode.summary,
                            isLoading = mode.isLoading,
                            onBack = viewModel::backFromMyTeam,
                            onMatchClick = viewModel::openMatchDetail,
                        )
                    }

                    is ScoreScreenMode.MatchDetailScreen -> {
                        MatchDetailContent(
                            mode = mode,
                            onBack = viewModel::backFromMatchDetail,
                        )
                    }
                }

                state.errorModal?.let { message ->
                    LightFullscreenModal(
                        message = message,
                        onClose = viewModel::dismissError,
                    )
                }
            }
        }
    }
}

@Composable
private fun LoadingContent(title: String, message: String) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text(title),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            LightText(
                text = message,
                variant = LightTextVariant.Detail,
                align = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
            )
        }
    }
}

// --- Scores ------------------------------------------------------------------

// Fixed width for MatchRow's leading slot in the common case — a status badge (FT/live-minute), a
// same-day kickoff time ("19:45", already grouped under a date header so no date prefix needed),
// or a My Team result badge. Narrowed on request: at 6.5f (sized for the much longer dated kickoff
// format below) this left a lot of dead space in front of team names for every row that wasn't
// using the longest case. Keeping this as a fixed width at all, rather than letting the slot's
// content size itself, is what keeps every row's team names starting at the same x position
// regardless of what leads them.
private val LEFT_SLOT_WIDTH = 4.2f

// Wider leading-slot width for the one case that doesn't fit LEFT_SLOT_WIDTH: My Team's "UPCOMING"
// card, whose kickoff label includes a date prefix ("9/25 19:45") since — unlike Scores/Fixtures —
// it isn't already grouped under a per-day header (see formatKickoffDateAndTime's doc comment in
// SoccerFormatting.kt). Only ever used for that one card, so it doesn't affect row alignment
// anywhere else — every other card's rows are internally consistent using LEFT_SLOT_WIDTH.
private val LEFT_SLOT_WIDTH_DATED = 6.5f

// Fixed width for MatchRow's trailing score slot — the badge/kickoff-time content that used to
// live here moved to the left slot above (see MatchRow's doc comment), so this only ever holds a
// plain score ("2 - 1", or blank before kickoff) and can stay narrower than the old combined slot.
private val SCORE_SLOT_WIDTH = 4f

// A light, legible green for a live match's minute-counter text — matches the reference (fotmob)
// screenshot's live-indicator hue. Used for text color only (see MatchStatusBadge); the badge's
// own pill background stays the same neutral translucent fill FT uses, so "live" reads as a color
// change on familiar UI rather than an entirely different shape.
private val LIVE_STATUS_GREEN = Color(0xFF4ADE80)

/** Small rounded badge for a match's status — FT (or Postponed/Cancelled/Suspended) and a live
 * minute counter now share the same pill shape, matching the reference fotmob screenshot's FT
 * badge; only the live case gets green text ([LIVE_STATUS_GREEN]) to set it apart from a finished
 * match. Sized by its own content — the caller (MatchRow) places it inside [LEFT_SLOT_WIDTH]'s
 * fixed-width slot rather than sizing it directly. */
@Composable
private fun MatchStatusBadge(text: String, isLive: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(LightThemeTokens.colors.content.copy(alpha = 0.12f))
            .padding(horizontal = 0.4f.gridUnitsAsDp(), vertical = 0.05f.gridUnitsAsDp()),
    ) {
        // Was Superfine (16), then Detail (20) after round 13's revert — now Fine (25), one size up
        // again, matched to the rest of MatchRow's text (team names/score) at the same size.
        LightText(
            text = text,
            variant = LightTextVariant.Fine,
            align = TextAlign.End,
            lighten = !isLive,
            color = if (isLive) LIVE_STATUS_GREEN else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Win/draw/loss badge colors for My Team's "RECENT RESULTS" list and its form-summary row (see
// ResultBadge/FormRow) — plain hardcoded colors, following the same pattern CARD_YELLOW/CARD_RED
// already use for the match-events tab rather than pulling from the Light theme's own palette.
private val RESULT_WIN_COLOR = Color(0xFF4CAF50)
private val RESULT_DRAW_COLOR = Color(0xFF9E9E9E)
private val RESULT_LOSS_COLOR = Color(0xFFD32F2F)

/** Small colored block badge for one match's result relative to My Team's followed team — mirrors
 * the fotmob reference screenshot's Form-column blocks. Used inline per match in [MatchRow]'s left
 * slot, My Team's "RECENT RESULTS" card only — see [Fixture.resultFor]. (Previously also repeated
 * for a league-form summary string on My Team's own standings header via a `FormRow` composable;
 * that header block was dropped entirely on request, and `FormRow` removed with it.) */
@Composable
private fun ResultBadge(result: MatchResult, modifier: Modifier = Modifier) {
    val (background, label) = when (result) {
        MatchResult.WIN -> RESULT_WIN_COLOR to "W"
        MatchResult.DRAW -> RESULT_DRAW_COLOR to "D"
        MatchResult.LOSS -> RESULT_LOSS_COLOR to "L"
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(0.4f.gridUnitsAsDp()))
            .background(background)
            .padding(horizontal = 0.35f.gridUnitsAsDp(), vertical = 0.1f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(text = label, variant = LightTextVariant.Superfine, color = Color.White)
    }
}

// Premier League (39) and Ligue 1 (61) — see TRACKED_COMPETITIONS in SoccerModels.kt — ship real
// crest logos that read as dark purple/navy on API-Football's CDN, which reads poorly against this
// app's black backgrounds. Recolored at render time rather than as a bundled replacement asset:
// this app has no local drawable for any league badge (every logo is fetched over the network, see
// ApiFootballApi.fetchLeagueLogos), so a runtime tint is what actually reaches every place a league
// logo is drawn (Scores' MatchGroupCard title icon, Standings' own header) without depending on
// what that fetch happens to return.
private val WHITE_TINTED_LEAGUE_IDS = setOf(39, 61)

/** [BlendMode.SrcIn] paints solid white everywhere the source bitmap has any alpha (i.e. the
 * badge's actual crest shape) and leaves fully-transparent pixels untouched — a plain silhouette
 * recolor, which is what "all white" was asked for, not a partial tint that would keep some of the
 * original shading. Returns null (no filter — original colors) for every other league. */
private fun leagueLogoColorFilter(leagueId: Int): ColorFilter? =
    if (leagueId in WHITE_TINTED_LEAGUE_IDS) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null

@Composable
private fun ScoresContent(
    groups: List<CompetitionGroup>,
    leagueLogos: Map<String, ByteArray>,
    onOpenSettings: () -> Unit,
    onOpenMyTeam: () -> Unit,
    onOpenStandingsTable: (Int, String) -> Unit,
    onOpenFixtures: () -> Unit,
    onMatchClick: (Fixture) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Was "Updating…" / "Updated {time}" / "Today" depending on refresh state — simplified to
        // always read "Today" on request. ScoreScreenMode.Scores.lastUpdated/isRefreshing (and
        // formatUpdatedAt in SoccerFormatting.kt) are unused by this screen now but left in place
        // in case a future refresh indicator wants them again.
        LightTopBar(
            center = LightTopBarCenter.Text(text = "Today"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // Was Copy (30) before the global one-step-down pass shifted it to Detail (20);
                // reverted back to Copy along with the rest of this view's text.
                LightText(
                    text = "No matches today in your leagues.",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                )
            }
        } else {
            LightScrollView(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                groups.forEachIndexed { index, group ->
                    MatchGroupCard(
                        title = group.leagueName.uppercase(),
                        titleLogoBytes = leagueLogos[group.leagueLogo],
                        titleLogoTint = leagueLogoColorFilter(group.leagueId),
                        matches = group.matches,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 0.75f.gridUnitsAsDp()),
                        onTitleLogoClick = if (competitionHasStandings(group.leagueId)) {
                            { onOpenStandingsTable(group.leagueId, group.leagueName) }
                        } else {
                            null
                        },
                        onMatchClick = onMatchClick,
                    )
                }
            }
        }

        // Bottom bar order (left to right): Settings, My Team, Fixtures — deliberately no Refresh
        // icon here, see SoccerViewModel's class doc comment for why (free-tier quota + frozen
        // historical data in Phase 1). Manual refresh lives in Settings instead. Standings used to
        // have its own button here too; it's gone now — the table is reached only by tapping a
        // league's logo above (see MatchGroupCard's onTitleLogoClick).
        LightBottomBar(
            items = listOf(
                LightBarButton.LightIcon(
                    icon = LightIcons.SETTINGS,
                    onClick = onOpenSettings,
                    contentDescription = "Settings",
                ),
                LightBarButton.Custom(
                    onClick = onOpenMyTeam,
                    contentDescription = "My Team",
                ) { SoccerBarIcon(R.drawable.ic_jersey_white, "My Team") },
                LightBarButton.Custom(
                    onClick = onOpenFixtures,
                    contentDescription = "Fixtures",
                ) { SoccerBarIcon(R.drawable.ic_calendar_dot_white, "Fixtures") },
            ),
        )
    }
}

/** A rounded card grouping a set of matches under one header — same card-per-group pattern as the
 * ESPN/football-data.org variants of this tool. Used for Scores (grouped by competition),
 * Fixtures (grouped by date), and My Team (upcoming/recent). [showFinishedStatus] controls whether
 * a finished match still prints its "FT"/"Postponed"/etc. label in the row's left slot.
 * [titleLogoBytes] is only ever passed by Scores' competition groups — Fixtures/My Team group by
 * date or a plain "RECENT RESULTS"/"UPCOMING" label, neither of which has a single league badge to
 * show. [titleLogoTint], when set, recolors that logo — see [leagueLogoColorFilter]'s doc comment
 * for why (only Scores' call site ever passes one, computed from the group's own league ID).
 * [onTitleLogoClick], when set (Scores only, and only for leagues with a real table — see
 * `competitionHasStandings` in SoccerModels.kt), makes the title logo itself tappable to jump
 * straight to that league's Standings table; this is now the table's *only* entry point, since the
 * Home screen's own Standings button was removed. [highlightTeamId] is only ever passed by My
 * Team's "RECENT RESULTS" card, to color each match's left slot by its result for that team
 * instead — see [MatchRow]. [allowKickoffLabelWrap] is only ever passed by Fixtures' per-league
 * list and My Team's "UPCOMING" card — see [MatchRow]. [suppressRepeatedKickoffTime], only ever
 * passed by Fixtures, blanks a scheduled match's kickoff time whenever it's identical to the
 * previous row's within this same card — see [MatchRow]'s `suppressKickoffLabel`. [showScoreSlot],
 * only ever set false by My Team's "UPCOMING" card, drops the trailing score column entirely
 * (rather than just leaving it visually empty) so the team-name text gets that width back — see
 * [MatchRow]. */
@Composable
private fun MatchGroupCard(
    title: String,
    matches: List<Fixture>,
    modifier: Modifier = Modifier,
    titleLogoBytes: ByteArray? = null,
    titleLogoTint: ColorFilter? = null,
    onTitleLogoClick: (() -> Unit)? = null,
    showFinishedStatus: Boolean = true,
    showDate: Boolean = false,
    highlightTeamId: Int? = null,
    allowKickoffLabelWrap: Boolean = false,
    suppressRepeatedKickoffTime: Boolean = false,
    showScoreSlot: Boolean = true,
    onMatchClick: (Fixture) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(1.2f.gridUnitsAsDp()))
            .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.08f))
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        ) {
            val titleLogoBitmap = titleLogoBytes?.let { bytes ->
                remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            }
            if (titleLogoBitmap != null) {
                Image(
                    bitmap = titleLogoBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    colorFilter = titleLogoTint,
                    modifier = Modifier
                        .size(1.4f.gridUnitsAsDp())
                        .let { if (onTitleLogoClick != null) it.lightClickable(onClick = onTitleLogoClick) else it }
                        .padding(end = 0.4f.gridUnitsAsDp()),
                )
            }
            // Was Detail (20) before the global one-step-down pass shifted it to Superfine (16);
            // reverted back to Detail along with the rest of Scores/Fixtures/My Team's match rows.
            LightText(text = title, variant = LightTextVariant.Detail, lighten = true)
        }
        matches.forEachIndexed { index, match ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.15f)),
                )
            }
            // Only a run of consecutive *scheduled* matches sharing the exact same kickoff instant
            // gets blanked past the first — a live/finished match's row shows a status badge, not a
            // kickoff time, in this same slot, so it's never a candidate either way.
            val suppressKickoff = suppressRepeatedKickoffTime && index > 0 &&
                !match.hasScore && !matches[index - 1].hasScore && match.utcDate == matches[index - 1].utcDate
            MatchRow(
                match,
                showFinishedStatus = showFinishedStatus,
                showDate = showDate,
                highlightTeamId = highlightTeamId,
                allowKickoffLabelWrap = allowKickoffLabelWrap,
                suppressKickoffLabel = suppressKickoff,
                showScoreSlot = showScoreSlot,
                onClick = { onMatchClick(match) },
            )
        }
    }
}

/** One match's row: a leading fixed-width slot ([LEFT_SLOT_WIDTH], or [LEFT_SLOT_WIDTH_DATED] for
 * My Team's "UPCOMING" card) for its status — live minute, "FT", a kickoff time, or (My Team's
 * "RECENT RESULTS" only) a colored [ResultBadge] — then the team names, then a trailing score slot
 * ([SCORE_SLOT_WIDTH]). The status used to trail the score on the right instead; moved to lead the
 * row instead, matching the reference fotmob layout, and incidentally removing the old score-drift
 * problem for free — the score now sits in its own fixed slot with nothing else competing for its
 * space, so it isn't affected by what leads the row. */
@Composable
private fun MatchRow(
    match: Fixture,
    showFinishedStatus: Boolean = true,
    // My Team's "UPCOMING" card is the one caller that isn't already grouped under a per-day
    // header (unlike Scores/Fixtures), so a bare kickoff time there could be mistaken for today's
    // game — see formatKickoffDateAndTime's doc comment in SoccerFormatting.kt.
    showDate: Boolean = false,
    // Set only by My Team's "RECENT RESULTS" card (showFinishedStatus = false there, so the FT
    // badge that would otherwise occupy this slot is already suppressed) — shows a colored W/D/L
    // result badge for this specific team in the same left-hand slot instead. See
    // Fixture.resultFor/ResultBadge.
    highlightTeamId: Int? = null,
    // Set only by Fixtures' per-league list and My Team's "UPCOMING" card, where the kickoff label
    // can be a full date + time (e.g. "9/13 19:45") or just a longer local time — letting it wrap
    // to a second line instead of ellipsizing. Left off (default) for Scores, which keeps its
    // original single-line/ellipsis behavior — see the SoccerHomeScreen font-size audit, §9, for
    // why Scores wasn't included even though it can clip the same way.
    allowKickoffLabelWrap: Boolean = false,
    // Set only by Fixtures, computed by MatchGroupCard: true when this row shares its exact
    // kickoff instant with the row directly above it in the same card, so the same time isn't
    // printed once per match sharing a kickoff — on request, see a real Fixtures screenshot.
    suppressKickoffLabel: Boolean = false,
    // False only for My Team's "UPCOMING" card, on request — every one of its matches is scheduled
    // (no score to show yet anyway), and dropping the slot entirely rather than just leaving it
    // empty gives the team-name text that width back, letting most matchups fit on one line.
    showScoreSlot: Boolean = true,
    onClick: () -> Unit,
) {
    // Only My Team's "UPCOMING" card ever hits the showDate+SCHEDULED branch below (its rows are
    // all upcoming, so this is consistent card-to-card, not row-to-row within one card) — everyone
    // else (Scores/Fixtures, and My Team's "RECENT RESULTS") uses the narrower common width.
    val usesDatedKickoffLabel = !match.hasScore && showDate && match.status == MatchStatus.SCHEDULED
    val leftSlotWidth = if (usesDatedKickoffLabel) LEFT_SLOT_WIDTH_DATED else LEFT_SLOT_WIDTH
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.65f.gridUnitsAsDp()),
    ) {
        Box(
            modifier = Modifier.width(leftSlotWidth.gridUnitsAsDp()),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (match.hasScore) {
                if (match.status.isLive) {
                    MatchStatusBadge(text = match.statusLabel(), isLive = true)
                } else if (showFinishedStatus) {
                    MatchStatusBadge(text = match.statusLabel(), isLive = false)
                } else {
                    val result = highlightTeamId?.let { match.resultFor(it) }
                    if (result != null) {
                        ResultBadge(result)
                    }
                }
            } else if (!suppressKickoffLabel) {
                // Upcoming match, no score yet — nothing to color-code, just the kickoff label.
                val label = if (usesDatedKickoffLabel) {
                    formatKickoffDateAndTime(match.utcDate)
                } else {
                    match.statusLabel()
                }
                // Was Superfine (16), then Detail (20) after round 13's revert — now Fine (25),
                // matched to the rest of MatchRow's text. maxLines is 2 (wrapping instead of
                // ellipsizing) only where allowKickoffLabelWrap opts in — see its doc comment above.
                LightText(
                    text = label,
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    maxLines = if (allowKickoffLabelWrap) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Was Copy (30), then Detail (20) after the global shift, back to Copy (30) after round
        // 13's revert — now Fine (25): one size down from Copy, on request, matched to the status
        // badge/kickoff label and score, which moved to Fine at the same time.
        LightText(
            text = "${match.homeTeamName} vs ${match.awayTeamName}",
            variant = LightTextVariant.Fine,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 0.5f.gridUnitsAsDp()),
        )
        if (showScoreSlot) {
            Box(
                modifier = Modifier.width(SCORE_SLOT_WIDTH.gridUnitsAsDp()),
                contentAlignment = Alignment.CenterEnd,
            ) {
                if (match.hasScore) {
                    // Was Detail (20), then Copy (30) after round 13's revert — now Fine (25),
                    // matched to the rest of MatchRow's text.
                    LightText(
                        text = match.scoreLabel(),
                        variant = LightTextVariant.Fine,
                        align = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// --- Settings ------------------------------------------------------------------

@Composable
private fun SettingsContent(
    selectedLeagueNames: List<String>,
    myTeamName: String?,
    onBack: () -> Unit,
    onOpenAttribution: () -> Unit,
    onOpenLeagueSelection: () -> Unit,
    onOpenMyTeamSetup: () -> Unit,
    onClearMyTeam: () -> Unit,
    onManualRefresh: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text("Settings"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            LeaguesRow(leagueNames = selectedLeagueNames, onClick = onOpenLeagueSelection)
            SettingRow(label = "My Team", value = myTeamName ?: "Not set", onClick = onOpenMyTeamSetup)
            if (myTeamName != null) {
                // Was Copy (30) before the global one-step-down pass shifted it to Detail (20);
                // reverted back to Copy along with the rest of Settings' text — then, on request,
                // sized down again to Detail (20): the section's grey sub-items (this and the team
                // name/"Not set" value above) read too large next to "My Team"'s own label, so both
                // are now Detail instead of Copy, one step below the label's new Fine (25).
                LightText(
                    text = "Forget My Team",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = onClearMyTeam)
                        .padding(top = 0.25f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
                )
            }
            // This build's only manual-refresh surface — see SoccerViewModel's class doc comment
            // for why there's no bottom-bar refresh icon. Was Copy (30) before the global
            // one-step-down pass shifted it to Detail (20); reverted back to Copy, then to Fine
            // (25) on request — see this fun's own doc comment on why "Leagues followed"/"My
            // Team"/"Refresh now"/"About" all share one size now. Top padding bumped from 0.25f to
            // 0.75f on request, so this reads as its own section — the same gap "Leagues followed"
            // and "My Team" already carry between each other above — rather than as a continuation
            // of "My Team"/"Forget My Team" right above it.
            LightText(
                text = "Refresh now",
                variant = LightTextVariant.Fine,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(onClick = onManualRefresh)
                    .padding(top = 0.75f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
            )
        }

        // Deliberately outside the scrollable Column above, not its last item: with a longer
        // leagues list ("Leagues followed" can grow to several lines) the previous placement put
        // "About" below the fold with no visual hint there was more to scroll to, so it read as
        // missing entirely. Pinning it here — a plain row, not the old centered/underlined
        // AttributionFooter, same ScoreScreenMode.Attribution destination — keeps it reachable
        // regardless of how long the scrollable list above gets.
        // Was Copy (30) before the global one-step-down pass shifted it to Detail (20); reverted
        // back to Copy along with the rest of Settings' text, then to Fine (25) on request so
        // "Leagues followed"/"My Team"/"Refresh now"/"About" all match — see the doc comment on
        // SettingRow's label param for the full reasoning. Top padding bumped from 0.5f to 0.75f on
        // request, matching the section-to-section gap above, so this reads as its own section the
        // same way "Leagues followed"/"My Team" do — on top of already sitting in its own pinned
        // footer position outside the scrollable list (see this fun's own doc comment above for
        // why).
        LightText(
            text = "About",
            variant = LightTextVariant.Fine,
            modifier = Modifier
                .fillMaxWidth()
                .lightClickable(onClick = onOpenAttribution)
                .padding(horizontal = 1f.gridUnitsAsDp())
                .padding(top = 0.75f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
        )
    }
}

@Composable
private fun SettingRow(label: String, value: String, onClick: (() -> Unit)?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.lightClickable(onClick = onClick) else it }
            .padding(vertical = 0.75f.gridUnitsAsDp()),
    ) {
        // Was Detail (20) before the global one-step-down pass shifted it to Superfine (16);
        // reverted back to Detail along with the rest of Settings' text. Colors flipped on request
        // (was lighten = true here, value below un-lightened) — the section label now reads as the
        // brighter, primary text, and its value as the secondary/grey one, matching how the "My
        // Team" section's sub-items (its value, and "Forget My Team" below) now read grey too.
        // Bumped one size on request, Detail (20) -> Fine (25) — the label was reading smaller than
        // its own (Copy-sized) value below it, the opposite of the intended hierarchy; "Leagues
        // followed"/"Refresh now"/"About" all moved to this same Fine size for consistency, per the
        // explicit "should all be the same font size" request.
        LightText(text = label, variant = LightTextVariant.Fine)
        // Was Heading (38), then Copy (30) — see font-size-audit.md recommendation #2 for why
        // Heading was too heavy for a plain settings row. The global one-step-down pass then
        // shifted it to Detail (20); reverted back to Copy, its round-8 size, per the "make
        // Settings text larger again" request — this view no longer follows the global shift. Sized
        // down again on request, Copy (30) -> Detail (20): now smaller than the label above it
        // (Fine, 25), matching the same relationship "Forget My Team" now has.
        LightText(text = value, variant = LightTextVariant.Detail, lighten = true)
    }
}

@Composable
private fun LeaguesRow(leagueNames: List<String>, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.75f.gridUnitsAsDp()),
    ) {
        // Was Detail (20) before the global one-step-down pass shifted it to Superfine (16);
        // reverted back to Detail along with the rest of Settings' text. Colors flipped on request
        // (was lighten = true here, each league name below un-lightened) — the "Leagues followed"
        // label now reads as the brighter, primary text, and each league name below it as the
        // secondary/grey one. Bumped one size on request, Detail (20) -> Fine (25) — see SettingRow's
        // label doc comment above for the full "should all be the same font size" reasoning; each
        // league name below stays Detail (20), already smaller, so no change needed there.
        LightText(text = "Leagues followed", variant = LightTextVariant.Fine)
        // Was Copy, then Detail (see font-size-audit.md recommendation #3 — with up to 15
        // trackable competitions, a user following several gets that many stacked lines, so this
        // matches the label above rather than standing out as heavier). The global one-step-down
        // pass then shifted it to Superfine (16); reverted back to Detail here — recommendation #3
        // still holds (matching the label above), this just undoes the global shift on top of it.
        leagueNames.forEach { name ->
            LightText(
                text = name,
                variant = LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(top = 0.2f.gridUnitsAsDp()),
            )
        }
    }
}

@Composable
private fun AttributionContent(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text("About"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 1.5f.gridUnitsAsDp()),
        ) {
            // Was Paragraph (24.5) — this app's own dedicated reading-text size — before the global
            // one-step-down pass shifted it to Detail (20); reverted back to Paragraph along with
            // the rest of Settings' (and its child pages') text.
            LightText(
                text = "Scores are provided by API-Football (api-football.com), a paid football " +
                    "data API. This tool covers the Premier League, Serie A, and the UEFA " +
                    "Champions League and Europa League.\n\n" +
                    "Requests go through a caching proxy this app's developer runs, which holds " +
                    "the API-Football key and absorbs the request load — there's nothing to set " +
                    "up or configure here. Scores, fixtures, and standings are live, " +
                    "current-season data.",
                variant = LightTextVariant.Paragraph,
            )
        }
    }
}

// --- League selection ------------------------------------------------------------------

@Composable
private fun LeagueSelectionContent(
    rows: List<LeagueSelectionRow>,
    onToggle: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text("Leagues"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            rows.forEach { row ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = { onToggle(row.id) })
                        .padding(vertical = 0.85f.gridUnitsAsDp()),
                ) {
                    // Was Copy (30) before the global one-step-down pass shifted it to Detail (20);
                    // reverted back to Copy — this is a Settings child page, same treatment.
                    LightText(text = row.name, variant = LightTextVariant.Copy, modifier = Modifier.weight(1f))
                    LightIcon(
                        icon = if (row.selected) LightIcons.TOGGLE_STATE_ON else LightIcons.TOGGLE_STATE_OFF,
                        size = 2f,
                        contentDescription = if (row.selected) "Following" else "Not following",
                    )
                }
            }
        }
    }
}

// --- Shared competition picker (Standings / Fixtures / My Team setup) ----------------------

@Composable
private fun CompetitionPickerContent(
    title: String,
    leagues: List<Competition>,
    onSelect: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(title),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            leagues.forEach { league ->
                LightText(
                    text = league.name,
                    variant = LightTextVariant.Detail,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = { onSelect(league.id, league.name) })
                        .padding(vertical = 0.85f.gridUnitsAsDp()),
                )
            }
        }
    }
}

// --- Standings ------------------------------------------------------------------

// POS narrowed from 0.13 to 0.08 (it only ever holds 1-2 digits) so TEAM starts further left,
// closing up the blank gap that used to sit in front of team names; the width that frees up plus
// TEAM's own reduction from 0.48 makes room for the new GF/GA columns below.
private val STANDINGS_POS_WEIGHT = 0.08f
private val STANDINGS_TEAM_WEIGHT = 0.34f
private val STANDINGS_MP_WEIGHT = 0.11f
private val STANDINGS_GF_WEIGHT = 0.11f
private val STANDINGS_GA_WEIGHT = 0.11f
private val STANDINGS_GD_WEIGHT = 0.12f
private val STANDINGS_PTS_WEIGHT = 0.13f

@Composable
private fun StandingsTableContent(
    leagueId: Int,
    leagueName: String,
    leagueLogoBytes: ByteArray?,
    rows: List<StandingsRow>,
    isLoading: Boolean,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            // LightTopBarCenter has no image slot (Text/TwoLineDetail only, both vendored SDK
            // types this app can't extend), so the badge itself renders just below the bar rather
            // than inline with the title.
            center = LightTopBarCenter.Text(leagueName),
            modifier = Modifier.padding(bottom = if (leagueLogoBytes != null) 0.2f.gridUnitsAsDp() else 0.5f.gridUnitsAsDp()),
        )

        val leagueLogoBitmap = leagueLogoBytes?.let { bytes ->
            remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
        }
        if (leagueLogoBitmap != null) {
            Image(
                bitmap = leagueLogoBitmap,
                contentDescription = "$leagueName badge",
                contentScale = ContentScale.Fit,
                colorFilter = leagueLogoColorFilter(leagueId),
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(2.2f.gridUnitsAsDp())
                    .padding(bottom = 0.5f.gridUnitsAsDp()),
            )
        }

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(text = "fetching standings...", variant = LightTextVariant.Fine)
            }
        } else if (rows.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(
                    text = "Standings aren't available for this league right now.",
                    variant = LightTextVariant.Fine,
                    align = TextAlign.Center,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                )
            }
        } else {
            // Non-null StandingsRow.group only for group-stage competitions (UEFA Champions
            // League / Europa League) — see ApiFootballStandingsLeagueDto.toStandingsRows's doc
            // comment in SoccerModels.kt for the two real shapes this was built against.
            val showGroupHeaders = rows.any { it.group != null }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 1f.gridUnitsAsDp(),
                        end = (1f + scrollBarGutterUnits(LightScrollBarPosition.Outside)).gridUnitsAsDp(),
                    ),
            ) {
                StandingsHeaderRow()
            }
            LightScrollView(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                var currentGroup: String? = null
                rows.forEachIndexed { index, row ->
                    val startsNewGroup = showGroupHeaders && row.group != null && row.group != currentGroup
                    if (startsNewGroup) {
                        currentGroup = row.group
                        LightText(
                            text = row.group.uppercase(),
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
                        )
                    } else if (index > 0) {
                        // Skipped right under a fresh group header — that header already reads as
                        // its own separator, so a divider directly beneath it would be redundant.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.15f)),
                        )
                    }
                    StandingsTableRow(row)
                }
            }
        }
    }
}

@Composable
private fun StandingsHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 0.4f.gridUnitsAsDp())) {
        LightText(text = "#", variant = LightTextVariant.Detail, lighten = true, modifier = Modifier.weight(STANDINGS_POS_WEIGHT))
        LightText(text = "TEAM", variant = LightTextVariant.Detail, lighten = true, modifier = Modifier.weight(STANDINGS_TEAM_WEIGHT))
        LightText(text = "MP", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_MP_WEIGHT))
        LightText(text = "GF", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_GF_WEIGHT))
        LightText(text = "GA", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_GA_WEIGHT))
        LightText(text = "GD", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_GD_WEIGHT))
        LightText(text = "PTS", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_PTS_WEIGHT))
    }
}

@Composable
private fun StandingsTableRow(row: StandingsRow) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 0.4f.gridUnitsAsDp()),
    ) {
        // Sized to match the column headers above (also Detail, after the one-size-larger pass
        // requested on top of the app's earlier global one-step-down pass) — see
        // font-size-audit.md recommendation #1: this is the densest row in the app (7 columns), and
        // matching the header size gives the data more room before a long team name has to
        // ellipsize.
        LightText(text = row.position.toString(), variant = LightTextVariant.Detail, modifier = Modifier.weight(STANDINGS_POS_WEIGHT))
        LightText(
            text = row.teamName,
            variant = LightTextVariant.Detail,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(STANDINGS_TEAM_WEIGHT),
        )
        LightText(text = row.played.toString(), variant = LightTextVariant.Detail, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_MP_WEIGHT))
        LightText(text = row.goalsFor.toString(), variant = LightTextVariant.Detail, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_GF_WEIGHT))
        LightText(text = row.goalsAgainst.toString(), variant = LightTextVariant.Detail, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_GA_WEIGHT))
        LightText(text = row.goalDifferenceLabel(), variant = LightTextVariant.Detail, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_GD_WEIGHT))
        LightText(text = row.points.toString(), variant = LightTextVariant.Detail, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_PTS_WEIGHT))
    }
}

private fun StandingsRow.goalDifferenceLabel(): String =
    if (goalDifference > 0) "+$goalDifference" else goalDifference.toString()

// --- Fixtures ------------------------------------------------------------------

@Composable
private fun FixturesContent(
    groups: List<FixtureDayGroup>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onMatchClick: (Fixture) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text("Results & Fixtures"),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(text = "fetching fixtures...", variant = LightTextVariant.Detail)
            }
        } else if (groups.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(
                    text = "No fixtures found for your leagues right now.",
                    variant = LightTextVariant.Detail,
                    align = TextAlign.Center,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                )
            }
        } else {
            val today = todayLocalDate()
            val targetIndex = remember(groups, today) {
                groups.indexOfFirst { it.date >= today }.takeIf { it >= 0 } ?: groups.lastIndex
            }
            val bringIntoViewRequester = remember(groups, today) { BringIntoViewRequester() }
            LaunchedEffect(groups, today) { bringIntoViewRequester.bringIntoView() }

            LightScrollView(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                // Two levels of grouping now, on request: a plain-text day header (was previously
                // itself a MatchGroupCard's title — moved out to a bare LightText since a day can
                // now contain several leagues' cards under it, not one flat match list), then one
                // MatchGroupCard per competition that has a match that day — same per-league
                // grouping/order Scores already uses (groupedForDisplay), just repeated per day.
                groups.forEachIndexed { dayIndex, day ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (dayIndex == 0) 0.dp else 1f.gridUnitsAsDp())
                            .let { if (dayIndex == targetIndex) it.bringIntoViewRequester(bringIntoViewRequester) else it },
                    ) {
                        LightText(
                            text = day.dateLabel,
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
                        )
                        day.leagueGroups.forEachIndexed { leagueIndex, leagueGroup ->
                            MatchGroupCard(
                                title = leagueGroup.leagueName.uppercase(),
                                matches = leagueGroup.matches,
                                modifier = Modifier.padding(top = if (leagueIndex == 0) 0.dp else 0.75f.gridUnitsAsDp()),
                                showFinishedStatus = false,
                                allowKickoffLabelWrap = true,
                                // On request: when several of this league's matches on this day
                                // kick off at the exact same instant, only the first shows a time —
                                // the rest leave that slot blank instead of repeating it.
                                suppressRepeatedKickoffTime = true,
                                onMatchClick = onMatchClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- My Team ---------------------------------------------------------------------

@Composable
private fun MyTeamTeamPickerContent(
    leagueName: String,
    teams: List<StandingsRow>,
    isLoading: Boolean,
    onSelect: (Int, String) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(leagueName),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(text = "fetching teams...", variant = LightTextVariant.Detail)
            }
        } else {
            LightScrollView(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                teams.sortedBy { it.teamName }.forEach { team ->
                    LightText(
                        text = team.teamName,
                        variant = LightTextVariant.Detail,
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable(onClick = { onSelect(team.teamId, team.teamName) })
                            .padding(vertical = 0.85f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyTeamContent(
    summary: MyTeamSummary?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onMatchClick: (Fixture) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(summary?.teamName ?: "My Team"),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(text = "fetching your team...", variant = LightTextVariant.Detail)
            }
        } else if (summary == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(
                    text = "Couldn't load My Team right now.",
                    variant = LightTextVariant.Detail,
                    align = TextAlign.Center,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                )
            }
        } else {
            LightScrollView(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                // Crest + today/next-match placeholder, moved inside the scroll view on request so
                // this row scrolls away with the rest of the content instead of staying pinned at
                // the top. The standings block that used to sit under a centered crest here was
                // dropped entirely (see MyTeamSummary.standingsRow's doc comment for why the field
                // itself is still fetched).
                MyTeamHeaderRow(summary = summary, onMatchClick = onMatchClick, modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()))
                // Order below is deliberate: recent results, then next results, then who's out —
                // injuries/suspensions dropped to the bottom of the scroll instead of leading it.
                if (summary.recentFixtures.isNotEmpty()) {
                    MatchGroupCard(
                        title = "RECENT RESULTS",
                        matches = summary.recentFixtures,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                        showFinishedStatus = false,
                        highlightTeamId = summary.teamId,
                        onMatchClick = onMatchClick,
                    )
                }
                if (summary.upcomingFixtures.isNotEmpty()) {
                    MatchGroupCard(
                        title = "UPCOMING",
                        matches = summary.upcomingFixtures,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                        showDate = true,
                        allowKickoffLabelWrap = true,
                        // On request: no match here has a score yet anyway, so the reserved score
                        // column was always empty — dropping it gives the "X vs Y" text that width
                        // back instead of wrapping to a second line.
                        showScoreSlot = false,
                        onMatchClick = onMatchClick,
                    )
                }
                if (summary.unavailable.isNotEmpty()) {
                    UnavailableBlock(
                        summary.unavailable,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
                    )
                }
                if (summary.featuredFixture == null && summary.unavailable.isEmpty() &&
                    summary.upcomingFixtures.isEmpty() && summary.recentFixtures.isEmpty()
                ) {
                    LightText(
                        text = "No data available for this team right now.",
                        variant = LightTextVariant.Detail,
                        align = TextAlign.Center,
                        lighten = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 1.5f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}

/** The crest + today/next-match placeholder row at the top of My Team's scroll content. Crest on
 * the left (was centered full-width above the standings block this replaced); the featured
 * match — [MyTeamSummary.featuredFixture] — on the right, clickable the same as any other match
 * row on this screen. */
@Composable
private fun MyTeamHeaderRow(summary: MyTeamSummary, onMatchClick: (Fixture) -> Unit, modifier: Modifier = Modifier) {
    // remember(bytes) keys on the byte array's identity, not its content — cheap enough here since
    // a new MyTeamSummary (and therefore a new array) only shows up once per fetch, never once per
    // frame. No AsyncImage here: the Light SDK's dependency allow-list rejects every third-party
    // image loader (confirmed against a real build failure for Coil), so this decodes bytes
    // MyTeamSummary already fetched (see ApiFootballApi.fetchMyTeamSummary) by hand, same as every
    // other image in this screen.
    val teamLogoBitmap = summary.teamLogoBytes?.let { bytes ->
        remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        if (teamLogoBitmap != null) {
            Image(
                bitmap = teamLogoBitmap,
                contentDescription = "${summary.teamName} crest",
                contentScale = ContentScale.Fit,
                // Shrunk from the old centered crest's 6f down to 4f to leave room for the featured
                // match placeholder beside it — the user only asked to move this to the left, not
                // resize it, so flagging this size change explicitly rather than burying it.
                modifier = Modifier.size(4f.gridUnitsAsDp()).padding(end = 0.75f.gridUnitsAsDp()),
            )
        }
        FeaturedMatchPlaceholder(
            summary = summary,
            onMatchClick = onMatchClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/** The today/next match placeholder beside the crest: opponent badge + name, and either a kickoff
 * time (match hasn't started) or the live/final box score (match has). [MyTeamSummary.featuredFixture]
 * is today's match if the team has one, otherwise its next upcoming fixture — see that field's doc
 * comment and [ApiFootballApi.fetchMyTeamSummary] for exactly how it's picked. */
@Composable
private fun FeaturedMatchPlaceholder(summary: MyTeamSummary, onMatchClick: (Fixture) -> Unit, modifier: Modifier = Modifier) {
    val fixture = summary.featuredFixture
    if (fixture == null) {
        LightText(
            text = "No upcoming match scheduled.",
            variant = LightTextVariant.Superfine,
            lighten = true,
            modifier = modifier,
        )
        return
    }

    val isHome = fixture.homeTeamId == summary.teamId
    val opponentName = if (isHome) fixture.awayTeamName else fixture.homeTeamName
    // formatFixtureDateHeader already returns "TODAY" for the real device-local date and a short
    // "SAT, SEP 12"-style header otherwise, so this reuses it directly rather than re-deriving the
    // same "is this today?" check a second time.
    val dateLabel = fixture.localDate()?.let { formatFixtureDateHeader(it) } ?: "NEXT MATCH"
    val opponentLogoBitmap = summary.featuredOpponentLogoBytes?.let { bytes ->
        remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }

    Column(modifier = modifier.lightClickable(onClick = { onMatchClick(fixture) })) {
        LightText(text = dateLabel, variant = LightTextVariant.Superfine, lighten = true)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 0.2f.gridUnitsAsDp()),
        ) {
            if (opponentLogoBitmap != null) {
                Image(
                    bitmap = opponentLogoBitmap,
                    contentDescription = "$opponentName crest",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(1.4f.gridUnitsAsDp()).padding(end = 0.35f.gridUnitsAsDp()),
                )
            }
            LightText(
                text = "vs $opponentName",
                variant = LightTextVariant.Detail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        // hasScore is true from kickoff onward (live, halftime, finished — see Fixture.hasScore),
        // not just once a match is over, so a live match's running score shows here too, not just
        // the final one. statusLabel() already resolves to a bare kickoff time for a still-scheduled
        // match (see its doc comment in SoccerFormatting.kt) — exactly the "what time the game is"
        // case the user asked for, with no separate branch needed here.
        LightText(
            text = if (fixture.hasScore) "${fixture.scoreLabel()} · ${fixture.statusLabel()}" else fixture.statusLabel(),
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(top = 0.1f.gridUnitsAsDp()),
        )
    }
}

@Composable
private fun UnavailableBlock(players: List<UnavailablePlayer>, modifier: Modifier = Modifier) {
    val injured = players.filter { it.kind == UnavailabilityKind.INJURED }
    val suspended = players.filter { it.kind == UnavailabilityKind.SUSPENDED }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(1.2f.gridUnitsAsDp()))
            .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.08f))
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
    ) {
        // Whole section bumped one size on request: Superfine (16) -> Detail (20), Detail -> Fine
        // (25) for player.playerName, so the relative sizing within this block is preserved.
        LightText(text = "UNAVAILABLE FOR NEXT MATCH", variant = LightTextVariant.Detail, lighten = true)
        if (injured.isNotEmpty()) {
            UnavailableGroup(label = "Injured", players = injured, modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()))
        }
        if (suspended.isNotEmpty()) {
            UnavailableGroup(label = "Suspended", players = suspended, modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()))
        }
    }
}

@Composable
private fun UnavailableGroup(label: String, players: List<UnavailablePlayer>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        LightText(text = label, variant = LightTextVariant.Detail, lighten = true)
        players.forEach { player ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = 0.2f.gridUnitsAsDp())) {
                LightText(
                    text = player.playerName,
                    variant = LightTextVariant.Fine,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                LightText(
                    text = if (player.isOut) "Out" else "Doubtful",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    align = TextAlign.End,
                )
            }
            // Was Fine, fixed to Detail: despite the name, Fine (25, design units) renders larger
            // than Detail (20) in this SDK's real type scale — see the font-size audit doc — so
            // Fine was actually making this secondary reason text bigger than the "Out"/"Doubtful"
            // label above it, the opposite of the intended caption-sized, de-emphasized treatment.
            // Shrunk to Superfine (16) by a later global one-step-down pass, now bumped back to
            // Detail on request, along with the rest of this section.
            LightText(
                text = player.reason,
                variant = LightTextVariant.Detail,
                lighten = true,
                modifier = Modifier.padding(top = 0.05f.gridUnitsAsDp()),
            )
        }
    }
}

// --- Match detail ------------------------------------------------------------

private enum class DetailTab(val label: String) {
    STATS("Stats"),
    TIMELINE("Events"),
    HOME_LINEUP("Home"),
    AWAY_LINEUP("Away"),
}

@Composable
private fun MatchDetailContent(mode: ScoreScreenMode.MatchDetailScreen, onBack: () -> Unit) {
    var selectedTab by remember(mode.fixtureId) { mutableStateOf(DetailTab.STATS) }

    // No team-colors dataset exists anywhere in this app (API-Football's crest/logo field is the
    // only per-team visual data it sends — kit colors aren't part of that response), so this reads
    // a representative color straight from the crest pixels already fetched for the header above.
    // See extractCrestAccentColor's doc comment for what that approximates and where it can't match
    // a shirt color a crest wouldn't reflect (e.g. a badge that's mostly white with a colored
    // crest mark, worn with a colored shirt).
    val homeTeamColor = rememberCrestAccentColor(mode.homeTeamLogoBytes)
    val awayTeamColor = rememberCrestAccentColor(mode.awayTeamLogoBytes)

    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text("Match"),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
        ) {
            MatchDetailHeader(
                homeTeamName = mode.homeTeamName,
                awayTeamName = mode.awayTeamName,
                homeTeamLogoBytes = mode.homeTeamLogoBytes,
                awayTeamLogoBytes = mode.awayTeamLogoBytes,
                scoreLabel = mode.scoreLabel,
                statusLabel = mode.statusLabel,
                isLive = mode.isLive,
                goalEvents = mode.detail?.events?.filter { it.type == MatchEventType.GOAL } ?: emptyList(),
            )

            val detail = mode.detail
            when {
                mode.isLoading -> {
                    LightText(
                        text = "fetching match details...",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 1.5f.gridUnitsAsDp()),
                    )
                }

                detail == null || detail.isEmpty() -> {
                    LightText(
                        text = "No additional details available for this match yet.",
                        variant = LightTextVariant.Detail,
                        align = TextAlign.Center,
                        lighten = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()).padding(top = 1.5f.gridUnitsAsDp()),
                    )
                }

                else -> {
                    DetailTabRow(
                        selected = selectedTab,
                        onSelect = { selectedTab = it },
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                    )
                    when (selectedTab) {
                        DetailTab.STATS -> {
                            if (detail.stats.isNotEmpty()) {
                                MatchStatsSection(detail.stats, modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()))
                            } else {
                                NoDataForTab(text = "No stats available for this match yet.")
                            }
                        }
                        DetailTab.TIMELINE -> {
                            if (detail.events.isNotEmpty()) {
                                EventTimelineSection(detail.events, modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()))
                            } else {
                                NoDataForTab(text = "No events available for this match yet.")
                            }
                        }
                        DetailTab.HOME_LINEUP -> LineupSection(
                            teamName = mode.homeTeamName,
                            lineup = detail.lineups.home,
                            teamColor = homeTeamColor,
                            coachPhotoBytes = mode.homeCoachPhotoBytes,
                            modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
                        )
                        DetailTab.AWAY_LINEUP -> LineupSection(
                            teamName = mode.awayTeamName,
                            lineup = detail.lineups.away,
                            teamColor = awayTeamColor,
                            coachPhotoBytes = mode.awayCoachPhotoBytes,
                            modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
                        )
                    }
                }
            }
        }
    }
}

private fun MatchDetail.isEmpty(): Boolean =
    stats.isEmpty() && events.isEmpty() && lineups.home == null && lineups.away == null

@Composable
private fun MatchDetailHeader(
    homeTeamName: String,
    awayTeamName: String,
    homeTeamLogoBytes: ByteArray?,
    awayTeamLogoBytes: ByteArray?,
    scoreLabel: String,
    statusLabel: String,
    isLive: Boolean,
    goalEvents: List<MatchEvent> = emptyList(),
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MatchDetailTeamBlock(name = homeTeamName, logoBytes = homeTeamLogoBytes, modifier = Modifier.weight(1f))
            // Was Title, shrunk to Heading (38, design units) in an earlier session — the crest
            // icons beside each team name below need the horizontal room Title used to take up.
            // Now Copy (30) after this round's global one-step-down pass; still the visually
            // dominant element on the row, just not oversized.
            LightText(
                text = scoreLabel,
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 0.5f.gridUnitsAsDp()),
            )
            MatchDetailTeamBlock(name = awayTeamName, logoBytes = awayTeamLogoBytes, modifier = Modifier.weight(1f))
        }

        if (isLive) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 0.4f.gridUnitsAsDp())
                    .clip(RoundedCornerShape(50.dp))
                    .background(LightThemeTokens.colors.content.copy(alpha = 0.12f))
                    .padding(horizontal = 0.4f.gridUnitsAsDp(), vertical = 0.15f.gridUnitsAsDp()),
            ) {
                LightText(text = statusLabel, variant = LightTextVariant.Superfine)
            }
        } else {
            LightText(
                text = statusLabel,
                variant = LightTextVariant.Superfine,
                lighten = true,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 0.4f.gridUnitsAsDp()),
            )
        }

        if (goalEvents.isNotEmpty()) {
            GoalScorersRow(
                homeScorers = goalEvents.filter { it.teamName == homeTeamName },
                awayScorers = goalEvents.filter { it.teamName == awayTeamName },
                modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
            )
        }
    }
}

/** One team's crest + name for [MatchDetailHeader], stacked and centered within its half of the
 * row. No AsyncImage available here — same constraint as [MyTeamContent]'s own crest (the Light
 * SDK's dependency allow-list rejects every third-party image loader) — so this hand-decodes the
 * bytes [SoccerViewModel.openMatchDetail] already fetched via [ApiFootballApi.fetchMatchCrests].
 * Renders name-only, same as before this feature existed, if [logoBytes] hasn't arrived yet (it's
 * fetched separately from the rest of the header, see that fetch's own doc comment) or the fetch
 * failed — a missing crest was never a reason to hide the name. */
@Composable
private fun MatchDetailTeamBlock(name: String, logoBytes: ByteArray?, modifier: Modifier = Modifier) {
    val logoBitmap = logoBytes?.let { bytes ->
        remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        if (logoBitmap != null) {
            Image(
                bitmap = logoBitmap,
                contentDescription = "$name crest",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(2f.gridUnitsAsDp())
                    .padding(bottom = 0.2f.gridUnitsAsDp()),
            )
        }
        // Was Detail (20, design units), not Copy (30): the API doesn't give us a short/abbreviated
        // team name, so the only lever we have to stop long names ("Manchester City", "FC
        // Copenhagen") from wrapping mid-word is a smaller font. Shrunk to Superfine (16) by an
        // earlier global one-step-down pass, then back up to Detail on request — one size larger
        // than the rest of this file's Superfine baseline. maxLines/Ellipsis stays as a safety net
        // for names this still doesn't fit.
        LightText(
            text = name,
            variant = LightTextVariant.Detail,
            align = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Decodes [logoBytes] once per byte-array identity and samples a representative color from it —
 * see [extractCrestAccentColor] for the sampling itself. Returns null while bytes haven't arrived,
 * on a decode failure, or when the crest has no qualifying pixels (extractCrestAccentColor's own
 * fallback case). */
@Composable
private fun rememberCrestAccentColor(logoBytes: ByteArray?): Color? = remember(logoBytes) {
    logoBytes?.let { bytes ->
        runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            .getOrNull()
            ?.let { extractCrestAccentColor(it) }
    }
}

/**
 * A representative "team color" sampled directly from a crest bitmap's pixels. There's no
 * team-colors dataset anywhere in this app, and none is practical to maintain by hand across the
 * 15 competitions this build tracks (see the competition list in SoccerModels.kt) — API-Football's
 * crest/logo URL is the only per-team visual data it sends at all, so this reads the color from
 * that image instead of a lookup table.
 *
 * This approximates a crest's dominant *badge* color, not necessarily the team's actual kit
 * color — the two usually match closely enough to read as "the team's color" (a mostly-red badge
 * usually belongs to a team that wears red), but not always: a badge that's mostly a neutral shield
 * shape with one small colored crest mark, worn by a team in an unrelated kit color, is a case this
 * can't get right without real kit-color data this app has no source for. Flagging this honestly
 * rather than presenting it as authoritative.
 *
 * Implementation: samples a bounded grid of pixels (not every pixel — a few hundred reads is
 * plenty for a crest-sized image and keeps this cheap), skips near-white/near-black/low-saturation
 * pixels (crest backgrounds, outline strokes, shading — not the badge's identifying color), buckets
 * the rest by coarsened RGB so antialiased edge pixels of the same underlying color count together,
 * and returns the most common bucket's average color. Returns null for a crest with no qualifying
 * pixels (e.g. a genuinely monochrome black/white badge) — callers fall back to the neutral dot
 * color this used before team colors existed.
 */
private fun extractCrestAccentColor(bitmap: Bitmap): Color? {
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 0 || height <= 0) return null

    // ~40 samples per axis keeps this to well under a thousand pixel reads even for a few-hundred-
    // pixel crest image, rather than reading every pixel.
    val strideX = (width / 40).coerceAtLeast(1)
    val strideY = (height / 40).coerceAtLeast(1)

    data class BucketAccum(var count: Int = 0, var r: Int = 0, var g: Int = 0, var b: Int = 0)
    val buckets = HashMap<Int, BucketAccum>()

    var y = 0
    while (y < height) {
        var x = 0
        while (x < width) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha >= 128) {
                val r = (pixel ushr 16) and 0xFF
                val g = (pixel ushr 8) and 0xFF
                val b = pixel and 0xFF
                val max = maxOf(r, g, b)
                val min = minOf(r, g, b)
                val saturation = if (max == 0) 0f else (max - min).toFloat() / max
                val brightness = max / 255f
                // Saturation/brightness bounds exclude near-white, near-black, and near-gray
                // pixels — the crest's background and outline strokes, not its identifying color.
                if (saturation > 0.25f && brightness in 0.15f..0.95f) {
                    // 5 bits/channel (32 buckets/channel) groups near-identical antialiased shades
                    // together instead of splitting their vote across many almost-equal colors.
                    val bucketKey = ((r shr 3) shl 10) or ((g shr 3) shl 5) or (b shr 3)
                    val accum = buckets.getOrPut(bucketKey) { BucketAccum() }
                    accum.count++
                    accum.r += r
                    accum.g += g
                    accum.b += b
                }
            }
            x += strideX
        }
        y += strideY
    }

    val winner = buckets.values.maxByOrNull { it.count } ?: return null
    // .toFloat() before dividing by count — plain Int division here would truncate each channel's
    // average (e.g. 100/3 == 33, not 33.3) before it's even scaled down to Color's 0f..1f range.
    return Color(
        red = (winner.r.toFloat() / winner.count) / 255f,
        green = (winner.g.toFloat() / winner.count) / 255f,
        blue = (winner.b.toFloat() / winner.count) / 255f,
    )
}

/** Black or white, whichever contrasts better against [background] — used for text drawn on top of
 * a per-team accent color (see [PitchNumberDot]), since that color is arbitrary (whatever a real
 * crest's dominant hue turns out to be) and the theme's default text color can't be assumed to
 * read against it the way it does against the app's own neutral surfaces. */
private fun legibleTextColorOn(background: Color): Color {
    val luminance = 0.299f * background.red + 0.587f * background.green + 0.114f * background.blue
    return if (luminance > 0.6f) Color.Black else Color.White
}

/** A compact, persistent goalscorer line shown under the score/status in [MatchDetailHeader],
 * regardless of which detail tab (Stats/Timeline/Lineups) is selected below — mirrors the
 * goalscorer summary most match-center UIs show under the box score. Home scorers align under
 * the home team name, away scorers under the away team name, matching the score row's layout. */
@Composable
private fun GoalScorersRow(
    homeScorers: List<MatchEvent>,
    awayScorers: List<MatchEvent>,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            homeScorers.forEach { event ->
                LightText(
                    text = event.goalScorerLabel(),
                    variant = LightTextVariant.Superfine,
                    lighten = true,
                    align = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Box(modifier = Modifier.width(1.5f.gridUnitsAsDp()))
        Column(modifier = Modifier.weight(1f)) {
            awayScorers.forEach { event ->
                LightText(
                    text = event.goalScorerLabel(),
                    variant = LightTextVariant.Superfine,
                    lighten = true,
                    align = TextAlign.Start,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** "Haaland 24'", or "Haaland (Penalty) 24'" when [MatchEvent.scorerQualifier] is set to something
 * other than a plain goal (see the doc comment on the "Goal" branch of
 * [ApiFootballEventDto.toMatchEvent] in SoccerModels.kt for what qualifier values are and aren't
 * verified against a real response). Last name only — [scorer] is API-Football's full player name
 * (e.g. "Erling Haaland"), which this compact header strip doesn't have room for two of side by
 * side; the full name still appears in the Events tab's own timeline row, which isn't
 * space-constrained the same way. */
private fun MatchEvent.goalScorerLabel(): String {
    val name = scorer?.substringAfterLast(' ') ?: "Goal"
    return if (scorerQualifier != null) "$name ($scorerQualifier) $minuteLabel" else "$name $minuteLabel"
}

@Composable
private fun MatchStatsSection(stats: List<MatchStatRow>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        stats.forEach { row ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(vertical = 0.35f.gridUnitsAsDp()),
            ) {
                // Whole row bumped one size on request: values Detail (20) -> Fine (25), label
                // Superfine (16) -> Detail (20) — same one-step-larger relationship preserved
                // between the two.
                LightText(text = row.homeValue, variant = LightTextVariant.Fine, align = TextAlign.Center, modifier = Modifier.weight(0.25f))
                LightText(
                    text = prettifyStatLabel(row.label),
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    align = TextAlign.Center,
                    modifier = Modifier.weight(0.5f),
                )
                LightText(text = row.awayValue, variant = LightTextVariant.Fine, align = TextAlign.Center, modifier = Modifier.weight(0.25f))
            }
        }
    }
}

/** API-Football's raw stat `type` strings aren't curated for display in this build (see the doc
 * comment on [ApiFootballStatisticsResponse] in SoccerModels.kt) — this just tidies up casing
 * (e.g. "expected_goals" -> "Expected Goals") rather than claiming to know the full set. */
private fun prettifyStatLabel(raw: String): String =
    raw.replace('_', ' ').split(' ').filter { it.isNotBlank() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

/** The match detail screen's Event Timeline tab — a flat, chronological, text-only feed (goals,
 * substitutions, cards, VAR reviews). [MatchEvent.headline]/[subtext] are already formatted
 * per-event-type by [ApiFootballEventDto.toMatchEvent] in SoccerModels.kt, so this just lays them
 * out; it doesn't need to know the event type itself beyond ordering. */
@Composable
private fun EventTimelineSection(events: List<MatchEvent>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        events.forEachIndexed { index, event ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.1f)),
                )
            }
            EventTimelineRow(event)
        }
    }
}

/** Small per-type marker shown next to each [EventTimelineRow], mirroring the icon column common
 * match-center UIs (e.g. FotMob) show beside goals/cards/subs — this project has no third-party
 * icon library or image loader available (see [MyTeamContent]'s doc comment on why), so every
 * marker here is either a plain Unicode glyph (renders through the system font, no asset needed)
 * or a small Compose-drawn shape, rather than a bitmap/vector icon asset. Card color is inferred
 * from [MatchEvent.headline] (it always leads with the raw "Yellow Card"/"Red Card"/"Second Yellow
 * card" detail text — see [ApiFootballEventDto.toMatchEvent]'s "Card" branch in SoccerModels.kt)
 * since the domain model doesn't carry a separate structured card-color field. */
@Composable
private fun MatchEventIcon(event: MatchEvent, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(1.4f.gridUnitsAsDp()), contentAlignment = Alignment.Center) {
        when (event.type) {
            MatchEventType.GOAL -> LightText(text = "⚽", variant = LightTextVariant.Superfine, align = TextAlign.Center)
            MatchEventType.SUBSTITUTION -> LightText(text = "⇄", variant = LightTextVariant.Superfine, align = TextAlign.Center)
            MatchEventType.CARD -> {
                val isRed = event.headline.contains("red", ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(width = 0.75f.gridUnitsAsDp(), height = 1.05f.gridUnitsAsDp())
                        .clip(RoundedCornerShape(0.12f.gridUnitsAsDp()))
                        .background(if (isRed) CARD_RED else CARD_YELLOW),
                )
            }
            MatchEventType.VAR, MatchEventType.OTHER -> Box(
                modifier = Modifier
                    .size(0.5f.gridUnitsAsDp())
                    .clip(CircleShape)
                    .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.4f)),
            )
        }
    }
}

private val CARD_YELLOW = Color(0xFFFBC02D)
private val CARD_RED = Color(0xFFD32F2F)

@Composable
private fun EventTimelineRow(event: MatchEvent) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 0.5f.gridUnitsAsDp())) {
        // Minute column bumped one size on request, Superfine (16) -> Detail (20) — the secondary
        // team/subtext line below the headline stays at Superfine, unchanged.
        LightText(
            text = event.minuteLabel,
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.width(2.4f.gridUnitsAsDp()),
        )
        MatchEventIcon(
            event = event,
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 0.5f.gridUnitsAsDp()),
        )
        Column(modifier = Modifier.weight(1f)) {
            // Was Copy, shrunk to Detail (matching the minute label and the secondary team/subtext
            // line below it, so the whole Events row read at one smaller, consistent size instead
            // of the headline standing out as the biggest text in the tab), then Superfine after a
            // later global one-step-down pass. Bumped one size on request, back to Detail — this is
            // the event's own description text ("Goal — Haaland", "Yellow Card — Smith", etc.), see
            // MatchEvent.headline's doc comment in SoccerModels.kt.
            LightText(text = event.headline, variant = LightTextVariant.Detail, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val secondary = listOfNotNull(event.teamName.takeIf { it.isNotBlank() }, event.subtext).joinToString(" · ")
            if (secondary.isNotBlank()) {
                LightText(
                    text = secondary,
                    variant = LightTextVariant.Superfine,
                    lighten = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 0.1f.gridUnitsAsDp()),
                )
            }
        }
    }
}

@Composable
private fun DetailTabRow(selected: DetailTab, onSelect: (DetailTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.4f.gridUnitsAsDp()),
    ) {
        DetailTab.entries.forEach { tab ->
            DetailTabButton(
                text = tab.label,
                isSelected = tab == selected,
                onClick = { onSelect(tab) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun DetailTabButton(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(0.8f.gridUnitsAsDp()))
            .background(LightThemeTokens.colors.contentSecondary.copy(alpha = if (isSelected) 0.2f else 0.08f))
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.5f.gridUnitsAsDp(), horizontal = 0.15f.gridUnitsAsDp()),
    ) {
        LightText(
            text = text,
            variant = LightTextVariant.Superfine,
            lighten = !isSelected,
            align = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun NoDataForTab(text: String) {
    LightText(
        text = text,
        variant = LightTextVariant.Detail,
        align = TextAlign.Center,
        lighten = true,
        modifier = Modifier.fillMaxWidth().padding(top = 1.5f.gridUnitsAsDp()),
    )
}

/**
 * One team's starting XI laid out by real pitch row, using API-Football's own `grid` field
 * directly — unlike the ESPN variant of this tool, there's no client-side formation *inference*
 * here, since API-Football sends both a `formation` label and a real per-player pitch position.
 * See [groupedByPitchRow] in SoccerModels.kt.
 *
 * Laid out as a vertical pitch: each pitch line (GK, defense, midfield, attack) is a horizontal
 * band of number-only dots, and the bands stack bottom-to-top with the goalkeeper's band at the
 * very bottom — [groupedByPitchRow] returns rows goalkeeper-first, so this reverses that list
 * before rendering, per that function's own doc comment in SoccerModels.kt. Both home and away
 * render the same way; there's no more mirroring one team's pitch left-to-right against the
 * other's the way the old horizontal layout did, since a shared "keeper at the bottom" orientation
 * doesn't need it. Player names moved out of the pitch itself (a name per dot didn't leave enough
 * width when the same players were laid out as up-to-5-wide columns before this rewrite — see the
 * git history if you want that version) and into [LineupRosterList] alongside it, in the same
 * top-to-bottom order as the pitch bands so the two stay easy to cross-reference by number.
 */
@Composable
private fun LineupSection(
    teamName: String,
    lineup: TeamLineup?,
    teamColor: Color?,
    coachPhotoBytes: ByteArray?,
    modifier: Modifier = Modifier,
) {
    if (lineup == null || lineup.startXI.isEmpty()) {
        NoDataForTab(text = "No lineup available for $teamName yet.")
        return
    }

    // Reversed so the most advanced line renders first (topmost) and the goalkeeper's line last
    // (bottommost) — see this function's own doc comment above.
    val pitchRows = remember(lineup) { lineup.startXI.groupedByPitchRow().asReversed() }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 0.6f.gridUnitsAsDp()),
        ) {
            // This row already shares its width with the formation label, so even Detail-size
            // team names ("Manchester City") can still be too wide to fit on one line — maxLines=1
            // + Ellipsis trades a truncated name ("Mancheste…") for avoiding an ugly mid-word wrap.
            // Bumped one size on request (Superfine -> Detail), along with the rest of this tab
            // except PitchNumberDot's own number — see that composable's call site below.
            LightText(
                text = teamName,
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            lineup.formation?.let { LightText(text = it, variant = LightTextVariant.Detail, lighten = true) }
        }

        Row(modifier = Modifier.fillMaxWidth()) {
            // Same top-to-bottom order as the pitch bands to the right of it, so a number on the
            // pitch and its name in this list line up roughly at a glance without needing a legend.
            LineupRosterList(
                pitchRows = pitchRows,
                modifier = Modifier.weight(0.44f).padding(end = 0.6f.gridUnitsAsDp()),
            )

            Column(
                modifier = Modifier
                    .weight(0.56f)
                    .clip(RoundedCornerShape(1.2f.gridUnitsAsDp()))
                    .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.08f))
                    .padding(vertical = 0.8f.gridUnitsAsDp(), horizontal = 0.3f.gridUnitsAsDp()),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.9f.gridUnitsAsDp()),
            ) {
                pitchRows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
                            0.3f.gridUnitsAsDp(),
                            Alignment.CenterHorizontally,
                        ),
                    ) {
                        row.forEach { player -> PitchNumberDot(player, dotColor = teamColor) }
                    }
                }
            }
        }

        lineup.coachName?.let { coachName ->
            // Real headshot data, curl-confirmed on this exact endpoint at the user's request —
            // see the doc comment on ApiFootballCoachDto.photo in SoccerModels.kt. Same
            // decode-bytes-by-hand pattern as every other image in this app (no AsyncImage
            // available), and the same "just omit it" fallback when there's no photo (coach not
            // sent an id/photo, or the fetch failed) rather than a placeholder.
            val coachBitmap = coachPhotoBytes?.let { bytes ->
                remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.3f.gridUnitsAsDp()),
                modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
            ) {
                if (coachBitmap != null) {
                    Image(
                        bitmap = coachBitmap,
                        contentDescription = "$coachName headshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(1.6f.gridUnitsAsDp()).clip(CircleShape),
                    )
                }
                // Was Fine, fixed to Detail — see the font-size audit doc: Fine (25, design units)
                // is bigger than Detail (20) in this SDK's real type scale despite the name
                // suggesting the opposite, so this caption was rendering almost as large as the
                // primary player names above it. Shrunk to Superfine (16) by a later global
                // one-step-down pass, now bumped back to Detail on request along with the rest of
                // this tab (except PitchNumberDot's own number).
                LightText(text = "Coach: $coachName", variant = LightTextVariant.Detail, lighten = true)
            }
        }

        if (lineup.substitutes.isNotEmpty()) {
            SubstitutesBlock(lineup.substitutes, modifier = Modifier.padding(top = 1f.gridUnitsAsDp()))
        }
    }
}

/** The starting XI's names, in the same top-to-bottom (most-advanced-line-first) order as
 * [LineupSection]'s pitch bands — same visual role the old per-dot name label used to serve,
 * before there was enough width per player to keep names legible once dots stopped stretching
 * across up to 5 side-by-side columns. Same row shape [SubstitutesBlock] already uses (a
 * fixed-width number column beside a name that can truncate) for a consistent look across the
 * lineup tab. A little extra top padding between each pitch-line group (skipped for the very
 * first) gives a rough visual seam lining this list up with the row bands beside it, without
 * needing an explicit divider or label for every line. */
@Composable
private fun LineupRosterList(pitchRows: List<List<LineupPlayer>>, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        pitchRows.forEachIndexed { rowIndex, row ->
            row.forEachIndexed { playerIndex, player ->
                // Only the first player of each new group (not the very first group) gets the
                // extra gap above it — this marks the seam between pitch-line groups without
                // spacing every player within a group apart from their line-mates too.
                val topPadding = if (rowIndex > 0 && playerIndex == 0) 0.3f.gridUnitsAsDp() else 0.dp
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = topPadding, bottom = 0.15f.gridUnitsAsDp()),
                ) {
                    // Was Copy, shrunk to Detail (matching the team name/formation row and
                    // "Coach: {name}" above and below this list, rather than standing out as
                    // noticeably bigger), then Superfine after a later global one-step-down pass.
                    // Bumped back to Detail on request, along with the rest of this tab — except
                    // PitchNumberDot's own number (the pitch-diagram circles), which stays put.
                    // This roster-list number is plain text, not "behind a circle", so it's
                    // included in the bump.
                    LightText(
                        text = player.number?.toString() ?: "-",
                        variant = LightTextVariant.Detail,
                        lighten = true,
                        modifier = Modifier.width(1.6f.gridUnitsAsDp()),
                    )
                    LightText(
                        text = player.name,
                        variant = LightTextVariant.Detail,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun PitchNumberDot(player: LineupPlayer, dotColor: Color?, modifier: Modifier = Modifier) {
    // A saturated dotColor needs its own text color to stay legible — the theme's default content
    // color assumes the neutral, low-alpha background this dot had before team colors existed, and
    // can end up light-on-light or dark-on-dark against a real team hue.
    val numberColor = dotColor?.let { legibleTextColorOn(it) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(2.3f.gridUnitsAsDp())
            .clip(CircleShape)
            .background(dotColor?.copy(alpha = 0.55f) ?: LightThemeTokens.colors.contentSecondary.copy(alpha = 0.22f)),
    ) {
        // Deliberately left at Superfine (not bumped with the rest of the Lineup tab) — the user
        // asked to enlarge "all text one size Except for the numbers behind the circles", and this
        // dot's number is exactly that: it's already visually prominent inside its own colored
        // circle, unlike the roster list's plain-text numbers.
        LightText(text = player.number?.toString() ?: "-", variant = LightTextVariant.Superfine, color = numberColor)
    }
}

@Composable
private fun SubstitutesBlock(substitutes: List<LineupPlayer>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Whole block bumped one size on request (Superfine -> Detail), same as the rest of the
        // Lineup tab except PitchNumberDot's own number.
        LightText(text = "SUBSTITUTES", variant = LightTextVariant.Detail, lighten = true, modifier = Modifier.padding(bottom = 0.4f.gridUnitsAsDp()))
        substitutes.forEach { player ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 0.1f.gridUnitsAsDp())) {
                // Was Copy, shrunk to Detail (matching the starting XI roster list above, and the
                // team name/coach line, for one consistent size across the whole lineup tab rather
                // than the starters' names being smaller than the substitutes' own), then Superfine
                // after a later global one-step-down pass. Bumped back to Detail on request.
                LightText(
                    text = player.number?.toString() ?: "-",
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier.width(1.6f.gridUnitsAsDp()),
                )
                LightText(
                    text = player.name,
                    variant = LightTextVariant.Detail,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                player.position?.let {
                    LightText(text = it, variant = LightTextVariant.Detail, lighten = true, align = TextAlign.End)
                }
            }
        }
    }
}
