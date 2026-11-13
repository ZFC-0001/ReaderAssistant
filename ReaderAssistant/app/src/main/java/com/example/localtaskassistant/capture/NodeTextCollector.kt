package com.example.localtaskassistant.capture

import com.example.localtaskassistant.contract.TextBlock
import java.util.concurrent.CancellationException

/**
 * A 内部的节点读取边界。测试使用合成节点，Android 适配器使用真实节点。
 * identity 是本次读取内的节点身份，绝不是来源消息指纹或任务身份。
 */
internal interface ReadableNode {
    val identity: Any
    val text: String?
    val description: String?
    val childCount: Int
    fun childAt(index: Int): ReadableNode?
}

/**
 * 根节点深度为0。节点预算包含根读取及每次取子节点的尝试：
 * 空子节点、重复节点也消耗预算，避免损坏或变化中的树造成无界读取。
 */
internal data class NodeReadLimits(
    val maxDepth: Int = 30,
    val maxNodeReads: Int = 1500,
    val maxTextBlocks: Int = 250,
    val maxDurationMillis: Long = 2000,
) {
    init {
        require(maxDepth >= 0)
        require(maxNodeReads > 0)
        require(maxTextBlocks > 0)
        require(maxDurationMillis in 1..60_000)
    }
}

/**
 * A-01.1：可以在普通 JVM 中验证的节点文字整理，不依赖 Android。
 * 只按节点身份去除重叠窗口/重复根，不按正文去重，避免误删两条同文消息。
 * 所有临时状态仅存在于本次 read，结束后不留存节点或原文。
 */
internal class NodeTextCollector(
    private val limits: NodeReadLimits = NodeReadLimits(),
    private val nanoTime: () -> Long = System::nanoTime,
) {
    fun read(roots: List<ReadableNode>): ReadPage {
        val blocks = mutableListOf<TextBlock>()
        val seenNodes = mutableSetOf<Any>()
        var nodeReads = 0
        var truncated = false
        var stopped = false
        val started = nanoTime()
        fun withinBudget(): Boolean {
            if (stopped) return false
            if (nanoTime() - started >= limits.maxDurationMillis * 1_000_000) {
                truncated = true
                stopped = true
                return false
            }
            return true
        }

        fun claimRead(): Boolean {
            if (!withinBudget()) return false
            if (nodeReads >= limits.maxNodeReads) {
                truncated = true
                stopped = true
                return false
            }
            nodeReads++
            return true
        }

        fun append(value: String) {
            if (value.isEmpty() || stopped) return
            // 每次追加前检查，text + description 也不能突破块数上限。
            if (blocks.size >= limits.maxTextBlocks) {
                truncated = true
                stopped = true
                return
            }
            blocks.add(TextBlock(text = value, order = blocks.size))
        }

        fun visit(node: ReadableNode, depth: Int) {
            if (!withinBudget()) return
            if (depth > limits.maxDepth) {
                truncated = true
                return
            }
            try {
                if (!seenNodes.add(node.identity)) return
                val text = node.text?.trim().orEmpty()
                if (!withinBudget()) return
                append(text)
                if (stopped) return
                val description = node.description?.trim().orEmpty()
                if (!withinBudget()) return
                if (description != text) append(description)
                if (stopped) return

                val count = node.childCount
                if (!withinBudget()) return
                if (count < 0) {
                    truncated = true
                    return
                }
                for (index in 0 until count) {
                    if (!claimRead()) return
                    val child = node.childAt(index)
                    if (!withinBudget()) return
                    if (child == null) {
                        // 窗口可能在读取中变化，缺节点必须标记，不能当完整页面。
                        truncated = true
                    } else {
                        visit(child, depth + 1)
                    }
                    if (stopped) return
                }
            } catch (cancelled: CancellationException) {
                // CancellationException 也是 IllegalStateException 的子类，必须先传播取消。
                throw cancelled
            } catch (_: IllegalStateException) {
                // 节点失效时允许其他根继续读取，但整页不能冒充完整。
                truncated = true
            } catch (_: SecurityException) {
                // 权限不可用时立即停止，不再尝试其他节点。
                truncated = true
                stopped = true
            }
        }

        for (root in roots) {
            if (!claimRead()) break
            visit(root, 0)
        }
        // 时间预算是协作式的：单次Android远端调用仍可能超过预算，返回后停止继续读取。
        withinBudget()
        return ReadPage(blocks.toList(), truncated)
    }
}
