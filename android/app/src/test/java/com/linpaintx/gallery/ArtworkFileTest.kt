package com.linpaintx.gallery

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkFileTest {
    @Test
    fun linxKeepsLayersAndUndoHistory() {
        val temp = Files.createTempDirectory("linpaintx-file-test").toFile()
        try {
            val source = temp.resolve("source").apply { mkdirs() }
            source.resolve("layers").mkdirs()
            source.resolve("artwork.json").writeText("{\"format\":\"linpaintx-artwork\"}")
            source.resolve("layers/7.png").writeBytes(byteArrayOf(1, 2, 3))
            val history = byteArrayOf(9, 8, 7, 6)
            source.resolve("history.bin").writeBytes(history)

            val packed = ByteArrayOutputStream().also { ArtworkFile.write(source, it) }.toByteArray()
            val restored = temp.resolve("restored")

            assertTrue(ArtworkFile.read(ByteArrayInputStream(packed), restored))
            assertEquals(source.resolve("artwork.json").readText(), restored.resolve("artwork.json").readText())
            assertArrayEquals(history, restored.resolve("history.bin").readBytes())
            assertArrayEquals(byteArrayOf(1, 2, 3), restored.resolve("layers/7.png").readBytes())
        } finally {
            temp.deleteRecursively()
        }
    }
}
