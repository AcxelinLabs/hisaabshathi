package com.acxelin.hisaabsaathi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.acxelin.hisaabsaathi.R
import com.acxelin.hisaabsaathi.core.Lang

/*
 * Look: a wage slip ("parchi") written in blue ballpoint ink. Ink blue carries structure
 * and text, marigold is reserved for the one thing to press (the mic), and red/green are
 * used only for money owed / money earned so colour always means something.
 */
val Ink = Color(0xFF1B2A6B)
val InkSoft = Color(0xFF5A6390)
val Chalk = Color(0xFFEEF0F6)
val Paper = Color(0xFFFFFFFF)
val Marigold = Color(0xFFF2A900)
val Sindoor = Color(0xFFD2302C)
val Leaf = Color(0xFF1F7A4D)
val Rule = Color(0xFFC9CEE0)

// Names kept for the semantic roles used across the screens.
val Green = Leaf
val Red = Sindoor
val Amber = Marigold
val GreenLight = Color(0xFFE3F1EA)

val AppColors = lightColorScheme(
    primary = Ink, onPrimary = Color.White, primaryContainer = Color(0xFFDDE1F2), onPrimaryContainer = Ink,
    secondary = Marigold, onSecondary = Ink, secondaryContainer = Marigold, onSecondaryContainer = Ink,
    error = Sindoor, background = Chalk, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFE3E6F0), onSurfaceVariant = InkSoft, outline = Rule,
)

/** Anek is one type family drawn for every script the app speaks, so all six languages look alike. */
private fun anek(regular: Int, semi: Int, extra: Int) = FontFamily(
    Font(regular, FontWeight.Normal), Font(semi, FontWeight.Medium),
    Font(semi, FontWeight.SemiBold), Font(extra, FontWeight.Bold), Font(extra, FontWeight.ExtraBold),
)

fun fontFor(lang: Lang): FontFamily = when (lang) {
    Lang.HI -> anek(R.font.anek_devanagari_400, R.font.anek_devanagari_600, R.font.anek_devanagari_800)
    Lang.OR -> anek(R.font.anek_odia_400, R.font.anek_odia_600, R.font.anek_odia_800)
    Lang.TE -> anek(R.font.anek_telugu_400, R.font.anek_telugu_600, R.font.anek_telugu_800)
    Lang.KN -> anek(R.font.anek_kannada_400, R.font.anek_kannada_600, R.font.anek_kannada_800)
    Lang.TA -> anek(R.font.anek_tamil_400, R.font.anek_tamil_600, R.font.anek_tamil_800)
    Lang.EN -> anek(R.font.anek_latin_400, R.font.anek_latin_600, R.font.anek_latin_800)
}

fun typographyFor(lang: Lang): Typography {
    val f = fontFor(lang)
    val base = Typography()
    fun TextStyle.with() = copy(fontFamily = f, color = Ink)
    return Typography(
        displayLarge = base.displayLarge.with(), displayMedium = base.displayMedium.with(), displaySmall = base.displaySmall.with(),
        headlineLarge = base.headlineLarge.with(), headlineMedium = base.headlineMedium.with(), headlineSmall = base.headlineSmall.with(),
        titleLarge = base.titleLarge.with(), titleMedium = base.titleMedium.with(), titleSmall = base.titleSmall.with(),
        bodyLarge = base.bodyLarge.with().copy(fontSize = 17.sp, lineHeight = 26.sp), bodyMedium = base.bodyMedium.with(), bodySmall = base.bodySmall.with(),
        labelLarge = base.labelLarge.with().copy(fontSize = 16.sp), labelMedium = base.labelMedium.with().copy(fontSize = 13.sp), labelSmall = base.labelSmall.with(),
    )
}

@Composable
fun HisaabTheme(lang: Lang, content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = AppColors, typography = typographyFor(lang), content = content)

/** A paper slip torn off a pad: square top, zig-zag bottom. */
private fun tornEdge(tooth: Float) = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height - tooth)
    val teeth = maxOf(1, (size.width / (tooth * 2)).toInt())
    val w = size.width / teeth
    for (i in 0 until teeth) {
        val right = size.width - i * w
        lineTo(right - w / 2, size.height)
        lineTo(right - w, size.height - tooth)
    }
    close()
}

@Composable
fun Slip(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(tornEdge(14f)).background(Paper).padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 24.dp),
    ) { content() }
}

/** "Label ........ ₹600": the dotted leader of a hand-written bill. */
@Composable
fun LeaderRow(label: String, value: String, valueColor: Color = Ink, big: Boolean = false, top: Dp = 6.dp) {
    Row(Modifier.fillMaxWidth().padding(top = top), verticalAlignment = Alignment.Bottom) {
        Text(label, fontSize = if (big) 19.sp else 17.sp, fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal)
        Canvas(Modifier.weight(1f).height(if (big) 12.dp else 8.dp).padding(horizontal = 8.dp)) {
            drawLine(
                Rule, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 9f)),
            )
        }
        Text(value, fontSize = if (big) 32.sp else 20.sp, fontWeight = FontWeight.ExtraBold, color = valueColor)
    }
}

@Composable
fun DashedRule(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp).height(2.dp)) {
        drawLine(Ink, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)))
    }
}
