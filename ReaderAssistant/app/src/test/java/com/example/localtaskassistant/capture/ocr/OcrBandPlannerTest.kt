package com.example.localtaskassistant.capture.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrBandPlannerTest {
    @Test fun smallFrameUsesOneBand() { assertEquals(listOf(OcrBand(0,480)),OcrBandPlanner.plan(480)) }
    @Test fun phoneFrameUsesThreeOriginalSizeOverlappingBands() {
        assertEquals(listOf(OcrBand(0,1024),OcrBand(768,1792),OcrBand(1536,2560)),OcrBandPlanner.plan(2560))
    }
    @Test fun unevenHeightIsFullyCoveredWithoutGapsOrOversizedBand() {
        for(height in listOf(1025,2561,4097,8192)) {
            val plan=OcrBandPlanner.plan(height)
            assertEquals(0,plan.first().top); assertEquals(height,plan.last().bottom)
            assertTrue(plan.all { it.height in 1..1024 })
            plan.zipWithNext().forEach { (a,b) -> assertTrue(b.top>a.top && a.bottom-b.top>=256) }
        }
    }
    @Test fun copiedBandPreservesAllChannelsAndLeavesSourceUntouched() {
        val bytes=ByteArray(2*3*4) { it.toByte() }; val before=bytes.copyOf()
        val crop=OcrBandPlanner.copy(bytes,2,3,OcrBand(1,3))
        assertArrayEquals(bytes.copyOfRange(8,24),crop)
        crop.fill(0); assertArrayEquals(before,bytes)
    }
    @Test(expected=IllegalArgumentException::class) fun invalidBandRejected() {
        OcrBandPlanner.copy(ByteArray(4),1,1,OcrBand(0,2))
    }
    @Test(expected=IllegalArgumentException::class) fun excessiveOverlapRejected() { OcrBandPlanner.plan(2560,1024,1024) }
    @Test(expected=IllegalArgumentException::class) fun zeroHeightRejected() { OcrBandPlanner.plan(0) }
}
