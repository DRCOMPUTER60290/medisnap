package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = PolishDarkPrimary,
    onPrimary = PolishDarkOnPrimary,
    primaryContainer = PolishDarkPrimaryContainer,
    onPrimaryContainer = PolishDarkOnPrimaryContainer,
    secondary = PolishSecondary,
    background = PolishDarkBackground,
    onBackground = PolishDarkOnBackground,
    surface = PolishDarkSurface,
    onSurface = PolishDarkOnSurface,
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6CF),
    error = PolishError,
    errorContainer = PolishErrorContainer,
    onErrorContainer = PolishOnErrorContainer
  )

private val LightColorScheme =
  lightColorScheme(
    primary = PolishPrimary,
    onPrimary = PolishOnPrimary,
    primaryContainer = PolishPrimaryContainer,
    onPrimaryContainer = PolishOnPrimaryContainer,
    secondary = PolishSecondary,
    secondaryContainer = PolishSecondaryContainer,
    onSecondaryContainer = PolishOnSecondaryContainer,
    tertiary = PolishTertiary,
    tertiaryContainer = PolishTertiaryContainer,
    onTertiaryContainer = PolishOnTertiaryContainer,
    background = PolishBackground,
    onBackground = PolishOnBackground,
    surface = PolishSurface,
    onSurface = PolishOnSurface,
    surfaceVariant = PolishSurfaceVariant,
    onSurfaceVariant = PolishOnSurfaceVariant,
    outline = PolishOutline,
    error = PolishError,
    errorContainer = PolishErrorContainer,
    onErrorContainer = PolishOnErrorContainer
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disable dynamic color so our custom Professional Polish theme is preserved
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
