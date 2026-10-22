package com.peykich.amazonaireader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf

class MainActivity : ComponentActivity() {

    /** 共有メニューから受け取ったURL。画面側で読み取ったら null に戻す。 */
    private val sharedUrl = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) sharedUrl.value = urlFrom(intent)
        val extractorJs = assets.open("extractor.js").bufferedReader().use { it.readText() }
        setContent {
            ReaderTheme {
                ReaderScreen(
                    extractorJs = extractorJs,
                    sharedUrl = sharedUrl.value,
                    onSharedUrlConsumed = { sharedUrl.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        urlFrom(intent)?.let { sharedUrl.value = it }
    }

    private fun urlFrom(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return AmazonLoader.findAmazonUrl(intent.getStringExtra(Intent.EXTRA_TEXT))
            ?: intent.getStringExtra(Intent.EXTRA_TEXT)
    }
}
