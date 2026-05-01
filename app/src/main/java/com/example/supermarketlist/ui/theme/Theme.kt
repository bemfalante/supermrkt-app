package com.example.supermarketlist.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onBackground = DarkOnBackground,
    onSurface = DarkOnSurface
)

@Composable
fun SupermarketListTheme(
    content: @Composable () -> Unit
) {
    // Force dark theme as requested
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
