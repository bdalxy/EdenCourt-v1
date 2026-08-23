package com.bdalxy.edencourt.core.ui.theme

import androidx.compose.ui.graphics.Color

// 樱羽主题（临时配色方案，企划书 5.1.2）：主色淡粉蓝 + 点缀淡樱粉
val SakuraBlue = Color(0xFFA8C6F5)
val SakuraPink = Color(0xFFFFC9D4)

// 浅色
val LightBackground = Color(0xFFF7F7F7)
val LightSurface = Color(0xFFFFFFFF)
val LightOnBackground = Color(0xFF1A1A1A)
val LightOnSurface = Color(0xFF1A1A1A)

// 深色
val DarkBackground = Color(0xFF121212)
val DarkSurface = Color(0xFF1E1E1E)
val DarkOnBackground = Color(0xFFFAFAFA)
val DarkOnSurface = Color(0xFFFAFAFA)

// 深色下主/次色变体（T6.2：浅粉蓝/浅粉红在深色底上作气泡底时，白字对比度不足，
// 深色方案改用更深变体，DarkSakuraBlue 底 + 白字对比度约 5.3:1，达标 WCAG AA ≥4.5:1）
val DarkSakuraBlue = Color(0xFF4667B8)
val DarkSakuraPink = Color(0xFFC97A90)

// 文字次要色
val TextPrimary = Color(0xFF1A1A1A)
val TextSecondary = Color(0xFF757575)