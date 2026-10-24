package com.peykich.sofapanel.api

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * SwitchBot API v1.1 の署名。
 * トークン + t（ミリ秒）+ nonce を連結した文字列をシークレットで HMAC-SHA256 し、base64 にした値。
 */
object Signer {
    fun sign(token: String, secret: String, t: Long, nonce: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        val raw = mac.doFinal("$token$t$nonce".toByteArray(StandardCharsets.UTF_8))
        return Base64.getEncoder().encodeToString(raw)
    }

    fun headers(token: String, secret: String, t: Long, nonce: String): Map<String, String> = mapOf(
        "Authorization" to token,
        "t" to t.toString(),
        "nonce" to nonce,
        "sign" to sign(token, secret, t, nonce),
        "Content-Type" to "application/json; charset=utf8",
    )
}
