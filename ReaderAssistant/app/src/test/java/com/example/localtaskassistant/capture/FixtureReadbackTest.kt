package com.example.localtaskassistant.capture

import com.example.localtaskassistant.contract.TextBlock
import org.junit.Assert.*
import org.junit.Test

class FixtureReadbackTest {
    private val messages = listOf("A01_TEST_1 明天下午3点提交实验报告", "A01_TEST_2 地点信息楼302", "A01_TEST_3 收到")
    private fun page(texts: List<String>, truncated: Boolean = false) =
        ReadPage(texts.mapIndexed { index, text -> TextBlock(text, index) }, truncated)
    private fun sample(mask: Int = 7, truncated: Boolean = false, blocks: Int = 3) = FixtureSample(blocks, truncated, mask, 1000)

    @Test fun allThreePredefinedStringsAreRecognized() {
        val found = FixtureReadback.inspect(page(messages), 1000)
        assertEquals(3, found.hitCount)
        assertTrue(found.complete)
    }
    @Test fun arbitraryPrivateTextIsNotRetainedInDiagnosticData() {
        val found = FixtureReadback.inspect(page(listOf("合成的无关私密文本", messages[0])), 1000)
        assertEquals(1, found.hitCount)
        assertFalse(found.toString().contains("合成的无关私密文本"))
        assertFalse(found.toString().contains(messages[0]))
    }
    @Test fun duplicateSameMessageDoesNotBecomeThreeMatches() {
        assertEquals(1, FixtureReadback.inspect(page(List(3) { messages[0] }), 1000).hitCount)
    }
    @Test fun identifiersAloneDoNotProveMessageBodyReadable() {
        assertEquals(0, FixtureReadback.inspect(page(listOf("A01_TEST_1", "A01_TEST_2", "A01_TEST_3")), 1000).hitCount)
    }
    @Test fun sourceNodeMayWrapFixtureWithoutLoggingTheWrapper() {
        val found = FixtureReadback.inspect(page(messages.map { "合成的发送者：$it" }), 1000)
        assertTrue(found.complete)
        assertFalse(found.toString().contains("发送者"))
    }
    @Test fun truncatedPageCannotPassEvenWhenAllMessagesMatch() {
        assertFalse(FixtureReadback.inspect(page(messages, true), 1000).complete)
    }
    @Test fun excessiveBlocksCannotPass() { assertFalse(sample(blocks = 251).complete) }
    @Test fun emptyPageCannotPass() { assertFalse(sample(blocks = 0).complete) }
    @Test fun oneCompleteReadIsNotEnough() {
        assertFalse(FixtureProgressTracker().accept("run1", sample()).passed)
    }
    @Test fun twoCompleteReadsPass() {
        val tracker = FixtureProgressTracker()
        tracker.accept("run1", sample())
        assertTrue(tracker.accept("run1", sample()).passed)
    }
    @Test fun differentPartialReadsAreNotCombinedIntoAPass() {
        val tracker = FixtureProgressTracker()
        tracker.accept("run1", sample(mask = 1))
        tracker.accept("run1", sample(mask = 2))
        assertFalse(tracker.accept("run1", sample(mask = 4)).passed)
    }
    @Test fun incompleteReadResetsConsecutiveCount() {
        val tracker = FixtureProgressTracker()
        tracker.accept("run1", sample())
        tracker.accept("run1", sample(truncated = true))
        assertFalse(tracker.accept("run1", sample()).passed)
    }
    @Test fun newRunIdCannotReusePreviousPass() {
        val tracker = FixtureProgressTracker()
        tracker.accept("run1", sample())
        assertTrue(tracker.accept("run1", sample()).passed)
        val fresh = tracker.accept("run2", sample())
        assertFalse(fresh.passed)
        assertEquals(1, fresh.samples)
    }
    @Test fun runIdMustBeBoundedAndSafe() {
        assertThrows(IllegalArgumentException::class.java) { FixtureProgressTracker().accept("../escape", sample()) }
        assertThrows(IllegalArgumentException::class.java) { FixtureProgressTracker().accept("x".repeat(65), sample()) }
    }
    @Test fun countersRemainBoundedAfterManyReads() {
        val tracker = FixtureProgressTracker()
        var last = tracker.accept("run1", sample())
        repeat(10) { last = tracker.accept("run1", sample()) }
        assertEquals(2, last.consecutiveComplete)
    }
    @Test fun invalidMaskOrBlockCountIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { sample(mask = 8) }
        assertThrows(IllegalArgumentException::class.java) { sample(blocks = -1) }
    }
}
