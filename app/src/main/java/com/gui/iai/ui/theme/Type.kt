package com.gui.iai.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.gui.iai.R

/** Yuji Boku: caligrafia de pincel (títulos e kanji). Licença OFL. */
val BrushFont = FontFamily(Font(R.font.yuji_boku, FontWeight.Normal))

/** Shippori Mincho B1: serifada japonesa para textos. Licença OFL. */
val SerifFont = FontFamily(
    Font(R.font.shippori_regular, FontWeight.Normal),
    Font(R.font.shippori_bold, FontWeight.Bold)
)

val IaiTypography = Typography(
    displayLarge = TextStyle(fontFamily = BrushFont, fontSize = 72.sp, lineHeight = 80.sp),
    displayMedium = TextStyle(fontFamily = BrushFont, fontSize = 52.sp, lineHeight = 60.sp),
    displaySmall = TextStyle(fontFamily = BrushFont, fontSize = 40.sp, lineHeight = 48.sp),
    headlineLarge = TextStyle(fontFamily = BrushFont, fontSize = 34.sp, lineHeight = 42.sp),
    headlineMedium = TextStyle(fontFamily = BrushFont, fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall = TextStyle(fontFamily = BrushFont, fontSize = 24.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = SerifFont, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(
        fontFamily = SerifFont,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = 1.sp
    ),
    titleSmall = TextStyle(fontFamily = SerifFont, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = SerifFont, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = SerifFont, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = SerifFont, fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(
        fontFamily = SerifFont,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        letterSpacing = 2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = SerifFont,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        letterSpacing = 2.sp
    ),
    labelSmall = TextStyle(
        fontFamily = SerifFont,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        letterSpacing = 1.5.sp
    )
)
