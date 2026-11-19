package com.example.data

object ServerTemplates {

    const val ANDROID_ENTRY_CODE = """// 1. Set your Render Admin URL & GitHub Fallback once (or via PranavGate.configure)
PranavGate.DEFAULT_RENDER_URL = "https://your-pranav-admin.onrender.com";
PranavGate.DEFAULT_GITHUB_FALLBACK_URL =
    "https://raw.githubusercontent.com/YOUR_USER/YOUR_REPO/main/config.json";

// 2. Call inside Activity.onCreate(savedInstanceState)
PranavGate.check(this);"""

    const val BYTECODE_SIGNATURE = """// Single unified DEX class & method (Update + 1-Device License + Admin Kill-Switch)
Class:  Lcom/pranav/guard/PranavGate;
Method: check(Landroid/app/Activity;)V

// Activity.onCreate invocation reference:
invoke-static {p0}, Lcom/pranav/guard/PranavGate;->check(Landroid/app/Activity;)V"""

    const val GITHUB_CONFIG_JSON = """{
  "app_enabled": true,
  "maintenance_mode": false,
  "maintenance_title": "Service Paused",
  "maintenance_message": "Access is temporarily paused by PRANAV Admin.",
  "latest_version_code": 2,
  "latest_version_name": "2.0.0",
  "force_update": true,
  "update_title": "Critical Update Available",
  "update_message": "Update to v2.0.0 for new features and security patches.",
  "download_url": "https://github.com/YOUR_USER/YOUR_REPO/releases/latest",
  "require_license": true
}"""

    const val RENDER_ENV_VARS = """PORT=3000 (auto-injected by Render)
ADMIN_SECRET=your_strong_admin_password
GITHUB_TOKEN=ghp_your_repo_contents_token
GITHUB_REPO=your_username/pranav-admin-data
GITHUB_BRANCH=main"""

    const val RENDER_PACKAGE_JSON = """{
  "name": "pranav-admin-server",
  "version": "1.0.0",
  "main": "server.js",
  "scripts": { "start": "node server.js" },
  "dependencies": {
    "cors": "^2.8.5",
    "express": "^4.21.2"
  }
}"""
}
