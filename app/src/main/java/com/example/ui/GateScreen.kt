package com.example.ui

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.pranav.guard.PranavGate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GateScreen(
    uiState: PranavUiState,
    viewModel: PranavViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity

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
            PranavHeroBanner(
                badgeText = "PRANAV GATE v${uiState.appVersionName} (CODE ${uiState.appVersionCode})",
                title = "Unified Protection Engine",
                subtitle = "One-Device Hardware License + Force Update + Render Admin Kill-Switch inside one DEX class.",
                deviceId = uiState.deviceId,
                onCopyDeviceId = {
                    copyToClipboard(context, "PRANAV Device ID", uiState.deviceId)
                    viewModel.showMessage("Device ID copied to clipboard")
                }
            )

            // Endpoint configuration card
            PranavSectionCard(
                title = "Render & GitHub Endpoints",
                subtitle = "PranavGate fetches live config from Render first, then GitHub Raw JSON fallback.",
                icon = Icons.Filled.CloudSync
            ) {
                val badgeColor = when (uiState.connectionState) {
                    is GateConnectionState.Ready -> MaterialTheme.colorScheme.tertiary
                    is GateConnectionState.Checking -> MaterialTheme.colorScheme.secondary
                    is GateConnectionState.Error -> MaterialTheme.colorScheme.error
                    GateConnectionState.Unconfigured -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                val badgeLabel = when (val c = uiState.connectionState) {
                    is GateConnectionState.Ready -> "CONNECTED • ${c.source.uppercase()}"
                    GateConnectionState.Checking -> "SYNCING..."
                    is GateConnectionState.Error -> "ERROR: ${c.message}"
                    GateConnectionState.Unconfigured -> "NO ENDPOINT CONFIGURED"
                }
                StatusPill(label = badgeLabel, color = badgeColor)
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.renderUrlInput,
                    onValueChange = viewModel::updateRenderUrl,
                    label = { Text(stringResource(R.string.render_url_label)) },
                    placeholder = { Text("https://pranav-admin.onrender.com") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("render_url_input")
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.githubFallbackInput,
                    onValueChange = viewModel::updateGithubFallback,
                    label = { Text(stringResource(R.string.github_raw_label)) },
                    placeholder = { Text("https://raw.githubusercontent.com/user/repo/main/config.json") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("github_fallback_input")
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = viewModel::saveAndSyncGate,
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("save_sync_gate_button")
                    ) {
                        if (uiState.connectionState is GateConnectionState.Checking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.save_endpoint_cta))
                    }

                    FilledTonalButton(
                        onClick = {
                            if (activity != null) {
                                viewModel.runLivePranavGateCheck(activity)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .testTag("run_pranav_gate_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Run Gate")
                    }
                }
            }

            // One-Device License Key Card
            PranavSectionCard(
                title = "One-Device License Binding",
                subtitle = "Keys lock permanently to the first Android device until reset by Admin.",
                icon = Icons.Filled.Key,
                accentColor = MaterialTheme.colorScheme.secondary
            ) {
                StatusPill(
                    label = if (uiState.isLicenseValid) "DEVICE AUTHORIZED" else "LOCKED / UNVERIFIED",
                    color = if (uiState.isLicenseValid) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = uiState.licenseStatusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = uiState.licenseKeyInput,
                    onValueChange = viewModel::updateLicenseKeyInput,
                    label = { Text(stringResource(R.string.license_key_label)) },
                    placeholder = { Text("PRANAV-A1B2-C3D4-E5F6") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("license_key_input")
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = viewModel::verifyLicenseKeyNow,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("verify_license_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerifiedUser,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Verify & Bind to This Device")
                }
            }

            // Live Remote Config Status Readout
            if (uiState.liveGateConfig != null) {
                val cfg = uiState.liveGateConfig
                PranavSectionCard(
                    title = "Live Remote Policy",
                    subtitle = "Fetched directly from your Render / GitHub control plane",
                    icon = Icons.Filled.Security,
                    accentColor = MaterialTheme.colorScheme.tertiary
                ) {
                    PolicyRow("App Master Switch", if (cfg.appEnabled && !cfg.maintenanceMode) "ONLINE" else "PAUSED")
                    PolicyRow("Target Version", "v${cfg.latestVersionName} (code ${cfg.latestVersionCode})")
                    PolicyRow("Force Update", if (cfg.forceUpdate) "REQUIRED (NON-DISMISSIBLE)" else "Optional")
                    PolicyRow("1-Device License Gate", if (cfg.requireLicense) "ENFORCED" else "Open Access")
                    PolicyRow("APK / Release URL", cfg.downloadUrl.ifBlank { "Not set" })
                }
            }

            // Native PranavGate Dialog Previewer (always works even offline!)
            PranavSectionCard(
                title = "Native PranavGate Dialog Preview",
                subtitle = "Inspect the exact dialogs invoked by PranavGate.check(activity) in onCreate().",
                icon = Icons.Filled.SystemUpdate
            ) {
                Text(
                    text = uiState.gateCallbackLog,
                    fontFamily = JetBrainsMonoFontFamily,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            if (activity != null) {
                                PranavGate.showLocalPreviewDialog(
                                    activity,
                                    PranavGate.PreviewMode.FORCE_UPDATE
                                )
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("preview_force_update_button")
                    ) {
                        Icon(Icons.Filled.SystemUpdate, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Force Update")
                    }

                    OutlinedButton(
                        onClick = {
                            if (activity != null) {
                                PranavGate.showLocalPreviewDialog(
                                    activity,
                                    PranavGate.PreviewMode.LICENSE_GATE
                                )
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("preview_license_dialog_button")
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1-Device Login")
                    }

                    OutlinedButton(
                        onClick = {
                            if (activity != null) {
                                PranavGate.showLocalPreviewDialog(
                                    activity,
                                    PranavGate.PreviewMode.MAINTENANCE_BLOCK
                                )
                            }
                        },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("preview_maintenance_button")
                    ) {
                        Icon(Icons.Outlined.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kill Switch")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PolicyRow(label: String, value: String) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.background,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = value,
                fontFamily = JetBrainsMonoFontFamily,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
