package com.example.localtaskassistant.capture.ocr

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

class ProjectionPixelProbeTest {
    private fun rgba(vararg gray: Int): ByteBuffer = ByteBuffer.wrap(gray.flatMap { listOf(it.toByte(), it.toByte(), it.toByte(), 255.toByte()) }.toByteArray())
    @Test fun blackFrameIsNotEvidenceOfReadableContent() {
        val result = ProjectionPixelProbe.inspect(rgba(0, 0, 0, 0), 2, 2, 8, 4)
        assertEquals(0, result.nonBlackSamples); assertFalse(result.hasVariation)
    }
    @Test fun uniformWhiteFrameIsNotEvidenceOfReadableContent() {
        assertFalse(ProjectionPixelProbe.inspect(rgba(255, 255), 2, 1, 8, 4).hasVariation)
    }
    @Test fun contrastingPixelsProduceSignalOnly() {
        val result = ProjectionPixelProbe.inspect(rgba(0, 255), 2, 1, 8, 4)
        assertEquals(255, result.luminanceRange); assertEquals(1, result.nonBlackSamples); assertTrue(result.hasVariation)
    }
    @Test fun rowPaddingIsNotReadAsContent() {
        val data = ByteBuffer.wrap(byteArrayOf(0,0,0,-1, 127,127,127,127, 0,0,0,-1))
        assertFalse(ProjectionPixelProbe.inspect(data, 1, 2, 8, 4).hasVariation)
    }
    @Test fun nonzeroBufferPositionIsRespectedAndPreserved() {
        val data = rgba(255, 0, 255).apply { position(4) }
        assertTrue(ProjectionPixelProbe.inspect(data, 2, 1, 8, 4).hasVariation)
        assertEquals(4, data.position())
    }
    @Test fun bufferLimitIsRespected() {
        val data = rgba(0, 0, 255).apply { limit(8) }
        assertFalse(ProjectionPixelProbe.inspect(data, 2, 1, 8, 4).hasVariation)
    }
    @Test fun alphaOnlyVariationDoesNotCount() {
        val data = ByteBuffer.wrap(byteArrayOf(0,0,0,0, 0,0,0,-1))
        assertFalse(ProjectionPixelProbe.inspect(data, 2, 1, 8, 4).hasVariation)
    }
    @Test fun sampleCountIsBoundedForLargeFrame() {
        val result = ProjectionPixelProbe.inspect(ByteBuffer.allocate(200*200*4), 200, 200, 800, 4)
        assertEquals(576, result.sampleCount)
    }
    @Test(expected = IllegalArgumentException::class) fun shortBufferRejected() {
        ProjectionPixelProbe.inspect(ByteBuffer.allocate(7), 2, 1, 8, 4)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidPixelStrideRejected() {
        ProjectionPixelProbe.inspect(ByteBuffer.allocate(4), 1, 1, 4, 3)
    }
    @Test(expected = IllegalArgumentException::class) fun invalidRowStrideRejected() {
        ProjectionPixelProbe.inspect(ByteBuffer.allocate(8), 2, 1, 4, 4)
    }
    @Test(expected = IllegalArgumentException::class) fun hugeDimensionsRejectedBeforeIndexOverflow() {
        ProjectionPixelProbe.inspect(ByteBuffer.allocate(4), Int.MAX_VALUE, 2, Int.MAX_VALUE, 4)
    }
}
