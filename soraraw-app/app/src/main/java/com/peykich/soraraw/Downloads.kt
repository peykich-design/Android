package com.peykich.soraraw

import android.app.Activity
import android.app.DownloadManager
import android.content.ContentValues
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.File

/**
 * WebView からのダウンロードを「ダウンロード」フォルダに保存する。
 * - http/https：DownloadManager に任せる（Cookie を付けるのでログインが必要なファイルも取れる）
 * - data:：その場でデコードして保存
 * - blob:：ページ内の JavaScript で読み出し、Web メッセージで受け取って保存
 */
class Downloads(
    private val activity: Activity,
    private val webView: WebView,
    /** Android 9 以下でストレージの許可を取ってから処理を実行する */
    private val withStoragePermission: (() -> Unit) -> Unit,
) {

    /** WebView にページを読み込む前に呼ぶ（blob: 用の受け口を登録する）。 */
    fun install() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val name = URLUtil.guessFileName(url, contentDisposition, mimeType)
            withStoragePermission { start(url, userAgent, name, mimeType) }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            // 対象サイトのページからだけメッセージを受け取る
            WebViewCompat.addWebMessageListener(
                webView, BRIDGE, SiteRules.ALLOWED_ORIGINS,
            ) { _, message, _, _, _ -> onBlobMessage(message.data) }
        }
    }

    private fun start(url: String, userAgent: String?, name: String, mimeType: String?) {
        when {
            url.startsWith("http://") || url.startsWith("https://") ->
                enqueue(url, userAgent, name, mimeType)
            url.startsWith("data:") -> saveDataUrl(url, name, mimeType)
            url.startsWith("blob:") -> fetchBlob(url, name, mimeType)
            else -> toast(activity.getString(R.string.download_failed))
        }
    }

    private fun enqueue(url: String, userAgent: String?, name: String, mimeType: String?) {
        try {
            val request = DownloadManager.Request(Uri.parse(url)).apply {
                CookieManager.getInstance().getCookie(url)?.let { addRequestHeader("Cookie", it) }
                userAgent?.let { addRequestHeader("User-Agent", it) }
                if (!mimeType.isNullOrBlank()) setMimeType(mimeType)
                setTitle(name)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            }
            activity.getSystemService(DownloadManager::class.java).enqueue(request)
            toast(activity.getString(R.string.download_started, name))
        } catch (e: Exception) {
            toast(activity.getString(R.string.download_failed))
        }
    }

    private fun fetchBlob(url: String, name: String, mimeType: String?) {
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            toast(activity.getString(R.string.download_failed))
            return
        }
        val script = """
            (async function() {
              try {
                const blob = await (await fetch(${JSONObject.quote(url)})).blob();
                const reader = new FileReader();
                reader.onload = function() {
                  $BRIDGE.postMessage(JSON.stringify({
                    name: ${JSONObject.quote(name)},
                    mime: blob.type || ${JSONObject.quote(mimeType ?: "")},
                    data: reader.result
                  }));
                };
                reader.readAsDataURL(blob);
              } catch (e) {
                $BRIDGE.postMessage(JSON.stringify({ error: String(e) }));
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun onBlobMessage(data: String?) {
        val json = runCatching { JSONObject(data ?: "") }.getOrNull()
        if (json == null || json.has("error")) {
            toast(activity.getString(R.string.download_failed))
            return
        }
        saveDataUrl(json.optString("data"), json.optString("name", "download"), json.optString("mime"))
    }

    private fun saveDataUrl(dataUrl: String, name: String, mimeType: String?) {
        // 形式：data:[<mime>][;base64],<data>
        val comma = dataUrl.indexOf(',')
        if (comma < 0) {
            toast(activity.getString(R.string.download_failed))
            return
        }
        val header = dataUrl.substring(5, comma)
        val body = dataUrl.substring(comma + 1)
        val bytes = runCatching {
            if (header.endsWith(";base64")) Base64.decode(body, Base64.DEFAULT)
            else Uri.decode(body).toByteArray()
        }.getOrNull()
        val mime = mimeType?.takeIf { it.isNotBlank() }
            ?: header.substringBefore(';').ifBlank { "application/octet-stream" }
        if (bytes == null) {
            toast(activity.getString(R.string.download_failed))
            return
        }
        Thread {
            val ok = runCatching { saveBytes(name, mime, bytes) }.isSuccess
            activity.runOnUiThread {
                toast(
                    if (ok) activity.getString(R.string.download_saved, name)
                    else activity.getString(R.string.download_failed)
                )
            }
        }.start()
    }

    private fun saveBytes(name: String, mime: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = activity.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: error("insert failed")
            resolver.openOutputStream(uri)!!.use { it.write(bytes) }
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            var file = File(dir, name)
            var n = 1
            while (file.exists()) {
                file = File(dir, "${name.substringBeforeLast('.')}($n)" +
                    (name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }))
                n++
            }
            file.writeBytes(bytes)
            MediaScannerConnection.scanFile(activity, arrayOf(file.absolutePath), arrayOf(mime), null)
        }
    }

    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_SHORT).show()

    private companion object {
        const val BRIDGE = "SorarawDownloader"
    }
}
