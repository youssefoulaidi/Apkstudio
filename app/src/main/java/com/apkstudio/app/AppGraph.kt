package com.apkstudio.app

import android.app.Application
import com.apkstudio.app.data.github.GitHubRepository
import com.apkstudio.app.data.prefs.SessionManager
import com.apkstudio.app.ui.FlowState

/**
 * Minimal hand-written service locator shared by all screens.
 */
object AppGraph {
    lateinit var app: Application
        private set

    fun init(application: Application) {
        app = application
    }

    val session: SessionManager by lazy { SessionManager(app) }
    val github: GitHubRepository by lazy { GitHubRepository(app) }
    val flow: FlowState = FlowState()
}
