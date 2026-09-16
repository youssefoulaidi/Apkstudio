package com.apkstudio.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class ApkStudioApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
        // Default to Arabic on first launch; the user can switch in Settings.
        if (AppCompatDelegate.getApplicationLocales().isEmpty) {
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("ar"))
        }
    }
}
