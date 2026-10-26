package com.akillikumanda.app.platform.infrared

import android.content.Context
import android.content.pm.PackageManager
import android.hardware.ConsumerIrManager

/** ConsumerIrManager sarmalayıcısı. Yalnızca resmî Android API'sini kullanır. */
class AndroidIrHardware(context: Context) : IrHardware {
    private val manager: ConsumerIrManager? =
        runCatching { context.getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager }.getOrNull()

    override val featureDeclared: Boolean =
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_CONSUMER_IR)

    override val serviceAvailable: Boolean get() = manager != null

    override fun hasEmitter(): Boolean = manager?.hasIrEmitter() == true

    override fun carrierRanges(): List<IntRange> =
        manager?.carrierFrequencies?.map { it.minFrequency..it.maxFrequency }.orEmpty()

    override fun transmit(carrierHz: Int, pattern: IntArray) {
        val m = manager ?: throw IllegalStateException("IR hizmeti kullanılamıyor")
        m.transmit(carrierHz, pattern)
    }
}
