package com.peykich.sofapanel.domain

import com.peykich.sofapanel.api.Command
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
data class Macro(
    val id: String = newId(),
    val name: String = "",
    val icon: String = "play",
    val steps: List<Step> = emptyList(),
)

/** マクロの1ステップ。uid はリスト内で並べ替えるための識別子。 */
@Serializable
sealed class Step {
    abstract val uid: String
    abstract fun withUid(uid: String): Step
}

@Serializable
@SerialName("device")
data class DeviceStep(
    override val uid: String = newId(),
    val deviceId: String,
    val deviceName: String,
    val command: Command,
) : Step() {
    override fun withUid(uid: String) = copy(uid = uid)
}

@Serializable
@SerialName("wait")
data class WaitStep(override val uid: String = newId(), val seconds: Int) : Step() {
    override fun withUid(uid: String) = copy(uid = uid)
}

@Serializable
@SerialName("scene")
data class SceneStep(override val uid: String = newId(), val sceneId: String, val sceneName: String) : Step() {
    override fun withUid(uid: String) = copy(uid = uid)
}

@Serializable
@SerialName("macro")
data class MacroStep(override val uid: String = newId(), val macroId: String, val macroName: String) : Step() {
    override fun withUid(uid: String) = copy(uid = uid)
}

/** 書き出し・読み込み用のファイル形式 */
@Serializable
data class MacroFile(val version: Int = 1, val macros: List<Macro>)

/** 実行時に展開した1つの動作 */
sealed class Action {
    data class Send(val step: Step, val label: String) : Action()
    data class Wait(val seconds: Int) : Action()
}

object MacroRules {
    const val MAX_OPERATIONS = 50
    const val MAX_WAIT_SECONDS = 600

    fun formatSeconds(total: Int): String {
        val m = total / 60
        val s = total % 60
        return when {
            m == 0 -> "${s}秒"
            s == 0 -> "${m}分"
            else -> "${m}分${s}秒"
        }
    }

    fun label(step: Step, macros: List<Macro>): String = when (step) {
        is DeviceStep -> "${step.deviceName}：${step.command.label}"
        is WaitStep -> "${formatSeconds(step.seconds)}待つ"
        is SceneStep -> "シーン：${step.sceneName}"
        is MacroStep -> "マクロ：${macros.firstOrNull { it.id == step.macroId }?.name ?: step.macroName}"
    }

    /** 他のマクロ呼び出しを展開して、上から順に実行する動作の並びにする（循環は打ち切る） */
    fun flatten(macro: Macro, macros: List<Macro>): List<Action> {
        val out = mutableListOf<Action>()
        fun walk(m: Macro, stack: Set<String>) {
            for (step in m.steps) {
                when (step) {
                    is WaitStep -> out += Action.Wait(step.seconds.coerceIn(0, MAX_WAIT_SECONDS))
                    is MacroStep -> {
                        if (step.macroId in stack) continue
                        val child = macros.firstOrNull { it.id == step.macroId } ?: continue
                        walk(child, stack + child.id)
                    }
                    else -> out += Action.Send(step, label(step, macros))
                }
            }
        }
        walk(macro, setOf(macro.id))
        return out
    }

    fun operationCount(macro: Macro, macros: List<Macro>) = flatten(macro, macros).count { it is Action.Send }

    fun totalSeconds(macro: Macro, macros: List<Macro>) =
        flatten(macro, macros).sumOf { (it as? Action.Wait)?.seconds ?: 0 }

    /** macro（保存前の内容）を含めたとき、呼び出しが循環するか */
    fun hasCycle(macro: Macro, macros: List<Macro>): Boolean {
        val all = macros.filter { it.id != macro.id } + macro
        val byId = all.associateBy { it.id }
        fun visit(id: String, stack: Set<String>): Boolean {
            val m = byId[id] ?: return false
            for (s in m.steps.filterIsInstance<MacroStep>()) {
                if (s.macroId in stack) return true
                if (visit(s.macroId, stack + s.macroId)) return true
            }
            return false
        }
        return visit(macro.id, setOf(macro.id))
    }

    /** 保存できない理由。保存できるなら null */
    fun validate(macro: Macro, macros: List<Macro>): String? {
        if (macro.name.isBlank()) return "マクロ名を入れてください"
        if (macro.steps.isEmpty()) return "ステップを1つ以上追加してください"
        if (hasCycle(macro, macros)) return "マクロの呼び出しが循環しています（自分自身を呼ぶことはできません）"
        val all = macros.filter { it.id != macro.id } + macro
        val ops = operationCount(macro, all)
        if (ops > MAX_OPERATIONS) return "操作ステップは${MAX_OPERATIONS}個までです（今は${ops}個）"
        if (macro.steps.any { it is WaitStep && it.seconds !in 1..MAX_WAIT_SECONDS }) return "待ち時間は1秒〜10分にしてください"
        return null
    }
}
