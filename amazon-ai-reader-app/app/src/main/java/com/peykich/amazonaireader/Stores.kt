package com.peykich.amazonaireader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 比較リストに入れた商品。markdown は変換結果そのもの（AIにはこれを渡す）。 */
data class SavedProduct(val asin: String, val title: String, val price: String, val markdown: String)

/** AIの設定。APIキーはアプリ専用の保存領域（他のアプリからは読めない）に置く。 */
data class AiSettings(
    val provider: AiProvider,
    val keys: Map<AiProvider, String>,
    val models: Map<AiProvider, String>,
) {
    fun apiKey(p: AiProvider) = keys[p].orEmpty()
    fun model(p: AiProvider) = models[p].orEmpty().ifEmpty { p.defaultModel }
}

class AppStore(context: Context) {
    private val prefs = context.getSharedPreferences("amazon_ai_reader", Context.MODE_PRIVATE)

    fun loadSettings(): AiSettings {
        val provider = runCatching { AiProvider.valueOf(prefs.getString("provider", null) ?: "") }.getOrDefault(AiProvider.Claude)
        return AiSettings(
            provider = provider,
            keys = AiProvider.entries.associateWith { prefs.getString("key_${it.name}", "").orEmpty() },
            models = AiProvider.entries.associateWith { prefs.getString("model_${it.name}", "").orEmpty() },
        )
    }

    fun saveSettings(s: AiSettings) {
        prefs.edit().apply {
            putString("provider", s.provider.name)
            AiProvider.entries.forEach {
                putString("key_${it.name}", s.apiKey(it).trim())
                putString("model_${it.name}", s.models[it].orEmpty().trim())
            }
        }.apply()
    }

    fun loadProducts(): List<SavedProduct> = runCatching {
        val arr = JSONArray(prefs.getString("products", "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedProduct(o.optString("asin"), o.optString("title"), o.optString("price"), o.optString("markdown"))
        }
    }.getOrDefault(emptyList())

    fun saveProducts(list: List<SavedProduct>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().put("asin", it.asin).put("title", it.title).put("price", it.price).put("markdown", it.markdown))
        }
        prefs.edit().putString("products", arr.toString()).apply()
    }

    companion object {
        const val MAX_PRODUCTS = 5

        /** 商品ページの変換結果から比較リスト用のデータを作る。商品ページ以外は null。 */
        fun productFrom(result: ExtractResult): SavedProduct? {
            val d = result.data
            if (d.optString("pageType") != "product") return null
            return SavedProduct(d.optString("asin"), d.optString("title"), d.optString("price"), result.markdown)
        }
    }
}
