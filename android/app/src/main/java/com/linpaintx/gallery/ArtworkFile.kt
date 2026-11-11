package com.linpaintx.gallery

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * A LinPaintX artwork as one file: its complete project folder in a zip. Keeping the native folder
 * layout preserves layers, models, references, time-lapse data and undo/redo history.
 */
object ArtworkFile {
    private const val MAX_ENTRIES = 100_000
    private const val MAX_UNPACKED_BYTES = 1L shl 30

    /** Writes every regular file in project folder [dir] to [output]. */
    fun write(dir: File, output: OutputStream) {
        val root = dir.canonicalFile
        ZipOutputStream(output.buffered()).use { zip ->
            root.walkTopDown().filter(File::isFile).forEach { file ->
                val name = file.canonicalFile.relativeTo(root).invariantSeparatorsPath
                if (name.isEmpty() || name.contains("..")) return@forEach
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().buffered().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    /**
     * Unzips an artwork file into the empty folder [dir]; false (and nothing kept) if it isn't
     * one. Only the files an artwork has are taken, and none outside the folder.
     */
    fun read(input: InputStream, dir: File): Boolean {
        val root = dir.canonicalFile
        var found = false
        var entries = 0
        var unpacked = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (++entries > MAX_ENTRIES) error("too many files")
                    val name = entry.name.replace('\\', '/')
                    if (entry.isDirectory || name.isBlank() || name.startsWith('/') || name.split('/').any { it == ".." }) continue
                    val target = File(root, name).canonicalFile
                    if (!target.path.startsWith(root.path + File.separator)) continue
                    target.parentFile?.mkdirs()
                    target.outputStream().buffered().use { out ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            unpacked += read
                            if (unpacked > MAX_UNPACKED_BYTES) error("project is too large")
                            out.write(buffer, 0, read)
                        }
                    }
                    if (name == "artwork.json") found = true
                }
            }
        } catch (_: Exception) {
            dir.deleteRecursively()
            return false
        }
        if (!found) dir.deleteRecursively()
        return found
    }
}
