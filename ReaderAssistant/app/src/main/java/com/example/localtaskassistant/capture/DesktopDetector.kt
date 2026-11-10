package com.example.localtaskassistant.capture

/** 成员一 A-06 提供“返回桌面”事件；成员二据此汇总待确认任务。 */
interface DesktopDetector {
    fun isDesktop(packageName: String): Boolean
}
class PendingDesktopDetector : DesktopDetector {
    override fun isDesktop(packageName: String): Boolean {
        // TODO(A-06): 动态解析默认 HOME，验证最近任务/通知栏/锁屏/多窗口，禁止写死 MIUI。
        return false
    }
}
