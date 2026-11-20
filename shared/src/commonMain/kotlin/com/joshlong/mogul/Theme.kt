package com.joshlong.mogul

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import clip_kmp.shared.generated.resources.Res
import clip_kmp.shared.generated.resources.ibm_plex_mono_400
import clip_kmp.shared.generated.resources.ibm_plex_sans_400
import clip_kmp.shared.generated.resources.ibm_plex_sans_600
import clip_kmp.shared.generated.resources.space_grotesk_700
import org.jetbrains.compose.resources.Font

// the studio's look (client/src/assets/base.css, the day palette), as in the clip extension's popup.css
@Immutable
object Palette {
    val bg = Color(0xFF9CCBE8)
    val chrome = Color(0xFF132A52)
    val panelBg = Color(0xFFF3F7FB)
    val chromeInk = Color(0xFFF2F6FC)
    val ink = Color(0xFF0A0F1A)
    val muted = Color(0xFF405069)
    val label = Color(0xFF1B2840)
    val fieldBg = Color(0xFFFFFFFF)
    val fieldBorder = Color(0xFF74889F)
    val accent = Color(0xFF1F4F9C)
    val accentHover = Color(0xFF1F3D70)
    val accentInk = Color(0xFFFFFFFF)
    val disabled = Color(0xFF8FA2BD)
    val danger = Color(0xFFC0392B)
    val topbar = Color(0xFF0A0F1A)
    val brand = Color(0xFF9CCBE8)
}

object Space {
    val gutter = 16.dp // 12pt
    val gutterHalf = 8.dp
    val radius = 7.5.dp // 0.5em of the 15px body
}

@Immutable
data class Fonts(val display: FontFamily, val body: FontFamily, val mono: FontFamily)

val LocalFonts = staticCompositionLocalOf<Fonts> { error("no fonts: wrap the content in MogulTheme") }

@Immutable
class Type(fonts: Fonts) {
    val body = TextStyle(fontFamily = fonts.body, fontSize = 15.sp, color = Palette.ink)
    val label = TextStyle(fontFamily = fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 13.6.sp, color = Palette.label)
    val small = TextStyle(fontFamily = fonts.body, fontSize = 12.8.sp, color = Palette.muted)
    val mono = TextStyle(fontFamily = fonts.mono, fontSize = 12.8.sp, color = Palette.ink)
    val panelHeader = TextStyle(
        fontFamily = fonts.display, fontWeight = FontWeight.Bold, fontSize = 15.sp,
        letterSpacing = (-0.01).em, color = Palette.chromeInk,
    )
    val brandName = TextStyle(
        fontFamily = fonts.display, fontWeight = FontWeight.Bold, fontSize = 25.6.sp,
        lineHeight = 25.6.sp, letterSpacing = (-0.05).em, color = Palette.chromeInk,
    )
    val brandProduct = TextStyle(fontFamily = fonts.mono, fontSize = 10.4.sp, letterSpacing = 0.24.em, color = Palette.brand)
    val user = TextStyle(fontFamily = fonts.body, fontSize = 13.6.sp, color = Palette.brand)
    val savedTitle = TextStyle(fontFamily = fonts.display, fontWeight = FontWeight.Bold, fontSize = 17.25.sp, color = Palette.ink)
    val error = TextStyle(fontFamily = fonts.body, fontSize = 12.75.sp, color = Palette.danger)
    val button = TextStyle(fontFamily = fonts.body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Palette.accentInk)
}

val LocalType = staticCompositionLocalOf<Type> { error("no type: wrap the content in MogulTheme") }

@Composable
fun MogulTheme(content: @Composable () -> Unit) {
    val fonts = Fonts(
        display = FontFamily(Font(Res.font.space_grotesk_700, FontWeight.Bold)),
        body = FontFamily(Font(Res.font.ibm_plex_sans_400, FontWeight.Normal), Font(Res.font.ibm_plex_sans_600, FontWeight.SemiBold)),
        mono = FontFamily(Font(Res.font.ibm_plex_mono_400, FontWeight.Normal)),
    )
    // material only shows through in the dropdown menus and the text selection handles
    val colors = lightColorScheme(
        primary = Palette.accent,
        onPrimary = Palette.accentInk,
        surface = Palette.fieldBg,
        surfaceContainer = Palette.fieldBg,
        onSurface = Palette.ink,
        background = Palette.bg,
    )
    MaterialTheme(colorScheme = colors) {
        CompositionLocalProvider(LocalFonts provides fonts, LocalType provides Type(fonts)) {
            content()
        }
    }
}
