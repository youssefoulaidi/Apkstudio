package com.apkstudio.app.data.project

import android.content.Context
import com.apkstudio.app.R
import com.apkstudio.app.util.AppFailure
import com.apkstudio.app.util.ErrorMapper
import com.apkstudio.app.util.FailureException
import com.apkstudio.app.util.ZipUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class ProjectInfo(
    val rootDir: File,
    /** Display name of the app module, e.g. "app". */
    val module: String,
    /** Gradle path of the module, e.g. ":app" ("" when the root is the app). */
    val moduleTaskPath: String,
    /** Relative dir of the module, e.g. "app" ("" when root). */
    val moduleRelPath: String,
    val packageId: String?,
    val compileSdk: Int?,
    val minSdk: Int?,
    val targetSdk: Int?,
    val agpVersion: String?,
    val gradleVersion: String?,
    val kotlinVersion: String?,
    val hasWrapperScript: Boolean,
    val hasWrapperJar: Boolean,
    val fileCount: Int,
    val totalBytes: Long,
    val recommendedJdk: Int,
    val warnings: List<String>,
    val errors: List<String>
) {
    val isValid: Boolean get() = errors.isEmpty()
}

object ProjectAnalyzer {

    private val settingsNames = listOf("settings.gradle", "settings.gradle.kts", "settings.gradle.dcl")

    suspend fun analyze(ctx: Context, zipFile: File): Result<ProjectInfo> =
        withContext(Dispatchers.IO) {
            try {
                val workDir = File(ctx.cacheDir, "apkstudio-import/${System.currentTimeMillis()}")
                val extracted: List<File> = try {
                    ZipUtils.unzip(zipFile, workDir)
                } catch (e: Exception) {
                    deleteQuietly(workDir)
                    return@withContext Result.failure(
                        FailureException(
                            AppFailure(
                                ctx.getString(R.string.err_zip_title),
                                ctx.getString(R.string.err_zip_hint),
                                ctx.getString(R.string.err_zip_detail)
                            )
                        )
                    )
                }
                if (extracted.isEmpty()) {
                    deleteQuietly(workDir)
                    return@withContext Result.failure(
                        FailureException(
                            AppFailure(
                                ctx.getString(R.string.err_zip_title),
                                ctx.getString(R.string.err_zip_hint),
                                ctx.getString(R.string.err_zip_detail)
                            )
                        )
                    )
                }

                val root = findProjectRoot(workDir)
                if (root == null) {
                    return@withContext Result.failure(
                        FailureException(
                            AppFailure(
                                ctx.getString(R.string.err_not_android_title),
                                ctx.getString(R.string.err_not_android_hint),
                                ctx.getString(R.string.err_not_android_detail)
                            )
                        )
                    )
                }

                Result.success(inspectProject(ctx, root))
            } catch (t: Throwable) {
                Result.failure(FailureException(ErrorMapper.fromThrowable(ctx, t)))
            }
        }

    private fun inspectProject(ctx: Context, root: File): ProjectInfo {
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        val module = findAppModule(root)
        if (module == null) {
            errors.add(
                ctx.getString(R.string.err_no_app_module_title) + " — " +
                    ctx.getString(R.string.err_no_app_module_detail)
            )
        }

        val moduleDir = module?.second ?: root
        val moduleRelPath = if (moduleDir == root) "" else moduleDir.relativeTo(root).invariantSeparatorsPath
        val moduleTaskPath = if (moduleRelPath.isEmpty()) "" else ":" + moduleRelPath.replace("/", ":")

        val appBuild = readFirst(moduleDir, "build.gradle", "build.gradle.kts", "build.gradle.dcl")
        val rootBuild = readFirst(root, "build.gradle", "build.gradle.kts", "build.gradle.dcl")
        val toml = File(root, "gradle/libs.versions.toml").takeIf { it.isFile }?.readTextSafely() ?: ""

        val manifest = findManifest(moduleDir)
        val packageId = firstMatch(
            appBuild,
            "applicationId\\s*[= ]\\s*[\"']([^\"']+)[\"']",
            "namespace\\s*[= ]\\s*[\"']([^\"']+)[\"']"
        ) ?: manifest?.let {
            firstMatch(
                it.readTextSafely(),
                "package\\s*=\\s*\"([^\"]+)\""
            )
        }

        val compileSdk = firstMatch(appBuild, "compileSdk\\s*[= ]\\s*(\\d+)", "compileSdkVersion\\s*[= ]\\s*(\\d+)")?.toIntOrNull()
        val minSdk = firstMatch(appBuild, "minSdk\\s*[= ]\\s*(\\d+)", "minSdkVersion\\s*[= ]\\s*(\\d+)")?.toIntOrNull()
        val targetSdk = firstMatch(appBuild, "targetSdk\\s*[= ]\\s*(\\d+)", "targetSdkVersion\\s*[= ]\\s*(\\d+)")?.toIntOrNull()

        val agpVersion = firstMatch(
            rootBuild,
            "com\\.android\\.tools\\.build:gradle:([0-9][^\"'\\s]+)",
            "id\\s*\\(?\\s*[\"']com\\.android\\.(application|library)[\"']\\s*\\)?\\s*version\\s*[\"']([^\"']+)[\"']"
        ) ?: firstMatch(toml, "(?m)^\\s*agp\\s*=\\s*\"([^\"]+)\"")

        val kotlinVersion = firstMatch(
            rootBuild,
            "org\\.jetbrains\\.kotlin\\.gradle\\.plugin:([0-9][^\"'\\s]+)",
            "kotlin-gradle-plugin:([0-9][^\"'\\s]+)",
            "id\\s*\\(?\\s*[\"']org\\.jetbrains\\.kotlin\\.android[\"']\\s*\\)?\\s*version\\s*[\"']([^\"']+)[\"']"
        ) ?: firstMatch(toml, "(?m)^\\s*kotlin\\s*=\\s*\"([^\"]+)\"")

        val wrapperProps = File(root, "gradle/wrapper/gradle-wrapper.properties")
            .takeIf { it.isFile }?.readTextSafely() ?: ""
        val gradleVersion = firstMatch(wrapperProps, "distributionUrl\\s*=\\s*.*gradle-([0-9.]+)-")

        val hasWrapperScript = File(root, "gradlew").isFile
        val hasWrapperJar = File(root, "gradle/wrapper/gradle-wrapper.jar").isFile

        if (module != null && manifest == null) {
            errors.add("AndroidManifest.xml — missing in module \"${module.first}\"")
        }

        if (!hasWrapperScript) {
            warnings.add(ctx.getString(R.string.warn_no_wrapper))
        } else if (!hasWrapperJar) {
            warnings.add(ctx.getString(R.string.warn_no_wrapper_jar))
        }

        val agpMajor = agpVersion?.split(".")?.getOrNull(0)?.toIntOrNull()
        val agpMinor = agpVersion?.split(".")?.getOrNull(1)?.toIntOrNull()
        if (agpVersion != null && (agpMajor ?: 99) < 7) {
            warnings.add(ctx.getString(R.string.warn_old_agp, agpVersion))
        }
        if (gradleVersion != null && !versionAtLeast(gradleVersion, 7, 3)) {
            warnings.add(ctx.getString(R.string.warn_old_gradle, gradleVersion))
        }
        if (agpVersion == null && gradleVersion == null) {
            warnings.add(ctx.getString(R.string.warn_unknown_versions))
        }

        val usesGms = appBuild.contains("com.google.gms.google-services")
        if (usesGms && !hasGoogleServicesJson(moduleDir, root)) {
            warnings.add(ctx.getString(R.string.warn_no_gms))
        }

        val collected = PushCollector.collect(root)
        if (collected.files.isEmpty()) {
            errors.add(ctx.getString(R.string.err_not_android_detail))
        }
        if (collected.skippedBig.isNotEmpty()) {
            warnings.add(
                ctx.getString(
                    R.string.warn_big_files,
                    collected.skippedBig.size,
                    ZipUtils.formatBytes(PushCollector.MAX_FILE_BYTES)
                )
            )
        }

        val gradleRunsOn17 = gradleVersion != null && versionAtLeast(gradleVersion, 7, 3)
        val recommendedJdk = when {
            gradleVersion != null && !gradleRunsOn17 -> 11
            agpMajor != null && agpMajor >= 8 -> 17
            agpMajor == 7 && (agpMinor ?: 0) >= 3 -> 17
            agpMajor == 7 -> 11
            agpMajor != null && agpMajor < 7 -> 11
            else -> 17
        }

        return ProjectInfo(
            rootDir = root,
            module = module?.first ?: "app",
            moduleTaskPath = moduleTaskPath,
            moduleRelPath = moduleRelPath,
            packageId = packageId,
            compileSdk = compileSdk,
            minSdk = minSdk,
            targetSdk = targetSdk,
            agpVersion = agpVersion,
            gradleVersion = gradleVersion,
            kotlinVersion = kotlinVersion,
            hasWrapperScript = hasWrapperScript,
            hasWrapperJar = hasWrapperJar,
            fileCount = collected.files.size,
            totalBytes = collected.totalBytes,
            recommendedJdk = recommendedJdk,
            warnings = warnings,
            errors = errors
        )
    }

    private fun hasSettings(dir: File): Boolean =
        settingsNames.any { File(dir, it).isFile }

    private fun findProjectRoot(extracted: File): File? {
        if (hasSettings(extracted)) return extracted
        val children = extracted.listFiles()?.filter { it.isDirectory } ?: emptyList()
        if (children.size == 1 && hasSettings(children[0])) return children[0]
        return extracted.walkTopDown().maxDepth(4)
            .firstOrNull { it.isDirectory && hasSettings(it) }
    }

    /**
     * Returns (displayName, moduleDir) of the application module, or null.
     */
    private fun findAppModule(root: File): Pair<String, File>? {
        val appDir = File(root, "app")
        if (isApplicationModule(appDir)) return "app" to appDir
        val candidates = root.walkTopDown().maxDepth(3)
            .filter { it.isDirectory && it != root && isApplicationModule(it) }
            .toList()
        val best = candidates.minByOrNull { it.relativeTo(root).invariantSeparatorsPath.length }
            ?: return null
        return best.name to best
    }

    private fun isApplicationModule(dir: File): Boolean {
        val text = readFirst(dir, "build.gradle", "build.gradle.kts", "build.gradle.dcl")
        if (text.isEmpty()) return false
        return text.contains("com.android.application")
    }

    private fun findManifest(moduleDir: File): File? {
        val direct = File(moduleDir, "src/main/AndroidManifest.xml")
        if (direct.isFile) return direct
        return moduleDir.walkTopDown().maxDepth(5)
            .firstOrNull { it.isFile && it.name == "AndroidManifest.xml" }
    }

    private fun hasGoogleServicesJson(moduleDir: File, root: File): Boolean {
        val hits = moduleDir.walkTopDown().maxDepth(5)
            .any { it.isFile && it.name == "google-services.json" }
        if (hits) return true
        return root.walkTopDown().maxDepth(2)
            .any { it.isFile && it.name == "google-services.json" }
    }

    private fun readFirst(dir: File, vararg names: String): String {
        for (n in names) {
            val f = File(dir, n)
            if (f.isFile) return f.readTextSafely()
        }
        return ""
    }

    private fun File.readTextSafely(): String {
        return try {
            if (length() > 2 * 1024 * 1024) return ""
            readText(Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun firstMatch(text: String, vararg patterns: String): String? {
        if (text.isEmpty()) return null
        for (p in patterns) {
            val m = Regex(p).find(text) ?: continue
            for (i in 1 until m.groupValues.size) {
                val g = m.groupValues[i]
                if (g.isNotEmpty() && g[0].isDigit()) return g
            }
            // Fallback for patterns whose wanted group is not numeric-first
            // (e.g. applicationId): return the last non-empty group.
            for (i in m.groupValues.size - 1 downTo 1) {
                val g = m.groupValues[i]
                if (g.isNotEmpty() && i == m.groupValues.size - 1) return g
            }
        }
        return null
    }

    private fun versionAtLeast(version: String, major: Int, minor: Int): Boolean {
        val parts = version.split(".")
        val maj = parts.getOrNull(0)?.filter { it.isDigit() }?.toIntOrNull() ?: return false
        val min = parts.getOrNull(1)?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        return maj > major || (maj == major && min >= minor)
    }

    private fun deleteQuietly(file: File) {
        try {
            file.deleteRecursively()
        } catch (_: Exception) {
        }
    }
}
