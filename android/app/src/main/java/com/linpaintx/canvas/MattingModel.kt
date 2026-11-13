package com.linpaintx.canvas

import android.app.Application
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/**
 * The detailed background-cutting model: one ONNX file the person downloads once, checked against
 * its digest before the engine is told to use it. Until that succeeds — and whenever it fails —
 * background removal keeps the small model shipped inside the app, so the switch never runs dry.
 *
 * The file is kept in the app's own folder, next to the brushes: a phone that downloaded it once
 * installs it again at every start-up through [restore], without asking the network at all.
 */
object MattingModel {

    /** The name the file keeps on the phone and in the download address; the two may not drift. */
    internal const val FILE_NAME = "isnet-general-use.onnx"

    /** Our own release's asset: the Apache-2.0 IS-Net weights, kept where the app's files live. */
    internal const val DOWNLOAD_URL =
        "https://github.com/Linvyxai/LinPaintX/releases/download/matting-isnet-1/" + FILE_NAME

    /** The sha256 of the file that is served; anything else is deleted, never installed. */
    private const val DIGEST = "4c56bbc21588459dda11efba5a4a8ee163969da109ae170fb1988c1c2ea4a90a"

    /** What the file weighs; the progress line counts bytes of this. */
    const val BYTES = 176_213_804L

    /** Where the downloaded model lives; [download] writes a `.part` beside it and renames. */
    fun file(app: Application): File = File(app.filesDir, FILE_NAME)

    /**
     * Installs the file an earlier download left behind, if it is there; whether it worked. At
     * start-up, so keeping the better cut costs no data after the first time.
     */
    suspend fun restore(app: Application): Boolean = withContext(Dispatchers.Default) {
        val kept = file(app)
        kept.isFile && com.linpaintx.core.installMattingModel(kept.path)
    }

    /**
     * Takes the model and installs it; false when the network, the disk or the file itself said
     * no, in which case the embedded model stays in charge. [progress] hears the bytes done of
     * [BYTES] as they arrive. A half-taken file never becomes the model: it lands as `.part` and
     * is renamed only once its digest is the one asked for.
     */
    suspend fun download(app: Application, progress: (Long) -> Unit): Boolean = withContext(Dispatchers.Default) {
        val target = file(app)
        val part = File(target.parentFile, target.name + ".part")
        var connection: HttpURLConnection? = null
        try {
            // statvfs wants a path that exists, and the model itself may not be there yet
            if (app.filesDir.usableSpace < BYTES) return@withContext false
            connection = URL(DOWNLOAD_URL).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext false
            val digest = MessageDigest.getInstance("SHA-256")
            part.outputStream().use { to ->
                connection.inputStream.use { from ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    var since = 0L
                    while (true) {
                        val read = from.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                        to.write(buffer, 0, read)
                        done += read
                        since += read
                        if (since >= 512 * 1024) {
                            // a cancelled download must stop within half a megabyte, not at its end
                            currentCoroutineContext().ensureActive()
                            since = 0
                            progress(done)
                        }
                    }
                }
            }
            val hex = sha256Hex(digest.digest())
            if (hex != DIGEST) return@withContext false
            if (target.isFile && !target.delete()) return@withContext false
            if (!part.renameTo(target)) return@withContext false
            com.linpaintx.core.installMattingModel(target.path)
        } catch (e: IOException) {
            // the network, the disk or the stream broke: the phone keeps the model it has
            false
        } finally {
            connection?.disconnect()
            part.delete() // never leave a half file for the next start to find
        }
    }
}

/**
 * Digest bytes as the lower-case hex the published ones are written in. A Java byte is signed, so
 * 200 arrives as -56 and printing it raw would give eight f's; mask to eight bits first.
 */
internal fun sha256Hex(bytes: ByteArray): String =
    bytes.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
