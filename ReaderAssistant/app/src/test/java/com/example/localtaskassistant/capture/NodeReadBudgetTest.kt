package com.example.localtaskassistant.capture

import org.junit.Assert.*
import org.junit.Test

/** 用可控单调时钟验证慢节点边界，不依赖真实等待。 */
class NodeReadBudgetTest {
    private class SlowNode(private val tick: () -> Unit) : ReadableNode {
        override val identity: Any = Any()
        override val text: String get() { tick(); return "合成内容" }
        override val description: String? = null
        override val childCount = 0
        override fun childAt(index: Int): ReadableNode? = null
    }
    @Test fun timeoutAfterSlowTextMarksTruncatedAndDoesNotAcceptLateText() {
        var now = 0L
        val collector = NodeTextCollector(NodeReadLimits(maxDurationMillis = 2)) { now }
        val result = collector.read(listOf(SlowNode { now += 2_000_000 }))
        assertTrue(result.truncated)
        assertTrue(result.blocks.isEmpty())
    }
    @Test fun timeoutStopsBeforeReadingAnotherRoot() {
        var now = 0L
        var extraReads = 0
        val collector = NodeTextCollector(NodeReadLimits(maxDurationMillis = 2)) { now }
        val result = collector.read(listOf(SlowNode { now += 2_000_000 }, SlowNode { extraReads++ }))
        assertTrue(result.truncated)
        assertEquals(0, extraReads)
    }
    @Test fun fastNodeRemainsComplete() {
        val collector = NodeTextCollector(NodeReadLimits(maxDurationMillis = 2)) { 0L }
        val result = collector.read(listOf(SlowNode {}))
        assertFalse(result.truncated)
        assertEquals(listOf("合成内容"), result.blocks.map { it.text })
    }
    @Test(expected = IllegalArgumentException::class) fun zeroBudgetIsInvalid() {
        NodeReadLimits(maxDurationMillis = 0)
    }
}
