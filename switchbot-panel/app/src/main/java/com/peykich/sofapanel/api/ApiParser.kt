package com.peykich.sofapanel.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

/** SwitchBot API の応答（{"statusCode":100,"body":...,"message":"success"}）の読み取り */
object ApiParser {
    val json = Json { ignoreUnknownKeys = true }

    data class Envelope(val statusCode: Int, val message: String, val body: JsonElement?)

    fun envelope(text: String): Envelope {
        val obj = try {
            json.parseToJsonElement(text) as? JsonObject
        } catch (e: Exception) {
            null
        } ?: throw ApiError.BadResponse()
        val code = (obj["statusCode"] as? JsonPrimitive)?.intOrNull ?: throw ApiError.BadResponse()
        return Envelope(code, obj.str("message").orEmpty(), obj["body"])
    }

    fun devices(body: JsonElement?): List<Device> {
        val obj = body as? JsonObject ?: return emptyList()
        val real = (obj["deviceList"] as? JsonArray).orEmpty().mapNotNull { e ->
            val d = e as? JsonObject ?: return@mapNotNull null
            val id = d.str("deviceId") ?: return@mapNotNull null
            Device(
                id = id,
                name = d.str("deviceName") ?: id,
                type = d.str("deviceType").orEmpty(),
                infrared = false,
                hubId = d.str("hubDeviceId").orEmpty(),
            )
        }
        val infrared = (obj["infraredRemoteList"] as? JsonArray).orEmpty().mapNotNull { e ->
            val d = e as? JsonObject ?: return@mapNotNull null
            val id = d.str("deviceId") ?: return@mapNotNull null
            Device(
                id = id,
                name = d.str("deviceName") ?: id,
                type = d.str("remoteType").orEmpty(),
                infrared = true,
                hubId = d.str("hubDeviceId").orEmpty(),
            )
        }
        return real + infrared
    }

    fun scenes(body: JsonElement?): List<Scene> =
        (body as? JsonArray).orEmpty().mapNotNull { e ->
            val s = e as? JsonObject ?: return@mapNotNull null
            val id = s.str("sceneId") ?: return@mapNotNull null
            Scene(id, s.str("sceneName") ?: id)
        }

    fun climate(body: JsonElement?): Climate? {
        val obj = body as? JsonObject ?: return null
        val t = (obj["temperature"] as? JsonPrimitive)?.doubleOrNull
        val h = (obj["humidity"] as? JsonPrimitive)?.doubleOrNull?.toInt()
        if (t == null && h == null) return null
        return Climate(t, h)
    }

    /** プラグなどの通電状態（"power":"on"/"off"）。取れなければ null */
    fun power(body: JsonElement?): Boolean? {
        val obj = body as? JsonObject ?: return null
        return when (obj.str("power")?.lowercase()) {
            "on" -> true
            "off" -> false
            else -> null
        }
    }

    fun commandBody(command: Command): String = JsonObject(
        mapOf(
            "command" to JsonPrimitive(command.command),
            "parameter" to JsonPrimitive(command.parameter),
            "commandType" to JsonPrimitive(command.commandType),
        ),
    ).toString()

    private fun JsonObject.str(key: String): String? {
        val p = this[key] as? JsonPrimitive ?: return null
        if (p is JsonNull) return null
        return p.content
    }
}
