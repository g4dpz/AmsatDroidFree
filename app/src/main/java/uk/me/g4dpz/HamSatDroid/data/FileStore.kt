// SPDX-License-Identifier: GPL-3.0-or-later
package uk.me.g4dpz.HamSatDroid.data

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Small file helper for app data in one directory.
 *
 * Writes are atomic: the data goes to a temporary file in the same directory, is flushed to disk, and then replaces
 * the target in one step, so a crash or failure part-way through leaves the previous file intact. Reads never throw:
 * a missing or unreadable file gives `null`, so callers can fall back to defaults.
 *
 * File names must be plain names (no path separators), so callers cannot write outside the directory.
 */
class FileStore(private val dir: File) {

    /**
     * Atomically replaces [name] with [bytes].
     *
     * @throws IOException if the data cannot be written; the previous contents of [name] are left unchanged
     * @throws IllegalArgumentException if [name] is not a plain file name
     */
    @Throws(IOException::class)
    fun writeAtomic(name: String, bytes: ByteArray) {
        val target = fileFor(name)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create directory $dir")
        val temp = File(dir, "$name.tmp")
        try {
            FileOutputStream(temp).use { out ->
                out.write(bytes)
                out.flush()
                out.fd.sync()
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            temp.delete()
            throw e
        }
    }

    /** Atomically replaces [name] with [text] encoded as UTF-8. */
    @Throws(IOException::class)
    fun writeTextAtomic(name: String, text: String) = writeAtomic(name, text.toByteArray(Charsets.UTF_8))

    /** The contents of [name], or `null` if it is missing or cannot be read. */
    fun readBytesOrNull(name: String): ByteArray? = try {
        fileFor(name).takeIf { it.isFile }?.readBytes()
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

    /** The contents of [name] decoded as UTF-8, or `null` if it is missing or cannot be read. */
    fun readTextOrNull(name: String): String? = readBytesOrNull(name)?.toString(Charsets.UTF_8)

    fun exists(name: String): Boolean = fileFor(name).isFile

    /** Last modified time in milliseconds, or `null` if the file does not exist. */
    fun lastModified(name: String): Long? = fileFor(name).takeIf { it.isFile }?.lastModified()

    /** Deletes [name] and any leftover temporary file. Returns true if the file is gone afterwards. */
    fun delete(name: String): Boolean {
        val file = fileFor(name)
        File(dir, "$name.tmp").delete()
        return !file.exists() || file.delete()
    }

    private fun fileFor(name: String): File {
        require(name.isNotEmpty() && name != "." && name != ".." && '/' !in name && '\\' !in name && '\u0000' !in name) {
            "Invalid file name: $name"
        }
        return File(dir, name)
    }
}
