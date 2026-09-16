package com.apkstudio.app.util

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import java.util.zip.ZipFile

object ZipUtils {

    @Throws(IOException::class)
    fun copyUriToFile(context: Context, uri: Uri, dest: File) {
        dest.parentFile?.mkdirs()
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        } ?: throw IOException("Cannot open selected file")
    }

    /**
     * Unzips with Zip-Slip protection. Returns the extracted files.
     */
    @Throws(IOException::class)
    fun unzip(zipFile: File, destDir: File): List<File> {
        val out = mutableListOf<File>()
        destDir.mkdirs()
        val canonicalDest = destDir.canonicalPath + File.separator
        ZipFile(zipFile).use { zip ->
            val entries = zip.entries()
            if (!entries.hasMoreElements()) throw IOException("Empty archive")
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val target = File(destDir, entry.name)
                if (!target.canonicalPath.startsWith(canonicalDest)) {
                    throw IOException("Unsafe entry: ${entry.name}")
                }
                if (entry.isDirectory) {
                    target.mkdirs()
                    continue
                }
                target.parentFile?.mkdirs()
                zip.getInputStream(entry).use { input ->
                    FileOutputStream(target).use { output ->
                        input.copyTo(output)
                    }
                }
                out.add(target)
            }
        }
        return out
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.US, "%.1f MB", mb)
        return String.format(Locale.US, "%.2f GB", mb / 1024.0)
    }

    fun formatDuration(totalSeconds: Long): String {
        val m = totalSeconds / 60
        val s = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", m, s)
    }
}
