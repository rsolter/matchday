package com.thelightphone.soccerfootball

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
import androidx.compose.ui.graphics.Color
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
import kotlin.time.Instant

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
                            lastUpdated = mode.lastUpdated,
                            isRefreshing = mode.isRefreshing,
                            onOpenSettings = viewModel::openSettings,
                            onOpenMyTeam = viewModel::openMyTeam,
                            onOpenStandings = viewModel::openStandingsPicker,
                            onOpenFixtures = viewModel::openFixturesPicker,
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

                    is ScoreScreenMode.StandingsPicker -> {
                        CompetitionPickerContent(
                            title = "Standings",
                            leagues = mode.leagues,
                            onSelect = viewModel::openStandingsTable,
                            onBack = viewModel::backFromStandingsPicker,
                        )
                    }

                    is ScoreScreenMode.Standings -> {
                        StandingsTableContent(
                            leagueName = mode.leagueName,
                            rows = mode.rows,
                            isLoading = mode.isLoading,
                            onBack = viewModel::backFromStandingsTable,
                        )
                    }

                    is ScoreScreenMode.FixturesPicker -> {
                        CompetitionPickerContent(
                            title = "Fixtures",
                            leagues = mode.leagues,
                            onSelect = viewModel::openFixtures,
                            onBack = viewModel::backFromFixturesPicker,
                        )
                    }

                    is ScoreScreenMode.Fixtures -> {
                        FixturesContent(
                            leagueName = mode.leagueName,
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
                variant = LightTextVariant.Copy,
                align = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 1f.gridUnitsAsDp()),
            )
        }
    }
}

// --- Scores ------------------------------------------------------------------

private val SCORE_COLUMN_WIDTH = 6f

@Composable
private fun ScoresContent(
    groups: List<CompetitionGroup>,
    lastUpdated: Instant?,
    isRefreshing: Boolean,
    onOpenSettings: () -> Unit,
    onOpenMyTeam: () -> Unit,
    onOpenStandings: () -> Unit,
    onOpenFixtures: () -> Unit,
    onMatchClick: (Fixture) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            center = LightTopBarCenter.Text(
                text = when {
                    isRefreshing -> "Updating…"
                    lastUpdated != null -> "Updated ${formatUpdatedAt(lastUpdated)}"
                    else -> "Today"
                },
            ),
            modifier = Modifier.padding(bottom = 0.25f.gridUnitsAsDp()),
        )

        if (groups.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
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
                        matches = group.matches,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 0.75f.gridUnitsAsDp()),
                        onMatchClick = onMatchClick,
                    )
                }
            }
        }

        // Bottom bar order (left to right): Settings, My Team, Standings, Fixtures — deliberately
        // no Refresh icon here, see SoccerViewModel's class doc comment for why (free-tier quota
        // + frozen historical data in Phase 1). Manual refresh lives in Settings instead.
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
                    onClick = onOpenStandings,
                    contentDescription = "Standings",
                ) { SoccerBarIcon(R.drawable.ic_table_white, "Standings") },
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
 * a finished match still prints its "FT"/"Postponed"/etc. label under the score. */
@Composable
private fun MatchGroupCard(
    title: String,
    matches: List<Fixture>,
    modifier: Modifier = Modifier,
    showFinishedStatus: Boolean = true,
    showDate: Boolean = false,
    onMatchClick: (Fixture) -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(1.2f.gridUnitsAsDp()))
            .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.08f))
            .padding(horizontal = 1f.gridUnitsAsDp(), vertical = 0.75f.gridUnitsAsDp()),
    ) {
        LightText(
            text = title,
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )
        matches.forEachIndexed { index, match ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.15f)),
                )
            }
            MatchRow(match, showFinishedStatus = showFinishedStatus, showDate = showDate, onClick = { onMatchClick(match) })
        }
    }
}

@Composable
private fun MatchRow(
    match: Fixture,
    showFinishedStatus: Boolean = true,
    // My Team's "UPCOMING" card is the one caller that isn't already grouped under a per-day
    // header (unlike Scores/Fixtures), so a bare kickoff time there could be mistaken for today's
    // game — see formatKickoffDateAndTime's doc comment in SoccerFormatting.kt.
    showDate: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 0.65f.gridUnitsAsDp()),
    ) {
        LightText(
            text = "${match.homeTeamName} vs ${match.awayTeamName}",
            variant = LightTextVariant.Copy,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(SCORE_COLUMN_WIDTH.gridUnitsAsDp()),
        ) {
            if (match.hasScore) {
                LightText(text = match.scoreLabel(), variant = LightTextVariant.Copy, align = TextAlign.End)
            }
            if (match.status.isLive) {
                Box(
                    modifier = Modifier
                        .padding(top = 0.15f.gridUnitsAsDp())
                        .clip(RoundedCornerShape(50.dp))
                        .background(LightThemeTokens.colors.content.copy(alpha = 0.12f))
                        .padding(horizontal = 0.4f.gridUnitsAsDp(), vertical = 0.05f.gridUnitsAsDp()),
                ) {
                    LightText(text = match.statusLabel(), variant = LightTextVariant.Detail, align = TextAlign.End)
                }
            } else if (!match.hasScore || showFinishedStatus) {
                val label = if (showDate && match.status == MatchStatus.SCHEDULED) {
                    formatKickoffDateAndTime(match.utcDate)
                } else {
                    match.statusLabel()
                }
                LightText(
                    text = label,
                    variant = LightTextVariant.Detail,
                    align = TextAlign.End,
                    lighten = true,
                    modifier = if (match.hasScore) Modifier.padding(top = 0.15f.gridUnitsAsDp()) else Modifier,
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
                LightText(
                    text = "Forget My Team",
                    variant = LightTextVariant.Copy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .lightClickable(onClick = onClearMyTeam)
                        .padding(top = 0.25f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
                )
            }
            // This build's only manual-refresh surface — see SoccerViewModel's class doc comment
            // for why there's no bottom-bar refresh icon.
            LightText(
                text = "Refresh now",
                variant = LightTextVariant.Copy,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(onClick = onManualRefresh)
                    .padding(top = 0.25f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
            )
            // A plain row like the others above, not the separate centered footnote this used to
            // be (AttributionFooter, now removed) — same destination (ScoreScreenMode.Attribution,
            // titled "About" there), just no longer visually set apart from the rest of Settings.
            LightText(
                text = "About",
                variant = LightTextVariant.Copy,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(onClick = onOpenAttribution)
                    .padding(top = 0.25f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
            )
        }
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
        LightText(text = label, variant = LightTextVariant.Detail, lighten = true)
        LightText(text = value, variant = LightTextVariant.Heading)
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
        LightText(text = "Leagues followed", variant = LightTextVariant.Detail, lighten = true)
        leagueNames.forEach { name ->
            LightText(text = name, variant = LightTextVariant.Copy, modifier = Modifier.padding(top = 0.2f.gridUnitsAsDp()))
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
                    variant = LightTextVariant.Copy,
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
    leagueName: String,
    rows: List<StandingsRow>,
    isLoading: Boolean,
    onBack: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(leagueName),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(text = "fetching standings...", variant = LightTextVariant.Copy)
            }
        } else if (rows.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(
                    text = "Standings aren't available for this league right now.",
                    variant = LightTextVariant.Copy,
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
        LightText(text = row.position.toString(), variant = LightTextVariant.Copy, modifier = Modifier.weight(STANDINGS_POS_WEIGHT))
        LightText(
            text = row.teamName,
            variant = LightTextVariant.Copy,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(STANDINGS_TEAM_WEIGHT),
        )
        LightText(text = row.played.toString(), variant = LightTextVariant.Copy, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_MP_WEIGHT))
        LightText(text = row.goalsFor.toString(), variant = LightTextVariant.Copy, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_GF_WEIGHT))
        LightText(text = row.goalsAgainst.toString(), variant = LightTextVariant.Copy, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_GA_WEIGHT))
        LightText(text = row.goalDifferenceLabel(), variant = LightTextVariant.Copy, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_GD_WEIGHT))
        LightText(text = row.points.toString(), variant = LightTextVariant.Copy, align = TextAlign.End, modifier = Modifier.weight(STANDINGS_PTS_WEIGHT))
    }
}

private fun StandingsRow.goalDifferenceLabel(): String =
    if (goalDifference > 0) "+$goalDifference" else goalDifference.toString()

// --- Fixtures ------------------------------------------------------------------

@Composable
private fun FixturesContent(
    leagueName: String,
    groups: List<FixtureDateGroup>,
    isLoading: Boolean,
    onBack: () -> Unit,
    onMatchClick: (Fixture) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text(leagueName),
            modifier = Modifier.padding(bottom = 0.5f.gridUnitsAsDp()),
        )

        if (isLoading) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(text = "fetching fixtures...", variant = LightTextVariant.Copy)
            }
        } else if (groups.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(
                    text = "No fixtures found for this league right now.",
                    variant = LightTextVariant.Copy,
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
                groups.forEachIndexed { index, group ->
                    MatchGroupCard(
                        title = group.dateLabel,
                        matches = group.matches,
                        modifier = Modifier
                            .padding(top = if (index == 0) 0.dp else 0.75f.gridUnitsAsDp())
                            .let { if (index == targetIndex) it.bringIntoViewRequester(bringIntoViewRequester) else it },
                        showFinishedStatus = false,
                        onMatchClick = onMatchClick,
                    )
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
                LightText(text = "fetching teams...", variant = LightTextVariant.Copy)
            }
        } else {
            LightScrollView(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                teams.sortedBy { it.teamName }.forEach { team ->
                    LightText(
                        text = team.teamName,
                        variant = LightTextVariant.Copy,
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
                LightText(text = "fetching your team...", variant = LightTextVariant.Copy)
            }
        } else if (summary == null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LightText(
                    text = "Couldn't load My Team right now.",
                    variant = LightTextVariant.Copy,
                    align = TextAlign.Center,
                    lighten = true,
                    modifier = Modifier.padding(horizontal = 2f.gridUnitsAsDp()),
                )
            }
        } else {
            // No AsyncImage here: the Light SDK's dependency allow-list rejects every third-party
            // image loader (confirmed against a real build failure for Coil), so this decodes the
            // bytes MyTeamSummary already fetched (see ApiFootballApi.fetchMyTeamSummary) by hand.
            // remember(bytes) keys on the byte array's identity, not its content — cheap enough
            // here since a new MyTeamSummary (and therefore a new array) only shows up once per
            // fetch, never once per frame.
            val teamLogoBitmap = summary.teamLogoBytes?.let { bytes ->
                remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }
            }
            if (teamLogoBitmap != null) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        bitmap = teamLogoBitmap,
                        contentDescription = "${summary.teamName} crest",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .padding(bottom = 0.5f.gridUnitsAsDp())
                            .size(6f.gridUnitsAsDp()),
                    )
                }
            }
            LightScrollView(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                summary.standingsRow?.let { row ->
                    MyTeamStandingBlock(leagueName = summary.leagueName, row = row)
                }
                // Order below is deliberate: recent results, then next results, then who's out —
                // injuries/suspensions dropped to the bottom of the scroll instead of leading it.
                if (summary.recentFixtures.isNotEmpty()) {
                    MatchGroupCard(
                        title = "RECENT RESULTS",
                        matches = summary.recentFixtures,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                        showFinishedStatus = false,
                        onMatchClick = onMatchClick,
                    )
                }
                if (summary.upcomingFixtures.isNotEmpty()) {
                    MatchGroupCard(
                        title = "UPCOMING",
                        matches = summary.upcomingFixtures,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                        showDate = true,
                        onMatchClick = onMatchClick,
                    )
                }
                if (summary.unavailable.isNotEmpty()) {
                    UnavailableBlock(
                        summary.unavailable,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
                    )
                }
                if (summary.standingsRow == null && summary.unavailable.isEmpty() &&
                    summary.upcomingFixtures.isEmpty() && summary.recentFixtures.isEmpty()
                ) {
                    LightText(
                        text = "No data available for this team right now.",
                        variant = LightTextVariant.Copy,
                        align = TextAlign.Center,
                        lighten = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 1.5f.gridUnitsAsDp()),
                    )
                }
            }
        }
    }
}

@Composable
private fun MyTeamStandingBlock(leagueName: String, row: StandingsRow, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp())) {
        LightText(text = leagueName.uppercase(), variant = LightTextVariant.Detail, lighten = true)
        LightText(
            text = "#${row.position} · ${row.points} pts",
            variant = LightTextVariant.Heading,
            modifier = Modifier.padding(top = 0.2f.gridUnitsAsDp()),
        )
        LightText(
            text = "${row.win}W ${row.draw}D ${row.lose}L · ${row.goalDifferenceLabel()} GD" +
                (row.form?.let { " · form $it" } ?: ""),
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.padding(top = 0.15f.gridUnitsAsDp()),
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
                    variant = LightTextVariant.Copy,
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
            LightText(
                text = player.reason,
                variant = LightTextVariant.Fine,
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
                        variant = LightTextVariant.Copy,
                        lighten = true,
                        align = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 1.5f.gridUnitsAsDp()),
                    )
                }

                detail == null || detail.isEmpty() -> {
                    LightText(
                        text = "No additional details available for this match yet.",
                        variant = LightTextVariant.Copy,
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
                            mirrored = false,
                            modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
                        )
                        DetailTab.AWAY_LINEUP -> LineupSection(
                            teamName = mode.awayTeamName,
                            lineup = detail.lineups.away,
                            mirrored = true,
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
            // Heading (24sp), not Title (34sp) — the crest icons beside each team name below need
            // the horizontal room this used to take up; shrinking the score is the deliberate
            // trade-off (still the visually dominant element on the row, just not oversized).
            LightText(
                text = scoreLabel,
                variant = LightTextVariant.Heading,
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
        // Detail (20sp), not Copy (30sp): the API doesn't give us a short/abbreviated team name,
        // so the only lever we have to stop long names ("Manchester City", "FC Copenhagen") from
        // wrapping mid-word is a smaller font. maxLines/Ellipsis stays as a safety net for names
        // this still doesn't fit.
        LightText(
            text = name,
            variant = LightTextVariant.Detail,
            align = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
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

/** "Danilo 24'", or "Danilo (Penalty) 24'" when [MatchEvent.scorerQualifier] is set to something
 * other than a plain goal (see the doc comment on the "Goal" branch of
 * [ApiFootballEventDto.toMatchEvent] in SoccerModels.kt for what qualifier values are and aren't
 * verified against a real response). */
private fun MatchEvent.goalScorerLabel(): String {
    val name = scorer ?: "Goal"
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
                LightText(text = row.homeValue, variant = LightTextVariant.Copy, align = TextAlign.Center, modifier = Modifier.weight(0.25f))
                LightText(
                    text = prettifyStatLabel(row.label),
                    variant = LightTextVariant.Detail,
                    lighten = true,
                    align = TextAlign.Center,
                    modifier = Modifier.weight(0.5f),
                )
                LightText(text = row.awayValue, variant = LightTextVariant.Copy, align = TextAlign.Center, modifier = Modifier.weight(0.25f))
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
            MatchEventType.GOAL -> LightText(text = "⚽", variant = LightTextVariant.Copy, align = TextAlign.Center)
            MatchEventType.SUBSTITUTION -> LightText(text = "⇄", variant = LightTextVariant.Copy, align = TextAlign.Center)
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
            LightText(text = event.headline, variant = LightTextVariant.Copy, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
        variant = LightTextVariant.Copy,
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
 * Laid out left-to-right (goalkeeper's column on the left, forwards' column on the right) for the
 * home team, and mirrored right-to-left for the away team ([mirrored] = true) — as if the two
 * teams are attacking each other from opposite sides, same convention a real match-graphic pitch
 * view uses. Each pitch line is a *column* with its players stacked vertically rather than a row
 * with players side-by-side: on a narrow phone screen a line of 4-5 players sharing one row left
 * almost no width per name (hence names truncating to "Walukiew…", "McKen…"); stacked in a column,
 * each name gets the column's full width instead of splitting it with row-mates.
 */
@Composable
private fun LineupSection(teamName: String, lineup: TeamLineup?, mirrored: Boolean, modifier: Modifier = Modifier) {
    if (lineup == null || lineup.startXI.isEmpty()) {
        NoDataForTab(text = "No lineup available for $teamName yet.")
        return
    }

    // groupedByPitchRow() already returns goalkeeper-first; that's the left-to-right column order
    // we want for the home team as-is, and reversed (forwards-first) for the mirrored away team.
    val columns = remember(lineup, mirrored) {
        val gkFirst = lineup.startXI.groupedByPitchRow()
        if (mirrored) gkFirst.asReversed() else gkFirst
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 0.6f.gridUnitsAsDp()),
        ) {
            // This row already shares its width with the formation label, so even Detail-size
            // team names ("Manchester City") can still be too wide to fit on one line — maxLines=1
            // + Ellipsis trades a truncated name ("Mancheste…") for avoiding an ugly mid-word wrap.
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(1.2f.gridUnitsAsDp()))
                .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.08f))
                .padding(vertical = 1f.gridUnitsAsDp(), horizontal = 0.3f.gridUnitsAsDp()),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.3f.gridUnitsAsDp()),
            // Columns hold different player counts (1 for GK/lone-forward lines, up to 4-5 for a
            // back line or midfield) and each stacks from its own top by default, which is what
            // made the GK/forward columns look pinned to the top instead of spread across the
            // pitch's height. Centering the whole Row vertically fixes that in one line.
            verticalAlignment = Alignment.CenterVertically,
        ) {
            columns.forEach { column ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(0.7f.gridUnitsAsDp()),
                ) {
                    column.forEach { player -> PitchPlayerChip(player, modifier = Modifier.fillMaxWidth()) }
                }
            }
        }

        lineup.coachName?.let {
            LightText(
                text = "Coach: $it",
                variant = LightTextVariant.Fine,
                lighten = true,
                modifier = Modifier.padding(top = 0.5f.gridUnitsAsDp()),
            )
        }

        if (lineup.substitutes.isNotEmpty()) {
            SubstitutesBlock(lineup.substitutes, modifier = Modifier.padding(top = 1f.gridUnitsAsDp()))
        }
    }
}

@Composable
private fun PitchPlayerChip(player: LineupPlayer, modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(vertical = 0.1f.gridUnitsAsDp()),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(2.3f.gridUnitsAsDp())
                .clip(CircleShape)
                .background(LightThemeTokens.colors.contentSecondary.copy(alpha = 0.22f)),
        ) {
            LightText(text = player.number?.toString() ?: "-", variant = LightTextVariant.Detail)
        }
        LightText(
            text = player.name.substringAfterLast(' '),
            variant = LightTextVariant.Fine,
            lighten = true,
            align = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 0.15f.gridUnitsAsDp()).fillMaxWidth(),
        )
    }
}

@Composable
private fun SubstitutesBlock(substitutes: List<LineupPlayer>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        LightText(text = "SUBSTITUTES", variant = LightTextVariant.Detail, lighten = true, modifier = Modifier.padding(bottom = 0.4f.gridUnitsAsDp()))
        substitutes.forEach { player ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 0.1f.gridUnitsAsDp())) {
                LightText(
                    text = player.number?.toString() ?: "-",
                    variant = LightTextVariant.Copy,
                    lighten = true,
                    modifier = Modifier.width(2f.gridUnitsAsDp()),
                )
                LightText(
                    text = player.name,
                    variant = LightTextVariant.Copy,
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
