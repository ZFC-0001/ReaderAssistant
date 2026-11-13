package com.example.localtaskassistant.capture.ocr

/** 熄屏/锁屏停止独立于取帧回调：没有新Image时也必须结束会话。 */
internal object ProjectionScreenGuard {
    fun mustStop(interactive: Boolean, keyguardLocked: Boolean): Boolean = !interactive || keyguardLocked
}
