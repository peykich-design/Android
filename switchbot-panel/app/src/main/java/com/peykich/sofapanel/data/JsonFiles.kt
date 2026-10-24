package com.peykich.sofapanel.data

import android.content.Context
import android.util.AtomicFile
import com.peykich.sofapanel.api.ApiParser
import com.peykich.sofapanel.api.Device
import com.peykich.sofapanel.api.Scene
import com.peykich.sofapanel.domain.Macro
import com.peykich.sofapanel.domain.MacroFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

private fun AtomicFile.writeText(text: String) {
    val out = startWrite()
    try {
        out.write(text.toByteArray(Charsets.UTF_8))
        finishWrite(out)
    } catch (e: Exception) {
        failWrite(out)
        throw e
    }
}

private fun AtomicFile.readTextOrNull(): String? =
    runCatching { String(readFully(), Charsets.UTF_8) }.getOrNull()

/** マクロは JSON ファイルとして端末内に保存する */
class MacroStore(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "macros.json"))
    private val serializer = ListSerializer(Macro.serializer())
    private val _macros = MutableStateFlow(load())
    val macros: StateFlow<List<Macro>> = _macros

    private fun load(): List<Macro> = file.readTextOrNull()?.let {
        runCatching { ApiParser.json.decodeFromString(serializer, it) }.getOrNull()
    } ?: emptyList()

    @Synchronized
    fun save(macro: Macro) {
        val list = _macros.value.toMutableList()
        val i = list.indexOfFirst { it.id == macro.id }
        if (i >= 0) list[i] = macro else list += macro
        persist(list)
    }

    @Synchronized
    fun delete(id: String) = persist(_macros.value.filter { it.id != id })

    fun exportJson(): String =
        ApiParser.json.encodeToString(MacroFile.serializer(), MacroFile(macros = _macros.value))

    /** 読み込んだマクロを追加する（同じ ID は上書き）。読み込んだ件数を返す */
    @Synchronized
    fun importJson(text: String): Int {
        val imported = ApiParser.json.decodeFromString(MacroFile.serializer(), text).macros
        val byId = _macros.value.associateBy { it.id }.toMutableMap()
        imported.forEach { byId[it.id] = it }
        persist(byId.values.toList())
        return imported.size
    }

    private fun persist(list: List<Macro>) {
        file.writeText(ApiParser.json.encodeToString(serializer, list))
        _macros.value = list
    }
}

@Serializable
data class CachedLists(
    val devices: List<Device> = emptyList(),
    val scenes: List<Scene> = emptyList(),
    val fetchedAt: Long = 0,
)

/** 機器・シーン一覧のキャッシュ（取得は起動時と手動更新時だけ） */
class ListCache(context: Context) {
    private val file = AtomicFile(File(context.filesDir, "lists.json"))

    fun load(): CachedLists = file.readTextOrNull()?.let {
        runCatching { ApiParser.json.decodeFromString(CachedLists.serializer(), it) }.getOrNull()
    } ?: CachedLists()

    fun save(lists: CachedLists) = file.writeText(ApiParser.json.encodeToString(CachedLists.serializer(), lists))
}
