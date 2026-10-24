package com.peykich.sofapanel.api

import kotlinx.serialization.Serializable

@Serializable
data class Credentials(val token: String, val secret: String)

/** 機器。赤外線リモコン（infrared = true）は状態を取得できない。 */
@Serializable
data class Device(
    val id: String,
    val name: String,
    /** 実機は deviceType、赤外線リモコンは remoteType の値 */
    val type: String,
    val infrared: Boolean = false,
    val hubId: String = "",
)

@Serializable
data class Scene(val id: String, val name: String)

/** 機器へ送るコマンド。label は画面に出す名前。 */
@Serializable
data class Command(
    val command: String,
    val parameter: String = "default",
    val commandType: String = "command",
    val label: String = command,
)

data class Climate(val temperature: Double?, val humidity: Int?)
