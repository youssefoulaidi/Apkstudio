package com.apkstudio.app.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.apkstudio.app.data.github.GitHubClient

/**
 * Stores the GitHub token in EncryptedSharedPreferences (falls back
 * to plain private prefs on devices without working keystore).
 */
class SessionManager(private val appContext: Context) {

    private val prefs: SharedPreferences by lazy { createPrefs() }

    private fun createPrefs(): SharedPreferences {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "apkstudio_secure",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            appContext.getSharedPreferences("apkstudio_prefs", Context.MODE_PRIVATE)
        }
    }

    fun saveSession(token: String, login: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token.trim())
            .putString(KEY_LOGIN, login)
            .apply()
        GitHubClient.clear()
    }

    fun token(): String? = prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }

    fun login(): String? = prefs.getString(KEY_LOGIN, null)

    fun isLinked(): Boolean = token() != null

    fun clear() {
        prefs.edit().clear().apply()
        GitHubClient.clear()
    }

    companion object {
        private const val KEY_TOKEN = "github_token"
        private const val KEY_LOGIN = "github_login"
    }
}
