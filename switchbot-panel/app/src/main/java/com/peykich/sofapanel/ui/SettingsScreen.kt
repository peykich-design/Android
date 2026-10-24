@file:OptIn(ExperimentalLayoutApi::class)

package com.peykich.sofapanel.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.peykich.sofapanel.data.Accent
import com.peykich.sofapanel.data.AppSettings
import com.peykich.sofapanel.data.CardKind
import com.peykich.sofapanel.data.CardSlot
import com.peykich.sofapanel.data.PanelConfig
import com.peykich.sofapanel.domain.CommandCatalog
import com.peykich.sofapanel.domain.Macro
import com.peykich.sofapanel.domain.MacroRules

@Composable
fun SettingsScreen(vm: PanelViewModel) {
    val settings by vm.settings.collectAsState()
    val lists by vm.lists.collectAsState()
    val macros by vm.macros.collectAsState()
    val status by vm.repo.status.collectAsState()
    val hasCredentials by vm.repo.hasCredentials.collectAsState()
    val calls by vm.repo.apiCallsToday.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PanelButton("戻る", icon = PanelIcons.Back, enabled = hasCredentials) { vm.goMain() }
            Spacer(Modifier.width(16.dp))
            Text("設定", color = PanelColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(16.dp))
            status?.let {
                Text(
                    it.text,
                    color = if (it.error) PanelColors.Error else PanelColors.SubText,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ConnectionSection(vm, hasCredentials, lists.devices.size, lists.scenes.size, lists.fetchedAt, calls)
            CardsSection(vm, settings, lists.scenes.map { it.id to it.name }, macros)
            MacrosSection(vm, macros)
            PanelsSection(vm, settings)
            DisplaySection(vm, settings)
            ChargeSection(vm, settings)
            PinningSection()
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    PanelCard(Modifier.fillMaxWidth()) {
        Text(title, color = PanelColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun LabeledRow(label: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = PanelColors.SubText, fontSize = 14.sp, modifier = Modifier.width(160.dp))
        Column(Modifier.weight(1f)) { content() }
    }
}

@Composable
private fun Note(text: String) {
    Text(text, color = PanelColors.SubText, fontSize = 13.sp, lineHeight = 19.sp)
}

@Composable
private fun ConnectionSection(vm: PanelViewModel, hasCredentials: Boolean, devices: Int, scenes: Int, fetchedAt: Long, calls: Int) {
    var token by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    Section("SwitchBot との接続") {
        Note(
            "SwitchBot アプリの「プロフィール ＞ 設定」でアプリバージョンを数回タップすると「開発者向けオプション」が出ます。" +
                "そこにあるトークンとシークレットを入力してください。入力した値は端末内で暗号化して保存します。",
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = token,
                onValueChange = { token = it },
                label = { Text(if (hasCredentials) "トークン（保存済み・変更時のみ入力）" else "トークン") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = secret,
                onValueChange = { secret = it },
                label = { Text("シークレット") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            PanelButton("保存して接続テスト", icon = PanelIcons.Check, highlight = true) {
                vm.saveCredentials(token, secret)
                token = ""
                secret = ""
            }
            PanelButton("機器・シーン一覧を再取得", enabled = hasCredentials) { vm.refreshLists(silent = false) }
        }
        Spacer(Modifier.height(8.dp))
        val fetched = if (fetchedAt > 0) {
            java.text.SimpleDateFormat("M/d H:mm", java.util.Locale.JAPAN).format(java.util.Date(fetchedAt)) + " 取得"
        } else {
            "未取得"
        }
        Note("機器 ${devices}台・シーン ${scenes}件（$fetched）　今日の API 呼び出し：${calls}回（上限 1日10,000回）")
    }
}

@Composable
private fun CardsSection(vm: PanelViewModel, settings: AppSettings, scenes: List<Pair<String, String>>, macros: List<Macro>) {
    Section("メイン画面のカード") {
        Note("カードには SwitchBot のシーンか、アプリ内のマクロを割り当てます。マクロのカードは長押しで編集できます。")
        Spacer(Modifier.height(8.dp))
        settings.cards.forEachIndexed { i, slot ->
            val current = when (slot.kind) {
                CardKind.NONE -> "なし"
                CardKind.SCENE -> "シーン：" + (scenes.firstOrNull { it.first == slot.refId }?.second ?: slot.name)
                CardKind.MACRO -> "マクロ：" + (macros.firstOrNull { it.id == slot.refId }?.name ?: slot.name)
            }
            fun set(newSlot: CardSlot) = vm.updateSettings { s ->
                s.copy(cards = s.cards.mapIndexed { j, c -> if (j == i) newSlot else c })
            }
            val options = buildList<Pair<String, () -> Unit>> {
                add("なし" to { set(CardSlot()) })
                scenes.forEach { (id, name) -> add("シーン：$name" to { set(CardSlot(CardKind.SCENE, id, name)) }) }
                macros.forEach { m -> add("マクロ：${m.name}" to { set(CardSlot(CardKind.MACRO, m.id, m.name)) }) }
            }
            LabeledRow("カード${i + 1}") { ChoiceButton(current, options, Modifier.widthIn(max = 420.dp)) }
        }
    }
}

@Composable
private fun MacrosSection(vm: PanelViewModel, macros: List<Macro>) {
    var showNew by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Macro?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.exportMacros(it) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { vm.importMacros(it) }
    }
    Section("マクロ") {
        Note("「操作」と「待ち時間」を並べて、順番と間合いを正確に再現します。1本あたりの操作は${MacroRules.MAX_OPERATIONS}ステップまでです。")
        Spacer(Modifier.height(8.dp))
        macros.forEach { m ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(PanelIcons.byName(m.icon), null, tint = PanelColors.Text, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.name, color = PanelColors.Text, fontSize = 15.sp)
                    Text(
                        "${m.steps.size}ステップ・約${MacroRules.formatSeconds(MacroRules.totalSeconds(m, macros))}",
                        color = PanelColors.SubText,
                        fontSize = 12.sp,
                    )
                }
                PanelButton("編集") { vm.openEditor(m) }
                Spacer(Modifier.width(8.dp))
                PanelButton("", icon = PanelIcons.Trash) { deleting = m }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PanelButton("新規作成", icon = PanelIcons.Plus, highlight = true) { showNew = true }
            PanelButton("ファイルに書き出し", enabled = macros.isNotEmpty()) { exporter.launch("sofa-panel-macros.json") }
            PanelButton("ファイルから読み込み") { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        }
    }
    if (showNew) {
        AlertDialog(
            onDismissRequest = { showNew = false },
            title = { Text("新しいマクロ") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelButton("空から作る", Modifier.fillMaxWidth(), icon = PanelIcons.Plus) {
                        showNew = false
                        vm.newMacro(null)
                    }
                    if (macros.isNotEmpty()) Note("既存マクロを複製：")
                    macros.forEach { m ->
                        PanelButton(m.name, Modifier.fillMaxWidth(), icon = PanelIcons.Copy) {
                            showNew = false
                            vm.newMacro(m)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showNew = false }) { Text("キャンセル") } },
            containerColor = PanelColors.Card,
        )
    }
    deleting?.let { m ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("「${m.name}」を削除しますか？") },
            text = { Note("このマクロを割り当てたカードは「なし」になります。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMacro(m.id)
                    deleting = null
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("キャンセル") } },
            containerColor = PanelColors.Card,
        )
    }
}

@Composable
private fun PanelsSection(vm: PanelViewModel, settings: AppSettings) {
    val lists by vm.lists.collectAsState()
    val devices = lists.devices.filter { CommandCatalog.isControllable(it) }
    Section("メイン画面の個別操作パネル") {
        Note("パネルごとに機器を選び、出すボタン（最大4つ）を選びます。赤外線リモコンは SwitchBot アプリで登録したカスタムボタンの名前を追加できます。")
        settings.panels.forEachIndexed { i, panel ->
            fun set(p: PanelConfig) = vm.updateSettings { s -> s.copy(panels = s.panels.mapIndexed { j, c -> if (j == i) p else c }) }
            Spacer(Modifier.height(12.dp))
            val options = buildList<Pair<String, () -> Unit>> {
                add("なし" to { set(PanelConfig()) })
                devices.forEach { d -> add(d.name to { set(vm.defaultPanel(d, panel.title)) }) }
            }
            LabeledRow("パネル${i + 1}") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    ChoiceButton(panel.deviceName.ifBlank { "なし" }, options, Modifier.weight(1f))
                    var title by remember(panel.title, panel.deviceId) { mutableStateOf(panel.title) }
                    OutlinedTextField(
                        value = title,
                        onValueChange = {
                            title = it
                            set(panel.copy(title = it))
                        },
                        label = { Text("見出し（空なら機器名）") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            val device = lists.devices.firstOrNull { it.id == panel.deviceId }
            if (device != null) {
                val catalog = CommandCatalog.commandsFor(device).filter { it.command != "setBrightness" }
                val customs = panel.buttons.filter { it.commandType == "customize" }
                LabeledRow("ボタン") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        (catalog + customs).forEach { c ->
                            val selected = panel.buttons.any { it.command == c.command && it.parameter == c.parameter && it.commandType == c.commandType }
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    val next = if (selected) {
                                        panel.buttons.filterNot { it.command == c.command && it.parameter == c.parameter && it.commandType == c.commandType }
                                    } else {
                                        (panel.buttons + c).takeLast(4)
                                    }
                                    set(panel.copy(buttons = next))
                                },
                                label = { Text(c.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = LocalAccent.current.copy(alpha = 0.2f),
                                    selectedLabelColor = LocalAccent.current,
                                    labelColor = PanelColors.Text,
                                ),
                            )
                        }
                    }
                }
                if (CommandCatalog.supportsCustom(device)) {
                    var custom by remember(panel.deviceId) { mutableStateOf("") }
                    LabeledRow("カスタムボタン") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = custom,
                                onValueChange = { custom = it },
                                label = { Text("ボタン名（例：上・停止・下）") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            PanelButton("追加", enabled = custom.isNotBlank()) {
                                set(panel.copy(buttons = (panel.buttons + CommandCatalog.custom(custom.trim())).takeLast(4)))
                                custom = ""
                            }
                        }
                    }
                }
                if (CommandCatalog.supportsBrightness(device)) {
                    LabeledRow("明るさバー") {
                        Switch(
                            checked = panel.dimmer,
                            onCheckedChange = { set(panel.copy(dimmer = it)) },
                            colors = switchColors(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun switchColors() = SwitchDefaults.colors(
    checkedThumbColor = PanelColors.Bg,
    checkedTrackColor = LocalAccent.current,
    uncheckedThumbColor = PanelColors.SubText,
    uncheckedTrackColor = PanelColors.Button,
    uncheckedBorderColor = PanelColors.Border,
)

private fun hourLabel(min: Int) = "${min / 60}:${(min % 60).toString().padStart(2, '0')}"

@Composable
private fun DisplaySection(vm: PanelViewModel, settings: AppSettings) {
    var room by remember(settings.roomName) { mutableStateOf(settings.roomName) }
    Section("画面") {
        LabeledRow("部屋名") {
            OutlinedTextField(
                value = room,
                onValueChange = {
                    room = it
                    vm.updateSettings { s -> s.copy(roomName = it) }
                },
                singleLine = true,
                modifier = Modifier.widthIn(max = 420.dp),
            )
        }
        LabeledRow("待機画面までの時間") {
            ChoiceButton(
                "無操作${MacroRules.formatSeconds(settings.idleSeconds)}",
                listOf(15, 30, 60, 120, 300).map { sec -> "${MacroRules.formatSeconds(sec)}" to { vm.updateSettings { s -> s.copy(idleSeconds = sec) } } },
                Modifier.widthIn(max = 240.dp),
            )
        }
        LabeledRow("夜間は画面を黒くする") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Switch(
                    checked = settings.nightEnabled,
                    onCheckedChange = { on -> vm.updateSettings { s -> s.copy(nightEnabled = on) } },
                    colors = switchColors(),
                )
                val hours = (0 until 24).map { it * 60 }
                ChoiceButton(
                    hourLabel(settings.nightStart),
                    hours.map { m -> hourLabel(m) to { vm.updateSettings { s -> s.copy(nightStart = m) } } },
                    Modifier.width(100.dp),
                )
                Text("〜", color = PanelColors.SubText)
                ChoiceButton(
                    hourLabel(settings.nightEnd),
                    hours.map { m -> hourLabel(m) to { vm.updateSettings { s -> s.copy(nightEnd = m) } } },
                    Modifier.width(100.dp),
                )
            }
        }
        LabeledRow("アクセント色") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Accent.entries.forEach { a ->
                    PanelButton(a.label, highlight = settings.accent == a) { vm.updateSettings { s -> s.copy(accent = a) } }
                }
            }
        }
    }
}

@Composable
private fun ChargeSection(vm: PanelViewModel, settings: AppSettings) {
    val lists by vm.lists.collectAsState()
    val battery by vm.charge.battery.collectAsState()
    val lastAction by vm.charge.lastAction.collectAsState()
    val warning by vm.charge.warning.collectAsState()
    val plugs = lists.devices.filter { CommandCatalog.isPlug(it) }
    Section("充電管理") {
        Note(
            "端末に充電上限の機能（例：80%で充電を止める設定）があれば、そちらを優先してここは「なし」にしてください。" +
                "無い場合だけ、充電器をつないだプラグミニを選ぶと、残量に応じてアプリが ON/OFF します。" +
                "ネットが落ちると切り替えられないため、SwitchBot アプリ側でもプラグに「一定時間でOFF」のタイマーを設定してください。",
        )
        Spacer(Modifier.height(8.dp))
        LabeledRow("充電器のプラグ") {
            ChoiceButton(
                settings.chargePlugName.ifBlank { "なし（機能オフ）" },
                listOf<Pair<String, () -> Unit>>("なし（機能オフ）" to { vm.updateSettings { s -> s.copy(chargePlugId = "", chargePlugName = "") } }) +
                    plugs.map { d -> d.name to { vm.updateSettings { s -> s.copy(chargePlugId = d.id, chargePlugName = d.name) } } },
                Modifier.widthIn(max = 420.dp),
            )
        }
        LabeledRow("ONにする残量（下限）") {
            ChoiceButton(
                "${settings.chargeLow}%以下",
                (10..50 step 5).map { v -> "$v%" to { vm.updateSettings { s -> s.copy(chargeLow = v) } } },
                Modifier.width(140.dp),
            )
        }
        LabeledRow("OFFにする残量（上限）") {
            ChoiceButton(
                "${settings.chargeHigh}%以上",
                (60..100 step 5).map { v -> "$v%" to { vm.updateSettings { s -> s.copy(chargeHigh = v) } } },
                Modifier.width(140.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Note(
            "現在の残量：" + (battery?.let { "${it.level}%（${if (it.powered) "給電中" else "給電なし"}）" } ?: "不明") +
                (lastAction?.let { "　直近：$it" } ?: "") +
                (warning?.let { "　警告：$it" } ?: ""),
        )
        Note("安全のため、プラグの ON が連続4時間を超えたら残量に関わらず OFF にします。")
    }
}

@Composable
private fun PinningSection() {
    Section("専用端末にする（アプリ固定）") {
        Note(
            "1. 端末の「設定」で「アプリ固定」（「画面の固定」「アプリのピン留め」など機種により名前が違います）をオンにします。\n" +
                "2. このアプリを開いた状態で「最近使ったアプリ」画面を開き、このアプリのアイコンをタップして「固定」を選びます。\n" +
                "3. 解除するときは「戻る」と「最近使ったアプリ」を同時に長押しします。\n" +
                "設定画面へは、メイン画面の部屋名を2秒長押しすると入れます。",
        )
    }
}
