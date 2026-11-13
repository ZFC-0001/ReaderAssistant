package com.example.localtaskassistant.capture.ocr

import java.nio.ByteBuffer

/** 只在内存抽样像素，不生成Bitmap、图片文件、文字或可逆像素摘要。 */
internal data class ProjectionPixelSignal(
    val width: Int,
    val height: Int,
    val sampleCount: Int,
    val nonBlackSamples: Int,
    val luminanceRange: Int,
) {
    // 仅说明画面有亮度变化；不能证明选中了微信、不是遮罩、或文字清晰可读。
    val hasVariation: Boolean get() = nonBlackSamples > 0 && luminanceRange >= 12
}

internal object ProjectionPixelProbe {
    fun inspect(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): ProjectionPixelSignal {
        require(width in 1..8192 && height in 1..8192)
        require(pixelStride >= 4 && rowStride.toLong() >= width.toLong() * pixelStride)
        val start = buffer.position()
        val required = (height - 1L) * rowStride + (width - 1L) * pixelStride + 4L
        require(required <= buffer.remaining().toLong())
        val columns = minOf(24, width)
        val rows = minOf(24, height)
        var nonBlack = 0
        var minimum = 255
        var maximum = 0
        for (iy in 0 until rows) {
            val y = if (rows == 1) 0 else iy * (height - 1) / (rows - 1)
            for (ix in 0 until columns) {
                val x = if (columns == 1) 0 else ix * (width - 1) / (columns - 1)
                val index = (start.toLong() + y.toLong() * rowStride + x.toLong() * pixelStride).toInt()
                val red = buffer.get(index).toInt() and 255
                val green = buffer.get(index + 1).toInt() and 255
                val blue = buffer.get(index + 2).toInt() and 255
                if (maxOf(red, green, blue) > 8) nonBlack++
                val luminance = (red * 77 + green * 150 + blue * 29) shr 8
                minimum = minOf(minimum, luminance)
                maximum = maxOf(maximum, luminance)
            }
        }
        return ProjectionPixelSignal(width, height, columns * rows, nonBlack, maximum - minimum)
    }
}
