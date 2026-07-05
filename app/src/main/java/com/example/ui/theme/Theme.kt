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
    primary = HighDensityDarkPrimary,
    onPrimary = HighDensityDarkOnPrimary,
    primaryContainer = HighDensityDarkPrimaryContainer,
    onPrimaryContainer = HighDensityDarkOnPrimaryContainer,
    secondary = HighDensityDarkSecondary,
    onSecondary = HighDensityDarkOnSecondary,
    background = HighDensityDarkBackground,
    onBackground = HighDensityDarkOnBackground,
    surface = HighDensityDarkSurface,
    onSurface = HighDensityDarkOnSurface,
    surfaceVariant = HighDensityDarkSurfaceVariant,
    onSurfaceVariant = HighDensityDarkOnSurfaceVariant,
    outline = HighDensityDarkOutline
  )

private val LightColorScheme =
  lightColorScheme(
    primary = HighDensityPrimary,
    onPrimary = HighDensityOnPrimary,
    primaryContainer = HighDensityPrimaryContainer,
    onPrimaryContainer = HighDensityOnPrimaryContainer,
    secondary = HighDensitySecondary,
    onSecondary = HighDensityOnSecondary,
    secondaryContainer = HighDensitySecondaryContainer,
    onSecondaryContainer = HighDensityOnSecondaryContainer,
    background = HighDensityBackground,
    onBackground = HighDensityOnBackground,
    surface = HighDensitySurface,
    onSurface = HighDensityOnSurface,
    surfaceVariant = HighDensitySurfaceVariant,
    onSurfaceVariant = HighDensityOnSurfaceVariant,
    outline = HighDensityOutline,
    outlineVariant = HighDensityOutlineVariant
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // Disabling dynamic colors by default so our gorgeous High Density branding matches the specifications perfectly
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
