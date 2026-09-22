package com.thelightphone.soccerfootball

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.viewModelScope
import com.thelightphone.sdk.LightViewModel
import com.thelightphone.sdk.SimpleLightScreen
import kotlinx.coroutines.CompletableDeferred
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
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

sealed class ScoreScreenMode {
    data class Loading(val message: String) : ScoreScreenMode()
    /** On request, absorbs the former standalone Fixtures screen entirely: [days] now spans
     * [SCHEDULE_PAST_DAYS]..[SCHEDULE_FUTURE_DAYS] around [todayLocalDate] (was today-only) — see
     * [refresh]'s doc comment — grouped flat by day rather than day-then-league (see
     * [FixtureDay]/[groupedByDate] in SoccerModels.kt); the old `Fixtures` mode, its bottom-bar
     * icon, and its own separate fetch (`openFixtures`) are gone, this is the only schedule view
     * now. No more per-league logo map: the day-card redesign dropped the per-league crest header
     * in favor of a plain league short-name label per row (see MatchRow's font-size-audit entry),
     * so there's nothing left that needs [Competition]-keyed image bytes for this screen. */
    data class Scores(
        val days: List<FixtureDay>,
        val lastUpdated: Instant?,
        val isRefreshing: Boolean,
        /** Keyed by [Fixture.homeTeamLogo]/[Fixture.awayTeamLogo] URL — backs the per-match team
         * crests on each row's top line. Fetched as a follow-up in [refresh]; a missing entry just
         * means that one crest is skipped, not that the whole row falls back to text-only. */
        val teamLogos: Map<String, ByteArray> = emptyMap(),
        /** [Fixture.id]s of still-scheduled matches this list currently knows have a posted lineup
         * — checked by [refresh]'s lineup-availability follow-up (see that fun's doc comment) only
         * for matches kicking off within [LINEUP_CHECK_WINDOW], so most of the window's fixtures
         * never get an extra API call for this. A fixture's absence here just means either it isn't
         * close enough to kickoff yet to have been checked, or it was checked and nothing's posted
         * yet — [MatchRow] (SoccerHomeScreen.kt) shows the plain kickoff time in both cases,
         * "Lineups" only once its id actually lands in this set. */
        val lineupsAvailableFixtureIds: Set<Int> = emptySet(),
    ) : ScoreScreenMode()

    data class Settings(
        val selectedLeagueNames: List<String>,
        /** Null when no team has been picked yet — Settings shows "Not set" and My Team's row
         * opens the setup flow instead of the summary screen. */
        val myTeamName: String?,
    ) : ScoreScreenMode()
    data object Attribution : ScoreScreenMode()

    /** [leagueLogos] is keyed by [Competition.id] rather than a URL, unlike every other logo map on
     * this class — see [ApiFootballApi.fetchLeagueLogosByCompetitionId]'s doc comment for why this
     * screen needs its own fetch shape. Starts empty and fills in once that follow-up fetch resolves
     * (see [SoccerViewModel.openLeagueSelection]); a still-missing id after that just renders that
     * row without an icon, same as every other logo in this app. */
    data class LeagueSelection(
        val rows: List<LeagueSelectionRow>,
        val leagueLogos: Map<Int, ByteArray> = emptyMap(),
    ) : ScoreScreenMode()

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

    /** Reachable by tapping either team's badge on [MatchDetailScreen] — renders with the exact
     * same content [MyTeam] does (same [MyTeamSummary] shape, same [MyTeamContent] composable),
     * but for whichever team was tapped rather than the user's own saved team. Kept as its own
     * mode instead of reusing [MyTeam] directly: [MyTeam]'s back button always returns to Scores
     * (see [backFromMyTeam]), which would be wrong here — a badge tapped from a match detail
     * screen should return to that same match detail screen (see [openTeamDetail]/
     * [backFromTeamDetail]), not skip past it to Scores. */
    data class TeamDetail(
        val summary: MyTeamSummary?,
        val isLoading: Boolean,
    ) : ScoreScreenMode()

    /** Reachable by tapping a match row from Scores (which absorbed the former standalone Fixtures
     * screen — see that mode's doc comment) or My Team. [detail] is null while
     * [isLoading] is true, and stays null on a failed fetch — the header (teams/score/status)
     * still has everything it needs from the tapped [Fixture] itself. Named `MatchDetailScreen`
     * rather than `MatchDetail` to avoid colliding with the domain model of the same name. */
    data class MatchDetailScreen(
        val fixtureId: Int,
        val homeTeamId: Int,
        val awayTeamId: Int,
        val homeTeamName: String,
        val awayTeamName: String,
        /** The tapped fixture's own league id — used as the "league" for a per-team detail lookup
         * when either team's badge is tapped (see [openTeamDetail]). Applied identically to both
         * the home and away team since this app has no separate "each team's home league" concept
         * anywhere else — an approximation that can be technically wrong for a cross-league cup
         * tie (e.g. a Premier League side visiting a Championship side in the FA Cup), though
         * low-risk in practice: [MyTeamContent] (reused for [TeamDetail]) doesn't render any
         * standings-position block a wrong-league lookup could visibly get wrong (see
         * [MyTeamSummary.standingsRow]'s doc comment). */
        val leagueId: Int,
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
        /** Player headshot bytes for the lineup pitch, keyed by [LineupPlayer.id] — fetched in the
         * same follow-up phase as the coach photos above (once [detail]'s lineups are known, since
         * that's the only place [LineupPlayer.id] comes from) via
         * [ApiFootballApi.fetchPlayerPhotos]. One shared map for both teams (player ids are globally
         * unique, not per-team) rather than separate home/away maps, since [LineupSection]
         * (SoccerHomeScreen.kt) is called once per team anyway and just looks up each of its own
         * players' ids in it. A player missing from this map — no id, fetch failed, or this phase
         * hasn't resolved yet — falls back to the existing number-in-circle rendering, never a
         * blank space. */
        val playerPhotosById: Map<Int, ByteArray> = emptyMap(),
    ) : ScoreScreenMode()
}

/** [region] carries [Competition.region] through to [LeagueSelectionContent] (SoccerHomeScreen.kt)
 * so that screen can render one section header per country (plus "International Club" for
 * UCL/UEL) instead of one flat list — on request. */
data class LeagueSelectionRow(val id: Int, val name: String, val selected: Boolean, val region: String)

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
/** How far back/forward [refresh]'s fetch window reaches from [todayLocalDate] — on request, the
 * merged Scores/schedule list now looks back 2 weeks and ahead 4 weeks (was a symmetric 2 weeks
 * either side; before that, today-only for Scores, plus a separate 10-past/21-future window for the
 * now-retired standalone Fixtures screen — see that mode's own doc comment history). Asymmetric on
 * purpose this round, matching what was actually asked for; SettingsContent's About section
 * mentions this window in plain language too, on request, so keep the two in sync if this changes
 * again. */
private const val SCHEDULE_PAST_DAYS = 14
private const val SCHEDULE_FUTURE_DAYS = 28

/** How far ahead of kickoff [refresh] starts spending an extra API call per still-scheduled match
 * to check whether its lineup has been posted yet (see [ApiFootballApi.fetchLineupAvailability]).
 * This is a real per-fixture check, not a guess at exact posting time — a fixture outside this
 * window just never gets checked (kickoff time keeps showing), it's never told the wrong thing.
 * Set to 1 hour per the user's own confirmation that lineups only ever post within an hour of
 * kickoff (this project has no independent API-Football source for that timing) — was 2 hours the
 * round this was built, as an unverified margin-of-safety guess; narrowed on request now that
 * there's an actual answer. Since this only bounds which fixtures get checked (not when lineups
 * actually appear), a real narrowing done for the wrong reason would only ever cost a late "Lineups"
 * label on the rare match that leaks past an hour — never a wrong one. */
private val LINEUP_CHECK_WINDOW = 1.hours

/**
 * Phase 3: this app talks to its own caching proxy (`ApiFootballApi`'s `API_BASE`), not
 * API-Football directly, and no longer holds or prompts for an API key — the proxy holds the real
 * key server-side. Refresh here is still on-demand only (once on first load, and via the Scores
 * screen's bottom-bar Refresh icon) rather than a poll loop, even though the proxy is exactly the
 * kind of shared, budget-absorbing intermediary that would make background polling cheap across
 * installs — that's a real follow-up worth doing, just not part of this pass, which is scoped to
 * the proxy migration itself.
 */
class SoccerViewModel(
    private val dataStore: DataStore<Preferences>,
) : LightViewModel<Unit>() {
    private val api = ApiFootballApi()
    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(ScoreUiState())
    val uiState: StateFlow<ScoreUiState> = _uiState.asStateFlow()

    /** Completed once [loadInitialState] has read [SoccerPreferences.SELECTED_COMPETITIONS] from
     * disk and [selectedIds] holds the user's real, persisted selection — not [selectedIds]'s
     * all-competitions default below. Root-caused a real bug this round ("competitions followed
     * doesn't always reflect what's filtered after an app/device restart, other leagues like FA Cup
     * show anyway") that turned out to be a startup race, not a data/persistence bug: [init] and
     * [onScreenShow] each independently launch a coroutine on `Dispatchers.IO`, and
     * `Dispatchers.IO` has no ordering guarantee between two separately-launched coroutines — on a
     * cold start, [onScreenShow]'s `if (lastScores == null)` branch (true on first launch, since
     * nothing's loaded yet) could reach its own [refresh] call *before* [loadInitialState]'s
     * `dataStore.data.first()` had actually resolved, reading [selectedIds] while it still held its
     * all-competitions default — exactly the reported symptom, and exactly why it was intermittent
     * ("does not always") rather than every time: real coroutine-scheduling timing, not a
     * deterministic bug. Every [refresh] call site now awaits this before running, closing that
     * window instead of just narrowing it. */
    private val selectionLoaded = CompletableDeferred<Unit>()

    private var selectedIds: Set<Int> = TRACKED_COMPETITIONS.map { it.id }.toSet()
    private var lastScores: ScoreScreenMode.Scores? = null
    private var modeBeforeAttribution: ScoreScreenMode? = null
    private var modeBeforeMatchDetail: ScoreScreenMode? = null
    private var modeBeforeMyTeamSetup: ScoreScreenMode? = null
    private var modeBeforeTeamDetail: ScoreScreenMode? = null
    private var modeBeforeStandings: ScoreScreenMode? = null

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
            // The `finally` guarantees [selectionLoaded] always completes, even if
            // loadInitialState() throws (a bad/corrupt DataStore read, say) — without it, a failure
            // here would leave onScreenShow's own coroutine awaiting [selectionLoaded] forever,
            // trading the old intermittent "wrong leagues" bug for a new "stuck on Loading forever"
            // one on the unlucky case where the very read this is all guarding against also happens
            // to fail. [CompletableDeferred.complete] is a no-op (returns false) if
            // loadInitialState() already completed it normally, so this is never a double-signal.
            try {
                loadInitialState()
            } finally {
                selectionLoaded.complete(Unit)
            }
        }
    }

    override fun onScreenShow(screen: SimpleLightScreen<Unit>) {
        super.onScreenShow(screen)
        // Only fetches if nothing's loaded yet (first launch) — see the class doc comment for why
        // there's no poll-on-every-return here.
        if (lastScores == null) {
            viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
                // Waits for loadInitialState()'s own dataStore read to finish before touching
                // selectedIds at all — see [selectionLoaded]'s doc comment for the real bug this
                // closes (a cold-start race that could fetch with the all-competitions default
                // instead of the user's persisted selection). This suspends at most as long as that
                // one DataStore read takes, and not at all once it's already completed — by the time
                // any *later* onScreenShow fires, this Deferred is already resolved.
                selectionLoaded.await()
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
        // selectedIds now holds the user's real, persisted selection (or the correct
        // all-competitions default if they've genuinely never touched League Selection) — safe for
        // any refresh() call, including onScreenShow's own, to read from this point on. See
        // [selectionLoaded]'s doc comment. Completed here, right away, rather than waiting for this
        // whole function (cache load + its own refresh()) to finish, so onScreenShow's awaiting
        // coroutine unblocks as early as possible — [init]'s `finally` also completes this same
        // Deferred as a fallback for the loadInitialState() *throws* case; [CompletableDeferred.
        // complete] is a no-op on an already-completed Deferred, so having both is deliberate, not
        // a bug.
        selectionLoaded.complete(Unit)

        val cached = loadCachedMatches(prefs)
        if (cached != null) {
            val mode = ScoreScreenMode.Scores(cached.groupedByDate(), lastUpdated = null, isRefreshing = true)
            lastScores = mode
            updateState { it.copy(mode = mode) }
        } else {
            updateState { it.copy(mode = ScoreScreenMode.Loading(FETCHING_MESSAGE)) }
        }
        refresh(showSpinner = cached != null)
    }

    // Still keyed by a single calendar date, same as before the Scores/Fixtures merge — invalidates
    // (and triggers a full re-fetch via the null return below) the day the window itself shifts,
    // which is exactly when a cached blob would go stale anyway. Now caches roughly a month's worth
    // of fixtures across every followed league instead of just one day's — Preferences DataStore
    // has no hard size cap, but it's meant for small values, not a growing JSON blob; flagged as a
    // real tradeoff worth revisiting (e.g. a file-backed cache) if it ever causes a slow read/write
    // on a real device, which this sandbox has no way to check.
    private fun loadCachedMatches(prefs: Preferences): List<Fixture>? {
        val cachedDate = prefs[SoccerPreferences.CACHED_MATCHES_DATE]
        val cachedJson = prefs[SoccerPreferences.CACHED_MATCHES_JSON]
        if (cachedDate != todayLocalDate().toString() || cachedJson == null) return null
        val matches = runCatching { json.decodeFromString<List<Fixture>>(cachedJson) }.getOrNull() ?: return null
        return matches.filter { it.leagueId in selectedIds }
    }

    /** Fetches every followed league's matches across [SCHEDULE_PAST_DAYS]..[SCHEDULE_FUTURE_DAYS]
     * around [todayLocalDate] and folds the result into [ScoreUiState] — see the class doc comment
     * for why this isn't on a poll loop. Was today-only, scoped to Scores alone, before the
     * Scores/Fixtures merge (on request) folded the former standalone Fixtures screen's whole
     * window into this one fetch — see [ScoreScreenMode.Scores]'s doc comment. Otherwise behaves
     * like the ESPN/football-data.org variants: only touches what's on screen if the user is
     * looking at Scores/Loading, updates [lastScores] silently otherwise. */
    private suspend fun refresh(showSpinner: Boolean) {
        val isFirstLoad = _uiState.value.mode is ScoreScreenMode.Loading
        if (showSpinner) {
            updateState { state ->
                val mode = state.mode as? ScoreScreenMode.Scores ?: return@updateState state
                state.copy(mode = mode.copy(isRefreshing = true))
            }
        }

        val loadingStartedAt = Clock.System.now()
        val today = todayLocalDate()
        val dateFrom = today.minus(SCHEDULE_PAST_DAYS, DateTimeUnit.DAY).toString()
        val dateTo = today.plus(SCHEDULE_FUTURE_DAYS, DateTimeUnit.DAY).toString()
        val result = api.fetchFixturesForLeagues(selectedIds.toList(), dateFrom, dateTo)
        if (isFirstLoad) awaitMinimumLoading(loadingStartedAt)

        result.fold(
            onSuccess = { matches ->
                cacheMatches(matches)
                val days = matches.groupedByDate()
                val mode = ScoreScreenMode.Scores(
                    days = days,
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
                // Team crests and near-kickoff lineup availability fetch only now, as a follow-up,
                // run concurrently with each other — same pattern as MatchDetailScreen's coach
                // photos below: nothing here needs to block the schedule itself rendering. Crests
                // are silent on failure/blank, same "just render without a badge" convention as the
                // rest of this app's images; lineup availability is the same "omit rather than show
                // broken" idea applied to a status label instead of an image — see
                // fetchLineupAvailability's own doc comment. No more league-logo fetch here: the
                // day-card redesign dropped the per-league crest header (see ScoreScreenMode.Scores'
                // doc comment), so nothing on this screen needs it any more.
                val now = Clock.System.now()
                val lineupCandidateIds = matches.filter { it.isLineupCheckCandidate(now) }.map { it.id }
                val (teamLogos, lineupsAvailable) = coroutineScope {
                    val teamLogosDeferred = async {
                        api.fetchTeamLogos(matches.flatMap { listOf(it.homeTeamLogo, it.awayTeamLogo) })
                    }
                    // Skipped entirely (no request at all) when nothing's close enough to kickoff to
                    // be worth checking — the common case for most of the window.
                    val lineupsDeferred = async {
                        if (lineupCandidateIds.isEmpty()) emptySet() else api.fetchLineupAvailability(lineupCandidateIds)
                    }
                    teamLogosDeferred.await() to lineupsDeferred.await()
                }
                if (teamLogos.isNotEmpty() || lineupsAvailable.isNotEmpty()) {
                    val modeWithExtras = mode.copy(teamLogos = teamLogos, lineupsAvailableFixtureIds = lineupsAvailable)
                    lastScores = modeWithExtras
                    updateState { state ->
                        // Guards against a newer refresh() call having already replaced days by the
                        // time this slower follow-up resolves — don't stamp stale crests/labels onto
                        // whatever's on screen now.
                        if (state.mode is ScoreScreenMode.Scores && state.mode.days == days) {
                            state.copy(mode = modeWithExtras)
                        } else {
                            state
                        }
                    }
                }
            },
            onFailure = { error -> handleFailure(error) },
        )
    }

    /** True for a still-scheduled match kicking off within [LINEUP_CHECK_WINDOW] — the set of
     * fixtures [refresh] spends an extra `/fixtures/lineups` call checking on each refresh. See
     * [LINEUP_CHECK_WINDOW]'s own doc comment for why this width, and [Fixture.kickoffInstant]
     * (SoccerFormatting.kt) for the parse this relies on. */
    private fun Fixture.isLineupCheckCandidate(now: Instant): Boolean {
        if (status != MatchStatus.SCHEDULED) return false
        val timeToKickoff = (kickoffInstant() ?: return false) - now
        return timeToKickoff.isPositive() && timeToKickoff <= LINEUP_CHECK_WINDOW
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

    /** The Scores screen's bottom-bar Refresh icon — this build's entire manual-refresh surface
     * (moved here from a Settings row; see ScoresContent's LightBottomBar doc comment in
     * SoccerHomeScreen.kt for why). */
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
        // Fetched as a follow-up, same pattern as leagueLogos/teamLogos elsewhere on this class:
        // never blocks the row list itself from rendering, and a failed or still-in-flight fetch
        // just means those rows render without an icon a moment longer (or permanently, on failure)
        // rather than showing an error. Every TRACKED_COMPETITIONS id at once, not just followed
        // ones — the whole point of this screen is picking leagues you *aren't* already following.
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val logos = api.fetchLeagueLogosByCompetitionId(TRACKED_COMPETITIONS.map { it.id })
            if (logos.isNotEmpty()) {
                updateState { state ->
                    val mode = state.mode as? ScoreScreenMode.LeagueSelection ?: return@updateState state
                    state.copy(mode = mode.copy(leagueLogos = logos))
                }
            }
        }
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
        TRACKED_COMPETITIONS.map { LeagueSelectionRow(it.id, it.name, it.id in selectedIds, it.region) }

    // --- Standings -----------------------------------------------------------------
    //
    // Reached by tapping a league logo on Scores' per-competition headers (see
    // `competitionHasStandings` in SoccerModels.kt for the cup-competition guard), or — on
    // request — the league-rank line on My Team/Team Detail's header row (see
    // MyTeamHeaderRow's doc comment). [modeBeforeStandings] records whichever of those (or
    // anything else) was on screen when opened, same [modeBeforeTeamDetail]/
    // [modeBeforeMatchDetail] pattern as this file's other "opened from more than one place"
    // screens, so back always returns to where the user actually came from instead of
    // unconditionally landing on Scores the way this used to when Scores' league headers were
    // the only entry point.

    fun openStandingsTable(leagueId: Int, leagueName: String) {
        modeBeforeStandings = _uiState.value.mode
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
        val previous = modeBeforeStandings ?: lastScores ?: ScoreScreenMode.Loading(FETCHING_MESSAGE)
        modeBeforeStandings = null
        updateState { it.copy(mode = previous, errorModal = null) }
    }

    // The standalone Fixtures screen (openFixtures/backFromFixturesTable, ScoreScreenMode.Fixtures)
    // that used to live here is gone — merged into Scores' own refresh() on request (see that mode's
    // and refresh()'s doc comments). Scores is now the only schedule view, reached directly from the
    // bottom bar same as before, just covering the wider window itself instead of handing off to a
    // second screen.

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

    /** Resolves-or-fetches, then [ApiFootballApi.fetchMyTeamSummary] — the fetch logic shared by
     * [loadMyTeamSummary] (the user's own saved team) and [openTeamDetail] (an arbitrary tapped
     * team). Deliberately returns just the [Result] rather than also updating state: the two
     * callers write into different [ScoreScreenMode]s ([ScoreScreenMode.MyTeam] vs.
     * [ScoreScreenMode.TeamDetail]), so the state-update half stays separate in each. */
    private suspend fun fetchTeamSummary(teamId: Int, teamName: String, leagueId: Int): Result<MyTeamSummary> {
        val (resolvedLeagueId, resolvedLeagueName, standingsRow) = resolveDomesticStanding(teamId, leagueId)
        return api.fetchMyTeamSummary(teamId, teamName, resolvedLeagueId, resolvedLeagueName, standingsRow)
    }

    /** My Team's rank line is meant to always be a *domestic*-table position (see
     * `MyTeamHeaderRow.competitionIsDomestic` gate in SoccerHomeScreen.kt) — but [contextLeagueId]
     * (whichever league this team was opened from — a match's badge, a standings row, or the team
     * saved during My Team setup) is sometimes a UEFA competition instead, e.g. a team tapped out of
     * a Champions League match. On request: search every tracked *domestic* league with a table
     * ([TRACKED_COMPETITIONS], filtered) for [teamId], rather than trusting [contextLeagueId] to
     * already be domestic.
     *
     * Cheapest path first: a domestic league whose standings are already in [standingsCache] (the
     * common case — Standings and My Team setup both populate this cache, and a session that's
     * looked at more than one team usually already has most of them) needs no network call at all.
     * Only leagues *not* already cached are fetched, and those run concurrently (same
     * async/coroutineScope pattern as this file's other multi-league fetches, e.g. [refresh]'s team
     * logos/lineups) rather than one at a time — worst case here is six concurrent requests against
     * the proxy's shared budget, and only the first time in the process' lifetime any of the six
     * hasn't been looked at yet.
     *
     * Falls back to [contextLeagueId] itself — fetching its standings too if not yet cached — only
     * if [teamId] genuinely isn't in any tracked domestic table (a newly-promoted team the data
     * doesn't have yet, or every fetch above failed). That fallback can still resolve to a
     * continental id; [standingsRow] may end up null either way — both cases are handled by
     * [MyTeamHeaderRow] omitting the rank line rather than showing something misleading. */
    private suspend fun resolveDomesticStanding(teamId: Int, contextLeagueId: Int): Triple<Int, String, StandingsRow?> {
        val domesticLeagueIds = TRACKED_COMPETITIONS.filter { it.isDomestic && it.hasStandings }.map { it.id }

        fun findCached(): Pair<Int, StandingsRow>? = domesticLeagueIds.firstNotNullOfOrNull { id ->
            standingsCache[id]?.firstOrNull { it.teamId == teamId }?.let { id to it }
        }

        findCached()?.let { (id, row) -> return Triple(id, competitionName(id), row) }

        val uncachedIds = domesticLeagueIds.filter { it !in standingsCache }
        if (uncachedIds.isNotEmpty()) {
            coroutineScope {
                uncachedIds.map { id -> async { id to api.fetchStandings(id).getOrNull()?.rows } }
                    .forEach { deferred ->
                        val (id, rows) = deferred.await()
                        if (!rows.isNullOrEmpty()) standingsCache[id] = rows
                    }
            }
            findCached()?.let { (id, row) -> return Triple(id, competitionName(id), row) }
        }

        val fallbackStandings = standingsCache[contextLeagueId]
            ?: api.fetchStandings(contextLeagueId).getOrElse { StandingsFetchResult(rows = emptyList()) }.rows
                .also { if (it.isNotEmpty()) standingsCache[contextLeagueId] = it }
        return Triple(contextLeagueId, competitionName(contextLeagueId), fallbackStandings.firstOrNull { it.teamId == teamId })
    }

    private fun loadMyTeamSummary(teamId: Int, teamName: String, leagueId: Int) {
        updateState { it.copy(mode = ScoreScreenMode.MyTeam(summary = null, isLoading = true), errorModal = null) }
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val result = fetchTeamSummary(teamId, teamName, leagueId)
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

    // --- Team detail (arbitrary team, reached from a match's badge) ------------------

    /** Opens a read-only team page for whichever team's badge was tapped on [ScoreScreenMode.
     * MatchDetailScreen] — same [MyTeamContent] rendering "My Team" itself uses, populated for
     * [teamId] instead of the user's own saved team. See [ScoreScreenMode.TeamDetail]'s doc
     * comment for why this is a separate mode/back-target rather than reusing [ScoreScreenMode.
     * MyTeam] and [backFromMyTeam] directly. */
    fun openTeamDetail(teamId: Int, teamName: String, leagueId: Int) {
        modeBeforeTeamDetail = _uiState.value.mode
        updateState { it.copy(mode = ScoreScreenMode.TeamDetail(summary = null, isLoading = true), errorModal = null) }
        viewModelScope.launch(Dispatchers.IO + exceptionHandler) {
            val result = fetchTeamSummary(teamId, teamName, leagueId)
            result.fold(
                onSuccess = { summary ->
                    updateState { state ->
                        if (state.mode is ScoreScreenMode.TeamDetail) {
                            state.copy(mode = ScoreScreenMode.TeamDetail(summary, isLoading = false), errorModal = null)
                        } else {
                            state
                        }
                    }
                },
                onFailure = { error ->
                    updateState { state ->
                        val fallback = if (state.mode is ScoreScreenMode.TeamDetail) {
                            ScoreScreenMode.TeamDetail(summary = null, isLoading = false)
                        } else {
                            state.mode
                        }
                        state.copy(mode = fallback, errorModal = apiErrorMessage(error))
                    }
                },
            )
        }
    }

    fun backFromTeamDetail() {
        val previous = modeBeforeTeamDetail ?: lastScores ?: ScoreScreenMode.Loading(FETCHING_MESSAGE)
        modeBeforeTeamDetail = null
        updateState { it.copy(mode = previous, errorModal = null) }
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

    /** Opens the detail screen for a tapped match row, from Scores or My Team. The
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
            leagueId = match.leagueId,
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
                    // Player headshots — same "only fetchable once detail.lineups is known" reasoning
                    // as the coach photos just above (see MatchDetailScreen.playerPhotosById's doc
                    // comment), so folded into this same follow-up phase rather than a fourth launch.
                    // Silent on failure, same as coach photos/crests: a missing headshot just means
                    // that player's pitch dot falls back to the number-in-circle rendering.
                    val playerIds = (
                        (detail.lineups.home?.startXI.orEmpty() + detail.lineups.home?.substitutes.orEmpty()) +
                            (detail.lineups.away?.startXI.orEmpty() + detail.lineups.away?.substitutes.orEmpty())
                        ).mapNotNull { it.id }
                    if (playerIds.isNotEmpty()) {
                        val playerPhotos = api.fetchPlayerPhotos(playerIds)
                        updateState { state ->
                            val current = state.mode as? ScoreScreenMode.MatchDetailScreen
                            if (current != null && current.fixtureId == match.id) {
                                state.copy(mode = current.copy(playerPhotosById = playerPhotos))
                            } else {
                                state
                            }
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
