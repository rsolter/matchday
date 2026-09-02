package com.thelightphone.soccerfootball

import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

internal object SoccerPreferences {
    /** API-Football key, sent as the `x-apisports-key` header on every request. Unlike the ESPN
     * variant of this tool, API-Football needs one — see SoccerApi.kt. */
    val API_KEY = stringPreferencesKey("api_key")

    val CACHED_MATCHES_JSON = stringPreferencesKey("cached_matches_json")
    val CACHED_MATCHES_DATE = stringPreferencesKey("cached_matches_date")

    /** Which of [TRACKED_COMPETITIONS] IDs the user wants to follow, stored as strings (DataStore
     * has no int-set key type). Missing/absent means "all of them" (the pre-selection default) —
     * see SoccerViewModel. */
    val SELECTED_COMPETITIONS = stringSetPreferencesKey("selected_competitions")

    /** My Team: the followed team's API-Football team ID/name and which tracked competition it
     * plays in, so its standings/fixtures/injuries can be pulled without a separate lookup call.
     * All three are set together and cleared together (see SoccerViewModel.clearMyTeam) — treat
     * [MY_TEAM_ID] as null as the source of truth for "no team followed yet". */
    val MY_TEAM_ID = intPreferencesKey("my_team_id")
    val MY_TEAM_NAME = stringPreferencesKey("my_team_name")
    val MY_TEAM_LEAGUE_ID = intPreferencesKey("my_team_league_id")
}
