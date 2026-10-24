@file:OptIn(ExperimentalTextApi::class)

package com.peykich.sofapanel.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.peykich.sofapanel.R

/** 「ホテルの客室パネル」のような暗く静かな配色。アクセントは1色だけ */
object PanelColors {
    val Bg = Color(0xFF0E0F11)
    val StandbyBg = Color(0xFF08090A)
    val Card = Color(0xFF16181B)
    val Border = Color(0xFF26292E)
    val Button = Color(0xFF1F2226)
    val Text = Color(0xFFECE7DF)
    val SubText = Color(0xFF8E8A83)
    val Error = Color(0xFFD9826B)
}

val LocalAccent = staticCompositionLocalOf { Color(0xFFE0A458) }

private fun outfit(weight: Int) = Font(
    resId = R.font.outfit,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** 数字用フォント Outfit（可変フォント。時計は極細、その他は細字） */
val OutfitFamily = FontFamily(outfit(100), outfit(200), outfit(300), outfit(400), outfit(500))

/** 日本語は端末標準のゴシック体（Android の日本語フォントは Noto Sans CJK = Noto Sans JP と同じ字形） */
val JpFamily: FontFamily = FontFamily.SansSerif

@Composable
fun PanelTheme(accent: Color, content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = accent,
        onPrimary = PanelColors.Bg,
        secondary = accent,
        background = PanelColors.Bg,
        onBackground = PanelColors.Text,
        surface = PanelColors.Card,
        onSurface = PanelColors.Text,
        surfaceVariant = PanelColors.Button,
        onSurfaceVariant = PanelColors.SubText,
        surfaceContainer = PanelColors.Card,
        surfaceContainerHigh = PanelColors.Button,
        surfaceContainerHighest = PanelColors.Button,
        surfaceContainerLow = PanelColors.Card,
        outline = PanelColors.Border,
        outlineVariant = PanelColors.Border,
        error = PanelColors.Error,
    )
    CompositionLocalProvider(LocalAccent provides accent) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
