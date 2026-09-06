package com.thelightphone.soccerfootball

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
        /** Keyed by [CompetitionGroup.leagueLogo] URL, not league ID — a group's own logo URL is
         * the lookup key a caller already has in hand. Empty until [refresh]'s follow-up fetch
         * resolves; a group simply renders without a badge until then, same "omit rather than show
         * broken" convention as every other image on this screen. */
        val leagueLogos: Map<String, ByteArray> = emptyMap(),
        /** Keyed by [Fixture.homeTeamLogo]/[Fixture.awayTeamLogo] URL — backs the per-match team
         * crests in [MatchTeamsAndScoreCell] (SoccerHomeScreen.kt). Fetched alongside [leagueLogos]
         * as the same follow-up in [refresh]; a missing entry just means that one crest is skipped,
         * not that the whole row falls back to text-only. */
        val teamLogos: Map<String, ByteArray> = emptyMap(),
    ) : ScoreScreenMode()

    data class Settings(
        val selectedLeagueNames: List<String>,
        /** Null when no team has been picked yet — Settings shows "Not set" and My Team's row
         * opens the setup flow instead of the summary screen. */
        val myTeamName: String?,
    ) : ScoreScreenMode()
    data object Attribution : ScoreScreenMode()

    data class LeagueSelection(val rows: List<LeagueSelectionRow>) : ScoreScreenMode()

    data class Standings(
        val leagueId: Int,
        val leagueName: String,
        val rows: List<StandingsRow>,
        val isLoading: Boolean,
        val lastUpdated: Instant?,
        /** Fetched as a follow-up once [rows] resolves, same pattern as [MatchDetailScreen]'s coach
         * photos — see [StandingsFetchResult] for where the URL comes from. Null while unresolved,
         * on fetch failure, or if this league had no logo. */
        val leagueLogoBytes: ByteArray? = null,
    ) : ScoreScreenMode()

    /** All followed leagues' fixtures, grouped by day then by competition — see
     * [groupedByDateThenLeague]. No more standalone per-league picker/view (removed on request);
     * this is reached directly from the bottom bar, same as [Standings] is now reached only via a
     * league logo tap rather than its own picker. [leagueLogos] backs each day/league card's header
     * badge (see FixtureLeagueCard in SoccerHomeScreen.kt) — fetched as a follow-up the same way
     * [Scores.leagueLogos] is, keyed by the same [CompetitionGroup.leagueLogo] URL. [teamLogos],
     * keyed by [Fixture.homeTeamLogo]/[Fixture.awayTeamLogo] URL, backs the per-match team crests
     * in [MatchTeamsAndScoreCell] — fetched concurrently with [leagueLogos] in the same follow-up. */
    data class Fixtures(
        val groups: List<FixtureDayGroup>,
        val isLoading: Boolean,
        val lastUpdated: Instant?,
        val leagueLogos: Map<String, ByteArray> = emptyMap(),
        val teamLogos: Map<String, ByteArray> = emptyMap(),
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
        /** Team crest bytes for the header's team icons — fetched separately from [detail] (see
         * [openMatchDetail]) so a slow/failed stats-events-lineups fetch never blocks the crests
         * from appearing, and vice versa. Null until that fetch resolves, or if a side had no logo
         * URL / the fetch failed. */
        val homeTeamLogoBytes: ByteArray? = null,
        val awayTeamLogoBytes: ByteArray? = null,
        /** Coach headshot bytes for the lineup tab — fetched after [detail] resolves, since a
         * coach's photo URL lives inside the lineups response itself (see
         * [ApiFootballApi.fetchCoachPhotos]'s doc comment), unlike the crests above which are
         * already known from the tapped [Fixture]. Null until that follow-up fetch resolves, or if
         * a side had no coach/photo. */
        val homeCoachPhotoBytes: ByteArray? = null,
        val awayCoachPhotoBytes: ByteArray? = null,
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
                val groups = matches.groupedForDisplay()
                val mode = ScoreScreenMode.Scores(
                    groups = groups,
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
                // League badges and team crests fetch only now, as a follow-up, run concurrently
                // with each other — same pattern as MatchDetailScreen's coach photos below: nothing
                // here needs to block the scores themselves rendering. Silent on failure/blank, same
                // "just render without a badge" convention as the rest of this app's images.
                val (leagueLogos, teamLogos) = coroutineScope {
                    val leagueLogosDeferred = async { api.fetchLeagueLogos(groups.map { it.leagueLogo }) }
                    val teamLogosDeferred = async {
                        api.fetchTeamLogos(matches.flatMap { listOf(it.homeTeamLogo, it.awayTeamLogo) })
                    }
                    leagueLogosDeferred.await() to teamLogosDeferred.await()
                }
                if (leagueLogos.isNotEmpty() || teamLogos.isNotEmpty()) {
                    val modeWithLogos = mode.copy(leagueLogos = leagueLogos, teamLogos = teamLogos)
                    lastScores = modeWithLogos
                    updateState { state ->
                        // Guards against a newer refresh() call having already replaced groups by
                        // the time this slower logo fetch resolves — don't stamp stale badges onto
                        // whatever's on screen now.
                        if (state.mode is ScoreScreenMode.Scores && state.mode.groups == groups) {
                            state.copy(mode = modeWithLogos)
                        } else {
                            state
                        }
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

    // Only a genuine Kind.NETWORK failure (timeout, no connection, the proxy itself unreachable)
    // shows NETWORK_ERROR_MESSAGE — everything else surfaces its own real message instead of being
    // masked as a network problem. Previously any unclassified error (a 403 whitelist rejection, a
    // bad league id, an unexpected response shape) fell through to the generic "requires a network
    // connection" text, which was actively misleading for e.g. the MLS standings bug: the request
    // was reaching the proxy fine, it just wasn't a connectivity issue at all.
    private fun apiErrorMessage(error: Throwable): String = when {
        error is ApiFootballApiException && error.kind == ApiFootballApiException.Kind.RATE_LIMITED ->
            "Too many requests — try again in a minute."
        error is ApiFootballApiException && error.kind == ApiFootballApiException.Kind.NETWORK ->
            NETWORK_ERROR_MESSAGE
        error is ApiFootballApiException -> error.message ?: NETWORK_ERROR_MESSAGE
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

    /** [followedLeagues] narrowed to competitions with an actual league table — knockout cups
     * (FA Cup, Copa del Rey, etc.) have no `/standings` response to show, so they're left out of
     * the Standings and My Team league pickers rather than opening onto a permanently-empty
     * "not available" screen. They still show up normally in Scores/Fixtures. */
    private fun followedTableCompetitions(): List<Competition> = followedLeagues().filter { it.hasStandings }

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
    //
    // No standalone picker screen — the table is reached only by tapping a league logo
    // elsewhere (currently Scores' per-competition headers; see `competitionHasStandings`
    // in SoccerModels.kt for the cup-competition guard), so this section is just the table
    // itself plus its back navigation.

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
                onSuccess = { standingsResult ->
                    val rows = standingsResult.rows
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
                    // Badge fetch only now, as a follow-up — same pattern as Scores' league logos
                    // and MatchDetailScreen's coach photos: doesn't block the table itself rendering.
                    val logoBytes = standingsResult.leagueLogoUrl
                        .takeIf { it.isNotBlank() }
                        ?.let { api.fetchLeagueLogos(listOf(it))[it] }
                    if (logoBytes != null) {
                        updateState { state ->
                            if (state.mode is ScoreScreenMode.Standings && state.mode.leagueId == leagueId) {
                                state.copy(mode = state.mode.copy(leagueLogoBytes = logoBytes))
                            } else {
                                state
                            }
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
        updateState { it.copy(mode = lastScores ?: ScoreScreenMode.Loading(FETCHING_MESSAGE), errorModal = null) }
    }

    // --- Fixtures ------------------------------------------------------------------
    //
    // No standalone per-league picker/view (removed on request) — this is now a single screen
    // covering every followed league at once, grouped by day then by competition (see
    // groupedByDateThenLeague in SoccerModels.kt), reached directly from the bottom bar.

    fun openFixtures() {
        updateState {
            it.copy(mode = ScoreScreenMode.Fixtures(emptyList(), isLoading = true, lastUpdated = null), errorModal = null)
        }
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val today = todayLocalDate()
            val dateFrom = today.minus(FIXTURES_PAST_DAYS, DateTimeUnit.DAY).toString()
            val dateTo = today.plus(FIXTURES_FUTURE_DAYS, DateTimeUnit.DAY).toString()
            val result = api.fetchFixturesForLeagues(followedLeagues().map { it.id }, dateFrom, dateTo)
            result.fold(
                onSuccess = { matches ->
                    val groups = matches.groupedByDateThenLeague()
                    updateState { state ->
                        if (state.mode is ScoreScreenMode.Fixtures) {
                            state.copy(
                                mode = ScoreScreenMode.Fixtures(
                                    groups = groups,
                                    isLoading = false,
                                    lastUpdated = Clock.System.now(),
                                ),
                                errorModal = null,
                            )
                        } else {
                            state
                        }
                    }
                    // League badges and team crests fetch only now, as a follow-up, run concurrently
                    // with each other — same pattern refresh() already uses for Scores' own
                    // leagueLogos/teamLogos: doesn't block the fixtures themselves rendering, silent
                    // on failure/blank.
                    val (leagueLogos, teamLogos) = coroutineScope {
                        val leagueLogosDeferred = async {
                            api.fetchLeagueLogos(groups.flatMap { it.leagueGroups }.map { it.leagueLogo })
                        }
                        val teamLogosDeferred = async {
                            api.fetchTeamLogos(matches.flatMap { listOf(it.homeTeamLogo, it.awayTeamLogo) })
                        }
                        leagueLogosDeferred.await() to teamLogosDeferred.await()
                    }
                    if (leagueLogos.isNotEmpty() || teamLogos.isNotEmpty()) {
                        updateState { state ->
                            // Guards against a newer openFixtures() call having already replaced
                            // groups by the time this slower logo fetch resolves.
                            if (state.mode is ScoreScreenMode.Fixtures && state.mode.groups == groups) {
                                state.copy(mode = state.mode.copy(leagueLogos = leagueLogos, teamLogos = teamLogos))
                            } else {
                                state
                            }
                        }
                    }
                },
                onFailure = { error ->
                    updateState { state ->
                        val fallback = if (state.mode is ScoreScreenMode.Fixtures) {
                            ScoreScreenMode.Fixtures(emptyList(), false, null)
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
        updateState { it.copy(mode = lastScores ?: ScoreScreenMode.Loading(FETCHING_MESSAGE), errorModal = null) }
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
        updateState { it.copy(mode = ScoreScreenMode.MyTeamLeaguePicker(followedTableCompetitions()), errorModal = null) }
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
                onSuccess = { standingsResult ->
                    val rows = standingsResult.rows
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
        updateState { it.copy(mode = ScoreScreenMode.MyTeamLeaguePicker(followedTableCompetitions()), errorModal = null) }
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
            val standings = standingsCache[leagueId]
                ?: api.fetchStandings(leagueId).getOrElse { StandingsFetchResult(rows = emptyList()) }.rows
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
                    // Coach photos fetch only now, not alongside the crests fetch below — their
                    // URLs live inside detail.lineups itself (see fetchCoachPhotos's doc comment),
                    // so there's nothing to fetch until this point. Silent on failure, same as the
                    // crests: a missing headshot just means the "Coach: {name}" line renders
                    // without one, not something worth an errorModal over.
                    val (homeCoachBytes, awayCoachBytes) = api.fetchCoachPhotos(
                        detail.lineups.home?.coachPhotoUrl,
                        detail.lineups.away?.coachPhotoUrl,
                    )
                    updateState { state ->
                        val current = state.mode as? ScoreScreenMode.MatchDetailScreen
                        if (current != null && current.fixtureId == match.id) {
                            state.copy(mode = current.copy(homeCoachPhotoBytes = homeCoachBytes, awayCoachPhotoBytes = awayCoachBytes))
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
        // Crests fetch independently of stats/events/lineups above — see the doc comment on
        // MatchDetailScreen.homeTeamLogoBytes for why this is a separate launch rather than folded
        // into fetchMatchDetail's result. A failure here is silent (no errorModal): a missing crest
        // just means the header renders without an icon, not something worth interrupting the user
        // over the way a failed stats/events fetch is.
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val (homeBytes, awayBytes) = api.fetchMatchCrests(match.homeTeamLogo, match.awayTeamLogo)
            updateState { state ->
                val current = state.mode as? ScoreScreenMode.MatchDetailScreen
                if (current != null && current.fixtureId == match.id) {
                    state.copy(mode = current.copy(homeTeamLogoBytes = homeBytes, awayTeamLogoBytes = awayBytes))
                } else {
                    state
                }
            }
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
