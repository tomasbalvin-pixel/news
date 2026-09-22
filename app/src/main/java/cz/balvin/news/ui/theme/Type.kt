package cz.balvin.news.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val base = Typography()

val ZpravyTypography = base.copy(
    // Headlines run tighter than the Material default; a news list is read in
    // scans, not sentences.
    headlineSmall = base.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp,
    ),
    titleMedium = base.titleMedium.copy(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    ),
    bodyMedium = base.bodyMedium.copy(
        lineHeight = 21.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
    ),
)
