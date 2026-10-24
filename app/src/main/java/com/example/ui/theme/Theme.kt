package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private val LightColorScheme =
  lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = OnEmeraldPrimary,
    primaryContainer = EmeraldPrimaryContainer,
    onPrimaryContainer = OnEmeraldPrimaryContainer,
    secondary = HarvestAmberSecondary,
    onSecondary = OnHarvestAmberSecondary,
    secondaryContainer = HarvestAmberContainer,
    onSecondaryContainer = OnHarvestAmberContainer,
    tertiary = TerracottaTertiary,
    onTertiary = OnTerracottaTertiary,
    tertiaryContainer = TerracottaContainer,
    onTertiaryContainer = OnTerracottaContainer,
    background = SandBackgroundLight,
    onBackground = OnSandBackgroundLight,
    surface = PureSurfaceLight,
    onSurface = OnSandBackgroundLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
    error = CrimsonError,
    onError = Color.White,
    errorContainer = CrimsonErrorContainer,
    onErrorContainer = OnCrimsonErrorContainer,
  )

private val DarkColorScheme =
  darkColorScheme(
    primary = EmeraldPrimaryDark,
    onPrimary = OnEmeraldPrimaryDark,
    primaryContainer = EmeraldContainerDark,
    onPrimaryContainer = OnEmeraldContainerDark,
    secondary = HarvestAmberDark,
    onSecondary = OnHarvestAmberDark,
    secondaryContainer = HarvestContainerDark,
    onSecondaryContainer = OnHarvestContainerDark,
    tertiary = TerracottaDark,
    onTertiary = OnTerracottaDark,
    tertiaryContainer = TerracottaContainerDark,
    onTertiaryContainer = OnTerracottaContainerDark,
    background = ForestBackgroundDark,
    onBackground = OnForestBackgroundDark,
    surface = ForestSurfaceDark,
    onSurface = OnForestBackgroundDark,
    surfaceVariant = ForestSurfaceVariantDark,
    onSurfaceVariant = OnForestSurfaceVariantDark,
    outline = OutlineDark,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2),
  )

val AppShapes =
  Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  content: @Composable () -> Unit,
) {
  val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

  CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
    MaterialTheme(
      colorScheme = colorScheme,
      typography = AppTypography,
      shapes = AppShapes,
      content = content,
    )
  }
}
