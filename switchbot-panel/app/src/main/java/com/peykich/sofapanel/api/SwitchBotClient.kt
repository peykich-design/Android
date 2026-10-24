package com.peykich.sofapanel.api

import kotlinx.serialization.json.JsonElement
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID

data class HttpResult(val code: Int, val body: String)

/** HTTP の送受信。テストで差し替えられるように分けている。 */
fun interface Transport {
    @Throws(IOException::class)
    fun execute(method: String, url: String, headers: Map<String, String>, body: String?): HttpResult
}

class UrlConnectionTransport(private val timeoutMs: Int = 10_000) : Transport {
    override fun execute(method: String, url: String, headers: Map<String, String>, body: String?): HttpResult {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.useCaches = false
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * SwitchBot Open API v1.1 のクライアント（同期呼び出し。IO スレッドから呼ぶこと）。
 * 認証情報はログに出さない。
 */
class SwitchBotClient(
    private val credentials: () -> Credentials?,
    private val transport: Transport = UrlConnectionTransport(),
    private val now: () -> Long = System::currentTimeMillis,
    private val nonce: () -> String = { UUID.randomUUID().toString() },
    /** 呼び出し回数の記録用（1日の上限 10,000 回の目安） */
    private val onCall: () -> Unit = {},
) {
    fun devices(): List<Device> = ApiParser.devices(call("GET", "/v1.1/devices", null))

    fun scenes(): List<Scene> = ApiParser.scenes(call("GET", "/v1.1/scenes", null))

    fun status(deviceId: String): JsonElement? = call("GET", "/v1.1/devices/${enc(deviceId)}/status", null)

    fun command(deviceId: String, command: Command) {
        call("POST", "/v1.1/devices/${enc(deviceId)}/commands", ApiParser.commandBody(command))
    }

    fun executeScene(sceneId: String) {
        call("POST", "/v1.1/scenes/${enc(sceneId)}/execute", null)
    }

    private fun call(method: String, path: String, body: String?): JsonElement? {
        val c = credentials() ?: throw ApiError.NoCredentials()
        val headers = Signer.headers(c.token, c.secret, now(), nonce())
        onCall()
        val res = try {
            transport.execute(method, BASE_URL + path, headers, body ?: if (method == "POST") "{}" else null)
        } catch (e: SocketTimeoutException) {
            throw ApiError.Timeout()
        } catch (e: IOException) {
            throw ApiError.Network()
        }
        // 認証失敗と1日の上限超過はどちらも 401 になる
        if (res.code == 401 || res.code == 403) throw ApiError.Unauthorized()
        if (res.code !in 200..299) throw ApiError.Http(res.code)
        val env = ApiParser.envelope(res.body)
        if (env.statusCode != 100) throw ApiError.Status(env.statusCode)
        return env.body
    }

    private fun enc(id: String) = java.net.URLEncoder.encode(id, "UTF-8").replace("+", "%20")

    companion object {
        const val BASE_URL = "https://api.switch-bot.com"
    }
}
