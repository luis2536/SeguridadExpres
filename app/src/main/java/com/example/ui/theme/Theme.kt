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

private val DarkColorScheme =
  darkColorScheme(
    primary = NeonCyan,
    secondary = NeonBlue,
    tertiary = NeonGreen,
    background = BGDeep,
    surface = CardBg,
    onPrimary = BGDeep,
    onSecondary = LightSlate,
    onTertiary = BGDeep,
    onBackground = LightSlate,
    onSurface = LightSlate
  )

private val LightColorScheme = DarkColorScheme // Forced Dark theme for cybersecurity aesthetic

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = true, // Force dark theme
  dynamicColor: Boolean = false, // Disable dynamic colors for precise cyberpunk styling
  content: @Composable () -> Unit,
) {
  val colorScheme = DarkColorScheme

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
