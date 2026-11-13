package com.example.localtaskassistant.capture.ocr

import org.junit.Assert.*
import org.junit.Test

class OcrInputRulesTest {
    @Test fun redGreenBlueAlphaChannelsAreNotSwapped() {
        val pixels=byteArrayOf(10,20,30,-1, 40,50,60,127)
        val row=IntArray(2)
        OcrRgbaPixels.validate(pixels,2,1)
        OcrRgbaPixels.argbRow(pixels,2,0,row)
        assertEquals(0xff0a141e.toInt(),row[0]); assertEquals(0x7f28323c,row[1])
    }
    @Test fun secondRowUsesCorrectOffset() {
        val row=IntArray(1)
        OcrRgbaPixels.argbRow(byteArrayOf(0,0,0,-1, 1,2,3,-1),1,1,row)
        assertEquals(0xff010203.toInt(),row[0])
    }
    @Test fun callerInputIsNotModifiedByConversion() {
        val original=byteArrayOf(1,2,3,-1)
        val copy=original.copyOf()
        OcrRgbaPixels.argbRow(original,1,0,IntArray(1))
        assertArrayEquals(copy,original)
    }
    @Test(expected=IllegalArgumentException::class) fun rowPaddingMustBePackedBeforeAdapter() { OcrRgbaPixels.validate(ByteArray(12),2,1) }
    @Test(expected=IllegalArgumentException::class) fun shortInputRejected() { OcrRgbaPixels.validate(ByteArray(3),1,1) }
    @Test(expected=IllegalArgumentException::class) fun zeroDimensionRejected() { OcrRgbaPixels.validate(ByteArray(0),0,1) }
    @Test(expected=IllegalArgumentException::class) fun overflowSizedDimensionsRejected() { OcrRgbaPixels.validate(ByteArray(4),Int.MAX_VALUE,Int.MAX_VALUE) }
    @Test(expected=IllegalArgumentException::class) fun rowOutsideBufferRejected() { OcrRgbaPixels.argbRow(ByteArray(4),1,1,IntArray(1)) }
    @Test fun positionOrderAndWhitespaceAreNormalizedWithoutSemanticFiltering() {
        assertEquals(listOf("取消明天会议","地点302","过两天再说"),OcrLineOrdering.normalize(listOf(
            OcrLine(" 过两天再说 ",30,1),OcrLine(" 地点302 ",20,1),OcrLine("取消明天会议",10,1))))
    }
    @Test fun sameTextAtTwoPositionsIsNotDeduplicated() {
        assertEquals(listOf("收到","收到"),OcrLineOrdering.normalize(listOf(OcrLine("收到",1,1),OcrLine("收到",2,1))))
    }
    @Test fun emptyLinesIgnoredUnknownBoundsRetained() {
        assertEquals(listOf("任务","未知位置"),OcrLineOrdering.normalize(listOf(OcrLine(" ",0,0),OcrLine("未知位置",null,null),OcrLine("任务",5,5))))
    }
    @Test(expected=IllegalArgumentException::class) fun tooManyLinesDoesNotSilentlyTruncate() { OcrLineOrdering.normalize(List(251) { OcrLine("任务",it,0) }) }
    @Test(expected=IllegalArgumentException::class) fun hugeTextRejectedRatherThanSavedOrTruncated() { OcrLineOrdering.normalize(listOf(OcrLine("a".repeat(64_001),0,0))) }
    @Test fun unfinishedTaskKeepsSlotUntilCompletionEvenIfCallerStopsWaiting() {
        val gate=OcrRunGate()
        assertTrue(gate.tryBegin()); assertFalse(gate.tryBegin())
        gate.finish(); assertTrue(gate.tryBegin())
    }
    @Test fun closePreventsNewRequestsIncludingAfterOldTaskFinishes() {
        val gate=OcrRunGate()
        assertTrue(gate.tryBegin()); gate.close(); gate.finish(); assertFalse(gate.tryBegin())
    }
}
