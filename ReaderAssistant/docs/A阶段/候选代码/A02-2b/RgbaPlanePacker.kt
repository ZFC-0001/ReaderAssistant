package com.example.localtaskassistant.capture.ocr

import java.nio.ByteBuffer

/** ImageReader平面不能直接当紧密RGBA；仅复制有效像素，去掉行/像素padding。 */
internal object RgbaPlanePacker {
    fun pack(buffer: ByteBuffer, width: Int, height: Int, rowStride: Int, pixelStride: Int): ByteArray {
        require(width>0 && height>0 && width.toLong()*height<=OcrRgbaPixels.MAX_PIXELS)
        require(pixelStride>=4 && rowStride.toLong()>=width.toLong()*pixelStride)
        val needed=(height-1L)*rowStride+(width-1L)*pixelStride+4L
        require(needed<=buffer.remaining())
        val source=buffer.duplicate()
        val start=source.position()
        val output=ByteArray(width*height*4)
        try {
            for(y in 0 until height) {
                val row=(start.toLong()+y.toLong()*rowStride).toInt()
                if(pixelStride==4) { source.position(row); source.get(output,y*width*4,width*4) }
                else for(x in 0 until width) for(channel in 0..3)
                    output[(y*width+x)*4+channel]=source.get(row+x*pixelStride+channel)
            }
            return output
        } catch(error: Exception) { output.fill(0); throw error }
    }
}
