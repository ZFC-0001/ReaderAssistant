package com.example.readertest

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

data class CapturedText(
    val packageName: String,
    val className: String,
    val eventType: Int,
    val time: Long,
    val text: String
) {
    fun eventName(): String {
        val type = when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> "窗口切换"
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> "内容变化"
            else -> "事件$eventType"
        }
        val t = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(time)
        return "$type $t"
    }
}

object CaptureBuffer {
    const val MAX_ITEMS = 200
    val items = mutableListOf<CapturedText>()

    private val nameCache = mapOf(
        "com.tencent.mm" to "微信",
        "com.tencent.mobileqq" to "QQ"
    )
    var version = 0
        private set

    @Synchronized
    fun add(item: CapturedText) {
        items.add(0, item)
        if (items.size > MAX_ITEMS) items.removeAt(items.lastIndex)
        version++
    }

    fun appName(pkg: String): String = nameCache[pkg] ?: pkg
}

class ScreenReaderService : AccessibilityService() {

    private var lastRun = 0L
    private val handler = Handler(Looper.getMainLooper())
    private var pollingRunnable: Runnable? = null
    private var lastDiagnostic = ""

    override fun onServiceConnected() {
        super.onServiceConnected()
        CaptureBuffer.add(
            CapturedText("诊断", "", 0, System.currentTimeMillis(), "(服务) 已连接")
        )
        startPolling()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        if (pkg == packageName) return

        val now = System.currentTimeMillis()
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handler.postDelayed({
                    captureFromRoot(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, pkg)
                }, 600)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (now - lastRun < 1000) return
                lastRun = now
                captureFromRoot(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, pkg)
            }
        }
    }

    private fun startPolling() {
        pollingRunnable = object : Runnable {
            override fun run() {
                captureFromRoot(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED, null)
                handler.postDelayed(this, 2500)
            }
        }
        handler.postDelayed(pollingRunnable!!, 3000)
    }

    private fun stopPolling() {
        pollingRunnable?.let { handler.removeCallbacks(it) }
        pollingRunnable = null
    }

    private fun captureFromRoot(eventType: Int, expectedPkg: String?) {
        val root = rootInActiveWindow
        if (root == null) {
            addDiagnostic("(诊断) rootInActiveWindow 为 null")
            return
        }
        val rootPkg = root.packageName?.toString() ?: ""
        if (rootPkg.isEmpty()) {
            addDiagnostic("(诊断) 活动窗口存在但包名为空")
            return
        }
        if (rootPkg == packageName) return
        if (expectedPkg != null && rootPkg != expectedPkg) {
            addDiagnostic("(诊断) 事件包名=$expectedPkg ≠ 活动窗口=$rootPkg")
        }

        val allTexts = mutableListOf<String>()

        // 路径1：活动窗口根节点
        collectTexts(root, allTexts, 0)

        // 路径2：遍历所有窗口（兜底微信的独立子窗口）
        var windowCount = 1
        try {
            val allWindows = windows
            if (allWindows != null && allWindows.size > 1) {
                for (window in allWindows) {
                    val windowRoot = window.root ?: continue
                    val winPkg = windowRoot.packageName?.toString() ?: continue
                    if (winPkg != rootPkg) continue
                    windowCount++
                    collectTexts(windowRoot, allTexts, 0)
                }
            }
        } catch (_: Exception) {}

        val joined = allTexts.joinToString(" | ").trim().take(1000)

        if (joined.isBlank()) {
            addDiagnostic("(诊断) $rootPkg 已捕获（${windowCount}窗口）但无文字")
            return
        }

        val latest = CaptureBuffer.items.firstOrNull()
        if (latest != null && latest.packageName == rootPkg && latest.text == joined) return

        CaptureBuffer.add(CapturedText(rootPkg, "", eventType, System.currentTimeMillis(), joined))
    }

    private fun addDiagnostic(message: String) {
        if (message == lastDiagnostic) return
        lastDiagnostic = message
        CaptureBuffer.add(CapturedText("诊断", "", 0, System.currentTimeMillis(), message))
    }

    private fun collectTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int) {
        if (node == null || depth > 30 || out.size > 100) return
        node.text?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it) }
        for (i in 0 until node.childCount) {
            collectTexts(node.getChild(i), out, depth + 1)
        }
    }

    override fun onInterrupt() {
        stopPolling()
        CaptureBuffer.add(CapturedText("诊断", "", 0, System.currentTimeMillis(), "(服务) 被中断"))
    }
}