package com.peykich.sofapanel.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peykich.sofapanel.api.Climate
import com.peykich.sofapanel.data.CardKind
import com.peykich.sofapanel.data.PanelConfig
import com.peykich.sofapanel.data.StatusMessage
import com.peykich.sofapanel.domain.CommandCatalog
import com.peykich.sofapanel.domain.MacroRules
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun MainScreen(vm: PanelViewModel) {
    val settings by vm.settings.collectAsState()
    val now by vm.now.collectAsState()
    val status by vm.repo.status.collectAsState()
    val climate by vm.repo.climate.collectAsState()
    val online by vm.repo.online.collectAsState()
    val run by vm.runner.state.collectAsState()
    val macros by vm.macros.collectAsState()
    val lists by vm.lists.collectAsState()
    val lastRef by vm.repo.lastRunRef.collectAsState()
    val chargeWarning by vm.charge.warning.collectAsState()
    val lowBattery by vm.charge.lowBattery.collectAsState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TopBar(
            roomName = settings.roomName,
            now = now,
            status = status,
            climate = climate,
            online = online,
            warning = chargeWarning ?: if (lowBattery) "電池残量が15%未満です（プラグの通信を確認）" else null,
            onOpenSettings = vm::openSettings,
        )
        Row(Modifier.weight(1.2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            settings.cards.forEachIndexed { i, slot ->
                val macro = vm.cardMacro(slot)
                val sceneName = if (slot.kind == CardKind.SCENE) lists.scenes.firstOrNull { it.id == slot.refId }?.name ?: slot.name else null
                val running = macro != null && run?.macroId == macro.id
                val title = when (slot.kind) {
                    CardKind.NONE -> "未割り当て"
                    CardKind.SCENE -> sceneName.orEmpty()
                    CardKind.MACRO -> macro?.name ?: slot.name
                }
                val subtitle = when {
                    running -> run?.let { "${it.index}/${it.total}・残り${MacroRules.formatSeconds(it.remainingSeconds)}" }.orEmpty()
                    slot.kind == CardKind.SCENE -> "シーン"
                    macro != null -> {
                        val total = MacroRules.totalSeconds(macro, macros)
                        if (total > 0) "マクロ・約${MacroRules.formatSeconds(total)}" else "マクロ"
                    }
                    slot.kind == CardKind.NONE -> "設定で割り当て"
                    else -> "見つかりません"
                }
                RunCard(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    index = i,
                    vm = vm,
                    title = title,
                    subtitle = subtitle,
                    icon = when (slot.kind) {
                        CardKind.MACRO -> PanelIcons.byName(macro?.icon ?: "play")
                        CardKind.SCENE -> PanelIcons.Scene
                        CardKind.NONE -> PanelIcons.Plus
                    },
                    active = running || (slot.kind != CardKind.NONE && slot.refId == lastRef),
                    running = running,
                    dimmed = !online || slot.kind == CardKind.NONE,
                )
            }
        }
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            settings.panels.forEach { panel ->
                ControlPanel(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    panel = panel,
                    dimmed = !online,
                    onSend = { vm.sendPanel(panel, it) },
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    roomName: String,
    now: Long,
    status: StatusMessage?,
    climate: Climate?,
    online: Boolean,
    warning: String?,
    onOpenSettings: () -> Unit,
) {
    val accent = LocalAccent.current
    var holding by remember { mutableStateOf(false) }
    var showDetails by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
        // 部屋名を2秒長押しで設定画面へ（誤操作防止）
        Text(
            roomName.ifBlank { "部屋" },
            color = if (holding) accent else PanelColors.Text,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .widthIn(max = 200.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onPress = {
                        holding = true
                        val released = withTimeoutOrNull(2000) { tryAwaitRelease() }
                        holding = false
                        if (released == null) onOpenSettings()
                    })
                },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(20.dp))
        Text(
            SimpleDateFormat("H:mm", Locale.JAPAN).format(Date(now)),
            color = PanelColors.Text,
            fontFamily = OutfitFamily,
            fontWeight = FontWeight.Light,
            fontSize = 24.sp,
        )
        Spacer(Modifier.width(20.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            if (!online) {
                Chip(PanelIcons.Offline, "オフライン", PanelColors.Error)
                Spacer(Modifier.width(12.dp))
            }
            if (warning != null) {
                Chip(PanelIcons.Warning, warning, PanelColors.Error, Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(12.dp))
            }
            if (status != null) {
                Text(
                    status.text + if (status.details.isNotEmpty()) "（タップで内訳）" else "",
                    color = if (status.error) PanelColors.Error else PanelColors.SubText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(enabled = status.details.isNotEmpty()) { showDetails = true },
                )
            }
        }
        ClimateView(climate, 18)
    }
    if (showDetails && status != null) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text("閉じる") } },
            title = { Text(status.text) },
            text = { Column { status.details.forEach { Text("・$it", color = PanelColors.Text, fontSize = 14.sp) } } },
            containerColor = PanelColors.Card,
        )
    }
}

@Composable
private fun Chip(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = color, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ClimateView(climate: Climate?, sizeSp: Int) {
    if (climate == null) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        climate.temperature?.let {
            Icon(PanelIcons.Thermo, "温度", tint = PanelColors.SubText, modifier = Modifier.size((sizeSp + 2).dp))
            Text(
                String.format(Locale.US, "%.1f°", it),
                color = PanelColors.Text, fontFamily = OutfitFamily, fontWeight = FontWeight.Light, fontSize = sizeSp.sp,
            )
            Spacer(Modifier.width(12.dp))
        }
        climate.humidity?.let {
            Icon(PanelIcons.Drop, "湿度", tint = PanelColors.SubText, modifier = Modifier.size((sizeSp + 2).dp))
            Text("$it%", color = PanelColors.Text, fontFamily = OutfitFamily, fontWeight = FontWeight.Light, fontSize = sizeSp.sp)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RunCard(
    modifier: Modifier,
    index: Int,
    vm: PanelViewModel,
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
    running: Boolean,
    dimmed: Boolean,
) {
    val accent = LocalAccent.current
    val view = LocalView.current
    val shakeX = remember { Animatable(0f) }
    LaunchedEffect(index) {
        vm.shake.collect { i ->
            if (i == index) {
                // 失敗時は小さく左右に揺らす
                for (x in listOf(-10f, 10f, -7f, 7f, -3f, 0f)) shakeX.animateTo(x, tween(45))
            }
        }
    }
    Box(
        modifier
            .graphicsLayer {
                translationX = shakeX.value
                alpha = if (dimmed) 0.4f else 1f
            }
            .clip(CardShape)
            .background(PanelColors.Card)
            .border(1.dp, if (active) accent else PanelColors.Border, CardShape)
            // 触れた位置から波紋が広がる（Material の ripple）
            .combinedClickable(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    vm.onCardTap(index)
                },
                onLongClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    vm.onCardLongPress(index)
                },
            )
            .padding(20.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = if (active) accent else PanelColors.Text, modifier = Modifier.size(34.dp))
            Column {
                Text(
                    title,
                    color = PanelColors.Text,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 26.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    if (running) "$subtitle（タップで中断）" else subtitle,
                    color = if (running) accent else PanelColors.SubText,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ControlPanel(modifier: Modifier, panel: PanelConfig, dimmed: Boolean, onSend: (com.peykich.sofapanel.api.Command) -> Unit) {
    PanelCard(modifier.graphicsLayer { alpha = if (dimmed) 0.4f else 1f }) {
        val title = panel.title.ifBlank { panel.deviceName }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title.ifBlank { "個別操作" },
                color = PanelColors.SubText,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.weight(1f))
        if (panel.deviceId.isEmpty()) {
            Text("設定画面で機器を割り当て", color = PanelColors.SubText.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.weight(1f))
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (panel.dimmer) {
                    // 調光：オフ・明るさバー・オン
                    val off = panel.buttons.firstOrNull { it.command == "turnOff" }
                    val rest = panel.buttons.filter { it !== off }
                    off?.let { PanelButton("", Modifier.width(52.dp), icon = PanelIcons.Moon) { onSend(it) } }
                    DimmerBar(Modifier.weight(1f)) { onSend(CommandCatalog.brightness(it)) }
                    rest.take(2).forEach { c ->
                        val isOn = c.command == "turnOn"
                        PanelButton(if (isOn) "" else c.label, Modifier.width(52.dp), icon = if (isOn) PanelIcons.Sun else commandIcon(c)) { onSend(c) }
                    }
                } else {
                    panel.buttons.take(4).forEach { c ->
                        val icon = commandIcon(c)
                        PanelButton(if (icon != null) "" else c.label, Modifier.weight(1f), icon = icon) { onSend(c) }
                    }
                }
            }
        }
    }
}

/** 左右にドラッグ（またはタップ）で明るさを決め、指を離したときに送る */
@Composable
private fun DimmerBar(modifier: Modifier, onCommit: (Int) -> Unit) {
    val accent = LocalAccent.current
    var value by remember { mutableFloatStateOf(0.5f) }
    fun percent() = (value * 100).roundToInt().coerceIn(1, 100)
    Box(
        modifier
            .height(44.dp)
            .clip(ButtonShape)
            .background(PanelColors.Button)
            .pointerInput(Unit) {
                detectTapGestures { o ->
                    value = (o.x / size.width).coerceIn(0.01f, 1f)
                    onCommit(percent())
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> value = (o.x / size.width).coerceIn(0.01f, 1f) },
                    onDragEnd = { onCommit(percent()) },
                ) { change, dx ->
                    change.consume()
                    value = (value + dx / size.width).coerceIn(0.01f, 1f)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(value)
                .background(accent.copy(alpha = 0.22f)),
        )
        Text(
            "明るさ ${percent()}%",
            color = PanelColors.Text,
            fontSize = 13.sp,
            fontFamily = OutfitFamily,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}
