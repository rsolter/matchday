package com.thelightphone.soccerfootball

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
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.thelightphone.sdk.InitialScreen
import com.thelightphone.sdk.LightScreen
import com.thelightphone.sdk.SealedLightActivity
import com.thelightphone.sdk.rememberKeyboardOptions
import com.thelightphone.sdk.ui.LightBarButton
import com.thelightphone.sdk.ui.LightBottomBar
import com.thelightphone.sdk.ui.LightFullscreenModal
import com.thelightphone.sdk.ui.LightIcon
import com.thelightphone.sdk.ui.LightIcons
import com.thelightphone.sdk.ui.LightScrollBarPosition
import com.thelightphone.sdk.ui.LightScrollView
import com.thelightphone.sdk.ui.LightText
import com.thelightphone.sdk.ui.LightTextInputEditor
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
        val textFieldState = rememberTextFieldState("")
        val keyboardOptionsFlow = rememberKeyboardOptions()

        LightTheme(colors = themeColors) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LightThemeTokens.colors.background),
            ) {
                when (val mode = state.mode) {
                    is ScoreScreenMode.ApiKeyInput -> {
                        LightTextInputEditor(
                            title = "API-Football Key",
                            editorKey = state.apiKeyInputSession,
                            keyboardOptionsFlow = keyboardOptionsFlow,
                            state = textFieldState,
                            onSubmit = viewModel::submitApiKey,
                            onBack = viewModel::onApiKeyInputBack,
                            submitLabel = "SAVE",
                            showBackButton = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

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
                            maskedApiKey = mode.maskedApiKey,
                            selectedLeagueNames = mode.selectedLeagueNames,
                            myTeamName = mode.myTeamName,
                            onBack = viewModel::closeSettings,
                            onChangeKey = viewModel::openApiKeyInputFromSettings,
                            onClearKey = viewModel::clearApiKey,
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
                LightBarButton.LightIcon(
                    icon = LightIcons.STAR,
                    onClick = onOpenMyTeam,
                    contentDescription = "My Team",
                ),
                LightBarButton.LightIcon(
                    icon = LightIcons.NUMBERED_LIST,
                    onClick = onOpenStandings,
                    contentDescription = "Standings",
                ),
                LightBarButton.LightIcon(
                    icon = LightIcons.CALENDAR,
                    onClick = onOpenFixtures,
                    contentDescription = "Fixtures",
                ),
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
            MatchRow(match, showFinishedStatus = showFinishedStatus, onClick = { onMatchClick(match) })
        }
    }
}

@Composable
private fun MatchRow(match: Fixture, showFinishedStatus: Boolean = true, onClick: () -> Unit) {
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
                LightText(
                    text = match.statusLabel(),
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
    maskedApiKey: String,
    selectedLeagueNames: List<String>,
    myTeamName: String?,
    onBack: () -> Unit,
    onChangeKey: () -> Unit,
    onClearKey: () -> Unit,
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
            SettingRow(label = "API Key", value = maskedApiKey, onClick = onChangeKey)
            LightText(
                text = "Forget API Key",
                variant = LightTextVariant.Copy,
                modifier = Modifier
                    .fillMaxWidth()
                    .lightClickable(onClick = onClearKey)
                    .padding(top = 0.25f.gridUnitsAsDp(), bottom = 0.75f.gridUnitsAsDp()),
            )
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
        }

        AttributionFooter(onClick = onOpenAttribution)
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
private fun AttributionFooter(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .lightClickable(onClick = onClick)
            .padding(vertical = 1f.gridUnitsAsDp(), horizontal = 1f.gridUnitsAsDp()),
        contentAlignment = Alignment.Center,
    ) {
        LightText(
            text = "Scores from API-Football — learn more",
            variant = LightTextVariant.Fine,
            align = TextAlign.Center,
            lighten = true,
            underline = true,
        )
    }
}

@Composable
private fun AttributionContent(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        LightTopBar(
            leftButton = LightBarButton.LightIcon(icon = LightIcons.BACK, onClick = onBack),
            center = LightTopBarCenter.Text("Get an API Key"),
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
                    "data API with a limited free tier. This tool covers the Premier League, " +
                    "Serie A, and the UEFA Champions League and Europa League.\n\n" +
                    "To use it:\n\n" +
                    "1. On any browser, visit api-football.com and create an account.\n\n" +
                    "2. Copy the API key shown on your dashboard.\n\n" +
                    "3. Paste it into this tool.\n\n" +
                    "This build (Phase 1) is scoped to the free tier, which caps requests at " +
                    "100/day and only covers the 2022-2024 seasons — not live, current-season " +
                    "data. Scores, fixtures, and standings shown here are from a fixed point in " +
                    "the 2023/24 season rather than today's real matches; see the module README " +
                    "for the plan to move to live data on a paid tier.",
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

private val STANDINGS_POS_WEIGHT = 0.13f
private val STANDINGS_TEAM_WEIGHT = 0.48f
private val STANDINGS_MP_WEIGHT = 0.13f
private val STANDINGS_GD_WEIGHT = 0.13f
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
                rows.forEach { row ->
                    if (showGroupHeaders && row.group != null && row.group != currentGroup) {
                        currentGroup = row.group
                        LightText(
                            text = row.group.uppercase(),
                            variant = LightTextVariant.Detail,
                            lighten = true,
                            modifier = Modifier.padding(top = 0.75f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp()),
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
            val today = phase1Today()
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
            LightScrollView(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 1f.gridUnitsAsDp()),
            ) {
                summary.standingsRow?.let { row ->
                    MyTeamStandingBlock(leagueName = summary.leagueName, row = row)
                }
                if (summary.unavailable.isNotEmpty()) {
                    UnavailableBlock(summary.unavailable, modifier = Modifier.padding(top = 1f.gridUnitsAsDp()))
                }
                if (summary.upcomingFixtures.isNotEmpty()) {
                    MatchGroupCard(
                        title = "UPCOMING",
                        matches = summary.upcomingFixtures,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp()),
                        onMatchClick = onMatchClick,
                    )
                }
                if (summary.recentFixtures.isNotEmpty()) {
                    MatchGroupCard(
                        title = "RECENT RESULTS",
                        matches = summary.recentFixtures,
                        modifier = Modifier.padding(top = 1f.gridUnitsAsDp(), bottom = 1f.gridUnitsAsDp()),
                        showFinishedStatus = false,
                        onMatchClick = onMatchClick,
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
    TIMELINE("Timeline"),
    HOME_LINEUP("Home Lineup"),
    AWAY_LINEUP("Away Lineup"),
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
    scoreLabel: String,
    statusLabel: String,
    isLive: Boolean,
    goalEvents: List<MatchEvent> = emptyList(),
) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 0.5f.gridUnitsAsDp(), bottom = 0.25f.gridUnitsAsDp())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            LightText(
                text = homeTeamName,
                variant = LightTextVariant.Copy,
                align = TextAlign.End,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            LightText(
                text = scoreLabel,
                variant = LightTextVariant.Title,
                align = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 0.75f.gridUnitsAsDp()),
            )
            LightText(
                text = awayTeamName,
                variant = LightTextVariant.Copy,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
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

@Composable
private fun EventTimelineRow(event: MatchEvent) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 0.5f.gridUnitsAsDp())) {
        LightText(
            text = event.minuteLabel,
            variant = LightTextVariant.Detail,
            lighten = true,
            modifier = Modifier.width(2.4f.gridUnitsAsDp()),
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
            LightText(text = teamName, variant = LightTextVariant.Detail, lighten = true, modifier = Modifier.weight(1f))
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
