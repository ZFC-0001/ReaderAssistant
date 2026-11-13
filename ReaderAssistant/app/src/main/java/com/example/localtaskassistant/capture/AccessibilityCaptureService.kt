package com.example.localtaskassistant.capture

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.HandlerThread
import android.app.KeyguardManager
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import com.example.localtaskassistant.bootstrap.AssistantApplication
import com.example.localtaskassistant.contract.*
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.io.FileDescriptor
import java.io.PrintWriter
import android.content.pm.ApplicationInfo
import com.example.localtaskassistant.capture.ocr.WechatProjectionAccess
import com.example.localtaskassistant.capture.ocr.ProjectionScopeGate

/** 成员一。读取授权包正文之前检查范围；禁止在回调中查库或运行模型。 */
class AccessibilityCaptureService : AccessibilityService() {
    private val bus get() = (application as AssistantApplication).container.capture
    private var captureThread: HandlerThread? = null
    private var captureHandler: Handler? = null
    private val reader = QqAccessibilityReader()
    private var debugReadback: DebugQqReadback? = null
    // A-01.2a诊断只记固定步骤名和计数，不记包外内容、原文或节点树。
    @Volatile private var lastCaptureGate = "not_started"
    @Volatile private var captureAttempts = 0L
    private var lastRun = 0L
    private val poll = object : Runnable {
        override fun run() { capture(null); captureHandler?.postDelayed(this, 2500) }
    }
    override fun onServiceConnected() {
        bus.connected(true)
        WechatProjectionAccess.attach(this) {
            val state = bus.status.value
            // 先验授权，之后只取活动根的包名；不为OCR遍历其他应用的节点正文。
            if (!state.policy.enabled || TargetApps.WECHAT !in state.policy.allowedPackages || !state.accessibilityConnected) false
            else ProjectionScopeGate.allows(state.policy.enabled, true, state.accessibilityConnected,
                (getSystemService(POWER_SERVICE) as PowerManager).isInteractive,
                (getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked,
                rootInActiveWindow?.packageName?.toString())
        }
        if (captureThread == null) {
            captureThread = HandlerThread("A-QQ-capture").also { it.start() }
            captureHandler = Handler(captureThread!!.looper)
        }
        debugReadback?.close()
        debugReadback = DebugQqReadback(applicationContext) { session ->
            bus.isCurrentSession(session) && bus.status.value.accessibilityConnected &&
                TargetApps.QQ in bus.status.value.policy.allowedPackages
        }
        captureHandler?.removeCallbacksAndMessages(null)
        captureHandler?.postDelayed(poll, 2500)
    }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        // TODO(A-06): 使用窗口包名元数据接入 DesktopDetector；只在回到桌面时汇总。
        // 桌面检测不需要读取其他应用正文，不能为了该功能扩大授权范围。
        if (pkg !in bus.status.value.policy.allowedPackages) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> captureHandler?.postDelayed({ capture(pkg) }, 600)
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastRun < 1000) return
                lastRun = now
                captureHandler?.post { capture(pkg) }
            }
            else -> Unit // 服务只订阅窗口与内容变化，其余事件不处理。
        }
    }
    private fun capture(expectedPackage: String?) {
        captureAttempts = (captureAttempts + 1).coerceAtMost(1_000_000)
        lastCaptureGate = "entered"
        val state = bus.status.value
        if (!state.accessibilityConnected) { lastCaptureGate = "service_disconnected"; return }
        if (!state.policy.enabled) { lastCaptureGate = "policy_paused"; return }
        if (!(getSystemService(POWER_SERVICE) as PowerManager).isInteractive) { lastCaptureGate = "screen_off"; return }
        if ((getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) { lastCaptureGate = "keyguard_locked"; return }
        val root = rootInActiveWindow ?: run { lastCaptureGate = "root_unavailable"; return }
        val pkg = root.packageName?.toString() ?: run { lastCaptureGate = "root_package_missing"; return }
        if (pkg !in state.policy.allowedPackages) { lastCaptureGate = "root_outside_scope"; return }
        if (expectedPackage != null && pkg != expectedPackage) { lastCaptureGate = "event_root_mismatch"; return }
        if (pkg == TargetApps.WECHAT) {
            lastCaptureGate = "wechat_ocr_pending"
            bus.diagnostic("已测微信聊天无障碍不可读；等待 A-02 本地 OCR 接入")
            return
        }
        if (pkg != TargetApps.QQ) { lastCaptureGate = "unsupported_allowed_app"; return }
        val roots = mutableListOf(root)
        windows.orEmpty().mapNotNull { it.root }.forEach { other ->
            if (other.packageName?.toString() == pkg && other != root) roots.add(other)
        }
        lastCaptureGate = "qq_read_started"
        val page = reader.read(roots)
        lastCaptureGate = "qq_read_finished"
        // 远端节点调用返回时可能已暂停/断开；此时不交付本次读取。
        if (!bus.isCurrentSession(state.session) || !bus.status.value.accessibilityConnected) {
            lastCaptureGate = "session_ended_during_read"
            return
        }
        debugReadback?.record(pkg, page, state.session)
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
        WechatProjectionAccess.detach(this)
        captureHandler?.removeCallbacksAndMessages(null)
        bus.connected(false)
    }
    override fun dump(fd: FileDescriptor, writer: PrintWriter, args: Array<out String>) {
        // 仅调试包通过系统服务诊断读取；不扩大采集授权，不返回聊天信息。
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) == 0) return
        val state = bus.status.value
        writer.println("A_QQ_PROBE attempts=$captureAttempts gate=$lastCaptureGate")
        writer.println("A_QQ_PROBE policyEnabled=${state.policy.enabled} qqAllowed=${TargetApps.QQ in state.policy.allowedPackages} connected=${state.accessibilityConnected} currentSession=${bus.isCurrentSession(state.session)}")
        debugReadback?.dump(writer)
    }
    override fun onDestroy() {
        WechatProjectionAccess.detach(this)
        captureHandler?.removeCallbacksAndMessages(null)
        captureThread?.quitSafely()
        captureHandler = null
        captureThread = null
        debugReadback?.close()
        debugReadback = null
        bus.connected(false)
        super.onDestroy()
    }
}
