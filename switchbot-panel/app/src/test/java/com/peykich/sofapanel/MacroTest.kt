package com.peykich.sofapanel

import com.peykich.sofapanel.api.Command
import com.peykich.sofapanel.api.Device
import com.peykich.sofapanel.domain.Action
import com.peykich.sofapanel.domain.CommandCatalog
import com.peykich.sofapanel.domain.DeviceStep
import com.peykich.sofapanel.domain.Macro
import com.peykich.sofapanel.domain.MacroFile
import com.peykich.sofapanel.domain.MacroRules
import com.peykich.sofapanel.domain.MacroStep
import com.peykich.sofapanel.domain.SceneStep
import com.peykich.sofapanel.domain.WaitStep
import com.peykich.sofapanel.api.ApiParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MacroTest {
    private fun op(name: String) = DeviceStep(deviceId = name, deviceName = name, command = Command("turnOn", label = "電源"))

    /** 受け入れ基準の例：プロジェクター電源→待つ→スクリーン下→待つ→照明30% */
    private val movie = Macro(
        id = "movie", name = "映画",
        steps = listOf(
            op("プロジェクター"), WaitStep(seconds = 25),
            DeviceStep(deviceId = "s", deviceName = "スクリーン", command = CommandCatalog.custom("下")), WaitStep(seconds = 20),
            DeviceStep(deviceId = "l", deviceName = "照明", command = CommandCatalog.brightness(30)),
        ),
    )

    @Test
    fun flattenAndTotals() {
        val actions = MacroRules.flatten(movie, listOf(movie))
        assertEquals(5, actions.size)
        assertEquals(3, MacroRules.operationCount(movie, listOf(movie)))
        assertEquals(45, MacroRules.totalSeconds(movie, listOf(movie)))
        assertEquals("プロジェクター：電源", (actions[0] as Action.Send).label)
        assertEquals("25秒待つ", MacroRules.label(movie.steps[1], emptyList()))
        assertEquals("照明：明るさ30%", MacroRules.label(movie.steps[4], emptyList()))
        assertNull(MacroRules.validate(movie, emptyList()))
    }

    @Test
    fun nestedMacroIsExpanded() {
        val dim = Macro(id = "dim", name = "照明を暗く", steps = listOf(op("a"), WaitStep(seconds = 3), op("b")))
        val outer = Macro(id = "o", name = "外", steps = listOf(MacroStep(macroId = "dim", macroName = "x"), SceneStep(sceneId = "s", sceneName = "床照明オン")))
        val all = listOf(dim, outer)
        assertEquals(4, MacroRules.flatten(outer, all).size)
        assertEquals("マクロ：照明を暗く", MacroRules.label(outer.steps[0], all))
    }

    @Test
    fun cycleIsRejected() {
        val a = Macro(id = "a", name = "A", steps = listOf(MacroStep(macroId = "b", macroName = "B")))
        val b = Macro(id = "b", name = "B", steps = listOf(MacroStep(macroId = "a", macroName = "A")))
        assertTrue(MacroRules.hasCycle(a, listOf(b)))
        assertNotNull(MacroRules.validate(a, listOf(b)))
        val self = Macro(id = "s", name = "S", steps = listOf(MacroStep(macroId = "s", macroName = "S")))
        assertTrue(MacroRules.hasCycle(self, emptyList()))
        assertFalse(MacroRules.hasCycle(movie, emptyList()))
    }

    @Test
    fun atMost50Operations() {
        val big = Macro(id = "big", name = "大", steps = List(51) { op("d$it") })
        assertNotNull(MacroRules.validate(big, emptyList()))
        val ok = Macro(id = "ok", name = "可", steps = List(50) { op("d$it") } + WaitStep(seconds = 600))
        assertNull(MacroRules.validate(ok, emptyList()))
        val longWait = Macro(id = "w", name = "長", steps = listOf(WaitStep(seconds = 601)))
        assertNotNull(MacroRules.validate(longWait, emptyList()))
    }

    @Test
    fun jsonRoundTrip() {
        val text = ApiParser.json.encodeToString(MacroFile.serializer(), MacroFile(macros = listOf(movie)))
        assertTrue(text.contains("\"type\":\"wait\""))
        val back = ApiParser.json.decodeFromString(MacroFile.serializer(), text)
        assertEquals(movie, back.macros.single())
    }

    @Test
    fun catalogCommands() {
        val tv = Device("t", "テレビ", "TV", infrared = true)
        assertTrue(CommandCatalog.commandsFor(tv).any { it.command == "volumeAdd" })
        val bulb = Device("b", "照明", "Color Bulb")
        assertTrue(CommandCatalog.supportsBrightness(bulb))
        assertEquals("30", CommandCatalog.brightness(30).parameter)
        val others = Device("o", "スクリーン", "DIY Others", infrared = true)
        assertTrue(CommandCatalog.isControllable(others))
        assertEquals("customize", CommandCatalog.custom("下").commandType)
        assertFalse(CommandCatalog.isControllable(Device("h", "Hub 2", "Hub 2")))
        assertTrue(CommandCatalog.isPlug(Device("p", "プラグ", "Plug Mini (JP)")))
    }
}
