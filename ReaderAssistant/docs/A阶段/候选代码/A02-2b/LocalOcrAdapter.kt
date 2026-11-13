package com.example.localtaskassistant.capture.ocr

import android.graphics.Bitmap
import com.example.localtaskassistant.contract.OcrFrame
import com.example.localtaskassistant.contract.TextBlock
import com.example.localtaskassistant.contract.TextRecognizer
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import kotlinx.coroutines.*
import java.util.concurrent.Executor

/**
 * A-02.2候选：随包中文模型；不调用下载API，不写图片/识字文本到文件或日志。
 * 原始RGBA bytes归调用方所有，由调用方finally清零。适配器拥有新建Bitmap。
 * 一次最多一个SDK任务。超时/取消仅停止等待，Bitmap与client等SDK回调后释放。
 */
class LocalOcrAdapter(private val whitePageBinary: Boolean = false) : TextRecognizer, AutoCloseable {
    private val gate = OcrRunGate()
    internal val canAcceptFrame: Boolean get() = gate.canAccept()
    override suspend fun recognize(frame: OcrFrame): Result<List<TextBlock>> = withContext(Dispatchers.Default) {
        try { OcrRgbaPixels.validate(frame.bytes, frame.width, frame.height) }
        catch (_: IllegalArgumentException) { return@withContext Result.failure(IllegalArgumentException("OCR输入格式或尺寸无效")) }
        if (!gate.tryBegin()) return@withContext Result.failure(IllegalStateException("OCR忙碌或已关闭，未接受新画面"))
        var bitmap: Bitmap? = null
        var client: com.google.mlkit.vision.text.TextRecognizer? = null
        var completionOwnsResources = false
        try {
            val image = Bitmap.createBitmap(frame.width, frame.height, Bitmap.Config.ARGB_8888)
            bitmap = image
            val row = IntArray(frame.width)
            try {
                for (y in 0 until frame.height) {
                    if (y % 64 == 0) currentCoroutineContext().ensureActive()
                    OcrRgbaPixels.argbRow(frame.bytes, frame.width, y, row)
                    if (whitePageBinary) OcrWhitePageBinary.apply(row)
                    image.setPixels(row, 0, frame.width, 0, y, frame.width, 1)
                }
            } finally { row.fill(0) }
            currentCoroutineContext().ensureActive()
            val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
            client = recognizer
            val completed = CompletableDeferred<Result<List<TextBlock>>>()
            val task = recognizer.process(InputImage.fromBitmap(image, 0))
            // 先注册无条件完成清理，再开始可取消/可超时的等待。
            task.addOnCompleteListener(DIRECT) { result ->
                val outcome = try {
                    if (!result.isSuccessful) Result.failure(IllegalStateException("本地中文OCR未完成，未记录输入内容"))
                    else {
                        val lines = result.result.textBlocks.flatMap { block -> block.lines.map { line ->
                            OcrLine(line.text, line.boundingBox?.top, line.boundingBox?.left)
                        } }
                        Result.success(OcrLineOrdering.normalize(lines).mapIndexed { index, text -> TextBlock(text, index) })
                    }
                } catch (_: Exception) { Result.failure<List<TextBlock>>(IllegalStateException("OCR输出整理失败或超限")) }
                try { recognizer.close() } catch (_: RuntimeException) { /* 仍须释放Bitmap和槽位。 */ }
                try { image.recycle() } finally { gate.finish() }
                completed.complete(outcome)
            }
            completionOwnsResources = true
            withTimeoutOrNull(8_000) { completed.await() }
                ?: Result.failure(IllegalStateException("OCR等待超时；原生任务结束前不接受新画面"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            Result.failure(IllegalStateException("本地中文OCR启动失败，未记录输入内容"))
        } finally {
            if (!completionOwnsResources) {
                try { client?.close() } catch (_: RuntimeException) { }
                try { bitmap?.recycle() } finally { gate.finish() }
            }
        }
    }
    override fun close() { gate.close() }
    private companion object { val DIRECT = Executor { it.run() } }
}
