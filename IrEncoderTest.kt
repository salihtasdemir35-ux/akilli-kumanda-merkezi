package com.akillikumanda.app

import com.akillikumanda.app.core.model.IrProtocol
import com.akillikumanda.app.core.model.ProtocolData
import com.akillikumanda.app.platform.infrared.EncodeResult
import com.akillikumanda.app.platform.infrared.IrEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IrEncoderTest {
    private val enc = IrEncoder()
    private fun ok(r: EncodeResult) = (r as EncodeResult.Ok).signal

    @Test fun nec_hasHeaderAnd32BitsAndTrailer() {
        val s = ok(enc.encode(IrProtocol.NEC, 38000, ProtocolData.Scalar(0x20DF10EF, 32)))
        assertEquals(38000, s.carrierHz)
        assertEquals(2 + 64 + 2, s.pattern.size)
        assertEquals(9000, s.pattern[0]); assertEquals(4500, s.pattern[1])
        // 0x20DF10EF MSB önce: ilk bitler 0,0,1 → boşluklar 560,560,1690
        assertEquals(560, s.pattern[3]); assertEquals(560, s.pattern[5]); assertEquals(1690, s.pattern[7])
        assertEquals(108_000L, s.totalMicros)
    }

    @Test fun samsung_header4500() {
        val s = ok(enc.encode(IrProtocol.SAMSUNG32, 38000, ProtocolData.Scalar(0xE0E040BF, 32)))
        assertEquals(4500, s.pattern[0]); assertEquals(4500, s.pattern[1])
        assertEquals(68, s.pattern.size)
    }

    @Test fun sirc12_threeFramesOf45ms() {
        val s = ok(enc.encode(IrProtocol.SONY_SIRC12, 40000, ProtocolData.Scalar(0xA90, 12)))
        assertEquals(3 * (2 + 24), s.pattern.size)
        assertEquals(135_000L, s.totalMicros)
        assertEquals(1200, s.pattern[2]) // 0xA90 MSB = 1
    }

    @Test fun rc5_startsWithMarkAndAllPositive() {
        val s = ok(enc.encode(IrProtocol.PHILIPS_RC5, 36000, ProtocolData.Scalar(0x00C, 11)))
        assertEquals(889, s.pattern[0])
        assertTrue(s.pattern.all { it > 0 })
        assertTrue(s.pattern.all { it % 889 == 0 || it > 80_000 })
    }

    @Test fun rc5_toggleBitAlternates() {
        val a = ok(enc.encode(IrProtocol.PHILIPS_RC5, 36000, ProtocolData.Scalar(0x00C, 11))).pattern.toList()
        val b = ok(enc.encode(IrProtocol.PHILIPS_RC5, 36000, ProtocolData.Scalar(0x00C, 11))).pattern.toList()
        assertTrue(a != b)
    }

    @Test fun valueTooLarge_isRejected() {
        assertTrue(enc.encode(IrProtocol.SONY_SIRC12, 40000, ProtocolData.Scalar(0x1A90, 12)) is EncodeResult.Error)
    }

    @Test fun wrongBitCount_isRejected() {
        assertTrue(enc.encode(IrProtocol.NEC, 38000, ProtocolData.Scalar(0x10, 16)) is EncodeResult.Error)
    }

    @Test fun acState_needsCodec() {
        assertTrue(enc.encode(IrProtocol.RAW, 38000, ProtocolData.AcFullState("x")) is EncodeResult.Error)
    }

    @Test fun raw_rejectsNonPositive() {
        assertTrue(enc.encode(IrProtocol.RAW, 38000, ProtocolData.Raw(listOf(100, 0, 100))) is EncodeResult.Error)
    }
}
