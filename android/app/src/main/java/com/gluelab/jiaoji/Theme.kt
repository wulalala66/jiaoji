package com.gluelab.jiaoji

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Brand = Color(0xFF0E7263)
val BrandDark = Color(0xFF0A5348)
val BrandSoft = Color(0xFFDCEDE7)
val Bg = Color(0xFFF2F4F1)
val CardBg = Color(0xFFFFFFFF)
val Ink = Color(0xFF16211E)
val Ink2 = Color(0xFF5A6663)
val Ink3 = Color(0xFF8A9693)
val Line = Color(0xFFE4E9E5)
val SoftBg = Color(0xFFEFF3EF)
val Danger = Color(0xFFBD4632)
val DangerSoft = Color(0xFFFBE9E5)
val Warn = Color(0xFF9B6412)
val WarnSoft = Color(0xFFFBF1DD)
val Ok = Color(0xFF2C7A5B)
val OkSoft = Color(0xFFE2F0E9)
val OvenDoor = Color(0xFF3A618B)
val OvenDoorSoft = Color(0xFFE6EDF5)
val OvenWindow = Color(0xFF9B6412)
val OvenWindowSoft = Color(0xFFFBF0DC)

/**
 * 烘箱配色：新增烘箱时按 colorIdx 轮流取色，保证每个烘箱一眼能分清。
 * 靠门=蓝、靠窗=琥珀，其余是紫 / 青 / 玫红 / 橄榄。
 */
private val ovenAccents = listOf(
    OvenDoor, OvenWindow,
    Color(0xFF6A4B8C), Color(0xFF2F7A6B), Color(0xFFA24A6A), Color(0xFF57702F),
)

fun ovenAccent(colorIdx: Int): Color {
    val i = ((colorIdx % ovenAccents.size) + ovenAccents.size) % ovenAccents.size
    return ovenAccents[i]
}

fun ovenSoft(colorIdx: Int): Color = ovenAccent(colorIdx).copy(alpha = 0.13f)

private val scheme = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = BrandSoft,
    onPrimaryContainer = BrandDark,
    secondary = OvenDoor,
    onSecondary = Color.White,
    background = Bg,
    onBackground = Ink,
    surface = CardBg,
    onSurface = Ink,
    surfaceVariant = SoftBg,
    onSurfaceVariant = Ink2,
    outline = Line,
    outlineVariant = Line,
    error = Danger,
    onError = Color.White,
    errorContainer = DangerSoft,
    onErrorContainer = Danger,
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val typography = Typography(
    displaySmall = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    headlineSmall = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    titleSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp),
)

@Composable
fun JiaoJiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
