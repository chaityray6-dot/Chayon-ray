package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val PlusJakartaSansFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val InterFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.SemiBold)
)

val JetBrainsMonoFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium)
)

val NotoSansBengaliFamily = FontFamily(
    Font(R.font.noto_sans_bengali, FontWeight.Normal),
    Font(R.font.noto_sans_bengali, FontWeight.Medium),
    Font(R.font.noto_sans_bengali, FontWeight.Bold)
)

fun docuVerseTypography(isBengali: Boolean = false): Typography {
    val display = if (isBengali) NotoSansBengaliFamily else PlusJakartaSansFamily
    val body = if (isBengali) NotoSansBengaliFamily else InterFamily
    return Typography(
        displaySmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = (-0.25).sp
        ),
        headlineSmall = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp
        ),
        titleLarge = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            lineHeight = 26.sp
        ),
        titleMedium = TextStyle(
            fontFamily = display,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 22.sp
        ),
        titleSmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        bodySmall = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        labelLarge = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            lineHeight = 20.sp
        ),
        labelMedium = TextStyle(
            fontFamily = body,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp
        ),
        labelSmall = TextStyle(
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp
        )
    )
}
