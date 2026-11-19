package com.example.ui

import android.app.Activity
import android.app.Application
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.CreateKeyRequest
import com.example.data.GateConfig
import com.example.data.LicenseRecord
import com.example.data.LicenseVerifyRequest
import com.example.data.RenderApiFactory
import com.example.data.SettingsRepository
import com.pranav.guard.PranavGate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PranavTab {
    GATE, ADMIN, DEPLOY
}

sealed interface GateConnectionState {
    data object Unconfigured : GateConnectionState
    data object Checking : GateConnectionState
    data class Ready(val source: String, val message: String) : GateConnectionState
    data class Error(val message: String) : GateConnectionState
}

data class PranavUiState(
    val selectedTab: PranavTab = PranavTab.GATE,
    val deviceId: String = "",
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val appVersionName: String = "1.0",
    val appVersionCode: Long = 1L,
    val renderUrlInput: String = "",
    val githubFallbackInput: String = "",
    val adminSecretInput: String = "",
    val licenseKeyInput: String = "",
    val connectionState: GateConnectionState = GateConnectionState.Unconfigured,
    val liveGateConfig: GateConfig? = null,
    val licenseStatusText: String = "Not verified yet",
    val isLicenseValid: Boolean = false,
    val gateCallbackLog: String = "Ready — tap Run PranavGate.check(activity) to test.",
    // Admin panel state
    val adminLoading: Boolean = false,
    val adminConnected: Boolean = false,
    val githubSyncEnabled: Boolean = false,
    val adminError: String? = null,
    val editableConfig: GateConfig = GateConfig(),
    val issuedKeys: List<LicenseRecord> = emptyList(),
    val newKeyLabel: String = "",
    val newKeyDays: String = "30",
    val newCustomKey: String = "",
    val snackbarMessage: String? = null
)

class PranavViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = SettingsRepository(application)
    private val _uiState = MutableStateFlow(PranavUiState())
    val uiState: StateFlow<PranavUiState> = _uiState.asStateFlow()

    init {
        val ctx = getApplication<Application>()
        val pkgInfo = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        }.getOrNull()
        val vName = pkgInfo?.versionName ?: "1.0"
        @Suppress("DEPRECATION")
        val vCode = if (pkgInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pkgInfo.longVersionCode
        } else {
            pkgInfo?.versionCode?.toLong() ?: 1L
        }

        _uiState.update {
            it.copy(
                deviceId = PranavGate.getDeviceId(ctx),
                appVersionName = vName,
                appVersionCode = vCode
            )
        }

        viewModelScope.launch {
            repo.settingsFlow.collect { saved ->
                PranavGate.configure(ctx, saved.renderUrl, saved.githubFallbackUrl)
                _uiState.update { state ->
                    val nextRender = state.renderUrlInput.ifBlank { saved.renderUrl }
                    val nextGithub = state.githubFallbackInput.ifBlank { saved.githubFallbackUrl }
                    val nextSecret = state.adminSecretInput.ifBlank { saved.adminSecret }
                    val nextLicense = state.licenseKeyInput.ifBlank { saved.licenseKey }
                    val connState = if (nextRender.isBlank() && nextGithub.isBlank()) {
                        GateConnectionState.Unconfigured
                    } else if (state.connectionState is GateConnectionState.Unconfigured) {
                        GateConnectionState.Ready("Saved", "Endpoints ready. Tap Sync to fetch config.")
                    } else {
                        state.connectionState
                    }
                    state.copy(
                        renderUrlInput = nextRender,
                        githubFallbackInput = nextGithub,
                        adminSecretInput = nextSecret,
                        licenseKeyInput = nextLicense,
                        connectionState = connState
                    )
                }
            }
        }
    }

    fun selectTab(tab: PranavTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun updateRenderUrl(value: String) {
        _uiState.update { it.copy(renderUrlInput = value) }
    }

    fun updateGithubFallback(value: String) {
        _uiState.update { it.copy(githubFallbackInput = value) }
    }

    fun updateAdminSecret(value: String) {
        _uiState.update { it.copy(adminSecretInput = value) }
    }

    fun updateLicenseKeyInput(value: String) {
        _uiState.update { it.copy(licenseKeyInput = value.uppercase()) }
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun showMessage(msg: String) {
        _uiState.update { it.copy(snackbarMessage = msg) }
    }

    fun saveAndSyncGate() {
        val state = _uiState.value
        val renderUrl = state.renderUrlInput.trim().trimEnd('/')
        val githubUrl = state.githubFallbackInput.trim()

        viewModelScope.launch {
            repo.saveEndpoints(renderUrl, githubUrl, state.adminSecretInput)
            if (renderUrl.isBlank() && githubUrl.isBlank()) {
                _uiState.update {
                    it.copy(
                        connectionState = GateConnectionState.Unconfigured,
                        snackbarMessage = "Enter a Render Admin URL or GitHub Raw config URL."
                    )
                }
                return@launch
            }
            fetchGateConfig(renderUrl, githubUrl)
        }
    }

    private suspend fun fetchGateConfig(renderUrl: String, githubUrl: String) {
        _uiState.update { it.copy(connectionState = GateConnectionState.Checking) }
        val ctx = getApplication<Application>()

        var fetched: GateConfig? = null
        var sourceLabel = ""
        var errMsg = ""

        val api = RenderApiFactory.create(renderUrl)
        if (api != null) {
            runCatching {
                val res = api.getGateConfig(ctx.packageName, _uiState.value.appVersionCode)
                if (res.isSuccessful && res.body() != null) {
                    fetched = res.body()
                    sourceLabel = "Render Admin"
                } else {
                    errMsg = "Render HTTP ${res.code()}"
                }
            }.onFailure { errMsg = it.localizedMessage ?: "Render connection failed" }
        }

        if (fetched == null && githubUrl.startsWith("http")) {
            val rawApi = RenderApiFactory.create("https://raw.githubusercontent.com")
            if (rawApi != null) {
                runCatching {
                    val res = rawApi.getRawConfigFromUrl(githubUrl)
                    if (res.isSuccessful && res.body() != null) {
                        fetched = res.body()
                        sourceLabel = "GitHub Fallback"
                    } else {
                        errMsg = "GitHub HTTP ${res.code()}"
                    }
                }.onFailure { errMsg = it.localizedMessage ?: "GitHub fallback failed" }
            }
        }

        if (fetched != null) {
            _uiState.update {
                it.copy(
                    connectionState = GateConnectionState.Ready(sourceLabel, "Synced from $sourceLabel"),
                    liveGateConfig = fetched,
                    snackbarMessage = "Gate config synced ($sourceLabel)"
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    connectionState = GateConnectionState.Error(errMsg.ifBlank { "Invalid URL" }),
                    snackbarMessage = errMsg.ifBlank { "Could not connect to server." }
                )
            }
        }
    }

    fun verifyLicenseKeyNow() {
        val state = _uiState.value
        val key = state.licenseKeyInput.trim().uppercase()
        val renderUrl = state.renderUrlInput.trim().trimEnd('/')

        if (key.isBlank()) {
            _uiState.update { it.copy(snackbarMessage = "Enter a license key first.") }
            return
        }

        viewModelScope.launch {
            repo.saveLicenseKey(key)
            val api = RenderApiFactory.create(renderUrl)
            if (api == null) {
                _uiState.update {
                    it.copy(
                        isLicenseValid = false,
                        licenseStatusText = "Saved locally. Configure Render Admin URL to verify.",
                        snackbarMessage = "Render Admin URL required for 1-device verification."
                    )
                }
                return@launch
            }

            _uiState.update { it.copy(licenseStatusText = "Verifying with Render...") }
            runCatching {
                val res = api.verifyLicense(
                    LicenseVerifyRequest(
                        licenseKey = key,
                        deviceId = state.deviceId,
                        deviceModel = state.deviceModel,
                        packageName = getApplication<Application>().packageName
                    )
                )
                val body = res.body()
                if (res.isSuccessful && body?.valid == true) {
                    _uiState.update {
                        it.copy(
                            isLicenseValid = true,
                            licenseStatusText = body.message,
                            snackbarMessage = "Device locked & verified!"
                        )
                    }
                } else {
                    val reason = body?.message ?: "Rejected (HTTP ${res.code()})"
                    _uiState.update {
                        it.copy(
                            isLicenseValid = false,
                            licenseStatusText = reason,
                            snackbarMessage = reason
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLicenseValid = false,
                        licenseStatusText = err.localizedMessage ?: "Network error",
                        snackbarMessage = "Verification failed: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun runLivePranavGateCheck(activity: Activity) {
        val state = _uiState.value
        viewModelScope.launch {
            repo.saveEndpoints(state.renderUrlInput, state.githubFallbackInput, state.adminSecretInput)
            repo.saveLicenseKey(state.licenseKeyInput)
            PranavGate.check(
                activity,
                state.renderUrlInput,
                state.githubFallbackInput
            ) { allowed, msg ->
                _uiState.update {
                    it.copy(
                        gateCallbackLog = if (allowed) "ALLOWED: $msg" else "BLOCKED / PROMPTED: $msg",
                        isLicenseValid = allowed
                    )
                }
            }
        }
    }

    // --- Admin Console Actions ---

    fun loadAdminDashboard() {
        val state = _uiState.value
        val api = RenderApiFactory.create(state.renderUrlInput)
        if (api == null || state.adminSecretInput.isBlank()) {
            _uiState.update {
                it.copy(
                    adminError = "Enter both Render Admin URL and Admin Secret Token.",
                    adminConnected = false
                )
            }
            return
        }

        viewModelScope.launch {
            repo.saveEndpoints(state.renderUrlInput, state.githubFallbackInput, state.adminSecretInput)
            _uiState.update { it.copy(adminLoading = true, adminError = null) }
            runCatching {
                val res = api.getAdminDashboard(state.adminSecretInput.trim())
                if (res.isSuccessful && res.body() != null) {
                    val body = res.body()!!
                    _uiState.update {
                        it.copy(
                            adminLoading = false,
                            adminConnected = true,
                            githubSyncEnabled = body.githubSyncEnabled,
                            editableConfig = body.config,
                            liveGateConfig = body.config,
                            issuedKeys = body.keys,
                            snackbarMessage = "Admin dashboard synced"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            adminLoading = false,
                            adminConnected = false,
                            adminError = "Auth/Server error (HTTP ${res.code()})"
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        adminLoading = false,
                        adminConnected = false,
                        adminError = err.localizedMessage ?: "Failed to connect to Render"
                    )
                }
            }
        }
    }

    fun updateEditableConfig(transform: (GateConfig) -> GateConfig) {
        _uiState.update { it.copy(editableConfig = transform(it.editableConfig)) }
    }

    fun pushAdminConfig() {
        val state = _uiState.value
        val api = RenderApiFactory.create(state.renderUrlInput) ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(adminLoading = true) }
            runCatching {
                val res = api.updateAdminConfig(state.adminSecretInput.trim(), state.editableConfig)
                if (res.isSuccessful && res.body()?.ok == true) {
                    val updated = res.body()?.config ?: state.editableConfig
                    _uiState.update {
                        it.copy(
                            adminLoading = false,
                            editableConfig = updated,
                            liveGateConfig = updated,
                            snackbarMessage = "Pushed config to Render + GitHub!"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            adminLoading = false,
                            snackbarMessage = "Update failed (HTTP ${res.code()})"
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        adminLoading = false,
                        snackbarMessage = "Error: ${err.localizedMessage}"
                    )
                }
            }
        }
    }

    fun updateNewKeyFields(label: String? = null, days: String? = null, customKey: String? = null) {
        _uiState.update {
            it.copy(
                newKeyLabel = label ?: it.newKeyLabel,
                newKeyDays = days ?: it.newKeyDays,
                newCustomKey = customKey ?: it.newCustomKey
            )
        }
    }

    fun issueNewLicenseKey() {
        val state = _uiState.value
        val api = RenderApiFactory.create(state.renderUrlInput) ?: return
        val days = state.newKeyDays.toIntOrNull() ?: 30
        viewModelScope.launch {
            _uiState.update { it.copy(adminLoading = true) }
            runCatching {
                val res = api.createLicenseKey(
                    state.adminSecretInput.trim(),
                    CreateKeyRequest(
                        label = state.newKeyLabel.ifBlank { "Device User" },
                        daysValid = days,
                        customKey = state.newCustomKey.trim().ifBlank { null }
                    )
                )
                val body = res.body()
                if (res.isSuccessful && body?.ok == true && body.key != null) {
                    _uiState.update {
                        it.copy(
                            adminLoading = false,
                            issuedKeys = listOf(body.key) + it.issuedKeys,
                            newKeyLabel = "",
                            newCustomKey = "",
                            snackbarMessage = "Issued ${body.key.key}"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            adminLoading = false,
                            snackbarMessage = body?.message ?: "Failed (HTTP ${res.code()})"
                        )
                    }
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(adminLoading = false, snackbarMessage = err.localizedMessage)
                }
            }
        }
    }

    fun resetKeyDeviceLock(key: String) {
        val state = _uiState.value
        val api = RenderApiFactory.create(state.renderUrlInput) ?: return
        viewModelScope.launch {
            runCatching {
                val res = api.resetKeyDevice(state.adminSecretInput.trim(), key)
                val updated = res.body()?.key
                if (res.isSuccessful && updated != null) {
                    _uiState.update { s ->
                        s.copy(
                            issuedKeys = s.issuedKeys.map { if (it.key == key) updated else it },
                            snackbarMessage = "Device lock cleared for $key"
                        )
                    }
                }
            }
        }
    }

    fun toggleKeyRevoke(key: String) {
        val state = _uiState.value
        val api = RenderApiFactory.create(state.renderUrlInput) ?: return
        viewModelScope.launch {
            runCatching {
                val res = api.toggleRevokeKey(state.adminSecretInput.trim(), key)
                val updated = res.body()?.key
                if (res.isSuccessful && updated != null) {
                    _uiState.update { s ->
                        s.copy(
                            issuedKeys = s.issuedKeys.map { if (it.key == key) updated else it },
                            snackbarMessage = res.body()?.message ?: "Updated $key"
                        )
                    }
                }
            }
        }
    }

    fun deleteLicenseKey(key: String) {
        val state = _uiState.value
        val api = RenderApiFactory.create(state.renderUrlInput) ?: return
        viewModelScope.launch {
            runCatching {
                val res = api.deleteKey(state.adminSecretInput.trim(), key)
                if (res.isSuccessful) {
                    _uiState.update { s ->
                        s.copy(
                            issuedKeys = s.issuedKeys.filterNot { it.key == key },
                            snackbarMessage = "Deleted $key"
                        )
                    }
                }
            }
        }
    }
}
