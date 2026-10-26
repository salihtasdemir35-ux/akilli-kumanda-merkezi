package com.akillikumanda.app

import com.akillikumanda.app.core.model.*
import com.akillikumanda.app.platform.infrared.IrHardware

fun tvProfile(
    id: String = "tv.test.nec",
    brand: String = "LG",
    model: String? = null,
    hz: Int = 38000,
    commands: List<RemoteCommand> = listOf(
        RemoteCommand(CommandIds.MUTE, "Sessiz", "", ProtocolData.Scalar(0x20DF906F, 32), RepeatPolicy.ONCE, DeviceStateRequirement.ON),
    ),
    prefixes: List<String> = emptyList(),
) = RemoteProfile(
    id = id, category = DeviceCategory.TV, brand = brand, model = model, modelPrefixes = prefixes,
    protocol = IrProtocol.NEC, connectionType = ConnectionType.IR, carrierFrequencyHz = hz,
    commands = commands, source = "test", license = "test",
    verificationStatus = VerificationStatus.UNVERIFIED, version = 1,
)

class FakeIrHardware(
    override val featureDeclared: Boolean = true,
    override val serviceAvailable: Boolean = true,
    var emitter: Boolean = true,
    var ranges: List<IntRange> = listOf(30_000..60_000),
    var throwOnTransmit: Exception? = null,
) : IrHardware {
    val sent = mutableListOf<Pair<Int, IntArray>>()
    override fun hasEmitter() = emitter
    override fun carrierRanges() = ranges
    override fun transmit(carrierHz: Int, pattern: IntArray) {
        throwOnTransmit?.let { throw it }
        sent += carrierHz to pattern
    }
}
