package com.apkstudio.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.apkstudio.app.data.github.Repo
import com.apkstudio.app.data.github.WorkflowRun
import com.apkstudio.app.data.project.ProjectInfo

/**
 * State shared across the build flow screens (repo -> project -> commit -> run).
 */
class FlowState {
    var repo by mutableStateOf<Repo?>(null)
    var project by mutableStateOf<ProjectInfo?>(null)
    var commitSha by mutableStateOf<String?>(null)
    var run by mutableStateOf<WorkflowRun?>(null)

    val targetBranch: String
        get() = repo?.defaultBranch ?: "main"

    fun resetProject() {
        project = null
        commitSha = null
        run = null
    }

    fun resetAll() {
        repo = null
        resetProject()
    }
}
