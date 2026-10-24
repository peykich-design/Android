package com.peykich.sofapanel.api

/**
 * API 呼び出しの失敗。メッセージは画面の上部バーにそのまま出す。
 * トークンやシークレットをメッセージに含めないこと。
 */
sealed class ApiError(message: String) : Exception(message) {
    class NoCredentials : ApiError("トークンとシークレットが未設定です")
    class Unauthorized : ApiError("認証エラー（上限超過の可能性もあり）")
    class Timeout : ApiError("タイムアウト")
    class Network : ApiError("通信エラー")
    class Http(val code: Int) : ApiError("サーバーエラー（HTTP $code）")
    class BadResponse : ApiError("応答を読み取れません")
    class Status(val code: Int) : ApiError(statusText(code))

    /** 1回だけ再送してよい失敗（通信エラー・タイムアウト） */
    val retryable: Boolean get() = this is Timeout || this is Network

    companion object {
        fun statusText(code: Int): String = when (code) {
            151 -> "この機器では使えないコマンドです"
            152 -> "機器が見つかりません"
            160 -> "未対応のコマンドです"
            161 -> "機器がオフラインです"
            171 -> "ハブに届きません"
            190 -> "機器エラー"
            else -> "エラー（$code）"
        }
    }
}
