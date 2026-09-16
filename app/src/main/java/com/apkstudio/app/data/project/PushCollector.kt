package com.apkstudio.app.data.project

import java.io.File

data class PushFile(
    val path: String,
    val file: File,
    val executable: Boolean
)

data class Collected(
    val files: List<PushFile>,
    val skippedBig: List<String>,
    val totalBytes: Long
)

/**
 * Collects the files that will be pushed to GitHub, skipping
 * generated/temporary content and oversized files.
 */
object PushCollector {

    const val MAX_FILE_BYTES: Long = 20L * 1024 * 1024

    private val ignoredDirs = setOf(
        ".git", ".gradle", ".idea", "build", ".cxx",
        ".externalNativeBuild", "captures", ".kotlin", "node_modules"
    )
    private val ignoredFiles = setOf(
        "local.properties", ".DS_Store", "Thumbs.db"
    )

    fun collect(root: File): Collected {
        val files = mutableListOf<PushFile>()
        val skipped = mutableListOf<String>()
        var total = 0L
        root.walkTopDown()
            .onEnter { dir -> dir == root || dir.name !in ignoredDirs }
            .forEach { f ->
                if (!f.isFile) return@forEach
                if (f.name in ignoredFiles) return@forEach
                if (f.extension == "iml") return@forEach
                val rel = f.relativeTo(root).invariantSeparatorsPath
                if (f.length() > MAX_FILE_BYTES) {
                    skipped.add(rel)
                    return@forEach
                }
                val exec = f.name == "gradlew" || f.extension == "sh"
                files.add(PushFile(rel, f, exec))
                total += f.length()
            }
        return Collected(files.sortedBy { it.path }, skipped.sorted(), total)
    }
}
