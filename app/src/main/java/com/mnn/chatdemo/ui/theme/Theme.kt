package com.mnn.chatdemo.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = BluePrimary,
    onPrimary = BlueOnPrimary,
    primaryContainer = BlueContainer,
    onPrimaryContainer = OnBlueContainer,
    surfaceVariant = GraySurfaceVariant,
    onSurfaceVariant = OnGraySurfaceVariant,
    background = Background,
    onBackground = OnBackground,
    surface = Surface,
    onSurface = OnSurface,
    error = ErrorColor,
    onError = OnErrorColor
)

private val DarkColorScheme = darkColorScheme(
    primary = BlueContainer,
    onPrimary = OnBlueContainer,
    primaryContainer = BluePrimary,
    onPrimaryContainer = BlueOnPrimary,
    error = ErrorColor,
    onError = OnErrorColor
)

@Composable
fun MnnChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content
    )
}
