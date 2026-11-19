package com.acxelin.hisaabsaathi.recorder

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.acxelin.hisaabsaathi.core.Lang

/* The same look as HisaabSaathi (app/.../ui/Theme.kt): ledger green, marigold for the one button to press. */
val Ink = Color(0xFF14432A)          // deep ledger green: bars, headings, body text
val InkSoft = Color(0xFF4F6B5A)      // secondary text, 5.6:1 on Paper
val Chalk = Color(0xFFEFF4EE)        // screen background, a faint green-grey
val Paper = Color(0xFFFFFFFF)
val Marigold = Color(0xFFF2A900)
val Sindoor = Color(0xFFC62828)
val Leaf = Color(0xFF1E7B3C)         // money earned
val Rule = Color(0xFFC5D3C8)
val InkWash = Color(0xFFDCEADF)      // quoted speech, quiet surfaces
val InkRaised = Color(0xFF235C3B)    // a control sitting on the green background
val Notice = Color(0xFFFFF4D1)       // spoken answers and things to double-check
val NoticeInk = Color(0xFF6B5200)    // text on Notice; 7:1 contrast

// Names kept for the semantic roles used across the screens.
val Green = Leaf
val Red = Sindoor
val Amber = Marigold
val GreenLight = InkWash

val AppColors = lightColorScheme(
    primary = Ink, onPrimary = Color.White, primaryContainer = InkWash, onPrimaryContainer = Ink,
    secondary = Marigold, onSecondary = Ink, secondaryContainer = Marigold, onSecondaryContainer = Ink,
    error = Sindoor, background = Chalk, onBackground = Ink, surface = Paper, onSurface = Ink,
    surfaceVariant = Color(0xFFE2ECE4), onSurfaceVariant = InkSoft, outline = Rule,
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
    // No colour here: text takes the colour of what it sits on (white on buttons, ink on paper).
    fun TextStyle.with() = copy(fontFamily = f)
    return Typography(
        displayLarge = base.displayLarge.with(), displayMedium = base.displayMedium.with(), displaySmall = base.displaySmall.with(),
        headlineLarge = base.headlineLarge.with(), headlineMedium = base.headlineMedium.with(), headlineSmall = base.headlineSmall.with(),
        titleLarge = base.titleLarge.with(), titleMedium = base.titleMedium.with(), titleSmall = base.titleSmall.with(),
        bodyLarge = base.bodyLarge.with().copy(fontSize = 17.sp, lineHeight = 26.sp), bodyMedium = base.bodyMedium.with(), bodySmall = base.bodySmall.with(),
        labelLarge = base.labelLarge.with().copy(fontSize = 16.sp), labelMedium = base.labelMedium.with().copy(fontSize = 13.sp), labelSmall = base.labelSmall.with(),
    )
}

@Composable
fun RecorderTheme(lang: Lang, content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = AppColors, typography = typographyFor(lang), content = content)

