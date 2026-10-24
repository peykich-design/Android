package com.peykich.sofapanel.domain

/**
 * バッテリー残量に応じてプラグミニを ON/OFF する判断（通信はしない）。
 * - 残量が下限以下で ON、上限以上で OFF（状態が変わるときだけ API を呼ぶ）
 * - 切り替えて10分後も充電状態が合わなければ1回再送、それでもだめなら警告
 * - ON が連続4時間を超えたら残量に関わらず OFF
 * - 残量15%未満で警告
 */
class ChargeController(private val now: () -> Long = System::currentTimeMillis) {

    data class Config(val enabled: Boolean, val low: Int, val high: Int)

    sealed class Decision {
        data class Switch(val on: Boolean, val reason: String, val retry: Boolean = false) : Decision()
        data class Warn(val text: String) : Decision()
        /** 残量15%未満の警告を出す（true）／消す（false） */
        data class LowBattery(val active: Boolean) : Decision()
    }

    private data class Pending(val on: Boolean, val sentAt: Long, val retried: Boolean)

    /** 最後に送った状態。起動直後は分からないので null */
    var plugOn: Boolean? = null
        private set
    var onSince: Long? = null
        private set
    private var pending: Pending? = null
    private var lastFailureAt: Long? = null
    private var lowWarned = false

    fun reset() {
        plugOn = null
        onSince = null
        pending = null
        lastFailureAt = null
    }

    fun evaluate(cfg: Config, level: Int, powered: Boolean): List<Decision> {
        val out = mutableListOf<Decision>()
        if (!cfg.enabled) {
            reset()
            return out
        }
        val t = now()

        if (level < LOW_WARNING && !lowWarned) {
            lowWarned = true
            out += Decision.LowBattery(true)
        } else if (level >= LOW_WARNING && lowWarned) {
            lowWarned = false
            out += Decision.LowBattery(false)
        }

        // 直前の失敗から少し待つ（電池の通知のたびに API を呼ばない）
        val failure = lastFailureAt
        if (failure != null && t - failure < VERIFY_MS) return out

        // 安全策：連続4時間を超えたら OFF
        val since = onSince
        if (plugOn == true && since != null && t - since >= MAX_ON_MS) {
            out += Decision.Switch(false, "ONが4時間を超えたためOFF")
            return out
        }

        // 切り替えた結果の確認
        pending?.let { p ->
            val ok = if (p.on) powered else !powered
            when {
                ok -> pending = null
                t - p.sentAt >= VERIFY_MS -> {
                    if (!p.retried) {
                        out += Decision.Switch(p.on, "充電状態が変わらないため再送", retry = true)
                    } else {
                        pending = null
                        out += Decision.Warn(
                            if (p.on) "プラグをONにしましたが充電が始まりません" else "プラグをOFFにしましたが充電が続いています",
                        )
                    }
                    return out
                }
                else -> return out
            }
        }

        if (level <= cfg.low && plugOn != true) {
            if (plugOn == null && powered) {
                // すでに給電中なら ON とみなす
                plugOn = true
                onSince = t
            } else {
                out += Decision.Switch(true, "残量${level}%のためON")
            }
        } else if (level >= cfg.high && plugOn != false) {
            if (plugOn == null && !powered) {
                plugOn = false
            } else {
                out += Decision.Switch(false, "残量${level}%のためOFF")
            }
        }
        return out
    }

    /** 切り替えの API 呼び出しが成功したとき */
    fun onSwitched(on: Boolean, retry: Boolean) {
        val t = now()
        if (plugOn != true && on) onSince = t
        if (!on) onSince = null
        plugOn = on
        pending = Pending(on, t, retried = retry)
        lastFailureAt = null
    }

    /** 切り替えの API 呼び出しが失敗したとき */
    fun onSwitchFailed() {
        lastFailureAt = now()
    }

    companion object {
        const val LOW_WARNING = 15
        const val VERIFY_MS = 10 * 60_000L
        const val MAX_ON_MS = 4 * 60 * 60_000L
    }
}
