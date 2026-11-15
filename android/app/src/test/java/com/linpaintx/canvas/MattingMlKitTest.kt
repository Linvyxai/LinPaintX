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
        // because everything downstream (invert, feather, cut) works in them
        val mask = ByteArray(4 * 3)
        MattingMlKit.pour(buffer(1f, 1f, 1f, 1f), 1, 1, 2, 2, mask, 4, 3)
        val marked = mask.withIndex().filter { it.value.toInt() != 0 }.map { it.index }
        assertEquals(listOf(5, 6, 9, 10), marked)
        assertTrue(mask.all { (it.toInt() and 0xFF) == 0 || (it.toInt() and 0xFF) == 255 })
    }

    @Test
    fun aConfidenceOnTheFenceIsNotSubject() {
        // the edge of a subject is where the model is least sure; 0.5 exactly is left out so the
        // mask is the same on every run instead of tipping either way
        val mask = ByteArray(2)
        MattingMlKit.pour(buffer(0.5f, 0.5001f), 0, 0, 2, 1, mask, 2, 1)
        assertEquals(0, mask[0].toInt())
        assertEquals(255, mask[1].toInt() and 0xFF)
    }

    @Test
    fun aSubjectTouchingTheEdgeIsClippedNotDropped() {
        // a subject that runs past the picture comes back with a box that does; the part inside
        // the picture still has to be marked, and nothing may be written outside it
        val mask = ByteArray(3 * 2)
        MattingMlKit.pour(buffer(*FloatArray(9) { 1f }), -1, -1, 3, 3, mask, 3, 2)
        assertEquals(listOf(0, 1, 3, 4), mask.withIndex().filter { it.value.toInt() != 0 }.map { it.index })
    }

    @Test
    fun aBoxEntirelyOutsideWritesNothing() {
        val mask = ByteArray(4)
        MattingMlKit.pour(buffer(1f, 1f), 10, 10, 2, 1, mask, 2, 2)
        assertTrue(mask.all { it.toInt() == 0 })
    }

    private fun buffer(vararg values: Float): FloatBuffer =
        FloatBuffer.allocate(values.size).apply { put(values) }
}
