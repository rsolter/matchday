package com.thelightphone.soccerfootball

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * Renders one of Matchday's own bottom-bar drawables (this module's own `res/drawable/`, not the
 * vendored SDK's) with the same tinting behavior as [com.thelightphone.sdk.ui.LightIcon] — the theme's
 * content color, so it renders correctly in both Dark and Light LightOS themes.
 *
 * This can't just be a new [com.thelightphone.sdk.ui.LightIconConfiguration] entry the way sdk:ui's own
 * `LightIcons.kt` defines its icons: that's a `sealed class` declared in the sdk:ui Gradle module, and
 * Kotlin forbids subclassing a sealed class from a different module — not a style choice, a hard
 * compiler restriction ("Extending sealed classes or interfaces from a different module is
 * prohibited"). Wire this into a bottom-bar slot via `LightBarButton.Custom(content = { SoccerBarIcon(...) })`
 * instead of `LightBarButton.LightIcon`.
 */
@Composable
fun SoccerBarIcon(drawableRes: Int, contentDescription: String) {
    Icon(
        painter = painterResource(id = drawableRes),
        contentDescription = contentDescription,
        tint = LightThemeTokens.colors.content,
        modifier = Modifier.fillMaxSize(),
    )
}
