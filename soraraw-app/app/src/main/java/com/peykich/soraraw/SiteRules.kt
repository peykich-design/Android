package com.peykich.soraraw

/** 表示するサイトと、アプリ内で開くリンクの判定。 */
object SiteRules {
    const val START_URL = "https://soraraw.com/"
    const val HOST = "soraraw.com"

    /** blob: のダウンロードでメッセージを受け付ける配信元（WebViewCompat.addWebMessageListener の書式）。 */
    val ALLOWED_ORIGINS = setOf("https://$HOST", "https://*.$HOST")

    /** 対象サイトと同じドメイン（サブドメインを含む）なら true。アプリ内で開く。 */
    fun isInternalHost(host: String?): Boolean {
        val h = host?.lowercase()?.trimEnd('.') ?: return false
        return h == HOST || h.endsWith(".$HOST")
    }
}
