package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.LicenseRecord
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun AdminScreen(
    uiState: PranavUiState,
    viewModel: PranavViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 640.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Render Admin Auth Card
            PranavSectionCard(
                title = "Render Admin Console",
                subtitle = "Authenticate with your Render server to push updates and manage 1-device keys.",
                icon = Icons.Filled.AdminPanelSettings
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPill(
                        label = if (uiState.adminConnected) "ADMIN AUTHENTICATED" else "DISCONNECTED",
                        color = if (uiState.adminConnected) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (uiState.adminConnected) {
                        StatusPill(
                            label = if (uiState.githubSyncEnabled) "GITHUB SYNC ON" else "RENDER DISK ONLY",
                            color = if (uiState.githubSyncEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.renderUrlInput,
                    onValueChange = viewModel::updateRenderUrl,
                    label = { Text(stringResource(R.string.render_url_label)) },
                    placeholder = { Text("https://pranav-admin.onrender.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_render_url_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.adminSecretInput,
                    onValueChange = viewModel::updateAdminSecret,
                    label = { Text(stringResource(R.string.admin_secret_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("admin_secret_input")
                )

                if (!uiState.adminError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.adminError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = viewModel::loadAdminDashboard,
                    enabled = !uiState.adminLoading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("connect_admin_button")
                ) {
                    if (uiState.adminLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (uiState.adminConnected) "Refresh Dashboard" else "Connect Render Admin")
                }
            }

            if (uiState.adminConnected) {
                val cfg = uiState.editableConfig

                // Remote App Control & Force Update Editor
                PranavSectionCard(
                    title = "Force Update & App Kill-Switch",
                    subtitle = "Full control of app access, force updates, and GitHub release APK links.",
                    icon = Icons.Filled.Tune,
                    accentColor = MaterialTheme.colorScheme.secondary
                ) {
                    AdminToggleRow(
                        title = "App Master Switch",
                        subtitle = "Turn OFF to immediately block all clients",
                        checked = cfg.appEnabled,
                        tag = "toggle_app_enabled",
                        onCheckedChange = { enabled ->
                            viewModel.updateEditableConfig { it.copy(appEnabled = enabled) }
                        }
                    )
                    AdminToggleRow(
                        title = "Maintenance Lock",
                        subtitle = "Show non-dismissible PRANAV Maintenance notice",
                        checked = cfg.maintenanceMode,
                        tag = "toggle_maintenance",
                        onCheckedChange = { m ->
                            viewModel.updateEditableConfig { it.copy(maintenanceMode = m) }
                        }
                    )
                    AdminToggleRow(
                        title = "Require 1-Device License Key",
                        subtitle = "Gate app access behind verified hardware key",
                        checked = cfg.requireLicense,
                        tag = "toggle_require_license",
                        onCheckedChange = { req ->
                            viewModel.updateEditableConfig { it.copy(requireLicense = req) }
                        }
                    )
                    AdminToggleRow(
                        title = "Force Update (Non-Dismissible)",
                        subtitle = "Hide 'Later' button when latest_version_code > app version",
                        checked = cfg.forceUpdate,
                        tag = "toggle_force_update",
                        onCheckedChange = { f ->
                            viewModel.updateEditableConfig { it.copy(forceUpdate = f) }
                        }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = cfg.latestVersionCode.toString(),
                            onValueChange = { txt ->
                                val parsed = txt.filter { it.isDigit() }.toLongOrNull() ?: 1L
                                viewModel.updateEditableConfig { it.copy(latestVersionCode = parsed) }
                            },
                            label = { Text("Version Code") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_version_code_input")
                        )
                        OutlinedTextField(
                            value = cfg.latestVersionName,
                            onValueChange = { v ->
                                viewModel.updateEditableConfig { it.copy(latestVersionName = v) }
                            },
                            label = { Text("Version Name") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("admin_version_name_input")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = cfg.updateTitle,
                        onValueChange = { t ->
                            viewModel.updateEditableConfig { it.copy(updateTitle = t) }
                        },
                        label = { Text("Update Dialog Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = cfg.updateMessage,
                        onValueChange = { m ->
                            viewModel.updateEditableConfig { it.copy(updateMessage = m) }
                        },
                        label = { Text("Update Release Notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = cfg.downloadUrl,
                        onValueChange = { u ->
                            viewModel.updateEditableConfig { it.copy(downloadUrl = u) }
                        },
                        label = { Text("GitHub APK / Release URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = cfg.supportUrl,
                        onValueChange = { s ->
                            viewModel.updateEditableConfig { it.copy(supportUrl = s) }
                        },
                        label = { Text("Get Key / Admin Contact URL (Telegram/WhatsApp)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = viewModel::pushAdminConfig,
                        enabled = !uiState.adminLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("push_admin_config_button")
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.save_remote_config_cta))
                    }
                }

                // One-Device License Key Issuer
                PranavSectionCard(
                    title = "One-Device License Keys (${uiState.issuedKeys.size})",
                    subtitle = "Issue new single-device keys, reset device bindings, or revoke access.",
                    icon = Icons.Filled.Key,
                    accentColor = MaterialTheme.colorScheme.tertiary
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = uiState.newKeyLabel,
                            onValueChange = { viewModel.updateNewKeyFields(label = it) },
                            label = { Text("User / Note") },
                            placeholder = { Text("Rahul VIP") },
                            singleLine = true,
                            modifier = Modifier
                                .weight(1.4f)
                                .testTag("new_key_label_input")
                        )
                        OutlinedTextField(
                            value = uiState.newKeyDays,
                            onValueChange = { viewModel.updateNewKeyFields(days = it.filter { c -> c.isDigit() }) },
                            label = { Text("Days") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(0.7f)
                                .testTag("new_key_days_input")
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = uiState.newCustomKey,
                        onValueChange = { viewModel.updateNewKeyFields(customKey = it.uppercase()) },
                        label = { Text("Custom Key (Optional — blank = auto PRANAV-XXXX)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = viewModel::issueNewLicenseKey,
                        enabled = !uiState.adminLoading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("issue_key_button")
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.create_key_cta))
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    if (uiState.issuedKeys.isEmpty()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.background,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Key,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No license keys issued yet",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Create your first single-device key above.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            uiState.issuedKeys.forEach { record ->
                                LicenseKeyItemCard(
                                    record = record,
                                    onCopy = {
                                        copyToClipboard(context, "PRANAV License", record.key)
                                        viewModel.showMessage("Copied ${record.key}")
                                    },
                                    onResetDevice = { viewModel.resetKeyDeviceLock(record.key) },
                                    onToggleRevoke = { viewModel.toggleKeyRevoke(record.key) },
                                    onDelete = { viewModel.deleteLicenseKey(record.key) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Helpful empty guidance card before connecting
                PranavSectionCard(
                    title = "Need a Render Admin Backend?",
                    subtitle = "Switch to the Deploy tab for the complete GitHub + Render server files (server.js, package.json, config.json) and environment setup.",
                    icon = Icons.Outlined.Block,
                    accentColor = MaterialTheme.colorScheme.secondary
                ) {
                    OutlinedButton(
                        onClick = { viewModel.selectTab(PranavTab.DEPLOY) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .minimumInteractiveComponentSize()
                            .testTag("goto_deploy_tab_button")
                    ) {
                        Text("Open GitHub + Render Deploy Guide")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun AdminToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    tag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(tag)
        )
    }
}

@Composable
private fun LicenseKeyItemCard(
    record: LicenseRecord,
    onCopy: () -> Unit,
    onResetDevice: () -> Unit,
    onToggleRevoke: () -> Unit,
    onDelete: () -> Unit
) {
    val statusColor = when {
        record.revoked -> MaterialTheme.colorScheme.error
        !record.boundDeviceId.isNullOrBlank() -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.secondary
    }
    val statusText = when {
        record.revoked -> "REVOKED"
        !record.boundDeviceId.isNullOrBlank() -> "1-DEVICE LOCKED"
        else -> "UNBOUND (READY)"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.label.ifBlank { "Device User" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                StatusPill(label = statusText, color = statusColor)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = record.key,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy license key",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Text(
                text = if (!record.boundDeviceId.isNullOrBlank()) {
                    "Bound: ${record.boundDeviceId} (${record.boundDeviceModel ?: "Android"})"
                } else {
                    "Not bound to any device yet"
                },
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!record.expiresAt.isNullOrBlank()) {
                Text(
                    text = "Expires: ${record.expiresAt.take(10)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (!record.boundDeviceId.isNullOrBlank()) {
                    TextButton(onClick = onResetDevice) {
                        Icon(Icons.Filled.LockReset, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Reset Device")
                    }
                }
                TextButton(onClick = onToggleRevoke) {
                    Text(if (record.revoked) "Restore" else "Revoke")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete license key",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
