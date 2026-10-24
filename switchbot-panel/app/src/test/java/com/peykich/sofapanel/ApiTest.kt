package com.peykich.sofapanel

import com.peykich.sofapanel.api.ApiError
import com.peykich.sofapanel.api.ApiParser
import com.peykich.sofapanel.api.Command
import com.peykich.sofapanel.api.Credentials
import com.peykich.sofapanel.api.HttpResult
import com.peykich.sofapanel.api.Signer
import com.peykich.sofapanel.api.SwitchBotClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.SocketTimeoutException

class ApiTest {
    @Test
    fun signMatchesReference() {
        // Python: base64(hmac_sha256(secret, token + t + nonce))
        assertEquals(
            "wHKN3mpByflfRZLycig5vryToL3Qq1/a2KTNAKZjFUI=",
            Signer.sign("mytoken", "mysecret", 1661927531000, "abc-nonce"),
        )
    }

    @Test
    fun parsesDevicesAndInfraredRemotes() {
        val body = ApiParser.envelope(
            """{"statusCode":100,"body":{"deviceList":[{"deviceId":"A1","deviceName":"Hub 2","deviceType":"Hub 2","hubDeviceId":"000"},
               {"deviceId":"P1","deviceName":"充電プラグ","deviceType":"Plug Mini (JP)","hubDeviceId":null}],
               "infraredRemoteList":[{"deviceId":"02-1","deviceName":"プロジェクター","remoteType":"Projector","hubDeviceId":"A1"}]},"message":"success"}""",
        ).body
        val devices = ApiParser.devices(body)
        assertEquals(3, devices.size)
        assertEquals("Plug Mini (JP)", devices[1].type)
        assertEquals("", devices[1].hubId)
        assertTrue(devices[2].infrared)
        assertEquals("Projector", devices[2].type)
    }

    @Test
    fun parsesScenesAndClimate() {
        val scenes = ApiParser.scenes(
            ApiParser.envelope("""{"statusCode":100,"body":[{"sceneId":"S1","sceneName":"映画"}],"message":"success"}""").body,
        )
        assertEquals("映画", scenes.single().name)
        val c = ApiParser.climate(
            ApiParser.envelope("""{"statusCode":100,"body":{"temperature":24.5,"humidity":48},"message":"success"}""").body,
        )
        assertEquals(24.5, c!!.temperature!!, 0.001)
        assertEquals(48, c.humidity)
    }

    @Test
    fun commandBodyIsJson() {
        assertEquals(
            """{"command":"setBrightness","parameter":"30","commandType":"command"}""",
            ApiParser.commandBody(Command("setBrightness", "30")),
        )
    }

    private fun client(result: () -> HttpResult, calls: MutableList<Map<String, String>> = mutableListOf()) = SwitchBotClient(
        credentials = { Credentials("tok", "sec") },
        transport = { _, _, headers, _ ->
            calls += headers
            result()
        },
        now = { 1000L },
        nonce = { "n" },
    )

    @Test
    fun sendsSignedHeaders() {
        val calls = mutableListOf<Map<String, String>>()
        client({ HttpResult(200, """{"statusCode":100,"body":{},"message":"success"}""") }, calls).command("X", Command("turnOn"))
        val h = calls.single()
        assertEquals("tok", h["Authorization"])
        assertEquals("1000", h["t"])
        assertEquals("n", h["nonce"])
        assertEquals(Signer.sign("tok", "sec", 1000, "n"), h["sign"])
    }

    @Test
    fun statusOtherThan100IsFailure() {
        try {
            client({ HttpResult(200, """{"statusCode":161,"body":{},"message":"device offline"}""") }).command("X", Command("turnOn"))
            fail()
        } catch (e: ApiError.Status) {
            assertEquals(161, e.code)
            assertEquals("機器がオフラインです", e.message)
            assertFalse(e.retryable)
        }
    }

    @Test
    fun unauthorizedMentionsLimit() {
        try {
            client({ HttpResult(401, """{"message":"Unauthorized"}""") }).devices()
            fail()
        } catch (e: ApiError.Unauthorized) {
            assertTrue(e.message!!.contains("上限"))
        }
    }

    @Test
    fun timeoutIsRetryable() {
        try {
            client({ throw SocketTimeoutException() }).devices()
            fail()
        } catch (e: ApiError.Timeout) {
            assertTrue(e.retryable)
        }
    }
}
