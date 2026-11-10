package com.example.localtaskassistant.capture

import com.example.localtaskassistant.contract.*
import org.junit.Assert.*
import org.junit.Test

class CaptureBusTest {
    @Test fun pauseInvalidatesInFlightInferenceSession() {
        val bus = CaptureBus()
        bus.updatePolicy(CapturePolicy(true, setOf(TargetApps.QQ)))
        val old = bus.status.value.session
        assertTrue(bus.isCurrentSession(old))
        bus.updatePolicy(CapturePolicy(false, setOf(TargetApps.QQ)))
        assertFalse(bus.isCurrentSession(old))
        bus.updatePolicy(CapturePolicy(true, setOf(TargetApps.QQ)))
        assertFalse(bus.isCurrentSession(old))
    }
    @Test fun revokingOneAppInvalidatesQueuedContent() {
        val bus = CaptureBus()
        bus.updatePolicy(CapturePolicy(true, setOf(TargetApps.QQ, TargetApps.WECHAT)))
        val old = bus.status.value.session
        bus.updatePolicy(CapturePolicy(true, setOf(TargetApps.QQ)))
        assertFalse(bus.isCurrentSession(old))
    }
}
