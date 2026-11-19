package com.example.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pranav.guard.PranavGate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.pranavDataStore by preferencesDataStore(name = "pranav_control_settings")

data class SavedSettings(
    val renderUrl: String = "",
    val githubFallbackUrl: String = "",
    val adminSecret: String = "",
    val licenseKey: String = ""
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val RENDER_URL = stringPreferencesKey("render_url")
        val GITHUB_URL = stringPreferencesKey("github_url")
        val ADMIN_SECRET = stringPreferencesKey("admin_secret")
        val LICENSE_KEY = stringPreferencesKey("license_key")
    }

    val settingsFlow: Flow<SavedSettings> = context.pranavDataStore.data.map { prefs ->
        SavedSettings(
            renderUrl = prefs[Keys.RENDER_URL] ?: "",
            githubFallbackUrl = prefs[Keys.GITHUB_URL] ?: "",
            adminSecret = prefs[Keys.ADMIN_SECRET] ?: "",
            licenseKey = prefs[Keys.LICENSE_KEY] ?: PranavGate.getSavedLicenseKey(context)
        )
    }

    suspend fun saveEndpoints(renderUrl: String, githubUrl: String, adminSecret: String) {
        val cleanRender = renderUrl.trim().trimEnd('/')
        val cleanGithub = githubUrl.trim()
        context.pranavDataStore.edit { prefs ->
            prefs[Keys.RENDER_URL] = cleanRender
            prefs[Keys.GITHUB_URL] = cleanGithub
            prefs[Keys.ADMIN_SECRET] = adminSecret.trim()
        }
        PranavGate.configure(context, cleanRender, cleanGithub)
    }

    suspend fun saveLicenseKey(licenseKey: String) {
        val cleanKey = licenseKey.trim()
        context.pranavDataStore.edit { prefs ->
            prefs[Keys.LICENSE_KEY] = cleanKey
        }
        PranavGate.saveLicenseKey(context, cleanKey)
    }
}
