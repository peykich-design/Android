package com.peykich.sofapanel.domain

import com.peykich.sofapanel.api.Command
import com.peykich.sofapanel.data.PanelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class BatteryInfo(val level: Int, val powered: Boolean)

/** バッテリーの変化を受けて、必要なときだけプラグミニを切り替える */
class ChargeManager(private val scope: CoroutineScope, private val repo: PanelRepository) {
    private val controller = ChargeController()
    private val mutex = Mutex()
    private var lastPlugId = ""

    val battery = MutableStateFlow<BatteryInfo?>(null)
    /** 上部バーに出す充電の警告 */
    val warning = MutableStateFlow<String?>(null)
    val lowBattery = MutableStateFlow(false)
    /** 設定画面に出す直近の動作 */
    val lastAction = MutableStateFlow<String?>(null)

    fun onBattery(level: Int, powered: Boolean) {
        battery.value = BatteryInfo(level, powered)
        evaluate()
    }

    fun evaluate() {
        scope.launch {
            mutex.withLock { evaluateLocked() }
        }
    }

    private suspend fun evaluateLocked() {
        val b = battery.value ?: return
        val s = repo.settings.value
        if (s.chargePlugId != lastPlugId) {
            controller.reset()
            warning.value = null
            lastPlugId = s.chargePlugId
        }
        val cfg = ChargeController.Config(
            enabled = s.chargePlugId.isNotEmpty() && repo.hasCredentials.value,
            low = s.chargeLow,
            high = s.chargeHigh,
        )
        for (d in controller.evaluate(cfg, b.level, b.powered)) {
            when (d) {
                is ChargeController.Decision.LowBattery -> lowBattery.value = d.active
                is ChargeController.Decision.Warn -> warning.value = d.text
                is ChargeController.Decision.Switch -> {
                    val cmd = Command(if (d.on) "turnOn" else "turnOff")
                    try {
                        repo.withRetry { it.command(s.chargePlugId, cmd) }
                        controller.onSwitched(d.on, d.retry)
                        if (!d.retry) warning.value = null
                        lastAction.value = "プラグを${if (d.on) "ON" else "OFF"}にしました（${d.reason}）"
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        controller.onSwitchFailed()
                        warning.value = "充電プラグ：" + repo.reason(e, s.chargePlugName)
                    }
                }
            }
        }
    }
}
