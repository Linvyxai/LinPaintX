package com.linpaintx.canvas

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundMaskTest {
    @Test
    fun anEmptyMaskIsNotSomethingToCutBy() {
        assertFalse(backgroundMaskFound(ByteArray(320 * 320)))
        // the mask is 320x320 however big the picture was, so the sizes below are the real ones
        // measured on the photos: the disaster left 3 sure cells, a subject left thousands
        val disaster = ByteArray(320 * 320)
        for (k in 0 until 3) disaster[k] = 191.toByte() // 0.75: above a half
        assertFalse("3 cells of ${disaster.size} found nothing", backgroundMaskFound(disaster))
    }

    @Test
    fun aMaskWithASubjectPasses() {
        // the weakest real subject measured: 2 456 cells of 102 400
        val found = ByteArray(320 * 320)
        for (k in 0 until 2_456) found[k] = (-1).toByte() // 255: the strongest answer there is
        assertTrue(backgroundMaskFound(found))
        assertTrue(backgroundMaskFound(ByteArray(320 * 320) { -1 }))
    }

    @Test
    fun theEnginesUnsignedBytesAreReadAsUnsigned() {
        // a sure cell is 255, which a Kotlin ByteArray holds as -1: counting signed would call the
        // strongest mask in the file nothing found
        val sure = ByteArray(320 * 320) { if (it < 320 * 320 / 2) -1 else 0 }
        assertTrue(backgroundMaskFound(sure))
        // and 127, the biggest positive byte, is exactly not above a half
        assertFalse(backgroundMaskFound(ByteArray(320 * 320) { 127 }))
    }
}
