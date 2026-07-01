package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val ElegantDarkColorScheme = darkColorScheme(
    primary = ElegantPrimary,
    onPrimary = ElegantOnPrimary,
    primaryContainer = ElegantPrimaryContainer,
    onPrimaryContainer = ElegantOnPrimaryContainer,
    secondary = ElegantPrimary,
    onSecondary = ElegantOnPrimary,
    secondaryContainer = ElegantSecondaryContainer,
    onSecondaryContainer = ElegantOnSecondaryContainer,
    background = ElegantBackground,
    onBackground = ElegantOnBackground,
    surface = ElegantSurface,
    onSurface = ElegantOnSurface,
    surfaceVariant = ElegantSurface,
    onSurfaceVariant = ElegantOnSurfaceVariant,
    outline = ElegantOutline
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for the "Elegant Dark" design
    dynamicColor: Boolean = false, // Disable dynamic colors to preserve exact brand styles
    content: @Composable () -> Unit,
) {
    val colorScheme = ElegantDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
