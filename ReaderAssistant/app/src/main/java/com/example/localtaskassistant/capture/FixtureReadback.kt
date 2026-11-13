package com.example.localtaskassistant.capture

/** A内部调试数据，只含计数/布尔值；不持有消息、昵称或节点。 */
internal data class FixtureSample(val blockCount: Int, val truncated: Boolean, val hitMask: Int, val atMillis: Long) {
    init { require(blockCount >= 0 && hitMask in 0..7) }
    val hitCount: Int get() = Integer.bitCount(hitMask)
    val complete: Boolean get() = !truncated && blockCount in 1..250 && hitMask == 7
}

internal object FixtureReadback {
    private val fixtures = listOf(
        "A01_TEST_1 明天下午3点提交实验报告",
        "A01_TEST_2 地点信息楼302",
        "A01_TEST_3 收到",
    )
    fun inspect(page: ReadPage, atMillis: Long): FixtureSample {
        var mask = 0
        fixtures.forEachIndexed { index, expected ->
            if (page.blocks.any { it.text.contains(expected) }) mask = mask or (1 shl index)
        }
        return FixtureSample(page.blocks.size, page.truncated, mask, atMillis)
    }
}

internal data class FixtureProgress(
    val runId: String,
    val samples: Int,
    val consecutiveComplete: Int,
    val last: FixtureSample,
) {
    val passed: Boolean get() = consecutiveComplete >= 2
}

/** 两次完整读取才通过。新runId重置，不能用不同帧的零散命中拼成3/3。 */
internal class FixtureProgressTracker {
    private var previous: FixtureProgress? = null
    fun accept(runId: String, sample: FixtureSample): FixtureProgress {
        require(runId.matches(Regex("[A-Za-z0-9_-]{1,64}")))
        val old = previous?.takeIf { it.runId == runId }
        val count = if (old == null) 1 else (old.samples + 1).coerceAtMost(100_000)
        val consecutive = if (sample.complete) ((old?.consecutiveComplete ?: 0) + 1).coerceAtMost(2) else 0
        return FixtureProgress(runId, count, consecutive, sample).also { previous = it }
    }
}
