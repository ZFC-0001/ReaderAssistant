package com.example.localtaskassistant.capture

import android.view.accessibility.AccessibilityNodeInfo
import com.example.localtaskassistant.contract.TextBlock

data class ReadPage(val blocks: List<TextBlock>, val truncated: Boolean)

/** A 负责。保留节点顺序，不再把全屏拼成 1000 字后当成一条消息。 */
class QqAccessibilityReader {
    fun read(roots: List<AccessibilityNodeInfo>): ReadPage {
        val result = mutableListOf<TextBlock>()
        var visited = 0
        var truncated = false
        fun visit(node: AccessibilityNodeInfo?, depth: Int) {
            if (node == null) return
            if (depth > 30 || visited >= 1500 || result.size >= 250) { truncated = true; return }
            visited++
            val text = node.text?.toString()?.trim().orEmpty()
            val description = node.contentDescription?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) result.add(TextBlock(text, result.size))
            if (description.isNotEmpty() && description != text) result.add(TextBlock(description, result.size))
            for (index in 0 until node.childCount) visit(node.getChild(index), depth + 1)
        }
        roots.forEach { visit(it, 0) }
        // TODO(A-01): 结合 QQ 真机节点识别消息边界、发送人、聊天名称、原始时间及引用块。
        // 当前只是有序节点文本，不宣称已识别消息、本人昵称或源日期。
        return ReadPage(result, truncated)
    }
}
