package com.deskconnect.companion.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = SlateDark,
    background = SlateDark,
    surface = SlateSurface,
    onBackground = TextWhite,
    onSurface = TextWhite,
    error = CrimsonAbsent
)

@Composable
fun DeskConnectTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
