package com.peykich.sofapanel.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale

@Composable
fun PanelRoot(vm: PanelViewModel) {
    val settings by vm.settings.collectAsState()
    val background by vm.background.collectAsState()
    // 戻るボタン：設定・編集からはメインへ。メインでは何もしない（常設端末で誤って閉じないように）
    BackHandler {
        when (vm.screen) {
            is Screen.Editor -> vm.closeEditor()
            is Screen.Settings -> vm.goMain()
            else -> Unit
        }
    }
    PanelTheme(Color(settings.accent.argb)) {
        val target: Any = when (vm.idle) {
            IdleMode.ACTIVE -> vm.screen
            else -> vm.idle
        }
        Box(Modifier.fillMaxSize().background(PanelColors.Bg)) {
            // 画面切替は 0.3 秒のフェードだけ
            Crossfade(targetState = target, animationSpec = tween(300), label = "screen") { t ->
                when (t) {
                    IdleMode.STANDBY -> StandbyScreen(vm)
                    IdleMode.NIGHT -> NightScreen()
                    is Screen.Editor -> Box(Modifier.windowInsetsPadding(WindowInsets.displayCutout)) { MacroEditorScreen(vm, t) }
                    is Screen.Settings -> Box(Modifier.windowInsetsPadding(WindowInsets.displayCutout)) { SettingsScreen(vm) }
                    else -> Box(Modifier.fillMaxSize()) {
                        BackgroundLayer(background, settings.backgroundDim)
                        CompositionLocalProvider(LocalSurfaceAlpha provides settings.panelOpacity / 100f) {
                            Box(Modifier.windowInsetsPadding(WindowInsets.displayCutout)) { MainScreen(vm) }
                        }
                    }
                }
            }
        }
    }
}

/** 背景画像と、文字を読みやすくするための黒い重ね（dimPercent が濃さ） */
@Composable
fun BackgroundLayer(image: ImageBitmap?, dimPercent: Int) {
    if (image == null) return
    Image(
        bitmap = image,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize(),
    )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dimPercent.coerceIn(0, 100) / 100f)))
}
