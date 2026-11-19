package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PranavDarkScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = OnElectricCyan,
    primaryContainer = ElectricCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = AmberGold,
    onSecondary = OnAmberGold,
    secondaryContainer = AmberGoldContainer,
    onSecondaryContainer = AmberGold,
    tertiary = EmeraldOk,
    onTertiary = Color(0xFF022016),
    tertiaryContainer = EmeraldContainer,
    onTertiaryContainer = EmeraldOk,
    error = CrimsonAlert,
    onError = Color.White,
    errorContainer = CrimsonContainer,
    onErrorContainer = CrimsonAlert,
    background = ObsidianBg,
    onBackground = SlateText,
    surface = ObsidianSurface,
    onSurface = SlateText,
    surfaceVariant = ObsidianSurfaceVariant,
    onSurfaceVariant = SlateMuted,
    surfaceContainer = ObsidianSurfaceVariant,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    outline = SlateOutline
)

private val PranavLightScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0F4FA),
    onPrimaryContainer = Color(0xFF002026),
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFEBC2),
    onSecondaryContainer = Color(0xFF2B1A00),
    tertiary = EmeraldOk,
    onTertiary = Color.White,
    error = Color(0xFFC81E3D),
    onError = Color.White,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF475569),
    outline = LightOutline
)

val PranavShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun PranavTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PranavDarkScheme else PranavLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PranavShapes,
        content = content
    )
}
