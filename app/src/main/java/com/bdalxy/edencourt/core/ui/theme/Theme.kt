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
    // T12.x P1 修复：浅色主/次色（淡粉蓝 0xFFA8C6F5 / 淡樱粉 0xFFFFC9D4）上白字对比度仅约 1.5~1.75:1，
    // 远低于 WCAG AA 4.5:1——主按钮/FAB/chip 文字几乎不可读；改深色字（0xFF1A1A1A，对比度约 7~9:1 达标）
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = LightOnBackground,
    onSurface = LightOnSurface,
)

private val DarkColorScheme = darkColorScheme(
    // 深色下用更深变体保证白字对比度达标（T6.2）
    primary = DarkSakuraBlue,
    secondary = DarkSakuraPink,
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