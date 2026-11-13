package com.example.localtaskassistant.capture.ocr

import org.junit.Assert.*
import org.junit.Test

class ProjectionScreenGuardTest {
    @Test fun unlockedInteractiveScreenDoesNotStop() {
        assertFalse(ProjectionScreenGuard.mustStop(true, false))
    }
    @Test fun screenOffStopsEvenBeforeKeyguardLocks() {
        assertTrue(ProjectionScreenGuard.mustStop(false, false))
    }
    @Test fun keyguardStopsEvenWithDisplayInteractive() {
        assertTrue(ProjectionScreenGuard.mustStop(true, true))
    }
    @Test fun lockedScreenOffStops() {
        assertTrue(ProjectionScreenGuard.mustStop(false, true))
    }
    @Test fun sessionCanStopForLockWithoutReceivingAnyImage() {
        val session = ProjectionProbeSession("no_frames", 0)
        session.running()
        if (ProjectionScreenGuard.mustStop(false, true)) session.end(ProjectionProbeEnd.LOCKED)
        val stopped = session.snapshot(500)
        assertEquals(ProjectionProbePhase.STOPPED, stopped.phase)
        assertEquals(ProjectionProbeEnd.LOCKED, stopped.endReason)
        assertEquals(0, stopped.frames)
    }
}
