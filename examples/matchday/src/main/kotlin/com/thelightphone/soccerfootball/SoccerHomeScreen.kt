package com.thelightphone.soccerfootball

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.ImageBitmap
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
import java.io.File

@InitialScreen
class SoccerHomeScreen(sealedActivity: SealedLightActivity) :
    LightScreen<Unit, SoccerViewModel>(sealedActivity) {

    override val viewModelClass: Class<SoccerViewModel>
        get() = SoccerViewModel::class.java

    override fun createViewModel(): SoccerViewModel =
        SoccerViewModel(lightContext.dataStore, imageCacheDir = File(lightContext.filesDir, "image-cache"))

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
                        LoadingContent(title = "Matchday", message = mode.message)
                    }

                    is ScoreScreenMode.Scores -> {
                        ScoresContent(
                            days = mode.days,
                            teamLogos = mode.teamLogos,
                            lineupsAvailableFixtureIds = mode.lineupsAvailableFixtureIds,
                            onOpenSettings = viewModel::openSettings,
                            onOpenMyTeam = viewModel::openMyTeam,
                            onOpenCompetitions = viewModel::openCompetitions,
                            onOpenStandingsTable = viewModel::openStandingsTable,
                            onManualRefresh = viewModel::manualRefresh,
                            onMatchClick = viewModel::openMatchDetail,
                        )
                    }

                    is ScoreScreenMode.CompetitionPicker -> {
                        CompetitionPickerContent(
                            title = "Competitions",
                            leagues = mode.leagues,
                            onSelect = viewModel::openStandingsTable,
                            onBack = viewModel::backFromCompetitions,
                            emptyText = "Follow a league in Settings to see its table here.",
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
                        )
                    }

                    is ScoreScreenMode.Attribution -> {
                        AttributionContent(onBack = viewModel::closeAttribution)
                    }

                    is ScoreScreenMode.LeagueSelection -> {
                        LeagueSelectionContent(
                            rows = mode.rows,
                            leagueLogos = mode.leagueLogos,
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
                            selectedTab = mode.selectedTab,
                            onSelectTab = viewModel::selectStandingsTab,
                            statsTab = {
                                LeadersContent(
                                    mode = mode,
                                    onOpenStatPicker = viewModel::openStatPicker,
                                    onSelectStat = viewModel::selectLeaderStat,
                                    onSelectSort = viewModel::selectLeaderSort,
                                    onPlayerClick = { playerId, name -> viewModel.openPlayer(playerId, name, null) },
                                )
                            },
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
                            title = "My Team",
                            onBack = viewModel::backFromMyTeam,
                            onMatchClick = viewModel::openMatchDetail,
                            onOpenStandingsTable = viewModel::openStandingsTable,
                            tabs = mode.tabs,
                            onSelectTab = viewModel::selectTeamTab,
                            onPlayerClick = viewModel::openPlayer,
                            onOpenStatPicker = viewModel::openTeamStatPicker,
                            onSelectStat = viewModel::selectTeamStat,
                            onSelectStatSort = viewModel::selectTeamStatSort,
                        )
                    }

                    is ScoreScreenMode.MatchDetailScreen -> {
                        MatchDetailContent(
                            mode = mode,
                            onBack = viewModel::backFromMatchDetail,
                            onTeamClick = viewModel::openTeamDetail,
                            onSelectTab = viewModel::selectMatchDetailTab,
                            onPlayerClick = { player, photoBytes ->
                                player.id?.let { viewModel.openPlayer(it, player.name, photoBytes) }
                            },
                        )
                    }

                    is ScoreScreenMode.PlayerDetailScreen -> {
                        PlayerDetailContent(mode = mode, onBack = viewModel::backFromPlayer)
                    }

                    is ScoreScreenMode.TeamDetail -> {
                        // Same MyTeamContent rendering "My Team" itself uses — see
                        // ScoreScreenMode.TeamDetail's doc comment for why this is its own mode
                        // with its own back target rather than reusing MyTeam's. title left null:
                        // "My Team" wouldn't be accurate here (see MyTeamContent's own doc comment
                        // on this mode showing an arbitrary tapped team, not necessarily the user's
                        // saved one) and there's no better title available — same gap already
                        // flagged for this mode before the "My Team" title request came in.
                        MyTeamContent(
                            summary = mode.summary,
                            isLoading = mode.isLoading,
                            title = null,
                            onBack = viewModel::backFromTeamDetail,
                            onMatchClick = viewModel::openMatchDetail,
                            onOpenStandingsTable = viewModel::openStandingsTable,
                            tabs = mode.tabs,
                            onSelectTab = viewModel::selectTeamTab,
                            onPlayerClick = viewModel::openPlayer,
                            onOpenStatPicker = viewModel::openTeamStatPicker,
                            onSelectStat = viewModel::selectTeamStat,
                            onSelectStatSort = viewModel::selectTeamStatSort,
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

// MatchRow's leading slot — now the fixture's abbreviated competition (e.g. "UCL", "EPL",
// "Serie A" — see competitionShortName in SoccerModels.kt) for both of My Team's cards, on
// request, so a followed team's mixed league/cup/continental fixtures are distinguishable at a
// glance without opening the match. A handful of untracked/long competition names (e.g.
// "Championship", "Copa del Rey") fall back to their full name in competitionShortName and could
// still clip here — maxLines=1 + Ellipsis at the call site is the safety net for that case. This
// width, like every other fixed slot width in this file, is an estimate — no compiler/emulator in
// this sandbox to render-test against.
private val COMPETITION_SLOT_WIDTH = 4f

// MatchRow's trailing slot for My Team's "UPCOMING" card — the dated kickoff label ("9/25 19:45"),
// moved here from the row's leading slot now that the leading slot is the competition abbreviation
// instead (see COMPETITION_SLOT_WIDTH above). Same value the old leading "dated" slot used —
// already sized for this exact text (a date prefix "9/25" plus a 24-hour time), so kept unchanged
// rather than re-guessed.
private val DATE_TIME_SLOT_WIDTH = 6.5f

// MatchRow's trailing slot for My Team's "RECENT RESULTS" card — the small single-letter W/D/L
// [ResultBadge], moved here from the row's leading slot on request so a result row now reads
// competition / opponent / score / result, left to right.
private val RESULT_BADGE_SLOT_WIDTH = 1.8f

// Fixed width for MatchRow's trailing score slot (its sole remaining caller) — the badge/kickoff-
// time content that used to live here moved to the left slot above (see MatchRow's doc comment), so
// this only ever holds a plain score ("2 - 1", or blank before kickoff) and can stay narrower than
// the old combined slot MatchTeamsAndScoreCell used to reuse this width for, before that composable
// was retired along with Scores' switch to ScheduleMatchRow.
private val SCORE_SLOT_WIDTH = 4f

// Fixed square size for [TeamCrestImage] — sole caller now [ScheduleMatchRow], where the two crests
// are the row's true left/right columns (see that fun's doc comment) rather than sitting inline with
// just the top text line. That flagged-as-unverified follow-up nudge did turn out to be needed: 3
// (this file's first estimate for "fills roughly the row's full height") rendered fine in the emulator
// but was too tall on the real device — only ~4 matches fit on screen at once where 6-7 was wanted, so
// per that real-device report, down to 1.5. Still just an estimate, same caveat as before — the goal
// this time is for the crest to sit comfortably within the two-line text column's own height rather
// than dictating the row's height itself, so row height should now track the text stack (Fine top
// line + status/score bottom line) the way it did before crests grew into full-height columns; worth
// a specific check that neither the crest looks awkwardly small next to that text now, nor still
// dominates the row's height more than intended.
private val TEAM_CREST_SIZE = 1.5f

// A light, legible green for a live match's minute-counter text — matches the reference (fotmob)
// screenshot's live-indicator hue. Used for text color only (see MatchStatusBadge); the badge's
// own pill background stays the same neutral translucent fill FT uses, so "live" reads as a color
// change on familiar UI rather than an entirely different shape.
private val LIVE_STATUS_GREEN = Color(0xFF4ADE80)

/** Small rounded badge for a match's status — FT (or Postponed/Cancelled/Suspended) and a live
 * minute counter now share the same pill shape, matching the reference fotmob screenshot's FT
 * badge; only the live case gets green text ([LIVE_STATUS_GREEN]) to set it apart from a finished
 * match. Sized by its own content. [ScheduleMatchRow] is this badge's only caller now — My Team's
 * [MatchRow] used to place it in its leading slot, but that slot shows the fixture's competition
 * instead on request (see [COMPETITION_SLOT_WIDTH]), and My Team's redesigned rows have no badge
 * slot left for FT/live text (a My Team result shows as its score plus a [ResultBadge] instead —
 * see [MatchRow]'s doc comment). [ScheduleMatchRow] passes `Copy` — this app's existing "prominent
 * score" size, the same one Match Detail's own big score label already uses — to render this badge
 * genuinely bigger, on request (see that fun's doc comment for where it sits now); [variant]
 * defaults to `Detail` for a hypothetical future caller that doesn't override it. */
@Composable
private fun MatchStatusBadge(
    text: String,
    isLive: Boolean,
    variant: LightTextVariant = LightTextVariant.Detail,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50.dp))
            .background(LightThemeTokens.colors.content.copy(alpha = 0.12f))
            .padding(horizontal = 0.4f.gridUnitsAsDp(), vertical = 0.05f.gridUnitsAsDp()),
    ) {
        // Was Superfine (16), then Detail (20), then Fine (25), then back to Detail — sizing
        // history from when My Team's own MatchRow was still this badge's other caller (now
        // ScheduleMatchRow's only caller — see this fun's own doc comment). variant is a plain
        // parameter now, so ScheduleMatchRow's separate, larger `Copy` choice is unaffected.
        LightText(
            text = text,
            variant = variant,
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
 * the fotmob reference screenshot's Form-column blocks. Used inline per match in [MatchRow]'s
 * trailing [RESULT_BADGE_SLOT_WIDTH] slot, My Team's "RECENT RESULTS" card only — see
 * [Fixture.resultFor]. (Previously also repeated for a league-form summary string on My Team's own
 * standings header via a `FormRow` composable; that header block was dropped entirely on request,
 * and `FormRow` removed with it.) */
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

// Premier League (39), Ligue 1 (61), and UEFA Champions League (2) — see TRACKED_COMPETITIONS in
// SoccerModels.kt — ship real crest logos that read as dark purple/navy (or, for UCL, a busy
// multi-color star pattern) on API-Football's CDN, which reads poorly against this app's black
// backgrounds. Recolored at render time rather than as a bundled replacement asset: this app has no
// local drawable for any league badge (every logo is fetched over the network, see
// ApiFootballApi.fetchLeagueLogos), so a runtime tint is what actually reaches every place a league
// logo is drawn (Scores' MatchGroupCard title icon, Standings' own header, Results & Fixtures' row
// crest) without depending on what that fetch happens to return. UEFA Europa League (3) wants a
// *different* treatment — white except its orange parts — which a stateless ColorFilter can't
// express at all; see ORANGE_PRESERVE_LEAGUE_IDS/recolorWhiteExceptOrange below for that one.
// Eredivisie (88, navy), Süper Lig (203, black wordmark), and UEFA Conference League (848, black
// wordmark) joined in 1.4.0 for the same reason. Primeira Liga (94) and EFL Cup (48) ship on their
// own white/green backgrounds and read fine untinted. Leagues Cup (772, solid black wordmark)
// joined in 1.6.0; MLS, Liga MX, and the CONCACAF Champions Cup (whose dark badge carries white
// lettering that a tint would erase) read fine as they are.
private val WHITE_TINTED_LEAGUE_IDS = setOf(39, 61, 2, 88, 203, 848, 772)

/** [BlendMode.SrcIn] paints solid white everywhere the source bitmap has any alpha (i.e. the
 * badge's actual crest shape) and leaves fully-transparent pixels untouched — a plain silhouette
 * recolor, which is what "all white" was asked for, not a partial tint that would keep some of the
 * original shading. Returns null (no filter — original colors) for every other league, including
 * the Europa League (3), which is handled separately by [recolorWhiteExceptOrange] instead since
 * this uniform tint has no way to spare one color region. */
private fun leagueLogoColorFilter(leagueId: Int): ColorFilter? =
    if (leagueId in WHITE_TINTED_LEAGUE_IDS) ColorFilter.tint(Color.White, BlendMode.SrcIn) else null

// Hue range (in degrees, HSV) and minimum saturation/value that count as "orange" below. This is
// an unverified guess: this app never has a bundled copy of any league crest to look at (every logo
// is fetched over the network at runtime — see fetchLeagueLogos), so these thresholds were picked
// from the request's text description alone ("except the orange parts"), not from inspecting the
// actual Europa League image. Expect to retune after seeing it render on a real device.
private const val ORANGE_HUE_MIN = 10f
private const val ORANGE_HUE_MAX = 50f
private const val ORANGE_MIN_SATURATION = 0.35f
private const val ORANGE_MIN_VALUE = 0.30f

/** Leagues that need [recolorWhiteExceptOrange] instead of (or as well as) [leagueLogoColorFilter]
 * — currently just the UEFA Europa League (3). */
private val ORANGE_PRESERVE_LEAGUE_IDS = setOf(3)

/**
 * A [ColorFilter] (see [leagueLogoColorFilter]) can only repaint *every* opaque pixel the same
 * way — it has no way to look at what color a given pixel already is, so it cannot express "white
 * except the orange parts". This does that instead: it decodes to a mutable [Bitmap], tests each
 * non-transparent pixel's hue/saturation/value against [ORANGE_HUE_MIN]/[ORANGE_HUE_MAX] and the
 * two thresholds, and repaints every pixel that doesn't look orange to solid white — preserving
 * its original alpha exactly, so the crest's silhouette doesn't change — while leaving pixels that
 * do look orange completely untouched. See [ORANGE_HUE_MIN]'s doc comment for the caveat that this
 * hue window is a blind guess, not something verified against the real fetched crest.
 */
private fun recolorWhiteExceptOrange(source: Bitmap): Bitmap {
    val width = source.width
    val height = source.height
    if (width <= 0 || height <= 0) return source
    val mutable = source.copy(Bitmap.Config.ARGB_8888, true) ?: return source
    val pixels = IntArray(width * height)
    mutable.getPixels(pixels, 0, width, 0, 0, width, height)
    val hsv = FloatArray(3)
    for (i in pixels.indices) {
        val pixel = pixels[i]
        val alpha = (pixel ushr 24) and 0xFF
        if (alpha == 0) continue // leave fully-transparent pixels alone
        android.graphics.Color.colorToHSV(pixel, hsv)
        val isOrange = hsv[0] in ORANGE_HUE_MIN..ORANGE_HUE_MAX &&
            hsv[1] >= ORANGE_MIN_SATURATION &&
            hsv[2] >= ORANGE_MIN_VALUE
        if (!isOrange) {
            pixels[i] = (alpha shl 24) or 0x00FFFFFF // same alpha, solid white
        }
    }
    mutable.setPixels(pixels, 0, width, 0, 0, width, height)
    return mutable
}

/** Decodes a league crest and, only for [ORANGE_PRESERVE_LEAGUE_IDS] leagues, runs it through
 * [recolorWhiteExceptOrange] first — every other league is decoded as-is and recolored (if at all)
 * by the plain [leagueLogoColorFilter] tint applied at the `Image` composable instead. Centralizing
 * this here means all three places a league logo renders (Scores' title icon, Standings' header,
 * Results & Fixtures' row crest) automatically pick up the Europa League treatment identically. */
private fun decodeLeagueLogo(bytes: ByteArray, leagueId: Int): ImageBitmap? {
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val processed = if (leagueId in ORANGE_PRESERVE_LEAGUE_IDS) recolorWhiteExceptOrange(bitmap) else bitmap
    return processed.asImageBitmap()
}

@Composable
private fun ScoresContent(
    days: List<FixtureDay>,
    teamLogos: Map<String, ByteArray>,
    lineupsAvailableFixtureIds: Set<Int>,
    onOpenSettings: () -> Unit,
    onOpenMyTeam: () -> Unit,
    onOpenCompetitions: () -> Unit,
    onOpenStandingsTable: (Int, String) -> Unit,
    onManualRefresh: () -> Unit,
    onMatchClick: (Fixture) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header text was removed on request in an earlier round (this screen, My Team, and Results
        // & Fixtures all dropped theirs together); "Matchday Scores" added back here on request,
        // now that the app itself is named Matchday. My Team's own title came back the same round —
        // see MyTeamContent's title param. ScoreScreenMode.Scores.lastUpdated/isRefreshing (and
        // formatUpdatedAt in SoccerFormatting.kt) are unused by this screen but left in place in
        // case a future refresh indicator wants them again.
        LightTopBar(
            center = LightTopBarCenter.Text("Matchday Scores"),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (days.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // Was "No matches today in your leagues." before this screen absorbed the former
                // Fixtures screen's whole window on request — reworded since an empty result now
                // means the entire fetch window came back empty, not just today. Kept deliberately
                // vague about the exact span ("right now" rather than restating specific week
                // counts) so this string doesn't also need editing every time
                // SoccerViewModel's SCHEDULE_PAST_DAYS/SCHEDULE_FUTURE_DAYS window changes — this
                // round's asymmetric 2-weeks-back/4-weeks-ahead change is exactly the kind of edit
                // that would have silently gone stale here otherwise.
                LightText(
                    text = "No matches in your leagues right now.",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                )
            }
        } else {
            // Auto-scrolls to today's card on first composition of a given [days]/[today] pair —
            // ported from the former standalone Fixtures screen's own FixturesContent (now removed),
            // which had exactly this same problem (a long day-spanning list opening at its oldest
            // entry, forcing a manual scroll every time) and already solved it. Falls back to the
            // nearest *future* day if today itself has no matches (today has index -1 in that case;
            // the last day is only used if every day in the whole window is somehow in the past,
            // which shouldn't happen given how the window's computed).
            val today = todayLocalDate()
            val targetIndex = remember(days, today) {
                days.indexOfFirst { it.date >= today }.takeIf { it >= 0 } ?: days.lastIndex
            }
            val bringIntoViewRequester = remember(days, today) { BringIntoViewRequester() }
            LaunchedEffect(days, today) { bringIntoViewRequester.bringIntoView() }

            LightScrollView(
                // start-only: LightScrollView's own content Column already reserves 2 grid units on
                // the END for its scrollbar gutter (see scrollBarGutterUnits/LightScrollView.kt) —
                // stacking a symmetric horizontal padding on top of that gave every LightScrollView
                // screen in this app 1(ours)+2(SDK's) = 3 units on the right vs. 1 on the left, a
                // very visible dead gap on a real device (reported: cards not using the space to the
                // right). Fixed at every LightScrollView call site in this file, not just this one.
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 1f.gridUnitsAsDp()),
            ) {
                // One card per day, on request replacing the old per-league cards entirely — every
                // followed league's matches for that day sit together in one card now, in kickoff
                // order, thin grey dividers between rows (see ScheduleDayCard) rather than a
                // per-league sub-header. See FixtureDay/groupedByDate in SoccerModels.kt.
                days.forEachIndexed { index, day ->
                    ScheduleDayCard(
                        day = day,
                        teamLogos = teamLogos,
                        lineupsAvailableFixtureIds = lineupsAvailableFixtureIds,
                        modifier = Modifier
                            .padding(top = if (index == 0) 0.dp else 0.75f.gridUnitsAsDp())
                            .let { if (index == targetIndex) it.bringIntoViewRequester(bringIntoViewRequester) else it },
                        onOpenStandingsTable = onOpenStandingsTable,
                        onMatchClick = onMatchClick,
                    )
                }
            }
        }

        // Bottom bar order (left to right): Settings, My Team, Refresh — the Fixtures icon that used
        // to sit between My Team and Refresh is gone now that this screen absorbed the standalone
        // Fixtures screen entirely (see ScoreScreenMode.Scores' doc comment); there's nowhere left
        // for it to navigate to. Refresh itself is unchanged from the round that moved it here from
        // Settings — reuses LightIcons.REFRESH, the SDK's own built-in icon, same LightIcon pattern
        // as Settings' gear. onManualRefresh is the same viewModel::manualRefresh function as always.
        // Standings has no button here either — reached by tapping a match row's own league label
        // now (see ScheduleMatchRow), not a per-card header icon the way the old per-league cards
        // worked.
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
                    onClick = onOpenCompetitions,
                    contentDescription = "Competitions",
                ) { SoccerBarIcon(R.drawable.ic_trophy_white, "Competitions") },
                LightBarButton.LightIcon(
                    icon = LightIcons.REFRESH,
                    onClick = onManualRefresh,
                    contentDescription = "Refresh",
                ),
            ),
        )
    }
}

/** One league's matches within a single day's card, in kickoff order — see
 * `List<Fixture>.groupedByLeagueForDisplay` below. */
private data class DayLeagueGroup(val leagueId: Int, val matches: List<Fixture>)

/** Re-groups a day's flat, chronologically-sorted [FixtureDay.matches] (see [groupedByDate] in
 * SoccerModels.kt) back into per-league sections for display, on request — [ScheduleDayCard] used
 * to render the whole day as one flat list with each row carrying its own league's short name; now
 * every league gets one shared, centered header instead, with its matches (re-sorted by kickoff
 * time within the group, since the flat list's own sort only used league as a tiebreaker) beneath
 * it. Groups are ordered by [competitionDisplayOrder] — this app's fixed, existing league preference
 * order — rather than by whichever league's earliest match happens to kick off first, so a given
 * league lands in the same position within the card from one day to the next. */
private fun List<Fixture>.groupedByLeagueForDisplay(): List<DayLeagueGroup> = this
    .groupBy { it.leagueId }
    .map { (leagueId, matches) -> DayLeagueGroup(leagueId, matches.sortedBy { it.utcDate }) }
    .sortedBy { competitionDisplayOrder(it.leagueId) }

/** One day's card on the merged Scores/schedule list — every followed league's matches for [day],
 * grouped by league within the card (see [groupedByLeagueForDisplay]), each group under its own
 * centered header carrying that league's short name; replaces the old per-league [MatchGroupCard]
 * on request (that composable is unchanged and still backs My Team's own cards — see its doc
 * comment) as well as an even earlier version of this same card, which had gone fully flat with the
 * league's short name repeated on every row instead (see [ScheduleMatchRow]'s doc comment) — on
 * request, back to a shared per-league header now that repeating it on every row read as noisy.
 * Thin grey dividers separate league groups within a day rather than every individual match row —
 * plus one more, on request, directly under the date label itself, so consecutive day cards read as
 * visually separated even with no card fill of their own (see the no-fill note below).
 * [teamLogos], keyed by [Fixture.homeTeamLogo]/[Fixture.awayTeamLogo] URL, and
 * [lineupsAvailableFixtureIds] both come straight from [ScoreScreenMode.Scores] and are looked up
 * per-match below. [onOpenStandingsTable] is only ever invoked for a league that
 * [competitionHasStandings] — now gated on the per-league header tap, not a per-row one, since the
 * league name itself moved there. No card fill on request — the faint translucent grey
 * ([LightThemeTokens.colors.contentSecondary] at 8% alpha) that separated one day's card from the
 * next rendered as a visibly solid, distracting grey block on the real device rather than the subtle
 * tint it looked like in preview, so it's gone entirely now; only the divider between league groups
 * within a card (still [LightThemeTokens.colors.contentSecondary] at 15% alpha, unchanged) remains as
 * a visual separator. The [RoundedCornerShape] clip stays even though there's now nothing visibly
 * clipped by it — harmless, and cheap insurance if a background returns here later. */
@Composable
private fun ScheduleDayCard(
    day: FixtureDay,
    teamLogos: Map<String, ByteArray>,
    lineupsAvailableFixtureIds: Set<Int>,
    modifier: Modifier = Modifier,
    onOpenStandingsTable: (Int, String) -> Unit,
    onMatchClick: (Fixture) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(1.2f.gridUnitsAsDp()))
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
    ) {
        LightText(
            text = day.dateLabel,
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )
        // Thin grey divider under each date, on request ("add a thin grey line under each date...
        // to separate days") — same contentSecondary-at-15%-alpha line used between league groups
        // just below, so every divider in this card reads as one consistent style.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.5f.gridUnitsAsDp())
                .height(1.dp)
                .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.15f)),
        )
        val leagueGroups = remember(day) { day.matches.groupedByLeagueForDisplay() }
        leagueGroups.forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 0.5f.gridUnitsAsDp())
                        .height(1.dp)
                        .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.15f)),
                )
            }
            // Same size/color as the per-row label it replaces (LightTextVariant.Detail, lightened)
            // — on request, only the position (top center within the group, instead of repeated on
            // every row) and the tap target it carries changed.
            LightText(
                text = competitionShortName(group.leagueId),
                variant = LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 0.25f.gridUnitsAsDp())
                    .let {
                        if (competitionHasStandings(group.leagueId)) {
                            it.lightClickable(onClick = { onOpenStandingsTable(group.leagueId, competitionName(group.leagueId)) })
                        } else {
                            it
                        }
                    },
            )
            group.matches.forEach { match ->
                ScheduleMatchRow(
                    match = match,
                    homeLogoBytes = teamLogos[match.homeTeamLogo],
                    awayLogoBytes = teamLogos[match.awayTeamLogo],
                    lineupsAvailable = match.id in lineupsAvailableFixtureIds,
                    onClick = { onMatchClick(match) },
                )
            }
        }
    }
}

/** One match, three columns, on request — a from-scratch layout swap after a mock-up made clear the
 * previous few rounds' arrangement (crests inline with just the team-name line; badge/score in their
 * own full-height cluster to one side) wasn't what was meant by "bigger": [TeamCrestImage] for home
 * and away now ARE the row's true left and right columns (see [TEAM_CREST_SIZE]'s doc comment for
 * that constant's own up-then-back-down history — after a real-device look, the crest is sized to sit
 * within the middle column's own height rather than dictating the row's height itself); the middle
 * column, taking
 * whatever width is left over, stacks two centered lines — top, the "Home - Away" string, each name
 * now passed through [teamShortName] first (still [LightTextVariant.Fine], unchanged, matching team
 * names as before — teamShortName only swaps in a shorter name for the researched teams that have
 * one, see its doc comment; every other team's full name renders exactly as before); bottom, this match's
 * status/score, all centered together in one `Row` with `Arrangement.Center`: [MatchStatusBadge] then
 * the score side by side for a live/finished match ([Fixture.showsFinalOrLiveScore] — badge and
 * score both at `Fine`, on request ("make the FT/Min Played element the same size as the
 * live-score") after this row's own [MatchStatusBadge] calls previously used the (larger) `Copy`
 * variant; the badge alone for
 * a postponed/cancelled/suspended match (see [Fixture.isPostponedCancelledOrSuspended]) or a
 * still-scheduled one with a posted lineup; or, for a still-scheduled match with nothing posted yet,
 * the plain kickoff time instead of a badge.
 *
 * Unlike the two previous rounds' layouts, nothing here needs a hand-picked fixed width purely to
 * keep something centered — the crests are sized by [TEAM_CREST_SIZE] for their own sake, and the
 * middle column's two lines each center themselves independently within its `weight(1f)` width, so
 * there's no flanking-slot-width bookkeeping left to get wrong the way the last two rounds' width
 * constants did. The one thing carried over unverified from those rounds: [TEAM_CREST_SIZE]'s new,
 * much larger value is still just an estimate of what looks right at this row's full height, with no
 * compiler or device here to check it against. */
@Composable
private fun ScheduleMatchRow(
    match: Fixture,
    homeLogoBytes: ByteArray?,
    awayLogoBytes: ByteArray?,
    lineupsAvailable: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.65f.gridUnitsAsDp()),
    ) {
        TeamCrestImage(
            bytes = homeLogoBytes,
            contentDescription = match.homeTeamName,
            modifier = Modifier.padding(end = 0.5f.gridUnitsAsDp()),
        )
        Column(modifier = Modifier.weight(1f)) {
            // teamShortName is a no-op passthrough for any team without a researched short name
            // (see its doc comment in SoccerModels.kt) -- most teams render exactly as before.
            LightText(
                text = "${teamShortName(match.homeTeamName)} - ${teamShortName(match.awayTeamName)}",
                variant = LightTextVariant.Fine,
                align = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 0.25f.gridUnitsAsDp()),
            ) {
                when {
                    match.showsFinalOrLiveScore() -> {
                        // Was Copy (30) — bigger than the score text beside it (Fine, 25). On
                        // request ("the FT/Min Played element is larger than the live-score, can we
                        // make those the same size?"), matched down to Fine so the badge and score
                        // read at the same size.
                        MatchStatusBadge(
                            text = match.statusLabel(),
                            isLive = match.status.isLive,
                            variant = LightTextVariant.Fine,
                        )
                        LightText(
                            text = match.scoreLabel(),
                            variant = LightTextVariant.Fine,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (match.status.isLive) LIVE_STATUS_GREEN else null,
                            modifier = Modifier.padding(start = 0.4f.gridUnitsAsDp()),
                        )
                    }
                    // Same gap [Fixture.isPostponedCancelledOrSuspended] was added to close a few
                    // rounds ago — without this branch, one of these three statuses would fall
                    // through to the plain-kickoff-time branch below and read as an ordinary
                    // still-scheduled match.
                    match.isPostponedCancelledOrSuspended() -> {
                        // Matched to Fine along with the live/FT branch above, so this row's badge
                        // reads at one consistent size regardless of which branch renders it.
                        MatchStatusBadge(text = match.statusLabel(), isLive = false, variant = LightTextVariant.Fine)
                    }
                    lineupsAvailable -> {
                        MatchStatusBadge(text = "Lineups", isLive = false, variant = LightTextVariant.Fine)
                    }
                    else -> {
                        LightText(
                            text = formatKickoffTime(match.utcDate),
                            variant = LightTextVariant.Fine,
                            lighten = true,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        TeamCrestImage(
            bytes = awayLogoBytes,
            contentDescription = match.awayTeamName,
            modifier = Modifier.padding(start = 0.5f.gridUnitsAsDp()),
        )
    }
}

/** A rounded card grouping a set of matches under one header — same card-per-group pattern as the
 * ESPN/football-data.org variants of this tool. My Team's only remaining caller (upcoming/recent) —
 * Scores used to share this too, grouped by competition, before it moved to its own day-grouped
 * [ScheduleDayCard]/[ScheduleMatchRow] on request (see that pair's doc comments); Results & Fixtures
 * shared it even earlier, before that screen moved to its own headerless FixtureLeagueCard/
 * FixtureMatchRow and was later retired outright when Scores absorbed it. [titleLogoBytes]/
 * [titleLogoLeagueId]/[titleLogoTint]/[onTitleLogoClick] are dead weight now that Scores was this
 * card's only caller to ever pass them (a per-league crest header + Standings tap target) — left in
 * place rather than stripped, since My Team's own calls never reference them by name and a future
 * per-league use of this card isn't out of the question. [focusTeamId] is My Team's own followed
 * team id, passed by both cards now — it drives each row's opponent-only text (see
 * [Fixture.opponentLabel]) and, only when [showResultBadge] is also set (My Team's "RECENT RESULTS"
 * card only), the trailing [ResultBadge] too — see [MatchRow]. [allowKickoffLabelWrap] is only ever
 * passed by My Team's "UPCOMING" card — see [MatchRow]. [showScoreSlot], only ever set false by My
 * Team's "UPCOMING" card, drops the score column entirely (rather than just leaving it visually
 * empty) so the opponent text gets that width back — see [MatchRow]. No card fill on request, same
 * reasoning and same real-device finding as [ScheduleDayCard]'s own background removal: a low-alpha
 * grey fill rendered as a visibly solid block on the real device rather than the subtle tint it
 * looked like in preview. The divider between individual matches (still `contentSecondary` at 15%
 * alpha) is untouched — that's the "small grey line" the user asked to keep in its place. */
@Composable
private fun MatchGroupCard(
    title: String,
    matches: List<Fixture>,
    modifier: Modifier = Modifier,
    titleLogoBytes: ByteArray? = null,
    titleLogoLeagueId: Int? = null,
    titleLogoTint: ColorFilter? = null,
    onTitleLogoClick: (() -> Unit)? = null,
    showDate: Boolean = false,
    focusTeamId: Int? = null,
    showResultBadge: Boolean = false,
    allowKickoffLabelWrap: Boolean = false,
    showScoreSlot: Boolean = true,
    onMatchClick: (Fixture) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(1.2f.gridUnitsAsDp()))
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        ) {
            val titleLogoBitmap = titleLogoBytes?.let { bytes ->
                remember(bytes, titleLogoLeagueId) { decodeLeagueLogo(bytes, titleLogoLeagueId ?: -1) }
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
            MatchRow(
                match,
                showDate = showDate,
                focusTeamId = focusTeamId,
                showResultBadge = showResultBadge,
                allowKickoffLabelWrap = allowKickoffLabelWrap,
                showScoreSlot = showScoreSlot,
                onClick = { onMatchClick(match) },
            )
        }
    }
}

/** One team crest — used by [ScheduleMatchRow] as its true left/right column (see [TEAM_CREST_SIZE]'s
 * doc comment for how large that box actually ends up, and why) — always a fixed [TEAM_CREST_SIZE]
 * box regardless of whether [bytes] actually decoded, so one missing/failed crest doesn't shift that
 * row out of alignment with its neighbors (every other row still reserves the same space). No
 * recolor is ever applied here — [leagueLogoColorFilter]/[recolorWhiteExceptOrange] are treatments
 * for specific *league* badges that read poorly on black; team crests are fetched fresh per-team off
 * whatever API-Football serves and haven't shown that problem. */
@Composable
private fun TeamCrestImage(bytes: ByteArray?, contentDescription: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(TEAM_CREST_SIZE.gridUnitsAsDp()), contentAlignment = Alignment.Center) {
        val bitmap = bytes?.let { b -> remember(b) { BitmapFactory.decodeByteArray(b, 0, b.size)?.asImageBitmap() } }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = contentDescription,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** One My Team match row: a leading [COMPETITION_SLOT_WIDTH] slot for the fixture's abbreviated
 * competition, then opponent-only text ([Fixture.opponentLabel] — the followed team's own name is
 * never printed here), then an optional [SCORE_SLOT_WIDTH] score box, then a trailing slot that's
 * either a colored [ResultBadge] ([RESULT_BADGE_SLOT_WIDTH], "RECENT RESULTS") or a dated kickoff
 * label ([DATE_TIME_SLOT_WIDTH], "UPCOMING") — all four columns left to right, on request. My
 * Team's only remaining caller now — Scores moved to its own two-line [ScheduleMatchRow]/
 * [ScheduleDayCard] on request (see that pair's doc comments), which took the crest/combined-cell
 * display this row used to also support ([MatchTeamsAndScoreCell], its `showTeamCrests` param, and
 * the near-kickoff `lineupsAvailable` plumbing) down with it — none of that was ever set by My
 * Team, so removing it here changes nothing about how My Team renders. A live match sitting in
 * "RECENT RESULTS" (today's game, still in progress) is a known simplification this redesign
 * doesn't special-case: it renders its live score and a [ResultBadge] computed from that
 * in-progress score, with no separate "LIVE"/minute indicator — the four-column layout has no
 * slot left for one. */
@Composable
private fun MatchRow(
    match: Fixture,
    // My Team's "UPCOMING" card is the one caller that isn't already grouped under a per-day
    // header (unlike Scores), so a bare kickoff time there could be mistaken for today's game —
    // see formatKickoffDateAndTime's doc comment in SoccerFormatting.kt.
    showDate: Boolean = false,
    // My Team's followed team id — passed by both cards now (previously only "RECENT RESULTS")
    // since the opponent-only text ([Fixture.opponentLabel]) below needs it too. Nullable and
    // defensive, matching Fixture.resultFor's own reasoning, though in practice both real callers
    // always pass MyTeamSummary.teamId.
    focusTeamId: Int? = null,
    // Set only by My Team's "RECENT RESULTS" card — shows a colored W/D/L result badge for
    // [focusTeamId] in the row's trailing slot. See Fixture.resultFor/ResultBadge.
    showResultBadge: Boolean = false,
    // Set only by My Team's "UPCOMING" card, where the kickoff label can be a full date + time
    // (e.g. "9/13 19:45") or just a longer local time — letting it wrap to a second line instead
    // of ellipsizing. Left off (default) for Scores/Fixtures, which used their own separate row
    // composables even before the merge — see the SoccerHomeScreen font-size audit, §9.
    allowKickoffLabelWrap: Boolean = false,
    // False only for My Team's "UPCOMING" card, on request — every one of its matches is scheduled
    // (no score to show yet anyway), and dropping the slot entirely rather than just leaving it
    // empty gives the opponent text that width back, letting most matchups fit on one line.
    showScoreSlot: Boolean = true,
    onClick: () -> Unit,
) {
    // Only My Team's "UPCOMING" card ever hits the showDate+SCHEDULED branch below (its rows are
    // all upcoming, so this is consistent card-to-card, not row-to-row within one card) — its
    // "RECENT RESULTS" counterpart never sets showDate at all.
    val usesDatedKickoffLabel = !match.hasScore && showDate && match.status == MatchStatus.SCHEDULED
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.65f.gridUnitsAsDp()),
    ) {
        Box(
            modifier = Modifier.width(COMPETITION_SLOT_WIDTH.gridUnitsAsDp()),
            contentAlignment = Alignment.CenterStart,
        ) {
            LightText(
                text = competitionShortName(match.leagueId),
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        LightText(
            text = focusTeamId?.let { match.opponentLabel(it) } ?: "${match.homeTeamName} vs ${match.awayTeamName}",
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
        if (showResultBadge) {
            Box(
                modifier = Modifier
                    .width(RESULT_BADGE_SLOT_WIDTH.gridUnitsAsDp())
                    .padding(start = 0.3f.gridUnitsAsDp()),
                contentAlignment = Alignment.CenterEnd,
            ) {
                val result = focusTeamId?.let { match.resultFor(it) }
                if (result != null) {
                    ResultBadge(result)
                }
            }
        } else if (showDate) {
            Box(
                modifier = Modifier.width(DATE_TIME_SLOT_WIDTH.gridUnitsAsDp()),
                contentAlignment = Alignment.CenterEnd,
            ) {
                // Upcoming match, no score yet — the dated kickoff label; a postponed/cancelled/
                // suspended upcoming fixture falls back to its plain status text instead of a
                // stale kickoff time (see Fixture.isPostponedCancelledOrSuspended's doc comment
                // for the same gap elsewhere in this file).
                val label = if (usesDatedKickoffLabel) formatKickoffDateAndTime(match.utcDate) else match.statusLabel()
                LightText(
                    text = label,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    align = TextAlign.End,
                    maxLines = if (allowKickoffLabelWrap) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
                // reverted back to Copy along with the rest of Settings' text — then Detail (20) so
                // the section's grey sub-items (this and the team name/"Not set" value above) didn't
                // read too large next to "My Team"'s own label. On request, bumped to Fine (25) —
                // matching the label above and Scores' home/away team-name text (also Fine) — so the
                // largest text on this whole page tops out at that size instead of this reading
                // smaller than everything else.
                LightText(
                    text = "Forget My Team",
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = onClearMyTeam)
                        .padding(top = 0.25f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
                )
            }
            // "Refresh now" used to live here as this build's only manual-refresh surface — moved to
            // a bottom-bar icon on the main Scores screen instead (see ScoresContent's LightBottomBar
            // doc comment for why), so there's nothing left for this row to do.
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
        // down again to Detail (20), smaller than the label above it (Fine, 25) — then, on request,
        // bumped back up to Fine to match the label, so every row on this page tops out at the same
        // size as Scores' home/away team-name text (also Fine) rather than this reading smaller.
        LightText(text = value, variant = LightTextVariant.Fine, lighten = true)
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
        // Label text changed from "Leagues followed" to "Competitions followed" on request — this
        // row already lists cups (FA Cup, Coppa Italia, etc.) and UEFA competitions alongside the
        // domestic leagues, so "Competitions" is the more accurate umbrella term; nothing else about
        // this row (what it lists, its onClick target, its own fun name) changed.
        LightText(text = "Competitions followed", variant = LightTextVariant.Fine)
        // Was Copy, then Detail (see font-size-audit.md recommendation #3 — with up to 14
        // trackable competitions, a user following several gets that many stacked lines, so this
        // matched the label above rather than standing out as heavier). The global one-step-down
        // pass then shifted it to Superfine (16); reverted back to Detail — then, on request, bumped
        // to Fine (25) along with the rest of this page's text, matching Scores' home/away
        // team-name font as this page's new ceiling size.
        leagueNames.forEach { name ->
            LightText(
                text = name,
                variant = LightTextVariant.Fine,
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
                // The sentence naming which specific competitions this tool covers was removed on
                // request — that list is also shown live in Settings' own "Competitions followed"
                // row, so this static copy was a second, easily-stale place saying the same thing
                // (it still named only 4 of the now 14 tracked competitions).
                //
                // The scores-window sentence below is new, on request (see SoccerViewModel.kt's
                // SCHEDULE_PAST_DAYS/SCHEDULE_FUTURE_DAYS doc comment, which asks for this to stay in
                // sync with those constants if the window changes again) — written as "past two
                // weeks... upcoming four weeks" rather than restating the exact day counts, so it
                // doesn't need editing for a small day-count tweak, only if the window's actual
                // rough shape changes.
                text = "Scores are provided by API-Football (api-football.com), a paid football " +
                    "data API.\n\n" +
                    "Requests go through a caching proxy this app's developer runs, which holds " +
                    "the API-Football key and absorbs the request load — there's nothing to set " +
                    "up or configure here. Scores, fixtures, and standings are live, " +
                    "current-season data.\n\n" +
                    "The Matchday Scores feed shows matches from the past two weeks through the " +
                    "upcoming four weeks, so you can look back at recent results and ahead at " +
                    "what's coming up.",
                variant = LightTextVariant.Paragraph,
            )
        }
    }
}

// --- League selection ------------------------------------------------------------------

/** Grouped by [LeagueSelectionRow.region] into one section per country (plus "International
 * Club" for UCL/UEL), on request ("Reorganize 'Leagues followed' selection to be grouped by
 * country... e.g. Premier League, Championship, FA Cup would all be under England"). [rows]
 * arrives pre-ordered by [TRACKED_COMPETITIONS]' own declared order (England, Italy, Spain,
 * Germany, France, International Club — see that val's own grouping comments), and Kotlin's
 * [groupBy] preserves first-seen key order, so this doesn't need to sort the section headers
 * itself — they simply come out in that same order. */
@Composable
private fun LeagueSelectionContent(
    rows: List<LeagueSelectionRow>,
    leagueLogos: Map<Int, ByteArray>,
    onToggle: (Int) -> Unit,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text("Competitions"),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 1f.gridUnitsAsDp()),
        ) {
            val grouped = remember(rows) { rows.groupBy { it.region } }
            grouped.forEach { (region, regionRows) ->
                LightText(
                    text = region.uppercase(),
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 1f.gridUnitsAsDp())
                        .padding(top = 0.9f.gridUnitsAsDp(), bottom = 0.2f.gridUnitsAsDp()),
                )
                regionRows.forEach { row ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .lightClickable(onClick = { onToggle(row.id) })
                            .padding(vertical = 0.85f.gridUnitsAsDp()),
                    ) {
                        // Same decodeLeagueLogo (recolor-on-decode) treatment Scores/Fixtures/
                        // Standings already give a league crest, so e.g. Premier League/UCL still
                        // read as white here instead of the dark-on-black original. Bytes come from
                        // leagueLogos, keyed by id (see ScoreScreenMode.LeagueSelection's doc
                        // comment) rather than the URL-keyed maps every other screen uses. Row
                        // simply renders without an icon until the fetch resolves, or permanently
                        // if it fails — never a broken image.
                        val bytes = leagueLogos[row.id]
                        val logoBitmap = bytes?.let { b -> remember(b, row.id) { decodeLeagueLogo(b, row.id) } }
                        if (logoBitmap != null) {
                            Image(
                                bitmap = logoBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                colorFilter = leagueLogoColorFilter(row.id),
                                modifier = Modifier
                                    .size(1.4f.gridUnitsAsDp())
                                    .padding(end = 0.6f.gridUnitsAsDp()),
                            )
                        }
                        // Was Copy (30) before the global one-step-down pass shifted it to Detail
                        // (20); reverted back to Copy — this is a Settings child page, same
                        // treatment.
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
}

// --- Shared competition picker (My Team setup) ----------------------------------------------
//
// Named "shared" from when Standings and Fixtures each had their own picker screen reusing this
// same composable too — both are gone now (Standings moved to a league-logo tap, Fixtures to an
// all-leagues view), leaving My Team's league-picker step as the only caller.

@Composable
private fun CompetitionPickerContent(
    title: String,
    leagues: List<Competition>,
    onSelect: (Int, String) -> Unit,
    onBack: () -> Unit,
    emptyText: String? = null,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(title),
            modifier = Modifier.padding(bottom = 1f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 1f.gridUnitsAsDp()),
        ) {
            if (leagues.isEmpty() && emptyText != null) {
                NoDataForTab(text = emptyText)
            }
            leagues.forEach { league ->
                LightText(
                    text = league.name,
                    // Same size as Settings' own rows (SettingRow), on request.
                    variant = LightTextVariant.Fine,
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

// On request: W/D/L added, GD dropped to make room for them — first tried with POS narrowed
// further still (0.08 -> 0.045f), but that made the #/TEAM gap tighter than wanted, so POS is back
// to its pre-this-round 0.08. TEAM gives up the space POS just reclaimed (0.405 -> 0.37) to keep
// the row's total weight roughly where it was; the six MP/W/D/L/GF/GA columns still share one
// equal, tight weight (all single-or-double-digit for a season) and PTS keeps a little extra room
// for a runaway title race's occasional 3-digit total. Still a first estimate for the stat columns
// specifically — explicitly framed as an experiment now that team names can be shortened via
// [teamShortName] — just no longer for POS, which is back to a size already confirmed to read well.
//
// GF/GA condensed into one "+/-" column (e.g. "16-5") and GD reinstated as its own column, on
// request, matching the FotMob screenshot given as a reference. That's a net-zero column count (two
// removed, two added), but the new "+/-" column needs real room for a 5-character "10-12"-style
// value, unlike every other stat column here which never exceeds 2 digits — so it gets its own,
// wider [STANDINGS_DIFF_WEIGHT] rather than sharing [STANDINGS_STAT_WEIGHT]. That extra width comes
// half from the freed-up GF/GA space and half from TEAM (0.37 -> 0.325) — TEAM is still the widest
// column by a wide margin, but this is the second time it's given ground to a growing stat section;
// worth watching [teamShortName] ellipsizing more on a long name if that continues.
private val STANDINGS_POS_WEIGHT = 0.08f
private val STANDINGS_TEAM_WEIGHT = 0.325f
private val STANDINGS_STAT_WEIGHT = 0.075f // MP, W, D, L, and now GD all share this
private val STANDINGS_DIFF_WEIGHT = 0.12f // "+/-" (goals-for–goals-against, e.g. "16-5")
private val STANDINGS_PTS_WEIGHT = 0.10f

@Composable
private fun StandingsTableContent(
    leagueId: Int,
    leagueName: String,
    leagueLogoBytes: ByteArray?,
    rows: List<StandingsRow>,
    isLoading: Boolean,
    onBack: () -> Unit,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    statsTab: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(leagueName),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )
        // Table / Stats, on request — Stats is the competition's player leaderboard (LeadersContent).
        LabeledTabRow(
            labels = listOf("Table", "Stats"),
            selectedIndex = selectedTab,
            onSelect = onSelectTab,
            modifier = Modifier.padding(start = 1f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
        )
        // League badge image dropped on request ("remove league badges from all table views") —
        // this was the only one (Standings is this app's only table view). [leagueLogoBytes] is
        // left unused in this composable rather than unwinding its call-site plumbing and the
        // ViewModel/API fetch behind it (ScoreScreenMode.Standings.leagueLogoBytes,
        // SoccerViewModel's standings-logo follow-up) — same UI-only-removal call as
        // [LineupSection]'s coach headshot removal, for the same reason.

        if (selectedTab == 1) {
            statsTab()
        } else if (isLoading) {
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

            // Traced against LightScrollView.kt's own source this round (the earlier "+ 1f" here
            // was an unverified guess — see StandingsHeaderRow's doc comment history — this is now
            // measured against the SDK, not estimated): LightScrollView's Box takes the *exact*
            // modifier passed to it (here, `.weight(1f).fillMaxWidth().padding(start = 1f)` on the
            // LightScrollView call below), then internally gives its scrollable content Column
            // `fillMaxSize().padding(end = scrollBarGutterUnits(...))` — so that content's real
            // width is (this screen's width - 1f start - scrollBarGutterUnits end), nothing more.
            // This header Column was adding an *extra* leading "1f +" on top of the gutter, making
            // it a full grid unit (screenWidthDp / 27) narrower than the data rows actually are —
            // exactly the kind of proportional, rightward-increasing drift the screenshot showed
            // (a narrower header Row compresses every weighted column toward the left, and that
            // compression compounds moving right, matching what was reported). Fixed by dropping
            // that extra 1f so this end-padding is exactly [scrollBarGutterUnits], matching
            // LightScrollView's own internal end-padding on the content it wraps.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 1f.gridUnitsAsDp(),
                        end = scrollBarGutterUnits(LightScrollBarPosition.Outside).gridUnitsAsDp(),
                    ),
            ) {
                StandingsHeaderRow()
            }
            LightScrollView(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 1f.gridUnitsAsDp()),
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
                        // A heavier, more opaque line marks a promotion/relegation/qualification
                        // zone boundary (StandingsRow.zone changing between this row and the one
                        // above it) — e.g. after the last Champions League spot, before the first
                        // relegation spot — on request, in place of a full colored bar per row
                        // (see StandingsRow.zone's own doc comment for the not-yet-curl-verified
                        // caveat on the underlying data this is keyed off).
                        val isZoneBoundary = rows[index - 1].zone != row.zone
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isZoneBoundary) 2.dp else 1.dp)
                                .background(
                                    LightThemeTokens.colors.contentSecondary.copy(alpha = if (isZoneBoundary) 0.45f else 0.15f),
                                ),
                        )
                    }
                    StandingsTableRow(row)
                }
            }
        }
    }
}

// MP/W/D/L/+/-/GD/PTS switched from end- to center-aligned on request, header and data cells both
// (they have to move together — a header sitting somewhere its column's data doesn't just reads as
// misaligned). # stays as it was (start-aligned, no explicit align set); the TEAM column's own
// header label is gone now (see the blank LightText below), but the column itself keeps the same
// start alignment its team-name cells always had.
//
// Real bug found and fixed this round, from a second "still looks off" report with a real
// screenshot showing the drift growing larger column-by-column moving right (MP off by a little,
// PTS off by a lot) — that specific shape is what gave this away as a *width* mismatch between the
// header Row and the data Rows, not a per-column weight/align mismatch (those were already checked
// last round and are fine — see [StandingsTableRow]'s own cells). Traced against
// `sdk/ui/.../LightScrollView.kt`'s actual source (not available to re-render, but readable):
// LightScrollView gives its scrollable content Column `fillMaxSize().padding(end =
// scrollBarGutterUnits(position))`, on top of whatever modifier the caller already passed it (here,
// `.padding(start = 1f)`) — so the data rows' real width is (screen width - 1f start -
// scrollBarGutterUnits end). [StandingsTableContent]'s header Column, by contrast, was padded
// `end = (1f + scrollBarGutterUnits(...))` — an extra, un-traced "1f +" that made it a full grid
// unit narrower than the data rows. A narrower weighted Row compresses every column toward the
// left, and that compression compounds moving rightward across columns — exactly the growing drift
// reported. Fixed at the source: [StandingsTableContent]'s header Column now uses exactly
// `scrollBarGutterUnits(...)` for its end padding, matching LightScrollView's own internal
// end-padding on the content it wraps, no extra term. This one is measured against the SDK's actual
// layout code, not a render-tested guess.
@Composable
private fun StandingsHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 0.4f.gridUnitsAsDp())) {
        LightText(text = "#", variant = LightTextVariant.Detail, lighten = true, modifier = Modifier.weight(STANDINGS_POS_WEIGHT))
        // "TEAM" label dropped on request (matches the FotMob reference screenshot, whose team
        // column has no header text at all — just the numbered rank column and then straight into
        // the stat columns). Still an empty LightText with the same STANDINGS_TEAM_WEIGHT, not a
        // Spacer, so this column keeps reserving exactly the width [StandingsTableRow]'s own
        // team-name cell expects — removing the weighted slot entirely would have shifted every
        // column after it left, the same class of bug this round's alignment fix was about.
        LightText(text = "", variant = LightTextVariant.Detail, modifier = Modifier.weight(STANDINGS_TEAM_WEIGHT))
        LightText(text = "MP", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_STAT_WEIGHT))
        LightText(text = "W", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_STAT_WEIGHT))
        LightText(text = "D", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_STAT_WEIGHT))
        LightText(text = "L", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_STAT_WEIGHT))
        // Was separate "GF"/"GA" columns — condensed into one "+/-" column (e.g. "16-5") on request,
        // matching the FotMob reference screenshot. See [STANDINGS_DIFF_WEIGHT]'s doc comment for
        // why this gets its own, wider weight instead of sharing STANDINGS_STAT_WEIGHT.
        LightText(text = "+/-", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_DIFF_WEIGHT))
        // Goal difference, reinstated as its own column on request (it was dropped from this table
        // in an earlier round to make room for W/D/L — see this val block's own doc comment history
        // above). Shares STANDINGS_STAT_WEIGHT: same shape as MP/W/D/L, a short signed number.
        LightText(text = "GD", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_STAT_WEIGHT))
        LightText(text = "PTS", variant = LightTextVariant.Detail, lighten = true, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_PTS_WEIGHT))
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
        // font-size-audit.md recommendation #1: this is the densest row in the app (9 columns as of
        // the W/D/L addition, was 7), and matching the header size gives the data more room before
        // a long team name has to ellipsize.
        LightText(text = row.position.toString(), variant = LightTextVariant.Detail, modifier = Modifier.weight(STANDINGS_POS_WEIGHT))
        // teamShortName on request, now that this row's own column got tighter to make room for
        // W/D/L — see that fun's doc comment in SoccerModels.kt. Falls back to the full name
        // unchanged for any team without a researched short one, same as ScheduleMatchRow's use.
        LightText(
            text = teamShortName(row.teamName),
            variant = LightTextVariant.Detail,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(STANDINGS_TEAM_WEIGHT),
        )
        LightText(text = row.played.toString(), variant = LightTextVariant.Detail, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_STAT_WEIGHT))
        LightText(text = row.win.toString(), variant = LightTextVariant.Detail, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_STAT_WEIGHT))
        LightText(text = row.draw.toString(), variant = LightTextVariant.Detail, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_STAT_WEIGHT))
        LightText(text = row.lose.toString(), variant = LightTextVariant.Detail, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_STAT_WEIGHT))
        // "+/-" (goalsFor-goalsAgainst) and "GD" (signed goal difference) — see
        // StandingsHeaderRow's doc comment and SoccerFormatting.kt's goalsForAgainstLabel/
        // goalDifferenceLabel for why these replaced the old separate GF/GA columns.
        LightText(text = row.goalsForAgainstLabel(), variant = LightTextVariant.Detail, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_DIFF_WEIGHT))
        LightText(text = row.goalDifferenceLabel(), variant = LightTextVariant.Detail, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_STAT_WEIGHT))
        LightText(text = row.points.toString(), variant = LightTextVariant.Detail, align = TextAlign.Center, modifier = Modifier.weight(STANDINGS_PTS_WEIGHT))
    }
}

// The standalone Fixtures screen (FixturesContent/FixtureLeagueCard/FixtureMatchRow) that used to
// live here is gone — Scores absorbed it entirely on request (see ScoreScreenMode.Scores and
// ScoresContent's doc comments). showsFinalOrLiveScore below outlived that removal: ScheduleMatchRow
// (in ScoresContent's section above) still needs it for the same reason FixtureMatchRow originally
// did.

/** True once a match has a real, meaningful score to show — finished or currently in progress.
 * Deliberately narrower than [Fixture.hasScore] (which is just "not NS/TBD" and is also true for
 * postponed/cancelled/suspended matches with no real goals): using [hasScore] here would print a
 * fake "0 - 0" for those instead of "Postponed"/"Cancelled"/"Suspended" — a pre-existing quirk
 * elsewhere in this app (`MatchRow` has the same gap, papered over on My Team by a simultaneous
 * "Postponed" status badge alongside the fake score). [ScheduleMatchRow]'s center slot has nothing
 * else to fall back on, so it uses this plus [isPostponedCancelledOrSuspended] together to show the
 * real status text there instead of a fake score or a misleadingly-plain kickoff time. */
private fun Fixture.showsFinalOrLiveScore(): Boolean = status == MatchStatus.FINISHED || status.isLive

/** True for the three statuses that have no real score and aren't just "not yet kicked off" either
 * — [ScheduleMatchRow]'s center slot falls back to plain kickoff time whenever
 * [showsFinalOrLiveScore] is false, so this catches the one case that would be wrong to show a
 * kickoff time for: a match that was called off and isn't going to start at that time at all. */
private fun Fixture.isPostponedCancelledOrSuspended(): Boolean =
    status == MatchStatus.POSTPONED || status == MatchStatus.CANCELLED || status == MatchStatus.SUSPENDED

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
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 1f.gridUnitsAsDp()),
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
    title: String?,
    onBack: () -> Unit,
    onMatchClick: (Fixture) -> Unit,
    onOpenStandingsTable: (Int, String) -> Unit,
    tabs: TeamTabsState,
    onSelectTab: (Int) -> Unit,
    onPlayerClick: (playerId: Int, name: String, photoBytes: ByteArray?) -> Unit,
    onOpenStatPicker: () -> Unit,
    onSelectStat: (String) -> Unit,
    onSelectStatSort: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Header text was removed on request in an earlier round — this used to show
        // summary?.teamName (the actual team's name, not a literal "My Team" label) once loaded,
        // which was also this screen's only on-screen indicator of *which* team you're looking at
        // when reused for ScoreScreenMode.TeamDetail (an arbitrary tapped team, not necessarily the
        // user's own saved one — see that mode's doc comment). "My Team" added back as [title] on
        // request, but only for the MyTeam call site — TeamDetail still passes null, so Team Detail
        // and My Team still render identically with no in-page way to tell them apart when [title]
        // is null; that gap is still flagged, not solved, same as before.
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = title?.let { LightTopBarCenter.Text(it) },
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
                modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 1f.gridUnitsAsDp()),
            ) {
                // Crest + today/next-match placeholder, moved inside the scroll view on request so
                // this row scrolls away with the rest of the content instead of staying pinned at
                // the top. The standings block that used to sit under a centered crest here was
                // dropped entirely, though its rank now resurfaces in this same row's top-right
                // corner (see [MyTeamHeaderRow]'s doc comment).
                MyTeamHeaderRow(
                    summary = summary,
                    onMatchClick = onMatchClick,
                    onOpenStandingsTable = onOpenStandingsTable,
                    modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
                )
                // Matches / Squad, on request. The header row above stays put across both tabs.
                LabeledTabRow(
                    labels = listOf("Matches", "Squad", "Stats"),
                    selectedIndex = tabs.selectedTab,
                    onSelect = onSelectTab,
                    modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                )
                if (tabs.selectedTab == 1) {
                    // Who's out moved here from the Matches tab, on request: it's about players.
                    if (summary.unavailable.isNotEmpty()) {
                        UnavailableBlock(summary.unavailable, modifier = Modifier.padding(top = 1f.gridUnitsAsDp()))
                    }
                    SquadSection(tabs, onPlayerClick, modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()))
                    return@LightScrollView
                }
                if (tabs.selectedTab == 2) {
                    TeamStatsSection(
                        tabs = tabs,
                        onOpenStatPicker = onOpenStatPicker,
                        onSelectStat = onSelectStat,
                        onSelectSort = onSelectStatSort,
                        onPlayerClick = { playerId, name -> onPlayerClick(playerId, name, tabs.squadPhotos[playerId]) },
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
                    )
                    return@LightScrollView
                }
                // Recent results, then next results.
                if (summary.recentFixtures.isNotEmpty()) {
                    MatchGroupCard(
                        title = "RECENT RESULTS",
                        matches = summary.recentFixtures,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                        focusTeamId = summary.teamId,
                        showResultBadge = true,
                        onMatchClick = onMatchClick,
                    )
                }
                if (summary.upcomingFixtures.isNotEmpty()) {
                    MatchGroupCard(
                        title = "UPCOMING",
                        matches = summary.upcomingFixtures,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                        showDate = true,
                        focusTeamId = summary.teamId,
                        allowKickoffLabelWrap = true,
                        // On request: no match here has a score yet anyway, so the reserved score
                        // column was always empty — dropping it gives the opponent text that width
                        // back instead of wrapping to a second line.
                        showScoreSlot = false,
                        onMatchClick = onMatchClick,
                    )
                }
                if (summary.featuredFixture == null &&
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
 * match — [MyTeamSummary.featuredFixture] — in the middle, clickable the same as any other match
 * row on this screen; on request, [MyTeamSummary.standingsRow]'s league position now resurfaces
 * top-right, across from the crest — the standings block this row's own layout replaced is gone,
 * but the one number from it worth keeping at a glance (where this team actually sits) is back,
 * just relocated and tappable straight through to [StandingsTableContent] for that league
 * ([onOpenStandingsTable], threaded down from [SoccerViewModel.openStandingsTable] — see that
 * fun's doc comment for how "back" now correctly returns here instead of always landing on
 * Scores). Omitted (not just blank) when [MyTeamSummary.standingsRow] is null (standings haven't
 * loaded, or the team isn't in that competition's table — see that field's own doc comment) or,
 * on request, when the followed league is continental rather than domestic
 * ([competitionIsDomestic] — a UCL/UEL group-stage position isn't the domestic-table rank this row
 * is meant to show at a glance). */
@Composable
private fun MyTeamHeaderRow(
    summary: MyTeamSummary,
    onMatchClick: (Fixture) -> Unit,
    onOpenStandingsTable: (Int, String) -> Unit,
    modifier: Modifier = Modifier,
) {
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
        // Domestic leagues only, on request — a UCL/UEL group-stage position doesn't belong next to
        // a team's actual table standing, so this whole block is skipped for a team followed from a
        // continental context (competitionIsDomestic(summary.leagueId) == false) even when
        // standingsRow did resolve for it.
        if (summary.standingsRow != null && competitionIsDomestic(summary.leagueId)) {
            val standingsRow = summary.standingsRow
            // Two lines on request ("needs to be given more space") — one line at Fine size was
            // still cramped for a full league name (e.g. "16th · Championship" was ellipsizing to
            // "16th · Cha…" even after the 6.5f width bump). Rank on its own top line, the same
            // shorthand league name used on Scores (competitionShortName — e.g. "EPL", not the full
            // "Premier League") on its own line below, smaller since the rank is the number someone
            // actually glances here for. Both lines share the lightClickable so tapping either opens
            // Standings, same as before.
            //
            // No .align(Alignment.Top) — the outer Row is already verticalAlignment =
            // CenterVertically, and the explicit Top override was making this block sit noticeably
            // higher than the crest/featured-match content beside it ("strangely aligned" per user
            // report). End padding added because the enclosing LightScrollView only pads its start
            // edge (see the padding(start = 1f...) a few lines up at the call site), so this was the
            // only element in the row with nothing keeping it off the screen's right edge.
            Column(
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .widthIn(max = 6.5f.gridUnitsAsDp())
                    .padding(end = 1f.gridUnitsAsDp())
                    .lightClickable(onClick = { onOpenStandingsTable(summary.leagueId, summary.leagueName) }),
            ) {
                LightText(
                    text = standingsRow.position.asOrdinal(),
                    variant = LightTextVariant.Fine,
                    lighten = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LightText(
                    text = competitionShortName(summary.leagueId),
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 0.1f.gridUnitsAsDp()),
                )
            }
        }
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
        // Bumped along with the rest of this placeholder on request, Superfine (16) -> Detail (20)
        // — this fills the same slot as the date/opponent/kickoff text below when there's no match.
        LightText(
            text = "No upcoming match scheduled.",
            variant = LightTextVariant.Detail,
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

    // Whole block bumped one size on request ("increase the font size for all font next to the
    // badge — date of next match, vs who, kickoff time"): date Superfine (16) -> Detail (20),
    // opponent name and kickoff/score line Detail (20) -> Fine (25).
    Column(modifier = modifier.lightClickable(onClick = { onMatchClick(fixture) })) {
        LightText(text = dateLabel, variant = LightTextVariant.Detail, lighten = true)
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
                variant = LightTextVariant.Fine,
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
            variant = LightTextVariant.Fine,
            lighten = true,
            modifier = Modifier.padding(top = 0.1f.gridUnitsAsDp()),
        )
    }
}

// No card fill on request, same reasoning and same real-device finding as [ScheduleDayCard]'s own
// background removal: a low-alpha grey fill rendered as a visibly solid block on the real device
// rather than the subtle tint it looked like in preview. In its place, a thin divider (still
// `contentSecondary` at 15% alpha, matching [MatchGroupCard]'s between-match divider) separates the
// Injured/Suspended groups from each other, and [UnavailableGroup] adds the same divider between
// individual players — that's the "small grey line" the user asked to keep, extended down to the
// player level since a bare list of names with no card edge needs it more than a bordered one did.
@Composable
private fun UnavailableBlock(players: List<UnavailablePlayer>, modifier: Modifier = Modifier) {
    val injured = players.filter { it.kind == UnavailabilityKind.INJURED }
    val suspended = players.filter { it.kind == UnavailabilityKind.SUSPENDED }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(1.2f.gridUnitsAsDp()))
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
    ) {
        // Whole section bumped one size on request: Superfine (16) -> Detail (20), Detail -> Fine
        // (25) for player.playerName, so the relative sizing within this block is preserved.
        LightText(text = "UNAVAILABLE FOR NEXT MATCH", variant = LightTextVariant.Detail, lighten = true)
        if (injured.isNotEmpty()) {
            UnavailableGroup(label = "Injured", players = injured, modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()))
        }
        if (suspended.isNotEmpty()) {
            if (injured.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.5f.gridUnitsAsDp())
                        .height(1.dp)
                        .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.15f)),
                )
            }
            UnavailableGroup(label = "Suspended", players = suspended, modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()))
        }
    }
}

@Composable
private fun UnavailableGroup(label: String, players: List<UnavailablePlayer>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        LightText(text = label, variant = LightTextVariant.Detail, lighten = true)
        players.forEachIndexed { index, player ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.2f.gridUnitsAsDp())
                        .height(1.dp)
                        .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.15f)),
                )
            }
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
private fun MatchDetailContent(
    mode: ScoreScreenMode.MatchDetailScreen,
    onBack: () -> Unit,
    onTeamClick: (teamId: Int, teamName: String, leagueId: Int) -> Unit,
    onSelectTab: (Int) -> Unit,
    onPlayerClick: (player: LineupPlayer, photoBytes: ByteArray?) -> Unit,
) {
    // Held in the view model (see MatchDetailScreen.selectedTab) so it survives a trip to a player.
    val selectedTab = DetailTab.entries.getOrElse(mode.selectedTab) { DetailTab.STATS }
    val onLineupPlayerClick: (LineupPlayer) -> Unit = { player ->
        onPlayerClick(player, player.id?.let { mode.playerPhotosById[it] })
    }

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
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 1f.gridUnitsAsDp()),
        ) {
            MatchDetailHeader(
                homeTeamName = mode.homeTeamName,
                awayTeamName = mode.awayTeamName,
                homeTeamId = mode.homeTeamId,
                awayTeamId = mode.awayTeamId,
                leagueId = mode.leagueId,
                homeTeamLogoBytes = mode.homeTeamLogoBytes,
                awayTeamLogoBytes = mode.awayTeamLogoBytes,
                scoreLabel = mode.scoreLabel,
                statusLabel = mode.statusLabel,
                isLive = mode.isLive,
                goalEvents = mode.detail?.events?.filter { it.type == MatchEventType.GOAL } ?: emptyList(),
                onTeamClick = onTeamClick,
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
                    LabeledTabRow(
                        labels = DetailTab.entries.map { it.label },
                        selectedIndex = selectedTab.ordinal,
                        onSelect = onSelectTab,
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
                            playerPhotosById = mode.playerPhotosById,
                            playerRatings = mode.playerRatings,
                            onPlayerClick = onLineupPlayerClick,
                            modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
                        )
                        DetailTab.AWAY_LINEUP -> LineupSection(
                            teamName = mode.awayTeamName,
                            lineup = detail.lineups.away,
                            teamColor = awayTeamColor,
                            playerPhotosById = mode.playerPhotosById,
                            playerRatings = mode.playerRatings,
                            onPlayerClick = onLineupPlayerClick,
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
    homeTeamId: Int,
    awayTeamId: Int,
    leagueId: Int,
    homeTeamLogoBytes: ByteArray?,
    awayTeamLogoBytes: ByteArray?,
    scoreLabel: String,
    statusLabel: String,
    isLive: Boolean,
    goalEvents: List<MatchEvent> = emptyList(),
    onTeamClick: (teamId: Int, teamName: String, leagueId: Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            MatchDetailTeamBlock(
                name = homeTeamName,
                logoBytes = homeTeamLogoBytes,
                onClick = { onTeamClick(homeTeamId, homeTeamName, leagueId) },
                modifier = Modifier.weight(1f),
            )
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
            MatchDetailTeamBlock(
                name = awayTeamName,
                logoBytes = awayTeamLogoBytes,
                onClick = { onTeamClick(awayTeamId, awayTeamName, leagueId) },
                modifier = Modifier.weight(1f),
            )
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
                // Bumped one size on request ("increase the font for all elements in the header
                // except the team names and box score"), Superfine (16) -> Detail (20).
                LightText(text = statusLabel, variant = LightTextVariant.Detail)
            }
        } else {
            LightText(
                text = statusLabel,
                variant = LightTextVariant.Detail,
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
 * failed — a missing crest was never a reason to hide the name. Tapping anywhere on the block
 * (crest or name) opens that team's own page — see [onClick]/[SoccerViewModel.openTeamDetail].
 *
 * Crest bumped 2f -> 4f on request ("on any/all match pages, make the badges larger, same size as
 * they appear on my team page"). Interpreting "match pages" as this screen (`ScoreScreenMode.
 * MatchDetail`, one match shown at a time) and "my team page" size as [MyTeamHeaderRow]'s own
 * 4f team crest — not [TEAM_CREST_SIZE] (1.5f), the small crest used in Scores' and My Team's own
 * match *list* rows, which was deliberately sized down from an earlier 3f after real-device
 * feedback that it left only ~4 matches visible per screen; re-enlarging that one would undo that
 * fix. This screen only ever shows a single match, so the same crowding concern doesn't apply —
 * flagging the size choice since it wasn't unambiguous and hasn't been seen on a real device yet. */
@Composable
private fun MatchDetailTeamBlock(name: String, logoBytes: ByteArray?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val logoBitmap = logoBytes?.let { bytes ->
        remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.lightClickable(onClick = onClick),
    ) {
        if (logoBitmap != null) {
            Image(
                bitmap = logoBitmap,
                contentDescription = "$name crest",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .size(4f.gridUnitsAsDp())
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
                // Bumped one size on request, same header-wide pass as statusLabel above —
                // Superfine (16) -> Detail (20).
                LightText(
                    text = event.goalScorerLabel(),
                    variant = LightTextVariant.Detail,
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
                    variant = LightTextVariant.Detail,
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
                // Whole row bumped one size in an earlier round: values Detail (20) -> Fine (25),
                // label Superfine (16) -> Detail (20). On request, the label bumped one size again,
                // Detail (20) -> Fine (25) — now the same size as the values either side of it,
                // rather than one step smaller.
                LightText(text = row.homeValue, variant = LightTextVariant.Fine, align = TextAlign.Center, modifier = Modifier.weight(0.25f))
                LightText(
                    text = prettifyStatLabel(row.label),
                    variant = LightTextVariant.Fine,
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
            // Bumped with the rest of this tab's text on request, Superfine (16) -> Detail (20) —
            // still comfortably inside this Box's fixed 1.4f-grid-unit size.
            MatchEventType.GOAL -> LightText(text = "⚽", variant = LightTextVariant.Detail, align = TextAlign.Center)
            MatchEventType.SUBSTITUTION -> LightText(text = "⇄", variant = LightTextVariant.Detail, align = TextAlign.Center)
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
        // Minute column bumped one size in an earlier round, Superfine (16) -> Detail (20); bumped
        // one more on request, Detail -> Fine (25), same "increase all text" pass that hit the Stats
        // tab — the secondary team/subtext line below the headline goes from Superfine to Detail,
        // one step behind, same relationship as before.
        LightText(
            text = event.minuteLabel,
            variant = LightTextVariant.Fine,
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
            // later global one-step-down pass, then back to Detail. Bumped one more size on request,
            // Detail -> Fine — this is the event's own description text ("Goal — Haaland", "Yellow
            // Card — Smith", etc.), see MatchEvent.headline's doc comment in SoccerModels.kt.
            LightText(text = event.headline, variant = LightTextVariant.Fine, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val secondary = listOfNotNull(event.teamName.takeIf { it.isNotBlank() }, event.subtext).joinToString(" · ")
            if (secondary.isNotBlank()) {
                LightText(
                    text = secondary,
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 0.1f.gridUnitsAsDp()),
                )
            }
        }
    }
}

/** The row of equal-width tab buttons under a detail screen's header — Match Detail's
 * Stats/Events/Home/Away, and the player screen's Summary/Stats/Matches/Career. */
@Composable
private fun LabeledTabRow(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.4f.gridUnitsAsDp()),
    ) {
        labels.forEachIndexed { index, label ->
            DetailTabButton(
                text = label,
                isSelected = index == selectedIndex,
                onClick = { onSelect(index) },
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
            // Bumped one size on request (Superfine 16sp -> Detail 20sp), same progression used
            // elsewhere this session.
            variant = LightTextVariant.Detail,
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
 * band of number dots, and the bands stack bottom-to-top with the goalkeeper's band at the very
 * bottom — [groupedByPitchRow] returns rows goalkeeper-first, so this reverses that list before
 * rendering, per that function's own doc comment in SoccerModels.kt. Both home and away render
 * the same way; there's no mirroring one team's pitch left-to-right against the other's the way
 * the old horizontal layout did, since a shared "keeper at the bottom" orientation doesn't need
 * it. On request, each player's last name ([LineupPlayer.lastName]) now sits directly under their
 * own dot instead of in a separate side list (see [PitchPlayerColumn]) — an earlier round moved
 * names out of the pitch into a side roster list (`LineupRosterList`, removed) because a name per dot didn't leave
 * enough width when dots were laid out as up-to-5-wide columns; that side list is gone again now
 * that a per-dot label is what was actually asked for. Dot size ([PitchNumberDot]) and every
 * text size in this tab were unchanged by that particular round, on request — only the *layout*
 * (one column beside the pitch vs. one label per dot) moved, not any size. Dot/headshot size did
 * later grow on its own separate request — see [PITCH_DOT_SIZE_UNITS]'s doc comment — while the
 * name label size stayed exactly as it was here.
 *
 * The header row's leading slot (beneath the Stats/Events/Home/Away tab buttons) shows the coach's
 * name now, on request, not [teamName] — [teamName] is still this fun's early-return empty-state
 * text, and the coach-name slot's own fallback when there's no coach in the response, just no
 * longer the header's first choice. The "Coach: {name}" line that used to sit below the pitch is
 * gone with it (moved, not duplicated).
 */
@Composable
private fun LineupSection(
    teamName: String,
    lineup: TeamLineup?,
    teamColor: Color?,
    playerPhotosById: Map<Int, ByteArray>,
    playerRatings: Map<Int, Double>,
    onPlayerClick: (LineupPlayer) -> Unit,
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
            // On request, this leading slot now shows the coach's name instead of the team name —
            // moved up from its own line below the pitch (see this fun's own doc comment for what
            // used to live there). Falls back to [teamName] when [TeamLineup.coachName] is null (no
            // coach in this response) so the header never goes blank — [teamName] itself is still
            // guaranteed non-null (it's also this fun's early-return empty-state text above), just
            // no longer the *first* choice here. This row already shares its width with the
            // formation label, so even a Detail-size long name can still be too wide for one line —
            // maxLines=1 + Ellipsis trades a truncated name for avoiding an ugly mid-word wrap.
            LightText(
                text = lineup.coachName ?: teamName,
                variant = LightTextVariant.Detail,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            lineup.formation?.let { LightText(text = it, variant = LightTextVariant.Detail, lighten = true) }
        }

        // Full-width now that there's no side name list sharing this row with the pitch (see this
        // fun's own doc comment) — was two weighted columns (0.44f roster list / 0.56f pitch)
        // before this round.
        Column(
            // Grey fill behind the pitch removed on request ("remove the grey background behind
            // the formation visualization"). The clip stays — harmless with nothing to clip now,
            // cheap insurance if a background fill returns here later, same call this codebase's
            // made for other background removals (see ScheduleDayCard's own doc comment).
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(1.2f.gridUnitsAsDp()))
                .padding(vertical = 0.8f.gridUnitsAsDp(), horizontal = 0.3f.gridUnitsAsDp()),
            // Widened from 0.9f on request's implied need: each pitch-line band now stacks a name
            // label under its dots (see PitchPlayerColumn below), taller than the dot-only band
            // this spacing was originally tuned for, so the old gap would have crowded one row's
            // name against the next row's dots. An estimate, like every other spacing value in
            // this file — no compiler/emulator here to render-check it.
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(1.4f.gridUnitsAsDp()),
        ) {
            pitchRows.forEach { row ->
                // Each column takes an equal weighted share of the row's full width — on request
                // ("use the full width of screen"), replacing a fixed per-column width that left a
                // 2-3-player row (attack, midfield) bunched dead-center instead of spread out like
                // a real formation graphic. A 5-player defensive line already read as roughly full
                // width before this change; this makes every row's width consistent regardless of
                // player count, and gives a sparser row's longer surnames much more room before
                // they need PitchPlayerColumn's own maxLines=1 + Ellipsis fallback.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.3f.gridUnitsAsDp()),
                ) {
                    row.forEach { player ->
                        PitchPlayerColumn(
                            player,
                            dotColor = teamColor,
                            photoBytes = player.id?.let { playerPhotosById[it] },
                            rating = player.id?.let { playerRatings[it] },
                            modifier = Modifier
                                .weight(1f)
                                .lightClickable(enabled = player.id != null) { onPlayerClick(player) },
                        )
                    }
                }
            }
        }

        // The "Coach: {name}" line that used to live here moved up to this fun's header row on
        // request — see that row's own doc comment. Its headshot was dropped in an earlier round,
        // and the coach-photo fetch behind it has since been removed too.

        if (lineup.substitutes.isNotEmpty()) {
            SubstitutesBlock(
                lineup.substitutes,
                playerRatings = playerRatings,
                onPlayerClick = onPlayerClick,
                modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
            )
        }
    }
}

/** One pitch dot plus its player's last name directly underneath, on request — replaces the old
 * side [LineupSection] roster list (see that fun's own doc comment for the "why back to per-dot
 * labels" history). [PitchNumberDot] now renders a headshot image in place of the number-in-circle
 * when [photoBytes] is available (see that fun's own doc comment, on request: "replace icons with
 * numbers with head shot images... If the headshot is not available, please have the number in
 * circle as a stand in") — dot *size* is unchanged either way. This just wraps the dot with a name
 * label below, both centered so a short jersey number/headshot and a longer surname still line up
 * with each other and with neighboring dots in the same pitch-line band.
 * [modifier] is always an equal `Modifier.weight(1f)` from [LineupSection]'s pitch-row Row now, on
 * request ("use the full width of screen") — no fixed width of its own, so this column's actual
 * width (and therefore how much room the name label below the dot gets before it ellipsizes)
 * depends entirely on how many players share that row; a fixed width was tried first and rejected
 * for leaving a sparse 2-3-player row (attack, midfield) bunched dead-center instead of spread
 * across the pitch. */
@Composable
private fun PitchPlayerColumn(
    player: LineupPlayer,
    dotColor: Color?,
    photoBytes: ByteArray?,
    rating: Double?,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier,
    ) {
        PitchNumberDot(player, dotColor = dotColor, photoBytes = photoBytes)
        // Same Detail size the old side roster list used for names (see this fun's doc comment) —
        // "keep all the same sizes of text and dots" was explicit on request, so only this label's
        // position moved, not its size.
        LightText(
            text = player.lastName,
            variant = LightTextVariant.Detail,
            align = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 0.2f.gridUnitsAsDp()),
        )
        // Match rating, on request, as a second line under the name — a live or finished match's.
        rating?.let { RatingPill(it, modifier = Modifier.padding(top = 0.15f.gridUnitsAsDp())) }
    }
}

// Bumped from 2.3f on request ("images themselves should be larger"), sized against the *tightest*
// row this dot ever appears in — a 5-across defensive or midfield line, the widest [LineupSection]
// ever renders per that fun's own doc comment ("at most 5 players will ever be included in a single
// line"). Traced the same way the standings-column alignment fix was (see [StandingsTableContent]'s
// doc comment) — real padding chain, not a guess:
//   27 (LightGrid.WIDTH, full screen)
//   -  1 (MatchDetailContent's LightScrollView: Modifier.padding(start = 1f))
//   -  2 (LightScrollView's own unconditional end padding — scrollBarGutterUnits(Outside), see that
//         fun's doc comment)
//   = 24 available inside the scroll view
//   -  0.6 (LineupSection's pitch Column: padding(horizontal = 0.3f) on each side)
//   = 23.4 for the pitch-row Row's fillMaxWidth width
//   -  1.2 (that Row's Arrangement.spacedBy(0.3f) — 4 gaps between 5 columns)
//   = 22.2, split 5 equal PitchPlayerColumn weights = 4.44 grid units per column in a 5-wide row.
// 3.4f leaves 1.04 grid units of slack in that tightest case — split across both sides of the dot
// plus the 0.3f column gap, that's ~1.34 grid units of breathing room between two neighboring dots
// in a 5-across line, comfortably more than the dots' own gap was even at the old 2.3f size. A wider
// row (2-4 players) only has more room than this, never less, so 5-across is the only case that
// needed checking. Like every size in this file, this is a real measured-against-the-layout number,
// not a render-checked one — there's no compiler/emulator in this pipeline to confirm it on device.
private const val PITCH_DOT_SIZE_UNITS = 3.4f

/** [photoBytes] — this player's headshot, if [SoccerViewModel.openMatchDetail]'s follow-up fetch
 * (see [ScoreScreenMode.MatchDetailScreen.playerPhotosById]'s doc comment) found one — renders as a
 * circular image at [PITCH_DOT_SIZE_UNITS] (see that const's doc comment for why that size), no
 * fixed-width-then-fallback layout shift either way. Null (no id on this player, fetch failed, or
 * hasn't resolved yet) falls back to the pre-existing number-in-circle rendering, exactly per the
 * request ("If the headshot is not available, please have the number in circle as a stand in") —
 * this fallback is the *only* path for a substitute row too, since only the starting XI's own pitch
 * dots use this composable at all ([SubstitutesBlock] below has always rendered substitutes as a
 * plain number without a dot). The player *name* label below this dot ([PitchPlayerColumn]) is
 * deliberately untouched by this size bump — on request, only the image grows, not the text. */
@Composable
private fun PitchNumberDot(player: LineupPlayer, dotColor: Color?, photoBytes: ByteArray?, modifier: Modifier = Modifier) {
    val photoBitmap = photoBytes?.let { bytes ->
        remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    if (photoBitmap != null) {
        Image(
            bitmap = photoBitmap,
            contentDescription = player.name,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(PITCH_DOT_SIZE_UNITS.gridUnitsAsDp())
                .clip(CircleShape),
        )
        return
    }
    // A saturated dotColor needs its own text color to stay legible — the theme's default content
    // color assumes the neutral, low-alpha background this dot had before team colors existed, and
    // can end up light-on-light or dark-on-dark against a real team hue.
    val numberColor = dotColor?.let { legibleTextColorOn(it) }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(PITCH_DOT_SIZE_UNITS.gridUnitsAsDp())
            .clip(CircleShape)
            .background(dotColor?.copy(alpha = 0.55f) ?: LightThemeTokens.colors.contentSecondary.copy(alpha = 0.22f)),
    ) {
        // Deliberately left at Superfine (not bumped with the rest of the Lineup tab) — the user
        // asked to enlarge "all text one size Except for the numbers behind the circles", and this
        // dot's number is exactly that: it's already visually prominent inside its own colored
        // circle, unlike the (now per-dot, see PitchPlayerColumn) name label's plain text.
        LightText(text = player.number?.toString() ?: "-", variant = LightTextVariant.Superfine, color = numberColor)
    }
}

@Composable
private fun SubstitutesBlock(
    substitutes: List<LineupPlayer>,
    playerRatings: Map<Int, Double>,
    onPlayerClick: (LineupPlayer) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Whole block bumped one size on request (Superfine -> Detail), same as the rest of the
        // Lineup tab except PitchNumberDot's own number.
        LightText(text = "SUBSTITUTES", variant = LightTextVariant.Detail, lighten = true, modifier = Modifier.padding(bottom = 0.4f.gridUnitsAsDp()))
        substitutes.forEach { player ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(enabled = player.id != null) { onPlayerClick(player) }
                    .padding(vertical = 0.1f.gridUnitsAsDp()),
            ) {
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
                // Only substitutes who came on have a rating.
                player.id?.let { playerRatings[it] }?.let {
                    RatingPill(it, modifier = Modifier.padding(start = 0.5f.gridUnitsAsDp()))
                }
            }
        }
    }
}

// --- Player ------------------------------------------------------------------------------------

private enum class PlayerTab(val label: String) {
    SUMMARY("Summary"),
    STATS("Stats"),
    MATCHES("Matches"),
    CAREER("Career"),
}

private const val PLAYER_PHOTO_SIZE_UNITS = 4f
// Right-aligned number columns in the Summary/Stats rows — wide enough for "72.52" or "1,234" at
// Detail size, measured against the 24 grid units inside the scroll view (see PITCH_DOT_SIZE_UNITS'
// comment for that padding chain).
private const val PLAYER_VALUE_COLUMN_UNITS = 4f

/** A player's season (see [ScoreScreenMode.PlayerDetailScreen]), opened from a match lineup: a
 * header, then the same tab row Match Detail uses, switching between Summary (appearances, goals,
 * minutes... for the season, plus a per-competition line each), Stats (totals and per 90),
 * Matches (the season's match list), and Career (clubs and national teams). The selected tab is
 * plain local state: nothing navigates away from this screen and back into it. */
@Composable
private fun PlayerDetailContent(mode: ScoreScreenMode.PlayerDetailScreen, onBack: () -> Unit) {
    var selectedTab by remember(mode.playerId) { mutableStateOf(PlayerTab.SUMMARY) }

    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text("Player"),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        LightScrollView(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 1f.gridUnitsAsDp()),
        ) {
            PlayerHeader(mode)

            val detail = mode.detail
            when {
                mode.isLoading -> NoDataForTab(text = "fetching player...")
                detail == null -> NoDataForTab(
                    text = if (mode.notFound) "No stats for this player yet." else "Couldn't load this player.",
                )
                else -> {
                    LabeledTabRow(
                        labels = PlayerTab.entries.map { it.label },
                        selectedIndex = selectedTab.ordinal,
                        onSelect = { selectedTab = PlayerTab.entries[it] },
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                    )
                    val sectionModifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp())
                    when (selectedTab) {
                        PlayerTab.SUMMARY -> PlayerSummarySection(detail, sectionModifier)
                        PlayerTab.STATS -> PlayerStatsSection(detail, sectionModifier)
                        PlayerTab.MATCHES -> PlayerMatchesSection(detail.matches, sectionModifier)
                        PlayerTab.CAREER -> PlayerCareerSection(detail.career, sectionModifier)
                    }
                }
            }
        }
    }
}

/** Headshot (the one the lineup already downloaded), name, then team · position and age ·
 * nationality. Team and position come from the competition the player has the most minutes in —
 * the proxy lists competitions most-played first. */
@Composable
private fun PlayerHeader(mode: ScoreScreenMode.PlayerDetailScreen, modifier: Modifier = Modifier) {
    val detail = mode.detail
    val mainCompetition = detail?.competitions?.firstOrNull()
    val photo = mode.photoBytes?.let { bytes ->
        remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(end = 1f.gridUnitsAsDp()),
    ) {
        if (photo != null) {
            Image(
                bitmap = photo,
                contentDescription = mode.playerName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(PLAYER_PHOTO_SIZE_UNITS.gridUnitsAsDp()).clip(CircleShape),
            )
        }
        Column(modifier = Modifier.weight(1f).padding(start = (if (photo != null) 0.8f else 0f).gridUnitsAsDp())) {
            LightText(
                text = detail?.player?.name ?: mode.playerName,
                variant = LightTextVariant.Copy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            listOfNotNull(mainCompetition?.teamName, mainCompetition?.position)
                .joinToString(" · ")
                .takeIf { it.isNotEmpty() }
                ?.let { LightText(text = it, variant = LightTextVariant.Detail, lighten = true, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            listOfNotNull(detail?.player?.age?.let { "Age $it" }, detail?.player?.nationality)
                .joinToString(" · ")
                .takeIf { it.isNotEmpty() }
                ?.let { LightText(text = it, variant = LightTextVariant.Detail, lighten = true, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun PlayerSectionLabel(text: String, modifier: Modifier = Modifier) {
    LightText(text = text, variant = LightTextVariant.Detail, lighten = true, modifier = modifier.padding(bottom = 0.4f.gridUnitsAsDp()))
}

/** One label/value row, optionally with a second (per 90) value column. */
@Composable
private fun PlayerStatRow(label: String, value: String, per90: String? = null, showPer90Column: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(end = 1f.gridUnitsAsDp(), top = 0.15f.gridUnitsAsDp(), bottom = 0.15f.gridUnitsAsDp()),
    ) {
        LightText(text = label, variant = LightTextVariant.Detail, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        LightText(text = value, variant = LightTextVariant.Detail, align = TextAlign.End, modifier = Modifier.width(PLAYER_VALUE_COLUMN_UNITS.gridUnitsAsDp()))
        if (showPer90Column) {
            LightText(
                text = per90 ?: "",
                variant = LightTextVariant.Detail,
                lighten = true,
                align = TextAlign.End,
                modifier = Modifier.width(PLAYER_VALUE_COLUMN_UNITS.gridUnitsAsDp()),
            )
        }
    }
}

private fun Int?.statLabel(): String = this?.toString() ?: "-"

private fun Double?.twoDecimals(): String = this?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "-"

@Composable
private fun PlayerSummarySection(detail: PlayerDetail, modifier: Modifier = Modifier) {
    val totals = detail.totals
    Column(modifier = modifier.fillMaxWidth()) {
        if (totals == null) {
            NoDataForTab(text = "No league or European appearances this season yet.")
        } else {
            PlayerSectionLabel("THIS SEASON")
            PlayerStatRow("Matches", totals.appearances.statLabel())
            PlayerStatRow("Started", totals.starts.statLabel())
            PlayerStatRow("Minutes", totals.minutes.statLabel())
            PlayerStatRow("Goals", totals.goals.statLabel())
            PlayerStatRow("Assists", totals.assists.statLabel())
            PlayerStatRow("Rating", totals.rating.twoDecimals())
            PlayerStatRow("Yellow cards", totals.yellowCards.statLabel())
            PlayerStatRow("Red cards", totals.redCards.statLabel())
            if (detail.excludesDomesticCups) {
                LightText(
                    text = "League and European matches only — domestic cups aren't included.",
                    variant = LightTextVariant.Superfine,
                    lighten = true,
                    modifier = Modifier.padding(top = 0.4f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
                )
            }
            if (detail.competitions.size > 1) {
                PlayerSectionLabel("BY COMPETITION", modifier = Modifier.padding(top = 1f.gridUnitsAsDp()))
                detail.competitions.forEach { c ->
                    PlayerStatRow(
                        label = c.leagueId?.let { competitionShortName(it) } ?: c.leagueName ?: "-",
                        value = "${c.appearances.statLabel()} apps",
                        per90 = "${c.goals ?: 0}G ${c.assists ?: 0}A",
                        showPer90Column = true,
                    )
                }
            }
        }
    }
}

/** Totals and per-90 figures for the season. Goalkeepers lead with saves and goals conceded. */
@Composable
private fun PlayerStatsSection(detail: PlayerDetail, modifier: Modifier = Modifier) {
    val totals = detail.totals
    Column(modifier = modifier.fillMaxWidth()) {
        if (totals == null) {
            NoDataForTab(text = "No league or European appearances this season yet.")
        } else {
            val isGoalkeeper = detail.competitions.firstOrNull()?.position == "Goalkeeper"
            // (label, total, per-90 key in PlayerSeasonStats.per90 — null for no per-90 figure)
            val outfield = listOf(
                Triple("Goals", totals.goals.statLabel(), "goals"),
                Triple("Assists", totals.assists.statLabel(), "assists"),
                Triple("Shots", totals.shots.statLabel(), "shots"),
                Triple("Shots on target", totals.shotsOnTarget.statLabel(), "shots_on_target"),
                Triple("Key passes", totals.keyPasses.statLabel(), "key_passes"),
                Triple("Passes", totals.passes.statLabel(), "passes"),
                Triple("Pass accuracy", totals.passAccuracy?.let { "${it.toInt()}%" } ?: "-", null),
                Triple("Tackles", totals.tackles.statLabel(), "tackles"),
                Triple("Interceptions", totals.interceptions.statLabel(), "interceptions"),
                Triple("Duels won", totals.duelsWon.statLabel(), "duels_won"),
                Triple("Dribbles won", totals.dribblesWon.statLabel(), "dribbles_won"),
                Triple("Fouls drawn", totals.foulsDrawn.statLabel(), "fouls_drawn"),
                Triple("Fouls committed", totals.foulsCommitted.statLabel(), "fouls_committed"),
            )
            val keeper = listOf(
                Triple("Saves", totals.saves.statLabel(), "saves"),
                Triple("Goals conceded", totals.goalsConceded.statLabel(), null),
            )
            val rows = if (isGoalkeeper) keeper + outfield else outfield
            Row(modifier = Modifier.fillMaxWidth().padding(end = 1f.gridUnitsAsDp(), bottom = 0.2f.gridUnitsAsDp())) {
                LightText(text = "${totals.minutes ?: 0} MIN PLAYED", variant = LightTextVariant.Superfine, lighten = true, modifier = Modifier.weight(1f))
                LightText(text = "TOTAL", variant = LightTextVariant.Superfine, lighten = true, align = TextAlign.End, modifier = Modifier.width(PLAYER_VALUE_COLUMN_UNITS.gridUnitsAsDp()))
                LightText(text = "PER 90", variant = LightTextVariant.Superfine, lighten = true, align = TextAlign.End, modifier = Modifier.width(PLAYER_VALUE_COLUMN_UNITS.gridUnitsAsDp()))
            }
            rows.forEach { (label, total, per90Key) ->
                PlayerStatRow(
                    label = label,
                    value = total,
                    per90 = per90Key?.let { totals.per90[it].twoDecimals() },
                    showPer90Column = true,
                )
            }
        }
    }
}

// Player → Matches columns, right of the opponent: minutes ("90'") and rating ("7.6") need about
// three Fine-size characters; goals and assists one.
private const val PLAYER_MATCH_WIDE_COLUMN_UNITS = 2.8f
private const val PLAYER_MATCH_NARROW_COLUMN_UNITS = 1.8f

/** The season's matches, newest first — domestic cups included, unlike the season totals. Columns,
 * on request: opponent (date, competition, and result underneath), then minutes, rating, goals,
 * and assists at full size, under a small MIN / RTG / G / A header. */
@Composable
private fun PlayerMatchesSection(matches: List<PlayerMatch>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (matches.isEmpty()) {
            NoDataForTab(text = "No matches this season yet.")
        } else {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 1f.gridUnitsAsDp(), bottom = 0.2f.gridUnitsAsDp())) {
                Box(modifier = Modifier.weight(1f))
                PlayerMatchHeader("MIN", PLAYER_MATCH_WIDE_COLUMN_UNITS)
                PlayerMatchHeader("RTG", PLAYER_MATCH_WIDE_COLUMN_UNITS)
                PlayerMatchHeader("G", PLAYER_MATCH_NARROW_COLUMN_UNITS)
                PlayerMatchHeader("A", PLAYER_MATCH_NARROW_COLUMN_UNITS)
            }
            matches.forEach { match -> PlayerMatchRow(match) }
        }
    }
}

@Composable
private fun PlayerMatchHeader(text: String, widthUnits: Float) {
    LightText(
        text = text,
        variant = LightTextVariant.Superfine,
        lighten = true,
        align = TextAlign.End,
        modifier = Modifier.width(widthUnits.gridUnitsAsDp()),
    )
}

@Composable
private fun PlayerMatchRow(match: PlayerMatch) {
    val played = (match.minutes ?: 0) > 0
    // Zero goals/assists, and an unused substitute's minutes/rating, show as "–" so the matches
    // where something happened stand out.
    fun countLabel(value: Int?): String = value?.takeIf { it > 0 }?.toString() ?: "–"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(end = 1f.gridUnitsAsDp(), top = 0.3f.gridUnitsAsDp(), bottom = 0.3f.gridUnitsAsDp()),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LightText(
                text = "${if (match.home) "vs" else "@"} ${match.opponentName ?: "-"}",
                variant = LightTextVariant.Fine,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // The result badge that used to sit at the row's right edge gave its room to the stat
            // columns, on request; the result and score move here instead.
            val result = match.result?.let { r ->
                "$r ${match.goalsFor ?: "-"}-${match.goalsAgainst ?: "-"}"
            }
            LightText(
                text = listOfNotNull(
                    match.kickoff?.let { formatShortDate(it) },
                    match.leagueId?.let { competitionShortName(it) },
                    result,
                ).joinToString(" · "),
                variant = LightTextVariant.Superfine,
                lighten = true,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PlayerMatchValue(if (played) "${match.minutes}'" else "–", PLAYER_MATCH_WIDE_COLUMN_UNITS)
        PlayerMatchValue(
            match.rating?.takeIf { played }?.let { String.format(java.util.Locale.US, "%.1f", it) } ?: "–",
            PLAYER_MATCH_WIDE_COLUMN_UNITS,
        )
        PlayerMatchValue(countLabel(match.goals), PLAYER_MATCH_NARROW_COLUMN_UNITS)
        PlayerMatchValue(countLabel(match.assists), PLAYER_MATCH_NARROW_COLUMN_UNITS)
    }
}

@Composable
private fun PlayerMatchValue(text: String, widthUnits: Float) {
    LightText(
        text = text,
        variant = LightTextVariant.Fine,
        lighten = text == "–",
        align = TextAlign.End,
        modifier = Modifier.width(widthUnits.gridUnitsAsDp()),
    )
}

/** Clubs (youth sides dimmed), then national teams, each with the seasons the player was there. */
@Composable
private fun PlayerCareerSection(career: List<PlayerCareerTeam>?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (career.isNullOrEmpty()) {
            NoDataForTab(text = "No career history available.")
        } else {
            val (national, clubs) = career.partition { it.kind == "national" }
            if (clubs.isNotEmpty()) {
                PlayerSectionLabel("CLUBS")
                clubs.forEach { PlayerCareerRow(it) }
            }
            if (national.isNotEmpty()) {
                PlayerSectionLabel("NATIONAL TEAM", modifier = Modifier.padding(top = if (clubs.isNotEmpty()) 1f.gridUnitsAsDp() else 0f.gridUnitsAsDp()))
                national.forEach { PlayerCareerRow(it) }
            }
        }
    }
}

@Composable
private fun PlayerCareerRow(team: PlayerCareerTeam) {
    Row(modifier = Modifier.fillMaxWidth().padding(end = 1f.gridUnitsAsDp(), top = 0.15f.gridUnitsAsDp(), bottom = 0.15f.gridUnitsAsDp())) {
        LightText(
            text = team.teamName,
            variant = LightTextVariant.Detail,
            lighten = team.kind == "youth",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        LightText(
            // Seasons are API-Football's start years ("2022" = 2022/23).
            text = if (team.firstSeason == team.lastSeason) "${team.firstSeason}" else "${team.firstSeason}–${team.lastSeason}",
            variant = LightTextVariant.Detail,
            lighten = true,
            align = TextAlign.End,
        )
    }
}

// --- Stats leaderboards (a competition's Stats tab, and a team's) ------------------------------

// Width of the Total and Per 90 columns — "1,234" or "72.52" at Fine size, with room to spare
// (a two-digit Fine number needs more than 1.4 units: see SquadPlayerRow's number column).
private const val LEADER_VALUE_COLUMN_UNITS = 3.6f

/** The Competition screen's Stats tab: the stat button (see [StatChooserButton]), then either the
 * list of stats to rank by or the top-25 board ([LeaderboardTable]), scrolling beneath it. */
@Composable
private fun ColumnScope.LeadersContent(
    mode: ScoreScreenMode.Standings,
    onOpenStatPicker: () -> Unit,
    onSelectStat: (String) -> Unit,
    onSelectSort: (String) -> Unit,
    onPlayerClick: (playerId: Int, name: String) -> Unit,
) {
    val board = mode.leaders
    StatChooserButton(
        board = board,
        selectedStat = mode.selectedStat,
        pickerOpen = mode.statPickerOpen,
        onClick = onOpenStatPicker,
        modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
    )
    LightScrollView(
        modifier = Modifier.weight(1f).fillMaxWidth().padding(start = 1f.gridUnitsAsDp()),
    ) {
        when {
            mode.statPickerOpen -> StatPickerList(board, mode.selectedStat, onSelectStat)
            mode.leadersLoading -> NoDataForTab(text = "fetching stats...")
            board == null -> NoDataForTab(text = "Stats aren't available for this competition right now.")
            board.leaders.isEmpty() -> NoDataForTab(text = "No players to rank yet.")
            else -> LeaderboardTable(board, onSelectSort, showTeam = true, onPlayerClick = onPlayerClick)
        }
    }
}

/** My Team / Team Detail's Stats tab — the same board as a competition's, for the team's own
 * players. Lives inside that screen's own scroll view, so it doesn't scroll by itself. */
@Composable
private fun TeamStatsSection(
    tabs: TeamTabsState,
    onOpenStatPicker: () -> Unit,
    onSelectStat: (String) -> Unit,
    onSelectSort: (String) -> Unit,
    onPlayerClick: (playerId: Int, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        StatChooserButton(
            board = tabs.stats,
            selectedStat = tabs.statsStat,
            pickerOpen = tabs.statPickerOpen,
            onClick = onOpenStatPicker,
            modifier = Modifier.padding(end = 1f.gridUnitsAsDp()),
        )
        val board = tabs.stats
        when {
            tabs.statPickerOpen -> StatPickerList(board, tabs.statsStat, onSelectStat)
            tabs.statsLoading -> NoDataForTab(text = "fetching stats...")
            board == null || board.leaders.isEmpty() -> NoDataForTab(text = "No player stats for this team this season yet.")
            else -> LeaderboardTable(board, onSelectSort, showTeam = false, onPlayerClick = onPlayerClick)
        }
    }
}

/** The button naming the stat a board is ranked by; tapping it swaps the board for the list of
 * stats ([StatPickerList]). A hint underneath says so, except while that list is open. */
@Composable
private fun StatChooserButton(
    board: LeagueLeaders?,
    selectedStat: String,
    pickerOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = board?.stats?.firstOrNull { it.key == selectedStat }?.label ?: board?.label ?: "Goals"
    Column(modifier = modifier.fillMaxWidth()) {
        DetailTabButton(text = label, isSelected = true, onClick = onClick, modifier = Modifier.fillMaxWidth())
        if (!pickerOpen) {
            LightText(
                text = "Tap to rank by another stat",
                variant = LightTextVariant.Superfine,
                lighten = true,
                align = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 0.3f.gridUnitsAsDp(), bottom = 0.5f.gridUnitsAsDp()),
            )
        }
    }
}

/** Every stat the proxy can rank by (it sends the list with each board); the current one is the
 * only row not dimmed. Picking one closes the list and re-ranks. */
@Composable
private fun StatPickerList(board: LeagueLeaders?, selectedStat: String, onSelectStat: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp())) {
        board?.stats.orEmpty().forEach { option ->
            LightText(
                text = option.label,
                variant = LightTextVariant.Fine,
                lighten = option.key != selectedStat,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable { onSelectStat(option.key) }
                    .padding(vertical = 0.6f.gridUnitsAsDp()),
            )
        }
    }
}

/** Rank, player (with team, or minutes on a team's own board), Total, Per 90. The column the board
 * is ranked by is the brighter one; tap the other column's header to re-rank by it. Stats with no
 * per-90 figure (minutes, pass accuracy) show "–" there, and that header does nothing. */
@Composable
private fun LeaderboardTable(
    board: LeagueLeaders,
    onSelectSort: (String) -> Unit,
    showTeam: Boolean,
    onPlayerClick: (playerId: Int, name: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        board.minMinutesShare?.let { share ->
            LightText(
                text = "Ranked: players with ${(share * 100).toInt()}% or more of their team's minutes",
                variant = LightTextVariant.Superfine,
                lighten = true,
                modifier = Modifier.padding(bottom = 0.4f.gridUnitsAsDp(), end = 1f.gridUnitsAsDp()),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(end = 1f.gridUnitsAsDp(), bottom = 0.2f.gridUnitsAsDp()),
        ) {
            Box(modifier = Modifier.weight(1f))
            LeaderSortHeader("TOTAL", selected = board.sort == "total", enabled = true) { onSelectSort("total") }
            LeaderSortHeader("PER 90", selected = board.sort == "per_90", enabled = board.hasPer90) { onSelectSort("per_90") }
        }
        board.leaders.forEach { entry ->
            LeaderRow(entry, board, showTeam, onClick = { onPlayerClick(entry.playerId, entry.name) })
        }
    }
}

@Composable
private fun LeaderSortHeader(text: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    LightText(
        text = text,
        variant = LightTextVariant.Superfine,
        lighten = !selected,
        underline = selected,
        align = TextAlign.End,
        modifier = Modifier
            .width(LEADER_VALUE_COLUMN_UNITS.gridUnitsAsDp())
            .lightClickable(enabled = enabled && !selected, onClick = onClick),
    )
}

@Composable
private fun LeaderRow(entry: LeaderEntry, board: LeagueLeaders, showTeam: Boolean, onClick: () -> Unit) {
    val total = entry.total?.let { if (board.stat == "pass_accuracy") "${it.toInt()}%" else it.toLong().toString() } ?: "–"
    val per90 = entry.per90?.let { String.format(java.util.Locale.US, "%.2f", it) } ?: "–"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(end = 1f.gridUnitsAsDp(), top = 0.3f.gridUnitsAsDp(), bottom = 0.3f.gridUnitsAsDp()),
    ) {
        LightText(
            text = entry.rank?.toString() ?: "–",
            variant = LightTextVariant.Fine,
            lighten = true,
            modifier = Modifier.width(1.8f.gridUnitsAsDp()),
        )
        Column(modifier = Modifier.weight(1f)) {
            LightText(text = entry.name, variant = LightTextVariant.Fine, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = if (showTeam) entry.teamName?.let { teamShortName(it) } else entry.minutes?.let { "$it min" }
            subtitle?.let {
                LightText(text = it, variant = LightTextVariant.Superfine, lighten = true, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        LightText(
            text = total,
            variant = LightTextVariant.Fine,
            lighten = board.sort != "total",
            align = TextAlign.End,
            modifier = Modifier.width(LEADER_VALUE_COLUMN_UNITS.gridUnitsAsDp()),
        )
        LightText(
            text = per90,
            variant = LightTextVariant.Fine,
            lighten = board.sort != "per_90",
            align = TextAlign.End,
            modifier = Modifier.width(LEADER_VALUE_COLUMN_UNITS.gridUnitsAsDp()),
        )
    }
}

// --- Team: Squad tab ---------------------------------------------------------------------------

private val SQUAD_POSITION_LABELS = mapOf(
    "Goalkeeper" to "GOALKEEPERS",
    "Defender" to "DEFENDERS",
    "Midfielder" to "MIDFIELDERS",
    "Attacker" to "ATTACKERS",
)
private const val SQUAD_PHOTO_SIZE_UNITS = 2f

/** My Team / Team Detail's Squad tab: the registered squad by position (the proxy already sorts
 * it goalkeepers to attackers, then by shirt number), each row a headshot, number, and name.
 * Tapping a player opens their page. */
@Composable
private fun SquadSection(
    tabs: TeamTabsState,
    onPlayerClick: (playerId: Int, name: String, photoBytes: ByteArray?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        val squad = tabs.squad
        when {
            tabs.squadFailed -> NoDataForTab(text = "Couldn't load the squad right now.")
            squad == null -> NoDataForTab(text = "fetching squad...")
            squad.players.isEmpty() -> NoDataForTab(text = "No squad listed for this team.")
            else -> squad.players.groupBy { it.position }.forEach { (position, players) ->
                PlayerSectionLabel(
                    text = SQUAD_POSITION_LABELS[position] ?: "OTHER",
                    modifier = Modifier.padding(top = 0.8f.gridUnitsAsDp()),
                )
                players.forEach { player ->
                    SquadPlayerRow(player, tabs.squadPhotos[player.playerId], onPlayerClick)
                }
            }
        }
    }
}

@Composable
private fun SquadPlayerRow(
    player: SquadPlayer,
    photoBytes: ByteArray?,
    onPlayerClick: (playerId: Int, name: String, photoBytes: ByteArray?) -> Unit,
) {
    val photo = photoBytes?.let { bytes ->
        remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable { onPlayerClick(player.playerId, player.name, photoBytes) }
            .padding(end = 1f.gridUnitsAsDp(), top = 0.2f.gridUnitsAsDp(), bottom = 0.2f.gridUnitsAsDp()),
    ) {
        // Headshot, or an empty circle of the same size so names stay aligned while photos load.
        Box(
            modifier = Modifier
                .size(SQUAD_PHOTO_SIZE_UNITS.gridUnitsAsDp())
                .clip(CircleShape)
                .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.15f)),
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = player.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        LightText(
            text = player.number?.toString() ?: "-",
            variant = LightTextVariant.Fine,
            lighten = true,
            align = TextAlign.End,
            // 3 units less 0.6 padding: a two-digit number at Fine size wrapped at the old 2 units.
            modifier = Modifier.width(3f.gridUnitsAsDp()).padding(end = 0.6f.gridUnitsAsDp()),
        )
        LightText(
            text = player.name,
            variant = LightTextVariant.Fine,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

// --- Match ratings -------------------------------------------------------------------------------

// FotMob's rating colors, on request: 7.0 and up green, 6.0-6.9 orange, below 6 red. Green and red
// are the W/L badges' own.
private val RATING_MID_COLOR = Color(0xFFEF8A17)

/** A player's match rating ("7.2") in a small colored pill — see [RATING_MID_COLOR]. */
@Composable
private fun RatingPill(rating: Double, modifier: Modifier = Modifier) {
    val background = when {
        rating >= 7.0 -> RESULT_WIN_COLOR
        rating >= 6.0 -> RATING_MID_COLOR
        else -> RESULT_LOSS_COLOR
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(0.4f.gridUnitsAsDp()))
            .background(background)
            .padding(horizontal = 0.3f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = String.format(java.util.Locale.US, "%.1f", rating),
            variant = LightTextVariant.Superfine,
            color = Color.White,
        )
    }
}
