package com.example.localtaskassistant.capture.ocr

internal data class OcrBand(val top: Int,val bottom: Int) { val height get()=bottom-top }

/** 原尺寸纵向分块，不缩放、不更改颜色；重叠保留跨块文字。仅用于A诊断。 */
internal object OcrBandPlanner {
    fun plan(height: Int,maxHeight: Int=1024,overlap: Int=256): List<OcrBand> {
        require(height in 1..8192 && maxHeight>0 && overlap in 0 until maxHeight)
        if(height<=maxHeight) return listOf(OcrBand(0,height))
        val step=maxHeight-overlap
        val count=(height-maxHeight+step-1)/step+1
        require(count<=16)
        return List(count) { index ->
            val top=(index.toLong()*(height-maxHeight)/(count-1)).toInt()
            OcrBand(top,top+maxHeight)
        }
    }
    fun copy(bytes: ByteArray,width: Int,height: Int,band: OcrBand): ByteArray {
        OcrRgbaPixels.validate(bytes,width,height)
        require(band.top>=0 && band.bottom<=height && band.height>0)
        return bytes.copyOfRange(band.top*width*4,band.bottom*width*4)
    }
}
