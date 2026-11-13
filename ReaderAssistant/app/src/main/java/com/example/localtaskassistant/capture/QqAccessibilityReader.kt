package com.example.localtaskassistant.capture

import android.view.accessibility.AccessibilityNodeInfo
/**
 * A 负责：Android 节点适配，不在此实现任务语义和消息日期猜测。
 * 文字整理委托给可独立测试的 NodeTextCollector；对外 read 签名不变。
 */
class QqAccessibilityReader {
    private val collector = NodeTextCollector()

    fun read(roots: List<AccessibilityNodeInfo>): ReadPage =
        collector.read(roots.map { AndroidReadableNode(it) })

    // TODO(A-01.2): 以 QQ 真机结构验证消息边界、发送人、聊天名称、时间和引用。
    // A-01.1 的合成节点测试不能代替实际 QQ 可读性测试。
    private class AndroidReadableNode(private val source: AccessibilityNodeInfo) : ReadableNode {
        // Android 节点 equals/hashCode 依据源节点及窗口身份；只在当前读取内使用。
        override val identity: Any get() = source
        override val text: String? get() = source.text?.toString()
        override val description: String? get() = source.contentDescription?.toString()
        override val childCount: Int get() = source.childCount
        override fun childAt(index: Int): ReadableNode? = source.getChild(index)?.let { AndroidReadableNode(it) }
    }
}
