package com.example.localtaskassistant.capture.ocr

import org.junit.Assert.*
import org.junit.Test

class PreviewLeaseTest {
    @Test fun requiresExplicitArmingAndOnePublish() {
        val lease=PreviewLease()
        assertFalse(lease.canPublish("a",0)); lease.arm("a",1)
        assertTrue(lease.publish("a",2)); assertFalse(lease.publish("a",3))
    }
    @Test fun cannotTakeBeforeReadyOrTakeTwice() {
        val lease=PreviewLease(); lease.arm("a",1)
        assertFalse(lease.take(2)); lease.publish("a",3)
        assertTrue(lease.take(4)); assertFalse(lease.take(5))
    }
    @Test fun previousSessionCannotPublishIntoNewSession() {
        val lease=PreviewLease(); lease.arm("a",1); lease.arm("b",2)
        assertFalse(lease.publish("a",3)); assertTrue(lease.publish("b",4))
    }
    @Test fun deadlineIsExclusiveAndPublicationCannotExtendIt() {
        val lease=PreviewLease(); lease.arm("a",100)
        assertTrue(lease.publish("a",60_099)); assertFalse(lease.canTake(60_100))
        assertEquals(60_100L,lease.expiresAt)
    }
    @Test fun expiredFrameCannotBePublished() {
        val lease=PreviewLease(); lease.arm("a",100)
        assertFalse(lease.publish("a",60_100))
    }
    @Test fun clearRevokesBothCaptureAndDisplay() {
        val lease=PreviewLease(); lease.arm("a",0); lease.publish("a",1); lease.clear()
        assertFalse(lease.canTake(2)); assertFalse(lease.canPublish("a",2))
    }
}
