package com.akillikumanda.app.platform.capability

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.akillikumanda.app.core.model.ConnectionType
import com.akillikumanda.app.platform.infrared.IrHardware

data class DeviceCapabilities(
    val irFeatureDeclared: Boolean,
    val irServiceAvailable: Boolean,
    val irEmitter: Boolean,
    val irPermissionGranted: Boolean,
    val carrierRanges: List<IntRange>,
    val bluetoothHardware: Boolean,
    val bleHardware: Boolean,
    /** null: izin olmadan okunamadı. */
    val bluetoothEnabled: Boolean?,
    val wifiHardware: Boolean,
    /** Bu sürümde gerçekten kullanılabilen yöntemler. */
    val usableMethods: Set<ConnectionType>,
    val irNeedsExternalAccessory: Boolean,
) {
    val irUsable: Boolean get() = irServiceAvailable && irEmitter && irPermissionGranted
}

const val NO_IR_MESSAGE =
    "Telefonunuzda kullanılabilir bir kızılötesi verici bulunamadı. IR kumandaları için uyumlu bir harici aksesuar gerekebilir. Desteklenen Wi-Fi ve Bluetooth cihazlarını kullanmaya devam edebilirsiniz."

class DeviceCapabilityRepository(private val context: Context, private val ir: IrHardware) {

    fun read(): DeviceCapabilities {
        val pm = context.packageManager
        val emitter = runCatching { ir.hasEmitter() }.getOrDefault(false)
        val irPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.TRANSMIT_IR) ==
            PackageManager.PERMISSION_GRANTED
        val btEnabled = runCatching {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled
        }.getOrNull()
        val irUsable = ir.serviceAvailable && emitter && irPerm
        return DeviceCapabilities(
            irFeatureDeclared = ir.featureDeclared,
            irServiceAvailable = ir.serviceAvailable,
            irEmitter = emitter,
            irPermissionGranted = irPerm,
            carrierRanges = if (emitter) runCatching { ir.carrierRanges() }.getOrDefault(emptyList()) else emptyList(),
            bluetoothHardware = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH),
            bleHardware = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE),
            bluetoothEnabled = btEnabled,
            wifiHardware = pm.hasSystemFeature(PackageManager.FEATURE_WIFI),
            // MVP: yalnızca IR entegrasyonu var. Wi-Fi/BLE donanımı olsa bile entegrasyon yoksa "kullanılabilir" denmez.
            usableMethods = if (irUsable) setOf(ConnectionType.IR) else emptySet(),
            irNeedsExternalAccessory = !irUsable,
        )
    }
}
