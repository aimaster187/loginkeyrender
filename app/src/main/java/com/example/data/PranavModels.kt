package com.example.data

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class GateConfig(
    @Json(name = "app_enabled") val appEnabled: Boolean = true,
    @Json(name = "maintenance_mode") val maintenanceMode: Boolean = false,
    @Json(name = "maintenance_title") val maintenanceTitle: String = "System Maintenance",
    @Json(name = "maintenance_message") val maintenanceMessage: String = "App is temporarily paused by PRANAV Admin.",
    @Json(name = "latest_version_code") val latestVersionCode: Long = 1L,
    @Json(name = "latest_version_name") val latestVersionName: String = "1.0",
    @Json(name = "force_update") val forceUpdate: Boolean = false,
    @Json(name = "update_title") val updateTitle: String = "New Update Available",
    @Json(name = "update_message") val updateMessage: String = "Please install the latest release from GitHub.",
    @Json(name = "download_url") val downloadUrl: String = "",
    @Json(name = "support_url") val supportUrl: String = "",
    @Json(name = "require_license") val requireLicense: Boolean = true,
    @Json(name = "updated_at") val updatedAt: String = ""
)

@JsonClass(generateAdapter = true)
data class LicenseVerifyRequest(
    @Json(name = "license_key") val licenseKey: String,
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "device_model") val deviceModel: String,
    @Json(name = "package_name") val packageName: String
)

@JsonClass(generateAdapter = true)
data class LicenseVerifyResponse(
    @Json(name = "valid") val valid: Boolean = false,
    @Json(name = "status") val status: String = "",
    @Json(name = "message") val message: String = "",
    @Json(name = "bound_device_id") val boundDeviceId: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null
)

@JsonClass(generateAdapter = true)
data class LicenseRecord(
    @Json(name = "key") val key: String,
    @Json(name = "label") val label: String = "",
    @Json(name = "bound_device_id") val boundDeviceId: String? = null,
    @Json(name = "bound_device_model") val boundDeviceModel: String? = null,
    @Json(name = "expires_at") val expiresAt: String? = null,
    @Json(name = "revoked") val revoked: Boolean = false,
    @Json(name = "created_at") val createdAt: String = "",
    @Json(name = "last_check_at") val lastCheckAt: String? = null
)

@JsonClass(generateAdapter = true)
data class CreateKeyRequest(
    @Json(name = "label") val label: String,
    @Json(name = "days_valid") val daysValid: Int,
    @Json(name = "custom_key") val customKey: String? = null
)

@JsonClass(generateAdapter = true)
data class AdminDashboardResponse(
    @Json(name = "ok") val ok: Boolean = true,
    @Json(name = "config") val config: GateConfig = GateConfig(),
    @Json(name = "keys") val keys: List<LicenseRecord> = emptyList(),
    @Json(name = "github_sync_enabled") val githubSyncEnabled: Boolean = false
)

@JsonClass(generateAdapter = true)
data class ApiActionResponse(
    @Json(name = "ok") val ok: Boolean = false,
    @Json(name = "message") val message: String = "",
    @Json(name = "key") val key: LicenseRecord? = null,
    @Json(name = "config") val config: GateConfig? = null
)
