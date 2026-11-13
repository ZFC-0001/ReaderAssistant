package com.example.localtaskassistant.capture.ocr

/** 只统计聚合计数，不保留像素、位置矩阵、截图或可逆摘要；不能凭此断言文字可读。 */
internal data class OcrPixelSummary(
    val samples: Int, val alphaZero: Int, val alphaOpaque: Int,
    val minimumAlpha: Int, val maximumAlpha: Int,
    val darkSamples: Int, val lightSamples: Int,
    val minimumLuminance: Int, val maximumLuminance: Int, val meanLuminance: Int,
)

internal object OcrFrameDiagnostics {
    fun summarize(bytes: ByteArray, width: Int, height: Int, centerOnly: Boolean = false): OcrPixelSummary {
        OcrRgbaPixels.validate(bytes, width, height)
        val left=if(centerOnly) width/5 else 0
        val top=if(centerOnly) height/5 else 0
        val right=if(centerOnly) maxOf(left+1,width-width/5) else width
        val bottom=if(centerOnly) maxOf(top+1,height-height/5) else height
        val columns=minOf(48,right-left); val rows=minOf(48,bottom-top)
        var zero=0; var opaque=0; var minAlpha=255; var maxAlpha=0
        var dark=0; var light=0; var minLuma=255; var maxLuma=0; var sum=0
        for(iy in 0 until rows) for(ix in 0 until columns) {
            val x=left+if(columns==1) 0 else ix*(right-left-1)/(columns-1)
            val y=top+if(rows==1) 0 else iy*(bottom-top-1)/(rows-1)
            val at=(y*width+x)*4
            val alpha=bytes[at+3].toInt() and 255
            val luminance=((bytes[at].toInt() and 255)*77+
                (bytes[at+1].toInt() and 255)*150+(bytes[at+2].toInt() and 255)*29) shr 8
            if(alpha==0) zero++
            if(alpha==255) opaque++
            minAlpha=minOf(minAlpha,alpha); maxAlpha=maxOf(maxAlpha,alpha)
            if(luminance<=8) dark++
            if(luminance>=240) light++
            minLuma=minOf(minLuma,luminance); maxLuma=maxOf(maxLuma,luminance); sum+=luminance
        }
        val count=columns*rows
        return OcrPixelSummary(count,zero,opaque,minAlpha,maxAlpha,dark,light,minLuma,maxLuma,sum/count)
    }
}
