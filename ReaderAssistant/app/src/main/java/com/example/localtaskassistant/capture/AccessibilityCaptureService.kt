package com.example.localtaskassistant.capture

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.app.KeyguardManager
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import com.example.localtaskassistant.bootstrap.AssistantApplication
import com.example.localtaskassistant.contract.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

/** 成员一。读取授权包正文之前检查范围；禁止在回调中查库或运行模型。 */
class AccessibilityCaptureService : AccessibilityService() {
    private val bus get() = (application as AssistantApplication).container.capture
    private val handler = Handler(Looper.getMainLooper())
    private val reader = QqAccessibilityReader()
    private var lastRun = 0L
    private val poll = object : Runnable {
        override fun run() { capture(null); handler.postDelayed(this, 2500) }
    }
    override fun onServiceConnected() {
        bus.connected(true)
        handler.removeCallbacks(poll)
        handler.postDelayed(poll, 2500)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        // TODO(A-06): 使用窗口包名元数据接入 DesktopDetector；只在回到桌面时汇总。
        // 桌面检测不需要读取其他应用正文，不能为了该功能扩大授权范围。
        if (pkg !in bus.status.value.policy.allowedPackages) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> handler.postDelayed({ capture(pkg) }, 600)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastRun < 1000) return
                lastRun = now
                capture(pkg)
            }
            else -> Unit // 服务只订阅窗口与内容变化，其余事件不处理。
        }
    }
    private fun capture(expectedPackage: String?) {
        val state = bus.status.value
        if (!state.policy.enabled) return
        if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive) return
        if ((getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) return
        val root = rootInActiveWindow ?: return
        val pkg = root.packageName?.toString() ?: return
        if (pkg !in state.policy.allowedPackages) return
        if (expectedPackage != null && pkg != expectedPackage) return
        if (pkg == TargetApps.WECHAT) {
            bus.diagnostic("已测微信聊天无障碍不可读；等待 A-02 本地 OCR 接入")
            return
        }
        if (pkg != TargetApps.QQ) return
        val roots = mutableListOf(root)
        windows.orEmpty().mapNotNull { it.root }.forEach { other ->
            if (other.packageName?.toString() == pkg && other != root) roots.add(other)
        }
        val page = reader.read(roots)
        if (page.blocks.isEmpty()) { bus.diagnostic("授权窗口未提供可读文字"); return }
        val normalized = page.blocks.joinToString("\u001f") { it.text }
        val hash = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
            .joinToString("") { "%02x".format(it) }
        bus.offer(CapturedContent(
            captureId = UUID.randomUUID().toString(), session = state.session,
            packageName = pkg, method = CaptureMethod.ACCESSIBILITY, blocks = page.blocks,
            observedAt = Instant.now(), pageFingerprint = hash, truncated = page.truncated,
        ))
    }
    override fun onInterrupt() {
        handler.removeCallbacksAndMessages(null)
        bus.connected(false)
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        bus.connected(false)
        super.onDestroy()
    }
}
