package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Brand Core Palette
val BrandRoyalBlue = Color(0xFF2563EB)
val BrandIndigo = Color(0xFF4F46E5)
val BrandViolet = Color(0xFF7C3AED)
val BrandCyan = Color(0xFF06B6D4)
val BrandSapphire = Color(0xFF1E3A8A)

// Light Theme Surfaces & Text
val LightBackground = Color(0xFFF6F8FC)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceVariant = Color(0xFFEEF2F8)
val LightOutline = Color(0xFFD5DCE8)
val LightTextPrimary = Color(0xFF0F172A)
val LightTextSecondary = Color(0xFF475569)

// Dark Theme Surfaces & Text
val DarkBackground = Color(0xFF0A0F1E)
val DarkSurface = Color(0xFF11182C)
val DarkSurfaceVariant = Color(0xFF1B2541)
val DarkOutline = Color(0xFF2B395E)
val DarkTextPrimary = Color(0xFFF1F5F9)
val DarkTextSecondary = Color(0xFF94A3B8)

// Semantic Status
val SuccessEmerald = Color(0xFF10B981)
val WarningAmber = Color(0xFFF59E0B)
val ErrorRose = Color(0xFFEF4444)

// Reading Comfort Backgrounds
val ReaderSepiaBg = Color(0xFFF4ECD8)
val ReaderSepiaText = Color(0xFF3E2F1C)
val ReaderMintBg = Color(0xFFE9F5EC)
val ReaderMintText = Color(0xFF14311E)
val ReaderDarkBg = Color(0xFF121622)
val ReaderDarkText = Color(0xFFE2E8F0)

val PrimaryBrandGradient = Brush.linearGradient(
    colors = listOf(BrandRoyalBlue, BrandIndigo, BrandViolet)
)

val AccentCyanGradient = Brush.linearGradient(
    colors = listOf(BrandCyan, BrandRoyalBlue)
)
