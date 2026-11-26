package cn.sanchuang.jianqu.admission

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceAccessTest {
    @Test fun gateDecisionMatrix() {
        val allowed = setOf(DeviceAccess.ACTIVE, DeviceAccess.OFFLINE_VALID, DeviceAccess.PREVIEW_UNENFORCED)
        DeviceAccess.entries.forEach { assertEquals(it.name, it in allowed, it.allowsHome) }
    }
}
