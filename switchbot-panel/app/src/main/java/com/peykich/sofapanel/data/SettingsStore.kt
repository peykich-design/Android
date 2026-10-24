package com.peykich.sofapanel.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.peykich.sofapanel.api.ApiParser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/** 画面の配置や暗転時間などの設定（DataStore に JSON で保存） */
class SettingsStore(private val context: Context) {
    private val key = stringPreferencesKey("app_settings")

    val flow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        prefs[key]?.let { decode(it) } ?: AppSettings()
    }

    suspend fun current(): AppSettings = flow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.settingsDataStore.edit { prefs ->
            val old = prefs[key]?.let { decode(it) } ?: AppSettings()
            prefs[key] = ApiParser.json.encodeToString(AppSettings.serializer(), transform(old).normalized())
        }
    }

    private fun decode(text: String): AppSettings =
        runCatching { ApiParser.json.decodeFromString(AppSettings.serializer(), text).normalized() }
            .getOrDefault(AppSettings())
}
