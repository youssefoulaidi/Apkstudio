package com.apkstudio.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import com.apkstudio.app.ui.AppNav
import com.apkstudio.app.ui.theme.ApkStudioTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApkStudioTheme {
                AppNav()
            }
        }
    }
}
