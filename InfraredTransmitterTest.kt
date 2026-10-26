package com.akillikumanda.app

import com.akillikumanda.app.core.model.ConnectionType
import com.akillikumanda.app.platform.infrared.DefaultInfraredTransmitter
import com.akillikumanda.app.platform.infrared.IrSignal
import com.akillikumanda.app.platform.infrared.TransmitResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** NOT: Bunlar sahte donanımla (mock) yapılan mantık testleridir; gerçek IR donanım testi değildir. */
class InfraredTransmitterTest {
    private fun tx(hw: FakeIrHardware) = DefaultInfraredTransmitter(hw, Dispatchers.Unconfined)

    @Test fun noEmitter_returnsUnsupportedHardware() = runBlocking {
        val hw = FakeIrHardware(emitter = false)
        assertEquals(TransmitResult.UnsupportedHardware, tx(hw).send(tvProfile(), "MUTE"))
        assertTrue(hw.sent.isEmpty())
    }

    @Test fun noService_returnsUnsupportedHardware() = runBlocking {
        val hw = FakeIrHardware(serviceAvailable = false)
        assertEquals(TransmitResult.UnsupportedHardware, tx(hw).send(tvProfile(), "MUTE"))
    }

    @Test fun frequencyOutsideRanges_isRejected() = runBlocking {
        val hw = FakeIrHardware(ranges = listOf(36_000..36_000))
        val r = tx(hw).send(tvProfile(hz = 38_000), "MUTE")
        assertTrue(r is TransmitResult.UnsupportedFrequency)
        assertTrue(hw.sent.isEmpty())
    }

    @Test fun unknownRanges_allowSend() = runBlocking {
        val hw = FakeIrHardware(ranges = emptyList())
        assertEquals(TransmitResult.Success, tx(hw).send(tvProfile(), "MUTE"))
        assertEquals(1, hw.sent.size)
    }

    @Test fun missingCommand_isInvalidProfile() = runBlocking {
        assertTrue(tx(FakeIrHardware()).send(tvProfile(), "POWER") is TransmitResult.InvalidProfile)
    }

    @Test fun nonIrProfile_isInvalid() = runBlocking {
        val p = tvProfile().copy(connectionType = ConnectionType.WIFI)
        assertTrue(tx(FakeIrHardware()).send(p, "MUTE") is TransmitResult.InvalidProfile)
    }

    @Test fun emptyCommands_isInvalid() = runBlocking {
        assertTrue(tx(FakeIrHardware()).send(tvProfile(commands = emptyList()), "MUTE") is TransmitResult.InvalidProfile)
    }

    @Test fun hardwareException_isTransmissionError() = runBlocking {
        val hw = FakeIrHardware(throwOnTransmit = IllegalStateException("IR meşgul"))
        val r = tx(hw).send(tvProfile(), "MUTE")
        assertEquals(TransmitResult.TransmissionError("IR meşgul"), r)
    }

    @Test fun securityException_isPermissionDenied() = runBlocking {
        val hw = FakeIrHardware(throwOnTransmit = SecurityException("izin yok"))
        assertTrue(tx(hw).send(tvProfile(), "MUTE") is TransmitResult.PermissionDenied)
    }

    @Test fun tooLongPattern_isRejected() = runBlocking {
        val r = tx(FakeIrHardware()).sendRaw(IrSignal(38000, IntArray(10) { 300_000 }))
        assertTrue(r is TransmitResult.InvalidProfile)
    }

    @Test fun success_sendsEncodedPattern() = runBlocking {
        val hw = FakeIrHardware()
        assertEquals(TransmitResult.Success, tx(hw).send(tvProfile(), "MUTE"))
        assertEquals(38000, hw.sent.single().first)
        assertEquals(68, hw.sent.single().second.size)
    }
}
