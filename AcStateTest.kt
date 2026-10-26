package com.akillikumanda.app

import com.akillikumanda.app.feature.ac.AcMode
import com.akillikumanda.app.feature.ac.AcState
import org.junit.Assert.assertEquals
import org.junit.Test

class AcStateTest {
    @Test fun temperatureIsClamped() {
        var s = AcState(temperatureC = 29)
        repeat(5) { s = s.tempUp() }
        assertEquals(AcState.MAX_TEMP, s.temperatureC)
        repeat(30) { s = s.tempDown() }
        assertEquals(AcState.MIN_TEMP, s.temperatureC)
    }
    @Test fun modeCycles() {
        var s = AcState()
        repeat(AcMode.entries.size) { s = s.nextMode() }
        assertEquals(AcMode.COOL, s.mode)
    }
    @Test fun timerWraps() {
        var s = AcState(timerHours = AcState.MAX_TIMER)
        s = s.nextTimer()
        assertEquals(0, s.timerHours)
    }
}
