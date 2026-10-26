package com.akillikumanda.app.platform.infrared

import com.akillikumanda.app.core.model.IrProtocol
import com.akillikumanda.app.core.model.ProtocolData

/** ConsumerIrManager.transmit() için hazır sinyal: taşıyıcı frekans + açık/kapalı süreleri (µs). */
class IrSignal(val carrierHz: Int, val pattern: IntArray) {
    val totalMicros: Long get() = pattern.fold(0L) { acc, v -> acc + v }
}

sealed interface EncodeResult {
    data class Ok(val signal: IrSignal) : EncodeResult
    data class Error(val reason: String) : EncodeResult
}

/**
 * Protokole göre IR zamanlama dizisi üretir. Kodlar katalogdan gelir; burada kod üretilmez.
 * Değerler MSB önce, kod tablolarında yazıldığı bit sırasıyla gönderilir.
 */
class IrEncoder {
    private var rc5Toggle = false

    fun encode(protocol: IrProtocol, carrierHz: Int, data: ProtocolData): EncodeResult = when (data) {
        is ProtocolData.Raw -> encodeRaw(carrierHz, data)
        is ProtocolData.AcFullState ->
            EncodeResult.Error("Klima tam durum çerçevesi bir klima kodlayıcısı ile üretilmelidir")
        is ProtocolData.Scalar -> encodeScalar(protocol, carrierHz, data)
    }

    private fun encodeScalar(protocol: IrProtocol, carrierHz: Int, d: ProtocolData.Scalar): EncodeResult {
        if (d.bits <= 0 || d.bits > 64) return EncodeResult.Error("Geçersiz bit sayısı: ${d.bits}")
        if (d.bits < 64 && (d.value ushr d.bits) != 0L)
            return EncodeResult.Error("Kod değeri ${d.bits} bite sığmıyor")
        return when (protocol) {
            IrProtocol.NEC -> {
                if (d.bits != 32) EncodeResult.Error("NEC 32 bit olmalı")
                else EncodeResult.Ok(pulseDistance(carrierHz, 9000, 4500, 560, 560, 1690, d.value, 32, 108_000))
            }
            IrProtocol.SAMSUNG32 -> {
                if (d.bits != 32) EncodeResult.Error("Samsung32 32 bit olmalı")
                else EncodeResult.Ok(pulseDistance(carrierHz, 4500, 4500, 560, 560, 1690, d.value, 32, 108_000))
            }
            IrProtocol.SONY_SIRC12 -> {
                if (d.bits != 12) EncodeResult.Error("SIRC12 12 bit olmalı")
                else EncodeResult.Ok(sirc(carrierHz, d.value, 12))
            }
            IrProtocol.PHILIPS_RC5 -> {
                if (d.bits != 11) EncodeResult.Error("RC5 değeri 11 bit (5 adres + 6 komut) olmalı")
                else EncodeResult.Ok(rc5(carrierHz, d.value))
            }
            IrProtocol.RAW -> EncodeResult.Error("RAW protokolü skaler kod taşıyamaz")
        }
    }

    private fun encodeRaw(carrierHz: Int, d: ProtocolData.Raw): EncodeResult {
        if (d.patternMicros.isEmpty()) return EncodeResult.Error("Boş ham desen")
        if (d.patternMicros.any { it <= 0 }) return EncodeResult.Error("Ham desende sıfır/negatif süre var")
        return EncodeResult.Ok(IrSignal(carrierHz, d.patternMicros.toIntArray()))
    }

    /** NEC ve Samsung gibi darbe-aralık kodlamaları. Çerçeve, sabit periyoda tamamlanır. */
    private fun pulseDistance(
        carrierHz: Int, hdrMark: Int, hdrSpace: Int, bitMark: Int, zeroSpace: Int, oneSpace: Int,
        value: Long, bits: Int, framePeriod: Int,
    ): IrSignal {
        val p = ArrayList<Int>(4 + bits * 2)
        p += hdrMark; p += hdrSpace
        for (i in bits - 1 downTo 0) {
            p += bitMark
            p += if ((value ushr i) and 1L == 1L) oneSpace else zeroSpace
        }
        p += bitMark
        val used = p.sum()
        p += (framePeriod - used).coerceAtLeast(10_000)
        return IrSignal(carrierHz, p.toIntArray())
    }

    /** Sony SIRC: 2400 başlık, 1=1200/0=600 işaret + 600 boşluk, 45 ms periyot, 3 tekrar. */
    private fun sirc(carrierHz: Int, value: Long, bits: Int): IrSignal {
        val frame = ArrayList<Int>()
        frame += 2400; frame += 600
        for (i in bits - 1 downTo 0) {
            frame += if ((value ushr i) and 1L == 1L) 1200 else 600
            frame += 600
        }
        val used = frame.sum()
        frame[frame.lastIndex] = frame.last() + (45_000 - used).coerceAtLeast(0)
        val all = ArrayList<Int>(frame.size * 3)
        repeat(3) { all += frame }
        return IrSignal(carrierHz, all.toIntArray())
    }

    /** Philips RC5: Manchester, 889 µs yarım bit, 14 bit (2 başlangıç + toggle + 5 adres + 6 komut). */
    private fun rc5(carrierHz: Int, value: Long): IrSignal {
        rc5Toggle = !rc5Toggle
        val address = ((value ushr 6) and 0x1F).toInt()
        val command = (value and 0x3F).toInt()
        val bits = ArrayList<Int>(14)
        bits += 1; bits += 1; bits += if (rc5Toggle) 1 else 0
        for (i in 4 downTo 0) bits += (address shr i) and 1
        for (i in 5 downTo 0) bits += (command shr i) and 1
        // 1 = boşluk→işaret, 0 = işaret→boşluk
        val levels = ArrayList<Boolean>(28)
        for (b in bits) if (b == 1) { levels += false; levels += true } else { levels += true; levels += false }
        val runs = ArrayList<Pair<Boolean, Int>>()
        for (l in levels) {
            if (runs.isNotEmpty() && runs.last().first == l) runs[runs.lastIndex] = l to runs.last().second + 889
            else runs += l to 889
        }
        while (runs.isNotEmpty() && !runs.first().first) runs.removeAt(0) // desen işaretle başlamalı
        val pattern = runs.map { it.second }.toMutableList()
        if (runs.last().first) pattern += 90_000 else pattern[pattern.lastIndex] = pattern.last() + 90_000
        return IrSignal(carrierHz, pattern.toIntArray())
    }
}
