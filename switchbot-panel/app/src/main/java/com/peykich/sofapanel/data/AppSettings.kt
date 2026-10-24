package com.peykich.sofapanel.data

import com.peykich.sofapanel.api.Command
import kotlinx.serialization.Serializable

@Serializable
enum class CardKind { NONE, SCENE, MACRO }

/** メイン画面の実行カード1枚の割り当て */
@Serializable
data class CardSlot(val kind: CardKind = CardKind.NONE, val refId: String = "", val name: String = "")

/** メイン画面下段の個別操作パネル1つ */
@Serializable
data class PanelConfig(
    val title: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val buttons: List<Command> = emptyList(),
    /** 明るさのスライダーを出す（対応する照明のみ） */
    val dimmer: Boolean = false,
)

@Serializable
enum class Accent(val label: String, val argb: Long) {
    AMBER("アンバー", 0xFFE0A458),
    BLUE("青", 0xFF7AA7D9),
    PURPLE("紫", 0xFFAA94DB),
    GREEN("緑", 0xFF86B88F),
}

@Serializable
data class AppSettings(
    val roomName: String = "リビング",
    val cards: List<CardSlot> = List(CARD_COUNT) { CardSlot() },
    val panels: List<PanelConfig> = List(PANEL_COUNT) { PanelConfig() },
    /** 無操作から待機画面までの秒数 */
    val idleSeconds: Int = 30,
    val nightEnabled: Boolean = true,
    /** 夜間の画面オフ（分。0:00 からの経過） */
    val nightStart: Int = 60,
    val nightEnd: Int = 360,
    /** 充電器をつなぐプラグミニ。空なら充電管理はオフ */
    val chargePlugId: String = "",
    val chargePlugName: String = "",
    val chargeLow: Int = 30,
    val chargeHigh: Int = 80,
    val accent: Accent = Accent.AMBER,
    /** 背景画像を設定した時刻（0 なら背景画像なし）。画像の読み直しの目印にも使う */
    val backgroundVersion: Long = 0,
    /** 背景画像の上に重ねる黒の濃さ（%）。文字を読みやすくする */
    val backgroundDim: Int = 50,
    /** カード・パネルの不透明度（%）。100 で透けない */
    val panelOpacity: Int = 100,
    /** 待機画面にも背景画像を出す（さらに暗くして表示） */
    val backgroundOnStandby: Boolean = false,
    /** 初回の自動割り当てを済ませたか */
    val autoAssigned: Boolean = false,
) {
    fun normalized(): AppSettings = copy(
        cards = (cards + List(CARD_COUNT) { CardSlot() }).take(CARD_COUNT),
        panels = (panels + List(PANEL_COUNT) { PanelConfig() }).take(PANEL_COUNT),
    )

    companion object {
        const val CARD_COUNT = 4
        const val PANEL_COUNT = 3
    }
}
