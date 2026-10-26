package com.akillikumanda.app

import com.akillikumanda.app.core.model.CommandIds
import com.akillikumanda.app.core.model.ConnectionType
import com.akillikumanda.app.core.model.DeviceCategory
import com.akillikumanda.app.core.model.DeviceStateRequirement
import com.akillikumanda.app.core.model.ProtocolData
import com.akillikumanda.app.core.model.RemoteCommand
import com.akillikumanda.app.core.model.RepeatPolicy
import com.akillikumanda.app.feature.discovery.ConnectionPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionPolicyTest {
    @Test fun noIr_noOptionUsable() {
        assertTrue(ConnectionPolicy.options(DeviceCategory.TV, irUsable = false, hasIrProfiles = true).none { it.usable })
    }
    @Test fun irWithProfiles_onlyIrUsable() {
        val o = ConnectionPolicy.options(DeviceCategory.TV, irUsable = true, hasIrProfiles = true)
        assertEquals(listOf(ConnectionType.IR), o.filter { it.usable }.map { it.type })
    }
    @Test fun acWithoutProfiles_notUsable() {
        assertFalse(ConnectionPolicy.options(DeviceCategory.AC, irUsable = true, hasIrProfiles = false).any { it.usable })
    }
    @Test fun vehicle_neverUsable() {
        assertTrue(ConnectionPolicy.options(DeviceCategory.VEHICLE, irUsable = true, hasIrProfiles = true).none { it.usable })
    }
    @Test fun powerIsLastSafeCommand() {
        fun cmd(id: String) = RemoteCommand(id, id, "", ProtocolData.Scalar(1, 32), RepeatPolicy.ONCE, DeviceStateRequirement.ANY)
        val p = tvProfile(commands = listOf(cmd(CommandIds.POWER), cmd(CommandIds.VOL_UP), cmd(CommandIds.MUTE)))
        assertEquals(listOf(CommandIds.MUTE, CommandIds.VOL_UP, CommandIds.POWER), ConnectionPolicy.safeTestCommands(p))
    }
    @Test fun acHasNoBlindTestCommands() {
        val p = tvProfile().copy(category = DeviceCategory.AC)
        assertTrue(ConnectionPolicy.safeTestCommands(p).isEmpty())
    }
}
