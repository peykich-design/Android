package com.peykich.amazonaireader

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONObject
import org.json.JSONTokener

/** 変換結果。markdown はAI向けテキスト、data は抽出した生データ（JSON）。 */
data class ExtractResult(val markdown: String, val data: JSONObject)

/**
 * WebView でAmazonのページを普通のブラウザと同じように開き、
 * assets/extractor.js（Webアプリと同じ変換ロジック）を流し込んで結果を受け取る。
 */
class AmazonLoader(
    private val webView: WebView,
    private val extractorJs: String,
    private val onStatus: (String) -> Unit,
    private val onCaptcha: () -> Unit,
    private val onResult: (ExtractResult) -> Unit,
    private val onError: (String) -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    // CAPTCHA 画面のあと、ユーザーが解いて次のページに進んだら自動で変換を再開する
    private var resumeAfterCaptcha = false
    private var pending: Runnable? = null

    init {
        configure()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configure() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            // 変換ロジックはPC版ページのHTML構造に合わせてあるので、PC版を表示させる
            userAgentString = DESKTOP_UA
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                // Amazon以外のページや、他アプリを開くリンクには移動しない
                val uri = request.url
                return !(uri.scheme == "https" || uri.scheme == "http") || !isAllowedHost(uri.host)
            }

            override fun onPageFinished(view: WebView, url: String) {
                if (resumeAfterCaptcha) {
                    resumeAfterCaptcha = false
                    running = true
                    handler.postDelayed(timeout, TIMEOUT_MS)
                }
                if (running) scheduleExtract()
            }
        }
    }

    fun load(rawUrl: String) {
        val url = normalize(rawUrl)
        if (url == null) {
            onError("AmazonのURL（amazon.co.jp / amazon.com / amzn.asia など）を入力してください。")
            return
        }
        cancelTimers()
        running = true
        resumeAfterCaptcha = false
        onStatus("ページを読み込み中…")
        webView.loadUrl(url)
        handler.postDelayed(timeout, TIMEOUT_MS)
    }

    /** CAPTCHA を解いたあとなど、今表示中のページをもう一度変換する。 */
    fun extractCurrent() {
        cancelTimers()
        running = true
        scheduleExtract(0)
        handler.postDelayed(timeout, TIMEOUT_MS)
    }

    fun stop() {
        cancelTimers()
        running = false
        resumeAfterCaptcha = false
        webView.stopLoading()
    }

    private val timeout = Runnable {
        if (running) {
            running = false
            webView.stopLoading()
            onError("読み込みがタイムアウトしました。通信状況を確認して、もう一度試してください。")
        }
    }

    private fun cancelTimers() {
        pending?.let { handler.removeCallbacks(it) }
        handler.removeCallbacks(timeout)
    }

    // 転送などで onPageFinished が何度も呼ばれるので、最後の1回から少し待って実行する
    private fun scheduleExtract(delayMs: Long = 1200) {
        pending?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            onStatus("レビューを読み込み中…")
            // 下までスクロールして、後から読み込まれる部分（レビューなど）を表示させる
            webView.evaluateJavascript(
                "window.scrollTo(0, document.body ? document.body.scrollHeight : 0);",
                null,
            )
            handler.postDelayed({ runExtractor() }, 2000)
        }
        pending = task
        handler.postDelayed(task, delayMs)
    }

    private fun runExtractor() {
        if (!running) return
        val script = """
            (function () {
              $extractorJs
              var html = document.documentElement ? document.documentElement.innerHTML : "";
              if (document.querySelector('form[action*="validateCaptcha"]') ||
                  (/api-services-support@amazon\.com|To discuss automated access/.test(html) && !document.getElementById("productTitle"))) {
                return JSON.stringify({ captcha: true });
              }
              var d = amazonExtract(document, location.href);
              window.scrollTo(0, 0);
              return JSON.stringify({ data: d, md: amazonToMarkdown(d) });
            })();
        """.trimIndent()
        webView.evaluateJavascript(script) { raw ->
            if (!running) return@evaluateJavascript
            cancelTimers()
            running = false
            try {
                // evaluateJavascript は戻り値をJSONの文字列リテラルとして返す
                val json = JSONTokener(raw).nextValue() as? String
                    ?: throw IllegalStateException("結果が空です")
                val obj = JSONObject(json)
                if (obj.optBoolean("captcha")) {
                    resumeAfterCaptcha = true
                    onCaptcha()
                } else {
                    onResult(ExtractResult(obj.getString("md"), obj.getJSONObject("data")))
                }
            } catch (e: Exception) {
                onError("変換に失敗しました：${e.message}")
            }
        }
    }

    companion object {
        private const val TIMEOUT_MS = 45_000L
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"

        // server.js と同じ許可リスト：Amazonと公式短縮URLだけを開く
        private val ALLOWED_HOST = Regex(
            "^(?:[a-z0-9-]+\\.)*amazon\\.(?:co\\.jp|com|co\\.uk|de|fr|it|es|ca|com\\.au|com\\.mx|com\\.br|in|nl|se|pl|sg|ae|sa|com\\.tr|com\\.be|eg)$" +
                "|^(?:amzn\\.asia|amzn\\.to|amzn\\.eu|a\\.co)$",
            RegexOption.IGNORE_CASE,
        )
        private val PRODUCT_PATH = Regex("/(?:dp|gp/product|gp/aw/d)/([A-Z0-9]{10})", RegexOption.IGNORE_CASE)
        private val URL_IN_TEXT = Regex("https?://[^\\s　「」『』（）()<>\"]+")

        fun isAllowedHost(host: String?): Boolean = host != null && ALLOWED_HOST.matches(host)

        /** 商品ページは余計なパラメータを落として /dp/ASIN に揃える。Amazon以外は null。 */
        fun normalize(raw: String): String? {
            val text = raw.trim()
            val candidate = URL_IN_TEXT.find(text)?.value ?: if (text.contains("amazon") || text.contains("amzn")) "https://$text" else return null
            val uri = Uri.parse(candidate)
            if (!isAllowedHost(uri.host)) return null
            val asin = PRODUCT_PATH.find(uri.path ?: "")?.groupValues?.get(1)?.uppercase()
            return if (asin != null) "https://${uri.host}/dp/$asin" else candidate.replaceFirst("http://", "https://")
        }

        /** 共有されたテキスト（「商品名 https://amzn.asia/d/...」など）からAmazonのURLを探す。 */
        fun findAmazonUrl(text: String?): String? =
            text?.let { t -> URL_IN_TEXT.findAll(t).map { it.value }.firstOrNull { isAllowedHost(Uri.parse(it).host) } }
    }
}
