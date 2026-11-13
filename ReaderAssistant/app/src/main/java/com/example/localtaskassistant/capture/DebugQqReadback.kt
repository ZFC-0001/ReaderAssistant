package com.example.localtaskassistant.capture

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.PrintWriter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * A-01.2a服务内验证：仅debug应用＋私有control.txt显式开启时记录。
 * 输出只含固定夹具命中计数，不保存原文。独立于Instrumentation，不强停自身服务。
 */
internal class DebugQqReadback(context: Context, private val allowedSession: (Long) -> Boolean) : AutoCloseable {
    private val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    private val directory = File(context.applicationContext.filesDir, "qq-probe")
    private val control = File(directory, "control.txt")
    private val tracker = FixtureProgressTracker()
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
        ArrayBlockingQueue(1), { task -> Thread(task, "A-QQ-test-readback") },
        ThreadPoolExecutor.DiscardOldestPolicy())

    fun record(packageName: String, page: ReadPage, session: Long) {
        if (!debuggable || packageName != "com.tencent.mobileqq" || !allowedSession(session) || !control.isFile) return
        // 原文只在当前调用中查找预设测试串，不进入后台队列。
        val sample = FixtureReadback.inspect(page, System.currentTimeMillis())
        worker.execute {
            if (!allowedSession(session) || !control.isFile) return@execute
            try {
                if (control.length() !in 1L..64L || sample.atMillis < control.lastModified()) return@execute
                val runId = control.readText(Charsets.US_ASCII).trim()
                if (!runId.matches(Regex("[A-Za-z0-9_-]{1,64}"))) return@execute
                val state = tracker.accept(runId, sample)
                if (!allowedSession(session) || !control.isFile || control.readText(Charsets.US_ASCII).trim() != runId) return@execute
                val json = JSONObject().put("runId", state.runId)
                    .put("samples", state.samples).put("consecutiveComplete", state.consecutiveComplete)
                    .put("blocks", sample.blockCount).put("truncated", sample.truncated)
                    .put("fixtureHits", sample.hitCount).put("observedAtMillis", sample.atMillis)
                    .put("passed", state.passed)
                val file = AtomicFile(File(directory, "result.json"))
                val stream = file.startWrite()
                try {
                    stream.write(json.toString().toByteArray(Charsets.UTF_8))
                    file.finishWrite(stream)
                } catch (_: IOException) {
                    file.failWrite(stream)
                }
            } catch (_: IOException) {
                // 无有效报告即未通过，不影响正常采集，也不输出原文日志。
            } catch (_: SecurityException) {
                // 私有文件不可访问时保持诊断关闭。
            }
        }
    }

    override fun close() { worker.shutdownNow() }

    fun dump(writer: PrintWriter) {
        if (!debuggable) return
        writer.println("A_QQ_PROBE controlExists=${control.isFile} workerCompleted=${worker.completedTaskCount} workerQueued=${worker.queue.size} workerShutdown=${worker.isShutdown}")
    }
}
