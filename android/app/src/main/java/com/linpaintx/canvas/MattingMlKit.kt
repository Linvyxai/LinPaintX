package com.linpaintx.canvas

import android.graphics.Bitmap
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.subject.Subject
import com.google.mlkit.vision.segmentation.subject.SubjectSegmentation
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenter
import com.google.mlkit.vision.segmentation.subject.SubjectSegmenterOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.FloatBuffer
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Background removal by Google's own segmenter (ML Kit Subject Segmentation): every subject in the
 * picture gets its mask, full size, locally. The segmenter itself is a thin client — Google Play
 * Services downloads its module once and keeps it, so the first use waits for that and every later
 * one runs offline.
 *
 * It shares the engine's cut function with the wand (`cutWandMask`): the mask comes back 255 where
 * a subject is, and the caller inverts it so the panel's softness slider feathers the edge exactly
 * as it does for the other models.
 */
object MattingMlKit {

    private const val TAG = "MattingMlKit"

    /** How long the module's download is given before this device is told no. */
    private const val PATIENCE_MS = 5 * 60_000L

    private const val RETRY_MS = 10_000L

    /** A confidence above this is subject: below it the segmenter is guessing about the edge. */
    internal const val SUBJECT_CONFIDENCE = 0.5f

    /** Where the module stands. */
    enum class State {
        /** Play Services is fetching it; the built-in model cuts until it lands. */
        WAITING,

        /** Downloaded and usable offline. */
        READY,

        /** Cannot run here at all (no Play Services, a module this device will never get). */
        UNAVAILABLE,
    }

    @Volatile
    var state: State = State.WAITING
        private set

    /** One download at a time: every call while it runs gets the current state and starts nothing. */
    private val starting = AtomicBoolean(false)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val segmenter: SubjectSegmenter by lazy {
        SubjectSegmentation.getClient(
            SubjectSegmenterOptions.Builder()
                .enableMultipleSubjects(
                    SubjectSegmenterOptions.SubjectResultOptions.Builder()
                        // the confidence mask is the subject itself; the bitmap would be a colour
                        // copy of it and a dark subject would read as no subject at all
                        .enableConfidenceMask()
                        .build(),
                )
                .build(),
        )
    }

    /**
     * Asks Play Services for the module and reports where it ends up, on the main thread. A failed
     * attempt that can still win is tried again; once [PATIENCE_MS] is spent the device is told it
     * will not have the module, so the panel can stop waiting instead of promising forever.
     */
    fun prepare(onState: (State) -> Unit = {}) {
        if (state != State.WAITING || !starting.compareAndSet(false, true)) {
            onState(state)
            return
        }
        scope.launch {
            val deadline = System.nanoTime() + PATIENCE_MS * 1_000_000
            var answer = State.WAITING
            while (true) {
                val code = tryInit()
                if (code == null) {
                    answer = State.READY
                    break
                }
                if (!retryable(code) || System.nanoTime() >= deadline) {
                    answer = State.UNAVAILABLE
                    break
                }
                Log.i(TAG, "module is not here yet (code $code)")
                delay(RETRY_MS)
            }
            state = answer
            starting.set(false)
            Log.i(TAG, "module $answer")
            withContext(Dispatchers.Main) { onState(answer) }
        }
    }

    /** What the segmenter answered about one picture. */
    sealed interface Cut {
        /** The subjects' union as a full-size mask, 255 where a subject is. */
        class Found(val mask: ByteArray) : Cut

        /** It looked and found no subject: there is nothing on this picture to cut out. */
        data object Nothing : Cut

        /** It could not answer at all, so nothing can be said about the picture. */
        data class Failed(val reason: String) : Cut
    }

    /**
     * Asks the segmenter about the picture. "Nothing to cut" and "could not answer" are different
     * answers, so the panel never reports a broken run as a picture without a subject.
     */
    suspend fun mask(rgba: ByteArray, width: Int, height: Int): Cut = withContext(Dispatchers.IO) {
        if (state != State.READY) return@withContext Cut.Failed("module is not ready")
        var bitmap: Bitmap? = null
        try {
            val picture = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap = picture
            // the same byte order the rest of the app hands to a bitmap; the segmenter only looks
            // at shape and colour, so a channel swap here would still cut, only worse
            picture.copyPixelsFromBuffer(ByteBuffer.wrap(rgba))
            val result = Tasks.await(segmenter.process(InputImage.fromBitmap(picture, 0)), 60, TimeUnit.SECONDS)
            val subjects = result.subjects.orEmpty()
            if (subjects.isEmpty()) return@withContext Cut.Nothing
            val mask = ByteArray(width * height)
            for (subject in subjects) add(subject, mask, width, height)
            if (mask.none { it.toInt() != 0 }) Cut.Nothing else Cut.Found(mask)
        } catch (e: Exception) {
            Log.i(TAG, "cut failed: " + e.javaClass.simpleName + ": " + e.message)
            Cut.Failed(e.javaClass.simpleName + ": " + e.message)
        } finally {
            bitmap?.recycle()
        }
    }

    /** Pours one subject's confidence mask into the union, wherever the subject lies. */
    private fun add(subject: Subject, mask: ByteArray, width: Int, height: Int) {
        val confidence = subject.confidenceMask ?: return
        pour(confidence, subject.startX, subject.startY, subject.width, subject.height, mask, width, height)
    }

    /**
     * Copies a subject's box of confidences into the full-size mask, dropping whatever falls
     * outside the picture — a subject touching an edge comes back with a box that does.
     */
    internal fun pour(
        confidence: FloatBuffer,
        left: Int,
        top: Int,
        boxW: Int,
        boxH: Int,
        mask: ByteArray,
        width: Int,
        height: Int,
    ) {
        for (y in 0 until boxH) {
            val row = top + y
            if (row !in 0 until height) continue
            val source = y * boxW
            val target = row * width
            for (x in 0 until boxW) {
                val column = left + x
                if (column !in 0 until width) continue
                if (confidence.get(source + x) > SUBJECT_CONFIDENCE) mask[target + column] = 255.toByte()
            }
        }
    }

    /** The error code a download attempt ended with, or null when the module is here. */
    private fun tryInit(): Int? = try {
        Tasks.await(segmenter.getInitTask(), 60, TimeUnit.SECONDS)
        null
    } catch (e: Exception) {
        val cause = if (e is ExecutionException) e.cause else e
        (cause as? MlKitException)?.errorCode ?: MlKitException.UNKNOWN
    }

    /** Whether an error leaves the download able to finish later on its own. */
    internal fun retryable(code: Int): Boolean = when (code) {
        // still downloading, or a network that may come back
        MlKitException.UNAVAILABLE,
        MlKitException.NETWORK_ISSUE,
        MlKitException.DEADLINE_EXCEEDED,
        MlKitException.INTERNAL,
        MlKitException.UNKNOWN,
        -> true
        // no Play Services that can serve it, no space, a module this build cannot use
        else -> false
    }
}
