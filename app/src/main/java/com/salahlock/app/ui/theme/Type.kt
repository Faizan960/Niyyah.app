package com.salahlock.app.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.salahlock.app.R

/** Serif display face used for all editorial/display text in the Figma frames. */
val EbGaramond = FontFamily(
    Font(R.font.eb_garamond_regular, FontWeight.Normal),
    Font(R.font.eb_garamond_medium, FontWeight.Medium),
    Font(R.font.eb_garamond_semibold, FontWeight.SemiBold),
    Font(R.font.eb_garamond_medium_italic, FontWeight.Medium, FontStyle.Italic),
)

/** Sans UI face used for labels, body and metadata in the Figma frames. */
val HankenGrotesk = FontFamily(
    Font(R.font.hanken_grotesk_regular, FontWeight.Normal),
    Font(R.font.hanken_grotesk_medium, FontWeight.Medium),
    Font(R.font.hanken_grotesk_semibold, FontWeight.SemiBold),
    Font(R.font.hanken_grotesk_bold, FontWeight.Bold),
)

/**
 * Named text styles measured from the Figma light frames.
 * Sizes/line-heights/tracking are exact Figma values (px treated as sp/dp at 1x).
 */
object NiyyahType {
    /** 48/56, -0.96 — screen greeting, hero prayer name */
    val DisplayLarge = TextStyle(
        fontFamily = EbGaramond,
        fontWeight = FontWeight.Medium,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        letterSpacing = (-0.96).sp,
    )

    /** 36/44, -0.9 — NIYYAH wordmark in top app bar */
    val Wordmark = TextStyle(
        fontFamily = EbGaramond,
        fontWeight = FontWeight.Medium,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = (-0.9).sp,
    )

    /** 32/40 — hero card time */
    val HeadingMedium = TextStyle(
        fontFamily = EbGaramond,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
        lineHeight = 40.sp,
    )

    /** 24/39 — editorial quote / card headline */
    val Quote = TextStyle(
        fontFamily = EbGaramond,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 39.sp,
    )

    /** 14/20, +0.7, SemiBold — uppercase section labels, chips, prayer names */
    val LabelUppercase = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.7.sp,
    )

    /** 14/20, +1.4, SemiBold — wide-tracked date line */
    val LabelUppercaseWide = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 1.4.sp,
    )

    /** 16/24, Regular — body copy, times, metadata */
    val Body = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /** 16/24, Medium — emphasized body (active timeline time) */
    val BodyMedium = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    /** 12/16, Medium — small badges (NEXT PRAYER) */
    val Badge = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    /** 14/20, +0.7, SemiBold — button labels */
    val ButtonLabel = TextStyle(
        fontFamily = HankenGrotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.7.sp,
    )
}
