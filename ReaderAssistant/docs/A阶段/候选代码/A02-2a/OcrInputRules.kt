package com.example.localtaskassistant.capture.ocr

/** A内部输入约定：紧密排列RGBA8888，不是JPEG、带padding的Image plane或NV21。 */
internal object OcrRgbaPixels {
    const val MAX_PIXELS = 4_194_304L
    fun validate(bytes: ByteArray, width: Int, height: Int) {
        require(width > 0 && height > 0 && width.toLong() * height <= MAX_PIXELS) { "OCR画面尺寸无效或超限" }
        require(bytes.size.toLong() == width.toLong() * height * 4) { "OCR需要紧密排列的RGBA8888" }
    }
    fun argbRow(bytes: ByteArray, width: Int, row: Int, output: IntArray) {
        require(width > 0 && row >= 0 && output.size >= width)
        val start = row.toLong() * width * 4
        require(start + width.toLong() * 4 <= bytes.size)
        for (x in 0 until width) {
            val at = start.toInt() + x * 4
            val r = bytes[at].toInt() and 255
            val g = bytes[at+1].toInt() and 255
            val b = bytes[at+2].toInt() and 255
            val a = bytes[at+3].toInt() and 255
            output[x] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}

internal data class OcrLine(val text: String, val top: Int?, val left: Int?)
internal object OcrLineOrdering {
    fun normalize(lines: List<OcrLine>): List<String> {
        val kept = lines.filter { it.text.isNotBlank() }
        require(kept.size <= 250 && kept.sumOf { it.text.length.toLong() } <= 64_000) { "OCR内容超限，未返回未标记的截断结果" }
        // 仅是画面位置顺序，不猜发送人、聊天边界、源日期或引用关系。
        return kept.sortedWith(compareBy<OcrLine> { it.top ?: Int.MAX_VALUE }
            .thenBy { it.left ?: Int.MAX_VALUE }).map { it.text.trim() }
    }
}

/** 等待协程取消不等于原生任务已结束；只有SDK完成回调可归还这一个槽位。 */
internal class OcrRunGate {
    private var active = false
    private var closed = false
    @Synchronized fun tryBegin(): Boolean {
        if (active || closed) return false
        active = true
        return true
    }
    @Synchronized fun finish() { active = false }
    @Synchronized fun close() { closed = true }
}
