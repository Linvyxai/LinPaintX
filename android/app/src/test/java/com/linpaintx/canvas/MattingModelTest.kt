package com.linpaintx.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.HttpURLConnection
import java.security.MessageDigest

class MattingModelTest {
    @Test
    fun theDigestPrintsTheWayDigestsArePublished() {
        // the published check value for the model is lower-case hex; a Java byte is signed, so any
        // digest holds bytes this could print wrong - and "abc" does: bf, fe, da and more
        val abc = MessageDigest.getInstance("SHA-256").digest("abc".toByteArray())
        assertTrue(abc.any { it < 0 })
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex(abc),
        )
        // and the model's own check value round-trips as the text it is published in
        val model = "4c56bbc21588459dda11efba5a4a8ee163969da109ae170fb1988c1c2ea4a90a"
        assertEquals(model.lowercase(), model)
        assertEquals(64, model.length)
    }

    @Test
    fun thePanelCountsTheDownloadInMegabytesAsTheConstantSays() {
        // the button and the progress line share the number; if the asset is re-cut, this is where
        // the two stop agreeing silently
        assertEquals(168, (MattingModel.BYTES / (1024 * 1024)).toInt())
    }

    @Test
    fun theModelIsAskedForByTheSameNameItKeepsOnThePhone() {
        // one name, both sides of the download: the asset in the release and the file in the app's
        // folder are written from the same constant, so nothing can rename one and forget the other
        assertEquals("isnet-general-use.onnx", MattingModel.FILE_NAME)
        assertTrue(MattingModel.DOWNLOAD_URL.endsWith("/" + MattingModel.FILE_NAME))
    }

    /**
     * A release gate, not a suite member: asks the address the app will use and checks the served
     * bytes really carry the digest the app demands. Run by hand (`--tests ... -Dall`) before
     * cutting a release; it walks 168 megabytes, so nothing automatic should.
     */
    @Test
    @org.junit.Ignore("network and 168 MB; run by hand before a release")
    fun theServedFileCarriesTheDigestTheAppDemands() {
        val connection = java.net.URL(MattingModel.DOWNLOAD_URL).openConnection() as java.net.HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 30_000
        assertEquals(HttpURLConnection.HTTP_OK, connection.responseCode)
        val digest = MessageDigest.getInstance("SHA-256")
        var total = 0L
        connection.inputStream.use { from ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = from.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
                total += read
            }
        }
        assertEquals(MattingModel.BYTES, total)
        // the same check the app makes before it renames the file into place
        val published = sha256Hex(digest.digest())
        assertEquals("4c56bbc21588459dda11efba5a4a8ee163969da109ae170fb1988c1c2ea4a90a", published)
    }
}
