package com.drivetracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Colors matching the screenshot
val DarkBackground = Color(0xFF1A1A1C)
val CardBackground = Color(0xFF2C2C2E)
val CardBackgroundAlt = Color(0xFF242426)

val AccentGreen = Color(0xFF34C759)
val AccentTeal = Color(0xFF5AC8C8)
val AccentOrange = Color(0xFFFF9500)
val AccentPurple = Color(0xFFBF5AF2)
val AccentRed = Color(0xFFFF3B30)
val AccentBlue = Color(0xFF007AFF)
val AccentYellow = Color(0xFFFFCC00)
val AccentPink = Color(0xFFFF2D55)

val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF8E8E93)
val TextTertiary = Color(0xFF636366)

private val DarkColorScheme = darkColorScheme(
    primary = AccentTeal,
    secondary = AccentGreen,
    background = DarkBackground,
    surface = CardBackground,
    onPrimary = TextPrimary,
    onSecondary = TextPrimary,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun DriveTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
