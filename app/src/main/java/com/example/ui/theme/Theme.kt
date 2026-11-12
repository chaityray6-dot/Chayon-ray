package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DocuVerseLightColorScheme = lightColorScheme(
    primary = BrandRoyalBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = BrandSapphire,
    secondary = BrandIndigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0E7FF),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = BrandViolet,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF2E1065),
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightOutline,
    error = ErrorRose,
    onError = Color.White
)

private val DocuVerseDarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),
    onPrimary = Color(0xFF0A193A),
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF818CF8),
    onSecondary = Color(0xFF131144),
    secondaryContainer = Color(0xFF312E81),
    onSecondaryContainer = Color(0xFFE0E7FF),
    tertiary = Color(0xFFA78BFA),
    onTertiary = Color(0xFF220B4D),
    tertiaryContainer = Color(0xFF4C1D95),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkOutline,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

val DocuVerseShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

@Composable
fun DocuVerseTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    isBengali: Boolean = false,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DocuVerseDarkColorScheme
        else -> DocuVerseLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = docuVerseTypography(isBengali),
        shapes = DocuVerseShapes,
        content = content
    )
}
