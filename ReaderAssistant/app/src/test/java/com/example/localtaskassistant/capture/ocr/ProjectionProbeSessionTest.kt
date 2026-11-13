package com.example.localtaskassistant.capture.ocr

import org.junit.Assert.*
import org.junit.Test

class ProjectionProbeSessionTest {
    private val signal = ProjectionPixelSignal(10, 20, 100, 50, 255)
    @Test fun framesBeforeRunningAreIgnored() {
        val session = ProjectionProbeSession("run_1", 100)
        session.frame(signal, 110)
        assertEquals(0, session.snapshot(120).frames)
    }
    @Test fun stoppedSessionCannotAcceptMoreFramesOrRestart() {
        val session = ProjectionProbeSession("run_1", 100)
        session.running(); session.frame(signal, 110); session.end(ProjectionProbeEnd.USER_STOP)
        session.running(); session.frame(signal, 120)
        assertEquals(1, session.snapshot(130).frames)
        assertEquals(ProjectionProbePhase.STOPPED, session.snapshot(130).phase)
    }
    @Test fun noFrameIsNotSuccessAndLatencyRemainsUnknown() {
        val value = ProjectionProbeSession("run_1", 100).snapshot(150)
        assertNull(value.firstFrameLatencyMillis); assertEquals(0, value.variedFrames)
    }
    @Test fun scopeSkipResetsConsecutiveSignal() {
        val session = ProjectionProbeSession("run_1", 100)
        session.running(); session.frame(signal, 110); session.scopeSkipped(); session.frame(signal, 120)
        assertEquals(1, session.snapshot(130).consecutiveVariedFrames)
    }
    @Test fun firstFrameLatencyDoesNotMoveWithLaterFrames() {
        val session = ProjectionProbeSession("run_1", 100)
        session.running(); session.frame(signal, 110); session.frame(signal, 140)
        assertEquals(10L, session.snapshot(150).firstFrameLatencyMillis)
        assertEquals(2, session.snapshot(150).consecutiveVariedFrames)
    }
    @Test fun firstStopReasonIsRetained() {
        val session = ProjectionProbeSession("run_1", 100)
        session.end(ProjectionProbeEnd.START_FAILED); session.end(ProjectionProbeEnd.SERVICE_DESTROYED)
        assertEquals(ProjectionProbePhase.ERROR, session.snapshot(110).phase)
        assertEquals(ProjectionProbeEnd.START_FAILED, session.snapshot(110).endReason)
    }
    @Test fun newSessionDoesNotCarryPreviousFrames() {
        val old = ProjectionProbeSession("old", 100)
        old.running(); old.frame(signal, 110)
        assertEquals(0, ProjectionProbeSession("new", 120).snapshot(130).frames)
    }
}
