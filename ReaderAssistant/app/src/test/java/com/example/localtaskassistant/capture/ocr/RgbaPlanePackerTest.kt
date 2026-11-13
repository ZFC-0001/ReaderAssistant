package com.example.localtaskassistant.capture.ocr

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class RgbaPlanePackerTest {
    @Test fun removesRowPaddingWithoutDroppingLastPixel() {
        val input=ByteBuffer.wrap(byteArrayOf(1,2,3,4, 9,9,9,9, 5,6,7,8))
        assertArrayEquals(byteArrayOf(1,2,3,4,5,6,7,8),RgbaPlanePacker.pack(input,1,2,8,4))
    }
    @Test fun removesPixelPadding() {
        val input=ByteBuffer.wrap(byteArrayOf(1,2,3,4, 9,9,9,9, 5,6,7,8))
        assertArrayEquals(byteArrayOf(1,2,3,4,5,6,7,8),RgbaPlanePacker.pack(input,2,1,16,8))
    }
    @Test fun respectsPositionAndDoesNotMoveSource() {
        val input=ByteBuffer.wrap(byteArrayOf(9,9,9,9,1,2,3,4)).apply { position(4) }
        assertArrayEquals(byteArrayOf(1,2,3,4),RgbaPlanePacker.pack(input,1,1,4,4))
        assertEquals(4,input.position())
    }
    @Test fun supportsReadOnlyPlaneBuffers() {
        val input=ByteBuffer.wrap(byteArrayOf(1,2,3,4)).asReadOnlyBuffer()
        assertArrayEquals(byteArrayOf(1,2,3,4),RgbaPlanePacker.pack(input,1,1,4,4))
    }
    @Test fun ownsCopyAfterImageBufferIsReused() {
        val data=byteArrayOf(1,2,3,4)
        val result=RgbaPlanePacker.pack(ByteBuffer.wrap(data),1,1,4,4)
        data.fill(0)
        assertArrayEquals(byteArrayOf(1,2,3,4),result)
    }
    @Test(expected=IllegalArgumentException::class) fun truncatedLastRowRejected() { RgbaPlanePacker.pack(ByteBuffer.allocate(11),1,2,8,4) }
    @Test(expected=IllegalArgumentException::class) fun impossibleRowStrideRejected() { RgbaPlanePacker.pack(ByteBuffer.allocate(8),2,1,4,4) }
    @Test(expected=IllegalArgumentException::class) fun hugeDimensionsRejectedBeforeAllocation() { RgbaPlanePacker.pack(ByteBuffer.allocate(4),Int.MAX_VALUE,Int.MAX_VALUE,4,4) }
    @Test(expected=IllegalArgumentException::class) fun zeroDimensionRejected() { RgbaPlanePacker.pack(ByteBuffer.allocate(0),0,1,4,4) }
}
