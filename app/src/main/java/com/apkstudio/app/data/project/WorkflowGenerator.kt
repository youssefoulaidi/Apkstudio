package com.apkstudio.app.data.project

/**
 * Generates the GitHub Actions workflow that builds the Debug APK in the cloud.
 */
object WorkflowGenerator {

    const val FILE_NAME = "apkstudio-build.yml"
    const val PATH = ".github/workflows/$FILE_NAME"
    const val WORKFLOW_NAME = "ApkStudio Build"
    const val ARTIFACT_NAME = "apkstudio-debug-apk"

    fun generate(project: ProjectInfo): String {
        val task = if (project.moduleTaskPath.isEmpty()) {
            "assembleDebug"
        } else {
            project.moduleTaskPath + ":assembleDebug"
        }
        val apkGlob = if (project.moduleRelPath.isEmpty()) {
            "build/outputs/apk/debug/*.apk"
        } else {
            project.moduleRelPath + "/build/outputs/apk/debug/*.apk"
        }
        val jdk = project.recommendedJdk

        return """
            |name: $WORKFLOW_NAME
            |
            |on:
            |  workflow_dispatch:
            |
            |permissions:
            |  contents: read
            |
            |jobs:
            |  build:
            |    name: Build Debug APK
            |    runs-on: ubuntu-latest
            |    steps:
            |      - name: Checkout
            |        uses: actions/checkout@v4
            |
            |      - name: Set up JDK $jdk
            |        uses: actions/setup-java@v4
            |        with:
            |          distribution: 'temurin'
            |          java-version: '$jdk'
            |          cache: 'gradle'
            |
            |      - name: Build with Gradle wrapper
            |        if: hashFiles('gradlew') != ''
            |        run: chmod +x gradlew && ./gradlew $task --stacktrace
            |
            |      - name: Set up Gradle (fallback)
            |        if: hashFiles('gradlew') == ''
            |        uses: gradle/actions/setup-gradle@v3
            |
            |      - name: Build with system Gradle (fallback)
            |        if: hashFiles('gradlew') == ''
            |        run: gradle $task --stacktrace
            |
            |      - name: Upload Debug APK
            |        uses: actions/upload-artifact@v4
            |        with:
            |          name: $ARTIFACT_NAME
            |          path: $apkGlob
            |          if-no-files-found: error
            |          retention-days: 14
            |
        """.trimMargin()
    }
}
