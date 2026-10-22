package com.peykich.amazonaireader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.view.View
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

private enum class Format(val label: String) { Markdown("Markdown"), Json("JSON"), Prompt("プロンプト付き") }

private const val PROMPT_HEADER =
    "以下はAmazonのページ内容をAI向けに整理したものです。この情報だけに基づいて、商品の長所・短所・注意点を要約し、購入を検討している人へのアドバイスを書いてください。\n\n---\n\n"

@Composable
fun ReaderTheme(content: @Composable () -> Unit) {
    val colors = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF5CC3A6), onPrimary = Color(0xFF0D1412), secondaryContainer = Color(0xFF1F322C))
    } else {
        lightColorScheme(primary = Color(0xFF1F6F5C), onPrimary = Color.White, secondaryContainer = Color(0xFFE2EFE9))
    }
    MaterialTheme(colorScheme = colors, content = content)
}

@Composable
fun ReaderScreen(extractorJs: String, sharedUrl: String?, onSharedUrlConsumed: () -> Unit) {
    val context = LocalContext.current
    var url by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf("AmazonのURLを貼り付けるか、Amazonアプリの「共有」からこのアプリを選んでください。") }
    var isError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var showPage by remember { mutableStateOf(false) }
    var captcha by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExtractResult?>(null) }
    var format by rememberSaveable { mutableStateOf(Format.Markdown) }

    val webView = remember { WebView(context) }
    val loader = remember {
        AmazonLoader(
            webView = webView,
            extractorJs = extractorJs,
            onStatus = { status = it; isError = false },
            onCaptcha = {
                loading = false; captcha = true; showPage = true; isError = true
                status = "Amazonに確認画面（CAPTCHA）が表示されました。下のページで文字を入力してから「このページを変換」を押してください。"
            },
            onResult = {
                loading = false; captcha = false; showPage = false; isError = false
                result = it
                status = when (it.data.optString("pageType")) {
                    "product" -> "商品ページを変換しました（レビュー${it.data.optJSONArray("reviews")?.length() ?: 0}件）"
                    "search" -> "検索結果を変換しました（${it.data.optInt("resultCount")}件）"
                    else -> "商品・検索ページとして認識できなかったため、本文だけを抽出しました"
                }
            },
            onError = { loading = false; isError = true; status = it },
        )
    }
    DisposableEffect(Unit) { onDispose { loader.stop(); webView.destroy() } }

    fun start(target: String) {
        if (target.isBlank()) { isError = true; status = "URLを入力してください。"; return }
        loading = true; captcha = false; result = null
        loader.load(target)
    }

    LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            url = sharedUrl
            onSharedUrlConsumed()
            start(sharedUrl)
        }
    }

    val output = result?.let {
        when (format) {
            Format.Markdown -> it.markdown
            Format.Json -> it.data.toString(2)
            Format.Prompt -> PROMPT_HEADER + it.markdown
        }
    }

    Scaffold { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .imePadding(),
        ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Amazon AI Reader", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("AmazonのURL") },
                placeholder = { Text("https://amzn.asia/d/…") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { start(url) }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { start(url) }, enabled = !loading) { Text("変換する") }
                OutlinedButton(onClick = {
                    val clip = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                    val text = clip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                    url = AmazonLoader.findAmazonUrl(text) ?: text
                }) { Text("貼り付け") }
                if (loading) {
                    TextButton(onClick = { loader.stop(); loading = false; status = "中止しました。" }) { Text("中止") }
                }
            }

            if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { showPage = !showPage }) { Text(if (showPage) "ページを隠す" else "ページを表示") }
                if (captcha || showPage) {
                    OutlinedButton(onClick = { loading = true; loader.extractCurrent() }, enabled = !loading) { Text("このページを変換") }
                }
            }

            if (output != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Format.entries.forEach { f ->
                        FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.label) })
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        context.getSystemService(ClipboardManager::class.java)
                            ?.setPrimaryClip(ClipData.newPlainText("Amazon AI Reader", output))
                        Toast.makeText(context, "コピーしました", Toast.LENGTH_SHORT).show()
                    }) { Text("コピー") }
                    OutlinedButton(onClick = {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, output)
                        context.startActivity(Intent.createChooser(send, "AIアプリに送る"))
                    }) { Text("共有") }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SelectionContainer {
                        Text(
                            output,
                            modifier = Modifier.padding(12.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
        }
            // WebView はスクロール領域の外に置く。スクロールの中に置くと白い背景が画面全体を覆ってしまうため。
            // 普段は INVISIBLE（描画しないがページの読み込みとJavaScriptは動く）にしておく。
            AndroidView(
                factory = { webView },
                update = { it.visibility = if (showPage) View.VISIBLE else View.INVISIBLE },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (showPage) 420.dp else 1.dp)
                    .clipToBounds(),
            )
        }
    }
}
