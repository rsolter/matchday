package com.thelightphone.soccerfootball

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed class ScoreScreenMode {
    data class Loading(val message: String) : ScoreScreenMode()
    data class Scores(
        val groups: List<CompetitionGroup>,
        val lastUpdated: Instant?,
        val isRefreshing: Boolean,
    ) : ScoreScreenMode()

    data class Settings(
        val selectedLeagueNames: List<String>,
        /** Null when no team has been picked yet — Settings shows "Not set" and My Team's row
         * opens the setup flow instead of the summary screen. */
        val myTeamName: String?,
    ) : ScoreScreenMode()
    data object Attribution : ScoreScreenMode()

    data class LeagueSelection(val rows: List<LeagueSelectionRow>) : ScoreScreenMode()

    /** Only the leagues the user currently follows (see [ScoreScreenMode.LeagueSelection]). */
    data class StandingsPicker(val leagues: List<Competition>) : ScoreScreenMode()
    data class Standings(
        val leagueId: Int,
        val leagueName: String,
        val rows: List<StandingsRow>,
        val isLoading: Boolean,
        val lastUpdated: Instant?,
    ) : ScoreScreenMode()

    /** Only the leagues the user currently follows (see [ScoreScreenMode.LeagueSelection]). */
    data class FixturesPicker(val leagues: List<Competition>) : ScoreScreenMode()
    data class Fixtures(
        val leagueId: Int,
        val leagueName: String,
        val groups: List<FixtureDateGroup>,
        val isLoading: Boolean,
        val lastUpdated: Instant?,
    ) : ScoreScreenMode()

    /** My Team setup, step 1: pick which followed league the team plays in — there's no team
     * search endpoint verified for this build, so a team is picked from an already-loaded
     * league's standings table instead (see [ScoreScreenMode.MyTeamTeamPicker]). */
    data class MyTeamLeaguePicker(val leagues: List<Competition>) : ScoreScreenMode()

    /** My Team setup, step 2: pick the team itself from [leagueId]'s standings table. */
    data class MyTeamTeamPicker(
        val leagueId: Int,
        val leagueName: String,
        val teams: List<StandingsRow>,
        val isLoading: Boolean,
    ) : ScoreScreenMode()

    data class MyTeam(
        val summary: MyTeamSummary?,
        val isLoading: Boolean,
    ) : ScoreScreenMode()

    /** Reachable by tapping a match row from either Scores or Fixtures. [detail] is null while
     * [isLoading] is true, and stays null on a failed fetch — the header (teams/score/status)
     * still has everything it needs from the tapped [Fixture] itself. Named `MatchDetailScreen`
     * rather than `MatchDetail` to avoid colliding with the domain model of the same name. */
    data class MatchDetailScreen(
        val fixtureId: Int,
        val homeTeamId: Int,
        val awayTeamId: Int,
        val homeTeamName: String,
        val awayTeamName: String,
        val scoreLabel: String,
        val statusLabel: String,
        val isLive: Boolean,
        val detail: MatchDetail?,
        val isLoading: Boolean,
    ) : ScoreScreenMode()
}

data class LeagueSelectionRow(val id: Int, val name: String, val selected: Boolean)

data class ScoreUiState(
    val mode: ScoreScreenMode = ScoreScreenMode.Loading(LOADING_MESSAGE),
    val errorModal: String? = null,
)

internal const val LOADING_MESSAGE = "Loading…"
private const val FETCHING_MESSAGE = "fetching today's scores..."
private val MIN_LOADING_DISPLAY = 1.seconds

private const val NETWORK_ERROR_MESSAGE =
    "Soccer Pro requires a network connection. Connect to wi-fi or insert a data SIM to see scores."
private const val MIN_LEAGUES_MESSAGE = "Keep at least one league selected."

/** How far back/forward the Fixtures mode's window reaches from [todayLocalDate]. Wide enough to
 * cover a handful of matchdays either side without pulling a whole season's worth of matches —
 * matches the ESPN variant's window (see its README). */
private const val FIXTURES_PAST_DAYS = 10
private const val FIXTURES_FUTURE_DAYS = 21

/**
 * Phase 3: this app talks to its own caching proxy (`ApiFootballApi`'s `API_BASE`), not
 * API-Football directly, and no longer holds or prompts for an API key — the proxy holds the real
 * key server-side. Refresh here is still on-demand only (once on first load, and via Settings'
 * "Refresh now" row) rather than a poll loop, even though the proxy is exactly the kind of shared,
 * budget-absorbing intermediary that would make background polling cheap across installs — that's
 * a real follow-up worth doing, just not part of this pass, which is scoped to the proxy
 * migration itself.
 */
class SoccerViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {
    private val api = ApiFootballApi()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(ScoreUiState())
    val uiState: StateFlow<ScoreUiState> = _uiState.asStateFlow()

    private var selectedIds: Set<Int> = TRACKED_COMPETITIONS.map { it.id }.toSet()
    private var lastScores: ScoreScreenMode.Scores? = null
    private var modeBeforeAttribution: ScoreScreenMode? = null
    private var modeBeforeMatchDetail: ScoreScreenMode? = null
    private var modeBeforeMyTeamSetup: ScoreScreenMode? = null

    private var myTeamId: Int? = null
    private var myTeamName: String? = null
    private var myTeamLeagueId: Int? = null

    /** Per-league standings, filled in as the user visits Standings/My Team — reused rather than
     * re-fetched wherever possible (My Team's league-position lookup, the My Team team picker) so
     * a session doesn't quietly re-spend the proxy's shared daily budget on data it already has. */
    private val standingsCache = mutableMapOf<Int, List<StandingsRow>>()

    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        viewModelScope.launch(Dispatchers.Main) { handleFailure(throwable) }
    }

    init {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            loadInitialState()
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        // Only fetches if nothing's loaded yet (first launch) — see the class doc comment for why
        // there's no poll-on-every-return here.
        if (lastScores == null) {
            viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
                refresh(showSpinner = _uiState.value.mode is ScoreScreenMode.Scores)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        api.close()
    }

    private suspend fun loadInitialState() {
        val prefs = dataStore.data.first()
        selectedIds = prefs[SoccerPreferences.SELECTED_COMPETITIONS]?.mapNotNull { it.toIntOrNull() }?.toSet()
            ?.takeIf { it.isNotEmpty() } ?: TRACKED_COMPETITIONS.map { it.id }.toSet()
        myTeamId = prefs[SoccerPreferences.MY_TEAM_ID]
        myTeamName = prefs[SoccerPreferences.MY_TEAM_NAME]
        myTeamLeagueId = prefs[SoccerPreferences.MY_TEAM_LEAGUE_ID]

        val cached = loadCachedMatches(prefs)
        if (cached != null) {
            val mode = ScoreScreenMode.Scores(cached.groupedForDisplay(), lastUpdated = null, isRefreshing = true)
            lastScores = mode
            updateState { it.copy(mode = mode) }
        } else {
            updateState { it.copy(mode = ScoreScreenMode.Loading(FETCHING_MESSAGE)) }
        }
        refresh(showSpinner = cached != null)
    }

    private fun loadCachedMatches(prefs: Preferences): List<Fixture>? {
        val cachedDate = prefs[SoccerPreferences.CACHED_MATCHES_DATE]
        val cachedJson = prefs[SoccerPreferences.CACHED_MATCHES_JSON]
        if (cachedDate != todayLocalDate().toString() || cachedJson == null) return null
        val matches = runCatching { json.decodeFromString<List<Fixture>>(cachedJson) }.getOrNull() ?: return null
        return matches.filter { it.leagueId in selectedIds }
    }

    /** Fetches [todayLocalDate]'s matches and folds the result into [ScoreUiState] — see the class
     * doc comment for why this isn't on a poll loop. Behaves like the ESPN/football-data.org
     * variants otherwise: only touches what's on screen if the user is looking at Scores/Loading,
     * updates [lastScores] silently otherwise. */
    private suspend fun refresh(showSpinner: Boolean) {
        val isFirstLoad = _uiState.value.mode is ScoreScreenMode.Loading
        if (showSpinner) {
            updateState { state ->
                val mode = state.mode as? ScoreScreenMode.Scores ?: return@updateState state
                state.copy(mode = mode.copy(isRefreshing = true))
            }
        }

        val loadingStartedAt = Clock.System.now()
        val result = api.fetchTodaysMatches(selectedIds.toList())
        if (isFirstLoad) awaitMinimumLoading(loadingStartedAt)

        result.fold(
            onSuccess = { matches ->
                cacheMatches(matches)
                val mode = ScoreScreenMode.Scores(
                    groups = matches.groupedForDisplay(),
                    lastUpdated = Clock.System.now(),
                    isRefreshing = false,
                )
                lastScores = mode
                updateState { state ->
                    if (state.mode is ScoreScreenMode.Scores || state.mode is ScoreScreenMode.Loading) {
                        state.copy(mode = mode, errorModal = null)
                    } else {
                        state.copy(errorModal = null)
                    }
                }
            },
            onFailure = { error -> handleFailure(error) },
        )
    }

    private suspend fun cacheMatches(matches: List<Fixture>) {
        runCatching {
            dataStore.edit { prefs ->
                prefs[SoccerPreferences.CACHED_MATCHES_JSON] = json.encodeToString(matches)
                prefs[SoccerPreferences.CACHED_MATCHES_DATE] = todayLocalDate().toString()
            }
        }
    }

    private suspend fun awaitMinimumLoading(startedAt: Instant) {
        val remaining = MIN_LOADING_DISPLAY - (Clock.System.now() - startedAt)
        if (remaining.isPositive()) delay(remaining)
    }

    private fun handleFailure(error: Throwable) {
        updateState { state ->
            val nextMode = when (state.mode) {
                is ScoreScreenMode.Scores, is ScoreScreenMode.Loading ->
                    lastScores?.copy(isRefreshing = false) ?: ScoreScreenMode.Loading(FETCHING_MESSAGE)
                else -> state.mode
            }
            state.copy(mode = nextMode, errorModal = apiErrorMessage(error))
        }
    }

    private fun apiErrorMessage(error: Throwable): String = when {
        error is ApiFootballApiException && error.kind == ApiFootballApiException.Kind.RATE_LIMITED ->
            "Too many requests — try again in a minute."
        error is ApiFootballApiException && error.kind == ApiFootballApiException.Kind.PLAN_RESTRICTED ->
            error.message ?: NETWORK_ERROR_MESSAGE
        else -> NETWORK_ERROR_MESSAGE
    }

    /** Settings' "Refresh now" row — this build's entire manual-refresh surface, since there's no
     * bottom-bar refresh button (see the class doc comment). */
    fun manualRefresh() {
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            refresh(showSpinner = true)
        }
    }

    // --- Settings / navigation --------------------------------------------------

    fun openSettings() {
        updateState { it.copy(mode = settingsMode(), errorModal = null) }
    }

    fun closeSettings() {
        val existing = lastScores
        updateState { it.copy(mode = existing ?: ScoreScreenMode.Loading(FETCHING_MESSAGE), errorModal = null) }
    }

    fun openAttribution() {
        modeBeforeAttribution = _uiState.value.mode
        updateState { it.copy(mode = ScoreScreenMode.Attribution, errorModal = null) }
    }

    fun closeAttribution() {
        val previous = modeBeforeAttribution ?: settingsMode()
        modeBeforeAttribution = null
        updateState { it.copy(mode = previous, errorModal = null) }
    }

    fun dismissError() {
        updateState { it.copy(errorModal = null) }
    }

    private fun settingsMode(): ScoreScreenMode.Settings =
        ScoreScreenMode.Settings(followedLeagues().map { it.name }, myTeamName)

    private fun followedLeagues(): List<Competition> = TRACKED_COMPETITIONS.filter { it.id in selectedIds }

    // --- League selection --------------------------------------------------------

    fun openLeagueSelection() {
        updateState { it.copy(mode = ScoreScreenMode.LeagueSelection(buildLeagueRows()), errorModal = null) }
    }

    fun closeLeagueSelection() {
        updateState { it.copy(mode = settingsMode(), errorModal = null) }
    }

    fun toggleLeague(id: Int) {
        val newSelection = if (id in selectedIds) selectedIds - id else selectedIds + id
        if (newSelection.isEmpty()) {
            updateState { it.copy(errorModal = MIN_LEAGUES_MESSAGE) }
            return
        }
        selectedIds = newSelection
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                dataStore.edit { prefs ->
                    prefs[SoccerPreferences.SELECTED_COMPETITIONS] = newSelection.map { it.toString() }.toSet()
                }
            }
        }
        updateState { state ->
            val mode = state.mode as? ScoreScreenMode.LeagueSelection ?: return@updateState state
            state.copy(mode = mode.copy(rows = buildLeagueRows()))
        }
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) { refresh(showSpinner = false) }
    }

    private fun buildLeagueRows(): List<LeagueSelectionRow> =
        TRACKED_COMPETITIONS.map { LeagueSelectionRow(it.id, it.name, it.id in selectedIds) }

    // --- Standings -----------------------------------------------------------------

    fun openStandingsPicker() {
        updateState { it.copy(mode = ScoreScreenMode.StandingsPicker(followedLeagues()), errorModal = null) }
    }

    fun backFromStandingsPicker() {
        updateState { it.copy(mode = lastScores ?: ScoreScreenMode.Loading(FETCHING_MESSAGE), errorModal = null) }
    }

    fun openStandingsTable(leagueId: Int, leagueName: String) {
        updateState {
            it.copy(
                mode = ScoreScreenMode.Standings(leagueId, leagueName, emptyList(), isLoading = true, lastUpdated = null),
                errorModal = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val result = api.fetchStandings(leagueId)
            result.fold(
                onSuccess = { rows ->
                    standingsCache[leagueId] = rows
                    updateState { state ->
                        if (state.mode is ScoreScreenMode.Standings && state.mode.leagueId == leagueId) {
                            state.copy(
                                mode = ScoreScreenMode.Standings(leagueId, leagueName, rows, false, Clock.System.now()),
                                errorModal = null,
                            )
                        } else {
                            state
                        }
                    }
                },
                onFailure = { error ->
                    updateState { state ->
                        val fallback = if (state.mode is ScoreScreenMode.Standings && state.mode.leagueId == leagueId) {
                            ScoreScreenMode.Standings(leagueId, leagueName, emptyList(), false, null)
                        } else {
                            state.mode
                        }
                        state.copy(mode = fallback, errorModal = apiErrorMessage(error))
                    }
                },
            )
        }
    }

    fun backFromStandingsTable() {
        updateState { it.copy(mode = ScoreScreenMode.StandingsPicker(followedLeagues()), errorModal = null) }
    }

    // --- Fixtures ------------------------------------------------------------------

    fun openFixturesPicker() {
        updateState { it.copy(mode = ScoreScreenMode.FixturesPicker(followedLeagues()), errorModal = null) }
    }

    fun backFromFixturesPicker() {
        updateState { it.copy(mode = lastScores ?: ScoreScreenMode.Loading(FETCHING_MESSAGE), errorModal = null) }
    }

    fun openFixtures(leagueId: Int, leagueName: String) {
        updateState {
            it.copy(
                mode = ScoreScreenMode.Fixtures(leagueId, leagueName, emptyList(), isLoading = true, lastUpdated = null),
                errorModal = null,
            )
        }
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val today = todayLocalDate()
            val dateFrom = today.minus(FIXTURES_PAST_DAYS, DateTimeUnit.DAY).toString()
            val dateTo = today.plus(FIXTURES_FUTURE_DAYS, DateTimeUnit.DAY).toString()
            val result = api.fetchFixturesForLeague(leagueId, dateFrom, dateTo)
            result.fold(
                onSuccess = { matches ->
                    updateState { state ->
                        if (state.mode is ScoreScreenMode.Fixtures && state.mode.leagueId == leagueId) {
                            state.copy(
                                mode = ScoreScreenMode.Fixtures(
                                    leagueId = leagueId,
                                    leagueName = leagueName,
                                    groups = matches.groupedByDate(),
                                    isLoading = false,
                                    lastUpdated = Clock.System.now(),
                                ),
                                errorModal = null,
                            )
                        } else {
                            state
                        }
                    }
                },
                onFailure = { error ->
                    updateState { state ->
                        val fallback = if (state.mode is ScoreScreenMode.Fixtures && state.mode.leagueId == leagueId) {
                            ScoreScreenMode.Fixtures(leagueId, leagueName, emptyList(), false, null)
                        } else {
                            state.mode
                        }
                        state.copy(mode = fallback, errorModal = apiErrorMessage(error))
                    }
                },
            )
        }
    }

    fun backFromFixturesTable() {
        updateState { it.copy(mode = ScoreScreenMode.FixturesPicker(followedLeagues()), errorModal = null) }
    }

    // --- My Team ---------------------------------------------------------------------

    /** Bottom bar's My Team button: opens the summary directly if a team's already set, otherwise
     * starts the two-step setup flow. */
    fun openMyTeam() {
        val teamId = myTeamId
        val leagueId = myTeamLeagueId
        val name = myTeamName
        if (teamId == null || leagueId == null || name == null) {
            openMyTeamSetup()
            return
        }
        loadMyTeamSummary(teamId, name, leagueId)
    }

    fun openMyTeamSetup() {
        modeBeforeMyTeamSetup = _uiState.value.mode
        updateState { it.copy(mode = ScoreScreenMode.MyTeamLeaguePicker(followedLeagues()), errorModal = null) }
    }

    fun backFromMyTeamLeaguePicker() {
        val previous = modeBeforeMyTeamSetup ?: settingsMode()
        modeBeforeMyTeamSetup = null
        updateState { it.copy(mode = previous, errorModal = null) }
    }

    fun selectMyTeamLeague(leagueId: Int, leagueName: String) {
        updateState {
            it.copy(
                mode = ScoreScreenMode.MyTeamTeamPicker(leagueId, leagueName, emptyList(), isLoading = true),
                errorModal = null,
            )
        }
        val cached = standingsCache[leagueId]
        if (cached != null) {
            updateState { state ->
                if (state.mode is ScoreScreenMode.MyTeamTeamPicker && state.mode.leagueId == leagueId) {
                    state.copy(mode = state.mode.copy(teams = cached, isLoading = false))
                } else {
                    state
                }
            }
            return
        }
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val result = api.fetchStandings(leagueId)
            result.fold(
                onSuccess = { rows ->
                    standingsCache[leagueId] = rows
                    updateState { state ->
                        if (state.mode is ScoreScreenMode.MyTeamTeamPicker && state.mode.leagueId == leagueId) {
                            state.copy(mode = state.mode.copy(teams = rows, isLoading = false))
                        } else {
                            state
                        }
                    }
                },
                onFailure = { error ->
                    updateState { state ->
                        val fallback = if (state.mode is ScoreScreenMode.MyTeamTeamPicker && state.mode.leagueId == leagueId) {
                            state.mode.copy(isLoading = false)
                        } else {
                            state.mode
                        }
                        state.copy(mode = fallback, errorModal = apiErrorMessage(error))
                    }
                },
            )
        }
    }

    fun backFromMyTeamTeamPicker() {
        updateState { it.copy(mode = ScoreScreenMode.MyTeamLeaguePicker(followedLeagues()), errorModal = null) }
    }

    fun selectMyTeamTeam(teamId: Int, teamName: String, leagueId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                dataStore.edit { prefs ->
                    prefs[SoccerPreferences.MY_TEAM_ID] = teamId
                    prefs[SoccerPreferences.MY_TEAM_NAME] = teamName
                    prefs[SoccerPreferences.MY_TEAM_LEAGUE_ID] = leagueId
                }
            }
            myTeamId = teamId
            myTeamName = teamName
            myTeamLeagueId = leagueId
            loadMyTeamSummary(teamId, teamName, leagueId)
        }
    }

    private fun loadMyTeamSummary(teamId: Int, teamName: String, leagueId: Int) {
        updateState { it.copy(mode = ScoreScreenMode.MyTeam(summary = null, isLoading = true), errorModal = null) }
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val leagueName = competitionName(leagueId)
            val standings = standingsCache[leagueId] ?: api.fetchStandings(leagueId).getOrElse { emptyList() }
                .also { if (it.isNotEmpty()) standingsCache[leagueId] = it }
            val result = api.fetchMyTeamSummary(teamId, teamName, leagueId, leagueName, standings)
            result.fold(
                onSuccess = { summary ->
                    updateState { state ->
                        if (state.mode is ScoreScreenMode.MyTeam) {
                            state.copy(mode = ScoreScreenMode.MyTeam(summary, isLoading = false), errorModal = null)
                        } else {
                            state
                        }
                    }
                },
                onFailure = { error ->
                    updateState { state ->
                        val fallback = if (state.mode is ScoreScreenMode.MyTeam) {
                            ScoreScreenMode.MyTeam(summary = null, isLoading = false)
                        } else {
                            state.mode
                        }
                        state.copy(mode = fallback, errorModal = apiErrorMessage(error))
                    }
                },
            )
        }
    }

    fun backFromMyTeam() {
        updateState { it.copy(mode = lastScores ?: ScoreScreenMode.Loading(FETCHING_MESSAGE), errorModal = null) }
    }

    fun clearMyTeam() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                dataStore.edit { prefs ->
                    prefs.remove(SoccerPreferences.MY_TEAM_ID)
                    prefs.remove(SoccerPreferences.MY_TEAM_NAME)
                    prefs.remove(SoccerPreferences.MY_TEAM_LEAGUE_ID)
                }
            }
            myTeamId = null
            myTeamName = null
            myTeamLeagueId = null
            updateState { it.copy(mode = settingsMode(), errorModal = null) }
        }
    }

    // --- Match detail ----------------------------------------------------------------

    /** Opens the detail screen for a tapped match row, from Scores, Fixtures, or My Team. The
     * header (teams/score/status) comes straight from [match] and renders immediately; the
     * stats/timeline/lineups tabs fetch separately and fill in once loaded. */
    fun openMatchDetail(match: Fixture) {
        modeBeforeMatchDetail = _uiState.value.mode
        val mode = ScoreScreenMode.MatchDetailScreen(
            fixtureId = match.id,
            homeTeamId = match.homeTeamId,
            awayTeamId = match.awayTeamId,
            homeTeamName = match.homeTeamName,
            awayTeamName = match.awayTeamName,
            scoreLabel = match.scoreLabel(),
            statusLabel = match.statusLabel(),
            isLive = match.status.isLive,
            detail = null,
            isLoading = true,
        )
        updateState { it.copy(mode = mode, errorModal = null) }
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val result = api.fetchMatchDetail(match.id, match.homeTeamId, match.awayTeamId)
            result.fold(
                onSuccess = { detail ->
                    updateState { state ->
                        val current = state.mode as? ScoreScreenMode.MatchDetailScreen
                        if (current != null && current.fixtureId == match.id) {
                            state.copy(mode = current.copy(detail = detail, isLoading = false), errorModal = null)
                        } else {
                            state
                        }
                    }
                },
                onFailure = { error ->
                    updateState { state ->
                        val current = state.mode as? ScoreScreenMode.MatchDetailScreen
                        val fallback = if (current != null && current.fixtureId == match.id) {
                            current.copy(isLoading = false)
                        } else {
                            state.mode
                        }
                        state.copy(mode = fallback, errorModal = apiErrorMessage(error))
                    }
                },
            )
        }
    }

    fun backFromMatchDetail() {
        val previous = modeBeforeMatchDetail ?: lastScores ?: ScoreScreenMode.Loading(FETCHING_MESSAGE)
        modeBeforeMatchDetail = null
        updateState { it.copy(mode = previous, errorModal = null) }
    }

    private fun updateState(transform: (ScoreUiState) -> ScoreUiState) {
        _uiState.update(transform)
    }
}
