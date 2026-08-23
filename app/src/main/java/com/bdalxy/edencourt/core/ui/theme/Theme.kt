package com.bdalxy.edencourt.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = SakuraBlue,
    secondary = SakuraPink,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
)

private val DarkColorScheme = darkColorScheme(
    primary = SakuraBlue,
    secondary = SakuraPink,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface,
)

@Composable
fun EdenCourtTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = EdenCourtTypography,
        content = content,
    )
}