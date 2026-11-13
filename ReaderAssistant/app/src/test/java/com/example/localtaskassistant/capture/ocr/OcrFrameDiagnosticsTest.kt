package com.example.localtaskassistant.capture.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrFrameDiagnosticsTest {
    @Test fun transparentRgbIsReportedWithoutPretendingItIsOpaque() {
        val bytes=byteArrayOf(-1,-1,-1,0, 0,0,0,-1)
        val result=OcrFrameDiagnostics.summarize(bytes,2,1)
        assertEquals(1,result.alphaZero); assertEquals(1,result.alphaOpaque)
        assertEquals(1,result.darkSamples); assertEquals(1,result.lightSamples)
        assertEquals(127,result.meanLuminance)
        assertArrayEquals(byteArrayOf(-1,-1,-1,0, 0,0,0,-1),bytes)
    }
    @Test fun centerDistinguishesDarkContentFromBrightScreenEdges() {
        val bytes=ByteArray(10*10*4)
        for(y in 0 until 10) for(x in 0 until 10) {
            val index=(y*10+x)*4
            val channel=if(x in 2..7 && y in 2..7) 0.toByte() else (-1).toByte()
            for(c in 0..2) bytes[index+c]=channel
            bytes[index+3]=(-1).toByte()
        }
        val center=OcrFrameDiagnostics.summarize(bytes,10,10,true)
        assertEquals(36,center.samples); assertEquals(36,center.darkSamples)
        assertEquals(0,center.maximumLuminance)
        assertTrue(OcrFrameDiagnostics.summarize(bytes,10,10).lightSamples>0)
    }
    @Test fun largeFramesHaveBoundedSamples() {
        val result=OcrFrameDiagnostics.summarize(ByteArray(100*200*4),100,200)
        assertEquals(48*48,result.samples); assertEquals(result.samples,result.alphaZero)
    }
    @Test fun singlePixelCenterIsValid() {
        val result=OcrFrameDiagnostics.summarize(byteArrayOf(0,0,0,-1),1,1,true)
        assertEquals(1,result.samples); assertEquals(1,result.alphaOpaque)
    }
    @Test(expected=IllegalArgumentException::class) fun paddedInputMustBePackedFirst() {
        OcrFrameDiagnostics.summarize(ByteArray(12),2,1)
    }
}
