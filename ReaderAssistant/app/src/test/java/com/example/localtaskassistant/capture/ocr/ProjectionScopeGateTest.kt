package com.example.localtaskassistant.capture.ocr

import org.junit.Assert.*
import org.junit.Test

/** 同时满足授权、选择、服务连接、解锁和微信前台，才允许检查画面像素。 */
class ProjectionScopeGateTest {
    @Test fun onlyExplicitWechatForegroundIsAllowed() { assertTrue(ProjectionScopeGate.allows(true,true,true,true,false,"com.tencent.mm")) }
    @Test fun qqOrUnknownForegroundIsRejected() {
        assertFalse(ProjectionScopeGate.allows(true,true,true,true,false,"com.tencent.mobileqq"))
        assertFalse(ProjectionScopeGate.allows(true,true,true,true,false,null))
    }
    @Test fun pausedRecognitionRejected() { assertFalse(ProjectionScopeGate.allows(false,true,true,true,false,"com.tencent.mm")) }
    @Test fun unselectedWechatRejected() { assertFalse(ProjectionScopeGate.allows(true,false,true,true,false,"com.tencent.mm")) }
    @Test fun disconnectedAccessibilityRejected() { assertFalse(ProjectionScopeGate.allows(true,true,false,true,false,"com.tencent.mm")) }
    @Test fun lockedOrScreenOffRejected() {
        assertFalse(ProjectionScopeGate.allows(true,true,true,true,true,"com.tencent.mm"))
        assertFalse(ProjectionScopeGate.allows(true,true,true,false,false,"com.tencent.mm"))
    }
}
