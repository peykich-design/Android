package com.peykich.sofapanel

import com.peykich.sofapanel.domain.ChargeController
import com.peykich.sofapanel.domain.ChargeController.Decision
import com.peykich.sofapanel.domain.TimeRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RulesTest {
    @Test
    fun nightWindowWrapsMidnight() {
        assertTrue(TimeRules.inWindow(90, 60, 360)) // 1:30 は 1:00〜6:00 の中
        assertFalse(TimeRules.inWindow(360, 60, 360))
        assertTrue(TimeRules.inWindow(23 * 60 + 30, 23 * 60, 6 * 60))
        assertTrue(TimeRules.inWindow(30, 23 * 60, 6 * 60))
        assertFalse(TimeRules.inWindow(12 * 60, 23 * 60, 6 * 60))
        assertFalse(TimeRules.inWindow(100, 60, 60))
    }

    private var now = 0L
    private val cfg = ChargeController.Config(enabled = true, low = 30, high = 80)
    private fun minutes(m: Int) {
        now += m * 60_000L
    }

    @Test
    fun switchesOnAtLowAndOffAtHigh() {
        val c = ChargeController { now }
        assertTrue(c.evaluate(cfg, 50, false).isEmpty())
        val on = c.evaluate(cfg, 30, false).filterIsInstance<Decision.Switch>().single()
        assertTrue(on.on)
        c.onSwitched(true, false)
        // 同じ状態では API を呼ばない
        assertTrue(c.evaluate(cfg, 30, true).filterIsInstance<Decision.Switch>().isEmpty())
        minutes(60)
        val off = c.evaluate(cfg, 80, true).filterIsInstance<Decision.Switch>().single()
        assertFalse(off.on)
        c.onSwitched(false, false)
        assertTrue(c.evaluate(cfg, 79, false).isEmpty())
    }

    @Test
    fun retriesOnceThenWarns() {
        val c = ChargeController { now }
        c.evaluate(cfg, 20, false)
        c.onSwitched(true, false)
        minutes(5)
        assertTrue(c.evaluate(cfg, 20, false).filterIsInstance<Decision.Switch>().isEmpty())
        minutes(5)
        val retry = c.evaluate(cfg, 20, false).filterIsInstance<Decision.Switch>().single()
        assertTrue(retry.retry)
        c.onSwitched(true, true)
        minutes(10)
        val warn = c.evaluate(cfg, 20, false).filterIsInstance<Decision.Warn>().single()
        assertTrue(warn.text.contains("充電が始まりません"))
    }

    @Test
    fun forcesOffAfterFourHours() {
        val c = ChargeController { now }
        c.evaluate(cfg, 20, false)
        c.onSwitched(true, false)
        minutes(1)
        c.evaluate(cfg, 25, true)
        minutes(4 * 60)
        val off = c.evaluate(cfg, 60, true).filterIsInstance<Decision.Switch>().single()
        assertFalse(off.on)
    }

    @Test
    fun lowBatteryWarning() {
        val c = ChargeController { now }
        assertEquals(Decision.LowBattery(true), c.evaluate(cfg.copy(enabled = true), 14, true).first())
        assertTrue(c.evaluate(cfg, 14, true).none { it is Decision.LowBattery })
        assertEquals(Decision.LowBattery(false), c.evaluate(cfg, 15, true).first())
    }

    @Test
    fun unknownStateAdoptsCurrentPower() {
        val c = ChargeController { now }
        // 起動時にすでに給電中で残量が低いなら、ON とみなして API を呼ばない
        assertTrue(c.evaluate(cfg, 25, true).none { it is Decision.Switch })
        assertEquals(true, c.plugOn)
    }

    @Test
    fun disabledDoesNothing() {
        val c = ChargeController { now }
        assertTrue(c.evaluate(cfg.copy(enabled = false), 5, false).isEmpty())
    }
}
