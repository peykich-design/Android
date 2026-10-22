package com.peykich.amazonaireader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 比較リストの商品をAIに比較させる画面。 */
@Composable
fun CompareTab(
    products: List<SavedProduct>,
    onRemove: (SavedProduct) -> Unit,
    onClear: () -> Unit,
    settings: AiSettings,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var priorities by rememberSaveable { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CompareResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("比較リスト（${products.size} / ${AppStore.MAX_PRODUCTS}）", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (products.isEmpty()) {
            Text(
                "まだ商品がありません。「変換」で商品ページを変換し、「比較リストに追加」を押してください。2つ以上入れると比較できます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        products.forEachIndexed { i, p ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${i + 1}. ${p.title}", maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOf(p.price, p.asin).filter { it.isNotBlank() }.joinToString(" ・ "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onRemove(p) }) { Text("外す") }
                }
            }
        }
        if (products.isNotEmpty()) {
            TextButton(onClick = { onClear(); result = null }) { Text("リストを空にする") }
        }

        OutlinedTextField(
            value = priorities,
            onValueChange = { priorities = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("重視する点（任意）") },
            placeholder = { Text("例：予算1万円以内、静音性重視、一人暮らし用") },
        )
        Text(
            "使用するAI：${settings.provider.label}（${settings.model(settings.provider)}）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !running && products.size >= 2,
                onClick = {
                    running = true; error = null; result = null
                    scope.launch {
                        try {
                            result = withContext(Dispatchers.IO) { ProductComparer.compare(settings, products, priorities) }
                        } catch (e: AiException) {
                            error = e.message
                        } catch (e: Exception) {
                            error = "通信に失敗しました：${e.message ?: e.javaClass.simpleName}"
                        } finally {
                            running = false
                        }
                    }
                },
            ) { Text("AIで比較する") }
            OutlinedButton(onClick = onOpenSettings) { Text("設定") }
        }
        if (products.size < 2) {
            Text("比較するには商品を2つ以上入れてください。", style = MaterialTheme.typography.bodySmall)
        }
        if (running) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text("AIが比較しています（1〜2分かかることがあります）…", style = MaterialTheme.typography.bodySmall)
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        result?.let { r ->
            if (r.truncated) Text("出力が長すぎたため、途中で切れています。", color = MaterialTheme.colorScheme.error)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        ?.setPrimaryClip(ClipData.newPlainText("比較結果", r.text))
                    Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                }) { Text("コピー") }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, r.text)
                    context.startActivity(Intent.createChooser(send, "比較結果を送る"))
                }) { Text("共有") }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(r.text, modifier = Modifier.padding(12.dp), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp)
                }
            }
        }
    }
}

/** 使うAIとAPIキー・モデル名の設定画面。 */
@Composable
fun SettingsTab(settings: AiSettings, onSave: (AiSettings) -> Unit) {
    val context = LocalContext.current
    var provider by remember(settings) { mutableStateOf(settings.provider) }
    var keys by remember(settings) { mutableStateOf(settings.keys) }
    var models by remember(settings) { mutableStateOf(settings.models) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("比較に使うAI", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { p ->
                FilterChip(selected = provider == p, onClick = { provider = p }, label = { Text(p.label) })
            }
        }
        OutlinedTextField(
            value = keys[provider].orEmpty(),
            onValueChange = { keys = keys + (provider to it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("${provider.label} のAPIキー") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        OutlinedTextField(
            value = models[provider].orEmpty(),
            onValueChange = { models = models + (provider to it) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("モデル名") },
            placeholder = { Text(provider.defaultModel) },
            singleLine = true,
        )
        Text(
            when (provider) {
                AiProvider.Claude -> "APIキーは console.anthropic.com で発行できます（有料）。モデル名を空にすると ${provider.defaultModel} を使います。"
                AiProvider.Gemini -> "APIキーは aistudio.google.com で発行できます（無料枠あり）。モデル名を空にすると ${provider.defaultModel} を使います。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = {
            onSave(AiSettings(provider, keys, models))
            Toast.makeText(context, "保存しました", Toast.LENGTH_SHORT).show()
        }) { Text("保存") }
        Text(
            "APIキーはこの端末のアプリ専用領域に保存され、比較を実行したときに選んだAIサービスにだけ送られます。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
