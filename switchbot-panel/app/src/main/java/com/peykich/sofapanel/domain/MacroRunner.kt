package com.peykich.sofapanel.domain

import com.peykich.sofapanel.data.PanelRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/** 実行中マクロの進み具合（例：3/7、残り18秒） */
data class RunState(
    val macroId: String,
    val macroName: String,
    val index: Int,
    val total: Int,
    val remainingSeconds: Int,
)

/**
 * マクロをステップの上から順に実行する。アプリ全体のスコープで動くので、
 * 画面が待機・暗転しても止まらない（プロセスはフォアグラウンドサービスで維持）。
 */
class MacroRunner(private val scope: CoroutineScope, private val repo: PanelRepository) {
    private val _state = MutableStateFlow<RunState?>(null)
    val state: StateFlow<RunState?> = _state

    /** 失敗したステップがあったマクロの ID（カードを揺らす） */
    private val _failed = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val failed: SharedFlow<String> = _failed

    private var job: Job? = null
    private val token = AtomicInteger(0)

    @Synchronized
    fun start(macro: Macro, macros: List<Macro>) {
        val actions = MacroRules.flatten(macro, macros)
        val myToken = token.incrementAndGet()
        val previous = job
        // 別のマクロが実行中なら中断して切り替える
        previous?.cancel()
        _state.value = RunState(macro.id, macro.name, 0, actions.size, totalWait(actions))
        job = scope.launch {
            previous?.join()
            try {
                run(macro, actions, myToken)
            } finally {
                if (token.get() == myToken) _state.value = null
            }
        }
    }

    /** 実行中のマクロを中断する（確認なし） */
    @Synchronized
    fun cancel() {
        val name = _state.value?.macroName
        token.incrementAndGet()
        job?.cancel()
        job = null
        _state.value = null
        if (name != null) repo.notice("「$name」を中断しました")
    }

    private fun totalWait(actions: List<Action>) = actions.sumOf { (it as? Action.Wait)?.seconds ?: 0 }

    private suspend fun run(macro: Macro, actions: List<Action>, myToken: Int) {
        val failures = mutableListOf<String>()
        var remaining = totalWait(actions)
        fun publish(i: Int) {
            if (token.get() == myToken) {
                _state.value = RunState(macro.id, macro.name, i + 1, actions.size, remaining)
            }
        }
        actions.forEachIndexed { i, action ->
            when (action) {
                is Action.Send -> {
                    publish(i)
                    try {
                        repo.sendStep(action.step)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // 失敗したステップは飛ばして続ける
                        failures += "${action.label}：${repo.reason(e)}"
                    }
                }
                is Action.Wait -> {
                    var left = action.seconds
                    while (left > 0) {
                        publish(i)
                        delay(1000)
                        left--
                        remaining--
                    }
                }
            }
        }
        repo.lastRunName.value = macro.name
        repo.lastRunRef.value = macro.id
        if (failures.isEmpty()) {
            repo.post("「${macro.name}」を実行しました")
        } else {
            repo.post("「${macro.name}」：${failures.size}件失敗", error = true, details = failures)
            _failed.tryEmit(macro.id)
        }
    }
}
