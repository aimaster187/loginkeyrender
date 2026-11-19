package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.ServerTemplates

@Composable
fun DeployScreen(
    viewModel: PranavViewModel,
    modifier: Modifier = Modifier
) {
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
            PranavSectionCard(
                title = "GitHub + Render Deployment",
                subtitle = "Host your PRANAV Admin server on Render with automatic GitHub persistence.",
                icon = Icons.Filled.RocketLaunch
            ) {
                StepBullet("1. Push `render-admin-server/` (server.js, package.json, config.json) to a GitHub repository.")
                StepBullet("2. Create a new Web Service on Render connected to that GitHub repo (Build: `npm install`, Start: `npm start`).")
                StepBullet("3. Add `ADMIN_SECRET`, `GITHUB_TOKEN`, and `GITHUB_REPO` in Render Environment Variables so keys & updates survive restarts.")
                StepBullet("4. Paste your `https://*.onrender.com` URL in the Gate & Admin tabs.")
            }

            PranavSectionCard(
                title = "Single-DEX PranavGate Integration",
                subtitle = "Update, Force Dialog, Kill Switch, and 1-Device License Key all inside `com.pranav.guard.PranavGate`.",
                icon = Icons.Filled.Code,
                accentColor = MaterialTheme.colorScheme.secondary
            ) {
                Text(
                    text = "Call `PranavGate.check(this)` in your Activity's `onCreate()`. Zero third-party dependencies required inside the `com.pranav.guard` package.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            CodeSnippetCard(
                title = "Activity.onCreate() Hook",
                code = ServerTemplates.ANDROID_ENTRY_CODE,
                tagPrefix = "entry",
                onCopied = viewModel::showMessage
            )

            CodeSnippetCard(
                title = "Unified DEX Method Signature",
                code = ServerTemplates.BYTECODE_SIGNATURE,
                tagPrefix = "signature",
                onCopied = viewModel::showMessage
            )

            CodeSnippetCard(
                title = "GitHub Raw Fallback (config.json)",
                code = ServerTemplates.GITHUB_CONFIG_JSON,
                tagPrefix = "config_json",
                onCopied = viewModel::showMessage
            )

            CodeSnippetCard(
                title = "Render Environment Variables",
                code = ServerTemplates.RENDER_ENV_VARS,
                tagPrefix = "render_env",
                onCopied = viewModel::showMessage
            )

            CodeSnippetCard(
                title = "Render Server (package.json)",
                code = ServerTemplates.RENDER_PACKAGE_JSON,
                tagPrefix = "pkg_json",
                onCopied = viewModel::showMessage
            )

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun StepBullet(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 3.dp)
    )
}
