package com.nova.cfquota.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Extra semantic colors that adapt to light/dark, exposed via CompositionLocal. */
data class CfExtraColors(
    val panelBackground: Color,
    val bannerBackground: Color,
    val progressTrack: Color,
    val label: Color,
    val workers: Color,
    val pages: Color,
    val quota: Color,
    val brand: Color
)

val LocalCfExtraColors = staticCompositionLocalOf {
    CfExtraColors(
        panelBackground = PanelBlueLight,
        bannerBackground = BannerCreamLight,
        progressTrack = ProgressTrackLight,
        label = LightLabel,
        workers = WorkersGreen,
        pages = CfBlue,
        quota = QuotaOrange,
        brand = CfBlue
    )
}

private val LightColors = lightColorScheme(
    primary = CfBlue,
    onPrimary = Color.White,
    background = LightBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    outline = LightOutline
)

private val DarkColors = darkColorScheme(
    primary = CfBlue,
    onPrimary = Color.White,
    background = DarkBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    outline = DarkOutline
)

@Composable
fun CfQuotaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColors else LightColors
    val extra = if (darkTheme) {
        CfExtraColors(
            panelBackground = PanelBlueDark,
            bannerBackground = BannerCreamDark,
            progressTrack = ProgressTrackDark,
            label = DarkLabel,
            workers = WorkersGreen,
            pages = CfBlue,
            quota = QuotaOrange,
            brand = CfBlue
        )
    } else {
        CfExtraColors(
            panelBackground = PanelBlueLight,
            bannerBackground = BannerCreamLight,
            progressTrack = ProgressTrackLight,
            label = LightLabel,
            workers = WorkersGreen,
            pages = CfBlue,
            quota = QuotaOrange,
            brand = CfBlue
        )
    }
    CompositionLocalProvider(LocalCfExtraColors provides extra) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CfTypography,
            content = content
        )
    }
}
