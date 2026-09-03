package com.thelightphone.soccerfootball

import com.thelightphone.sdk.ui.LightIconConfiguration

/**
 * Soccer Pro's own bottom-bar icons, replacing three of the SDK's generic [com.thelightphone.sdk.ui.LightIcons]
 * with soccer-specific glyphs. Drawables live in this module's own `res/drawable/` (not the vendored
 * SDK's), so they're plain per-module Android resources — no wiring beyond the [LightIconConfiguration]
 * objects below and the `R.drawable.*` references AGP generates from those files.
 *
 * There's deliberately no replacement for Settings — [com.thelightphone.sdk.ui.LightIcons.SETTINGS]
 * (a gear) is already the right icon for that tab; only Fixtures/My Team/Standings got soccer-specific
 * treatments.
 */
object SoccerIcons {
    object FIXTURES : LightIconConfiguration(
        name = "Fixtures",
        drawableResource = R.drawable.ic_calendar_dot_white,
    )
    object MY_TEAM : LightIconConfiguration(
        name = "My Team",
        drawableResource = R.drawable.ic_jersey_white,
    )
    object STANDINGS : LightIconConfiguration(
        name = "Standings",
        drawableResource = R.drawable.ic_table_white,
    )
}
