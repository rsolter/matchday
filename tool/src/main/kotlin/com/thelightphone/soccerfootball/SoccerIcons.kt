package com.thelightphone.soccerfootball

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import com.thelightphone.sdk.ui.LightThemeTokens

/**
 * One of Matchday's own bottom-bar drawables (this module's own `res/drawable/`, not the SDK's),
 * tinted with the theme's content color the way [com.thelightphone.sdk.ui.LightIcon] tints the
 * SDK's icons — so the white vectors render correctly in both Dark and Light LightOS themes. Pass
 * it to `LightBarButton.Icon(painter = ...)`.
 *
 * Why not a [com.thelightphone.sdk.ui.LightIconConfiguration] entry like the SDK's own icons: that's
 * a `sealed class` in the sdk:ui module, and Kotlin forbids subclassing a sealed class from another
 * module. And why `LightBarButton.Icon` rather than a custom-composable bar button: Light's build
 * server compiles tools against the upstream SDK, whose `LightBarButton` has `Text`, `Icon`
 * (any painter, drawn untinted), and `LightIcon` — so the tint goes into the painter itself.
 */
@Composable
fun rememberSoccerBarIconPainter(drawableRes: Int): Painter {
    val base = painterResource(id = drawableRes)
    val tint = LightThemeTokens.colors.content
    return remember(base, tint) { TintedPainter(base, tint) }
}

/** Draws [delegate] with every pixel recolored to [tint] (an SrcIn tint, like `Icon`'s). */
private class TintedPainter(private val delegate: Painter, private val tint: Color) : Painter() {
    override val intrinsicSize: Size get() = delegate.intrinsicSize

    override fun DrawScope.onDraw() {
        with(delegate) { draw(size, colorFilter = ColorFilter.tint(tint)) }
    }
}
