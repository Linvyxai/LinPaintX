package com.linpaintx.canvas

import com.google.mlkit.common.MlKitException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.FloatBuffer

class MattingMlKitTest {

    @Test
    fun aDownloadThatCanStillFinishIsTriedAgain() {
        // "still downloading" and "the network dropped" are not the same as "never": the panel
        // must keep the built-in model cutting and wait, not hide Google for good
        assertTrue(MattingMlKit.retryable(MlKitException.UNAVAILABLE))
        assertTrue(MattingMlKit.retryable(MlKitException.NETWORK_ISSUE))
        assertTrue(MattingMlKit.retryable(MlKitException.DEADLINE_EXCEEDED))
        assertTrue(MattingMlKit.retryable(MlKitException.INTERNAL))
        assertTrue(MattingMlKit.retryable(MlKitException.UNKNOWN))
    }

    @Test
    fun aDownloadThatCannotFinishIsNotPromised() {
        // retrying these forever would leave the panel saying "getting ready" for a module this
        // device will never have
        assertFalse(MattingMlKit.retryable(MlKitException.UNSUPPORTED))
        assertFalse(MattingMlKit.retryable(MlKitException.NOT_ENOUGH_SPACE))
        assertFalse(MattingMlKit.retryable(MlKitException.PERMISSION_DENIED))
        assertFalse(MattingMlKit.retryable(MlKitException.INVALID_ARGUMENT))
    }

    @Test
    fun theSubjectsBoxLandsWhereItSitsInThePicture() {
        // a 2x2 subject at (1,1) of a 4x3 picture: the union is the picture's own coordinates,
        // because everything downstream (threshold, invert, feather, cut) works in them
        val confidence = ByteArray(4 * 3)
        MattingMlKit.pour(buffer(1f, 1f, 1f, 1f), 1, 1, 2, 2, confidence, 4, 3)
        val marked = confidence.withIndex().filter { it.value.toInt() != 0 }.map { it.index }
        assertEquals(listOf(5, 6, 9, 10), marked)
        assertTrue(confidence.all { (it.toInt() and 0xFF) == 0 || (it.toInt() and 0xFF) == 255 })
    }

    @Test
    fun theConfidenceIsKeptSoTheThresholdCanMove() {
        // the segmenter's answer is kept as it is (0..255), not cut here: the threshold is the
        // caller's, so moving the slider never runs the network again
        val confidence = ByteArray(3)
        MattingMlKit.pour(buffer(0f, 0.5f, 1f), 0, 0, 3, 1, confidence, 3, 1)
        assertEquals(listOf(0, 128, 255), confidence.map { it.toInt() and 0xFF })
        // and the threshold is where the cut lands: 0.5 keeps the half, a hair above drops it,
        // 0 keeps everything and 1 keeps only the surest
        assertEquals(listOf(0, 255, 255), MattingMlKit.subjectMask(confidence, 0.5f).map { it.toInt() and 0xFF })
        assertEquals(listOf(0, 0, 255), MattingMlKit.subjectMask(confidence, 0.51f).map { it.toInt() and 0xFF })
        assertEquals(listOf(255, 255, 255), MattingMlKit.subjectMask(confidence, 0f).map { it.toInt() and 0xFF })
        assertEquals(listOf(0, 0, 255), MattingMlKit.subjectMask(confidence, 1f).map { it.toInt() and 0xFF })
    }

    @Test
    fun theSurerSubjectWinsWhereTwoOverlap() {
        // two subjects over the same cell: the union keeps the more confident one, so a subject
        // the segmenter barely saw cannot drag down one it is sure of
        val confidence = ByteArray(1)
        MattingMlKit.pour(buffer(0.3f), 0, 0, 1, 1, confidence, 1, 1)
        MattingMlKit.pour(buffer(0.8f), 0, 0, 1, 1, confidence, 1, 1)
        assertEquals(204, confidence[0].toInt() and 0xFF)
        MattingMlKit.pour(buffer(0.1f), 0, 0, 1, 1, confidence, 1, 1)
        assertEquals(204, confidence[0].toInt() and 0xFF)
    }

    @Test
    fun aSubjectTouchingTheEdgeIsClippedNotDropped() {
        // a subject that runs past the picture comes back with a box that does; the part inside
        // the picture still has to be marked, and nothing may be written outside it
        val confidence = ByteArray(3 * 2)
        MattingMlKit.pour(buffer(*FloatArray(9) { 1f }), -1, -1, 3, 3, confidence, 3, 2)
        assertEquals(listOf(0, 1, 3, 4), confidence.withIndex().filter { it.value.toInt() != 0 }.map { it.index })
    }

    @Test
    fun aBoxEntirelyOutsideWritesNothing() {
        val confidence = ByteArray(4)
        MattingMlKit.pour(buffer(1f, 1f), 10, 10, 2, 1, confidence, 2, 2)
        assertTrue(confidence.all { it.toInt() == 0 })
    }

    private fun buffer(vararg values: Float): FloatBuffer =
        FloatBuffer.allocate(values.size).apply { put(values) }
}
