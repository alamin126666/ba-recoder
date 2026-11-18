package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.domain.model.ThemeMode

private val LightColorScheme = lightColorScheme(
    primary = EmeraldPrimary,
    onPrimary = Color.White,
    primaryContainer = SoftGreenContainer,
    onPrimaryContainer = EmeraldDeep,
    secondary = EmeraldDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD7F9E3),
    onSecondaryContainer = EmeraldDeep,
    tertiary = MintAccent,
    onTertiary = EmeraldDeep,
    background = MintTintBackground,
    onBackground = OnLightBackground,
    surface = PureWhiteSurface,
    onSurface = OnLightBackground,
    surfaceVariant = SoftGreenContainer,
    onSurfaceVariant = OnLightSecondary,
    outline = LightOutline,
    error = RecordRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkEmeraldPrimary,
    onPrimary = EmeraldDeep,
    primaryContainer = DarkGreenContainer,
    onPrimaryContainer = MintAccent,
    secondary = MintAccent,
    onSecondary = EmeraldDeep,
    secondaryContainer = DarkForestSurfaceHigh,
    onSecondaryContainer = OnDarkBackground,
    tertiary = EmeraldBright,
    onTertiary = EmeraldDeep,
    background = DarkForestBackground,
    onBackground = OnDarkBackground,
    surface = DarkForestSurface,
    onSurface = OnDarkBackground,
    surfaceVariant = DarkForestSurfaceHigh,
    onSurfaceVariant = OnDarkSecondary,
    outline = DarkOutline,
    error = RecordRed,
    onError = Color.White
)

val BAShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun BAScreenRecordTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = BAScreenRecordTypography,
        shapes = BAShapes,
        content = content
    )
}
