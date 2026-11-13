package com.example.localtaskassistant.capture

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

/** A-01.1：合成数据，不读手机、不需要网络、不保存真实聊天。 */
class NodeTextCollectorTest {
    private class Node(
        private val value: String? = null,
        private val spoken: String? = null,
        override val identity: Any = Any(),
        val children: MutableList<ReadableNode?> = mutableListOf(),
        var textFailure: RuntimeException? = null,
        var childFailure: RuntimeException? = null,
    ) : ReadableNode {
        override val text: String? get() { textFailure?.let { throw it }; return value }
        override val description: String? get() = spoken
        override val childCount: Int get() = children.size
        override fun childAt(index: Int): ReadableNode? { childFailure?.let { throw it }; return children[index] }
    }
    private fun ReadPage.texts() = blocks.map { it.text }
    private fun collect(vararg roots: ReadableNode) = NodeTextCollector().read(roots.toList())

    @Test fun emptyRootsAreAnEmptyCompletePage() {
        val page = collect()
        assertTrue(page.blocks.isEmpty())
        assertFalse(page.truncated)
    }
    @Test fun blankValuesAreSkippedAndWhitespaceIsTrimmed() {
        val page = collect(Node("  ", "\n"), Node("  提交报告  "))
        assertEquals(listOf("提交报告"), page.texts())
        assertFalse(page.truncated)
    }
    @Test fun traversalKeepsRootThenChildOrder() {
        val root = Node("群名", children = mutableListOf(Node("消息一"), Node("消息二")))
        val page = collect(root, Node("另一个窗口"))
        assertEquals(listOf("群名", "消息一", "消息二", "另一个窗口"), page.texts())
        assertEquals(listOf(0, 1, 2, 3), page.blocks.map { it.order })
    }
    @Test fun distinctTextAndDescriptionAreBothPreserved() {
        assertEquals(listOf("消息", "引用说明"), collect(Node("消息", "引用说明")).texts())
    }
    @Test fun sameNodeTextAndDescriptionAreNotDuplicatedAfterTrimming() {
        assertEquals(listOf("收到"), collect(Node(" 收到 ", "收到")).texts())
    }
    @Test fun identicalBodiesOnDifferentNodesAreNotDeleted() {
        assertEquals(listOf("明天交", "明天交"), collect(Node("明天交"), Node("明天交")).texts())
    }
    @Test fun duplicateRootIsOnlyReadOnce() {
        val root = Node("同一根")
        assertEquals(listOf("同一根"), collect(root, root).texts())
    }
    @Test fun overlappingRootAndChildDoNotDuplicateTheSameNode() {
        val child = Node("同一条消息")
        val root = Node("群名", children = mutableListOf(child))
        assertEquals(listOf("群名", "同一条消息"), collect(root, child).texts())
    }
    @Test fun separateWrappersWithEqualIdentityAreStillOneNode() {
        assertEquals(listOf("原节点"), collect(Node("原节点", identity = "window-1/node-2"),
            Node("重复包装", identity = "window-1/node-2")).texts())
    }
    @Test fun cycleTerminatesWithoutRepeatedText() {
        val root = Node("根")
        val child = Node("子")
        root.children.add(child)
        child.children.add(root)
        val page = collect(root)
        assertEquals(listOf("根", "子"), page.texts())
        assertFalse(page.truncated)
    }
    @Test fun anExactlyFullPageIsNotMarkedTruncated() {
        val page = NodeTextCollector(NodeReadLimits(maxTextBlocks = 1)).read(listOf(Node("唯一文字")))
        assertEquals(1, page.blocks.size)
        assertFalse(page.truncated)
    }
    @Test fun textAndDescriptionCannotExceedTheBlockLimit() {
        val page = NodeTextCollector(NodeReadLimits(maxTextBlocks = 1)).read(listOf(Node("正文", "描述")))
        assertEquals(listOf("正文"), page.texts())
        assertTrue(page.truncated)
    }
    @Test fun unseenAdditionalTextIsMarkedTruncated() {
        val page = NodeTextCollector(NodeReadLimits(maxTextBlocks = 1)).read(listOf(Node("第一条"), Node("第二条")))
        assertEquals(listOf("第一条"), page.texts())
        assertTrue(page.truncated)
    }
    @Test fun defaultLimitNeverProducesTwoHundredAndFiftyOneBlocks() {
        val roots = (1..249).map { Node("文字$it") } + Node("第250块", "不应变成第251块")
        val page = NodeTextCollector().read(roots)
        assertEquals(250, page.blocks.size)
        assertEquals("第250块", page.blocks.last().text)
        assertTrue(page.truncated)
    }
    @Test fun blankNodesAfterFullTextDoNotFalselyMeanTextWasLost() {
        val page = NodeTextCollector(NodeReadLimits(maxTextBlocks = 1)).read(listOf(Node("正文"), Node(" ")))
        assertFalse(page.truncated)
    }
    @Test fun nodeReadBudgetStopsBeforeReadingAnotherNode() {
        val page = NodeTextCollector(NodeReadLimits(maxNodeReads = 1)).read(listOf(Node("首节点"), Node("后节点")))
        assertEquals(listOf("首节点"), page.texts())
        assertTrue(page.truncated)
    }
    @Test fun exactNodeReadBudgetWithoutRemainingNodesIsComplete() {
        val page = NodeTextCollector(NodeReadLimits(maxNodeReads = 1)).read(listOf(Node("唯一节点")))
        assertFalse(page.truncated)
    }
    @Test fun childReadsConsumeTheSameBudget() {
        val root = Node("父", children = mutableListOf(Node("子一"), Node("子二")))
        val page = NodeTextCollector(NodeReadLimits(maxNodeReads = 2)).read(listOf(root))
        assertEquals(listOf("父", "子一"), page.texts())
        assertTrue(page.truncated)
    }
    @Test fun nullChildrenAlsoConsumeBudgetInsteadOfLoopingWithoutBound() {
        val root = Node("父", children = MutableList(20) { null })
        val page = NodeTextCollector(NodeReadLimits(maxNodeReads = 3)).read(listOf(root))
        assertEquals(listOf("父"), page.texts())
        assertTrue(page.truncated)
    }
    @Test fun depthZeroAllowsRootButMarksUnreadChild() {
        val root = Node("父", children = mutableListOf(Node("子")))
        val page = NodeTextCollector(NodeReadLimits(maxDepth = 0)).read(listOf(root))
        assertEquals(listOf("父"), page.texts())
        assertTrue(page.truncated)
    }
    @Test fun nodePastDepthLimitCanStillBeReadAsAShallowerRoot() {
        val child = Node("子")
        val root = Node("父", children = mutableListOf(child))
        val page = NodeTextCollector(NodeReadLimits(maxDepth = 0)).read(listOf(root, child))
        assertEquals(listOf("父", "子"), page.texts())
        assertTrue(page.truncated)
    }
    @Test fun missingChildMarksPartialButKeepsKnownSiblings() {
        val root = Node("父", children = mutableListOf(null, Node("可读子")))
        val page = collect(root)
        assertEquals(listOf("父", "可读子"), page.texts())
        assertTrue(page.truncated)
    }
    @Test fun expiredNodeMarksPartialAndOtherRootsCanStillBeRead() {
        val page = collect(Node(textFailure = IllegalStateException()), Node("另一根"))
        assertEquals(listOf("另一根"), page.texts())
        assertTrue(page.truncated)
    }
    @Test fun childReadFailureDoesNotPretendThePageIsComplete() {
        val root = Node("父", children = mutableListOf(Node("子")), childFailure = IllegalStateException())
        val page = collect(root, Node("另一根"))
        assertEquals(listOf("父", "另一根"), page.texts())
        assertTrue(page.truncated)
    }
    @Test fun permissionFailureStopsFurtherReads() {
        val page = collect(Node(textFailure = SecurityException()), Node("不能继续读取"))
        assertTrue(page.blocks.isEmpty())
        assertTrue(page.truncated)
    }
    @Test fun cancellationIsNotSwallowedAsAnExpiredNode() {
        assertThrows(CancellationException::class.java) {
            collect(Node(textFailure = CancellationException("合成测试取消")))
        }
    }
    @Test fun aNewReadDoesNotReuseOldVisitedNodes() {
        val collector = NodeTextCollector()
        val node = Node("重复读取仍交给后续页面去重")
        assertEquals(collector.read(listOf(node)), collector.read(listOf(node)))
    }
    @Test fun collectionDoesNotInventSenderOrParseDates() {
        val page = collect(Node("昨天开会"), Node("星期三13:41"))
        assertEquals(listOf("昨天开会", "星期三13:41"), page.texts())
        assertTrue(page.blocks.all { it.sender == null })
    }
    @Test fun invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { NodeReadLimits(maxDepth = -1) }
        assertThrows(IllegalArgumentException::class.java) { NodeReadLimits(maxNodeReads = 0) }
        assertThrows(IllegalArgumentException::class.java) { NodeReadLimits(maxTextBlocks = 0) }
    }
}
