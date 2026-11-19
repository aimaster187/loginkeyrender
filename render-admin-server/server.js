const express = require("express");
const cors = require("cors");
const crypto = require("crypto");
const fs = require("fs");
const path = require("path");

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 3000;
const ADMIN_SECRET = process.env.ADMIN_SECRET || "change-this-pranav-secret";
const GITHUB_TOKEN = process.env.GITHUB_TOKEN || "";
const GITHUB_REPO = process.env.GITHUB_REPO || ""; // e.g. "username/pranav-admin-data"
const GITHUB_BRANCH = process.env.GITHUB_BRANCH || "main";

const DATA_DIR = path.join(__dirname, "data");
const CONFIG_FILE = path.join(DATA_DIR, "config.json");
const KEYS_FILE = path.join(DATA_DIR, "licenses.json");

if (!fs.existsSync(DATA_DIR)) fs.mkdirSync(DATA_DIR, { recursive: true });

const defaultConfig = {
  app_enabled: true,
  maintenance_mode: false,
  maintenance_title: "Service Paused",
  maintenance_message: "Access is temporarily paused by PRANAV Admin.",
  latest_version_code: 1,
  latest_version_name: "1.0",
  force_update: false,
  update_title: "New Update Available",
  update_message: "A new version is ready on GitHub Releases.",
  download_url: "https://github.com",
  support_url: "https://t.me/",
  require_license: true,
  updated_at: new Date().toISOString()
};

function readJson(filePath, fallback) {
  try {
    if (fs.existsSync(filePath)) {
      return JSON.parse(fs.readFileSync(filePath, "utf8"));
    }
  } catch (err) {
    console.error("Read error:", err.message);
  }
  return fallback;
}

async function syncFileToGitHub(repoFilePath, jsonContent, commitMsg) {
  if (!GITHUB_TOKEN || !GITHUB_REPO) return;
  const apiUrl = `https://api.github.com/repos/${GITHUB_REPO}/contents/${repoFilePath}`;
  let sha = undefined;
  try {
    const getRes = await fetch(`${apiUrl}?ref=${GITHUB_BRANCH}`, {
      headers: {
        Authorization: `Bearer ${GITHUB_TOKEN}`,
        Accept: "application/vnd.github+json",
        "User-Agent": "pranav-render-admin"
      }
    });
    if (getRes.ok) {
      const existing = await getRes.json();
      sha = existing.sha;
    }
    const contentB64 = Buffer.from(JSON.stringify(jsonContent, null, 2)).toString("base64");
    await fetch(apiUrl, {
      method: "PUT",
      headers: {
        Authorization: `Bearer ${GITHUB_TOKEN}`,
        Accept: "application/vnd.github+json",
        "User-Agent": "pranav-render-admin",
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        message: commitMsg,
        content: contentB64,
        branch: GITHUB_BRANCH,
        ...(sha ? { sha } : {})
      })
    });
  } catch (err) {
    console.error("GitHub sync failed:", err.message);
  }
}

async function loadFromGitHubIfAvailable() {
  if (!GITHUB_TOKEN || !GITHUB_REPO) return;
  for (const [repoFile, localFile] of [
    ["config.json", CONFIG_FILE],
    ["licenses.json", KEYS_FILE]
  ]) {
    try {
      const res = await fetch(
        `https://api.github.com/repos/${GITHUB_REPO}/contents/${repoFile}?ref=${GITHUB_BRANCH}`,
        {
          headers: {
            Authorization: `Bearer ${GITHUB_TOKEN}`,
            Accept: "application/vnd.github+json",
            "User-Agent": "pranav-render-admin"
          }
        }
      );
      if (res.ok) {
        const data = await res.json();
        const decoded = Buffer.from(data.content, "base64").toString("utf8");
        fs.writeFileSync(localFile, decoded);
      }
    } catch (err) {
      console.error(`Bootstrap ${repoFile} failed:`, err.message);
    }
  }
}

let gateConfig = readJson(CONFIG_FILE, defaultConfig);
let licenseKeys = readJson(KEYS_FILE, []);

function saveConfig() {
  fs.writeFileSync(CONFIG_FILE, JSON.stringify(gateConfig, null, 2));
  syncFileToGitHub("config.json", gateConfig, "PRANAV Admin: update config.json");
}

function saveKeys() {
  fs.writeFileSync(KEYS_FILE, JSON.stringify(licenseKeys, null, 2));
  syncFileToGitHub("licenses.json", licenseKeys, "PRANAV Admin: update licenses.json");
}

function requireAdmin(req, res, next) {
  const token = req.headers["x-admin-secret"];
  if (!token || token !== ADMIN_SECRET) {
    return res.status(401).json({ ok: false, message: "Unauthorized: Invalid x-admin-secret" });
  }
  next();
}

function generatePranavKey() {
  const seg = () => crypto.randomBytes(2).toString("hex").toUpperCase();
  return `PRANAV-${seg()}-${seg()}-${seg()}`;
}

app.get("/health", (_req, res) => {
  res.json({
    service: "PRANAV Admin & One-Device Gate Server",
    status: "ONLINE",
    github_sync_enabled: Boolean(GITHUB_TOKEN && GITHUB_REPO),
    require_license: gateConfig.require_license,
    latest_version_code: gateConfig.latest_version_code
  });
});

app.get("/", (_req, res) => {
  res.type("html").send(`<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1" />
<title>PRANAV Admin Console</title>
<style>
  body { background:#070B14; color:#F1F5F9; font-family:system-ui,sans-serif; margin:0; padding:24px; }
  .wrap { max-width:760px; margin:0 auto; display:grid; gap:16px; }
  .card { background:#0F1624; border:1px solid #283548; border-radius:16px; padding:18px; }
  h1 { margin:0 0 6px; color:#00E5FF; font-size:22px; }
  h2 { margin:0 0 12px; font-size:17px; }
  input, textarea { width:100%; box-sizing:border-box; background:#070B14; color:#F1F5F9; border:1px solid #283548; border-radius:10px; padding:10px 12px; margin:6px 0 12px; }
  button { background:#00E5FF; color:#04131A; font-weight:700; border:none; border-radius:10px; padding:10px 14px; cursor:pointer; }
  button.sec { background:#1E293B; color:#CBD5E1; }
  button.warn { background:#FFB300; color:#1A1100; }
  button.err { background:#FF4D6D; color:#fff; }
  .row { display:flex; gap:10px; flex-wrap:wrap; align-items:center; }
  .key-item { background:#070B14; border:1px solid #283548; border-radius:12px; padding:12px; margin-top:10px; }
  .mono { font-family:monospace; color:#00E5FF; }
  label { display:flex; align-items:center; gap:8px; margin:6px 0; cursor:pointer; }
  label input[type=checkbox] { width:auto; margin:0; }
</style>
</head>
<body>
<div class="wrap">
  <div class="card">
    <h1>PRANAV RENDER ADMIN</h1>
    <div>One-Device License &amp; Force Update Control Panel</div>
    <div class="row" style="margin-top:12px">
      <input id="secret" type="password" placeholder="Enter ADMIN_SECRET" style="flex:1;margin:0" />
      <button onclick="loadDash()">Connect</button>
    </div>
    <div id="status" style="margin-top:8px;color:#94A3B8;font-size:13px"></div>
  </div>
  <div id="panel" style="display:none" class="wrap">
    <div class="card">
      <h2>Remote Update &amp; Kill Switch</h2>
      <label><input type="checkbox" id="app_enabled" /> App Enabled (Master Switch)</label>
      <label><input type="checkbox" id="maintenance_mode" /> Maintenance Lock</label>
      <label><input type="checkbox" id="require_license" /> Require 1-Device License Key</label>
      <label><input type="checkbox" id="force_update" /> Force Update (Non-Dismissible)</label>
      <div class="row">
        <div style="flex:1"><small>Version Code</small><input id="latest_version_code" type="number" /></div>
        <div style="flex:1"><small>Version Name</small><input id="latest_version_name" /></div>
      </div>
      <small>Update Title</small><input id="update_title" />
      <small>Changelog / Message</small><textarea id="update_message" rows="3"></textarea>
      <small>GitHub APK / Release URL</small><input id="download_url" />
      <small>Get Key / Admin Contact URL</small><input id="support_url" />
      <button onclick="saveConfig()">Save &amp; Sync Config</button>
    </div>
    <div class="card">
      <h2>Issue 1-Device License Key</h2>
      <div class="row">
        <input id="klabel" placeholder="User label (e.g. Pranav VIP)" style="flex:2;margin:0" />
        <input id="kdays" type="number" value="30" placeholder="Days" style="flex:1;margin:0" />
        <button onclick="issueKey()">Create Key</button>
      </div>
      <div id="keys"></div>
    </div>
  </div>
</div>
<script>
const h = () => ({ "Content-Type":"application/json", "x-admin-secret": document.getElementById("secret").value });
async function loadDash() {
  const r = await fetch("/api/admin/dashboard", { headers: h() });
  const d = await r.json();
  if (!r.ok) { document.getElementById("status").textContent = d.message || "Auth failed"; return; }
  document.getElementById("status").textContent = "Connected" + (d.github_sync_enabled ? " (GitHub Sync Active)" : "");
  document.getElementById("panel").style.display = "grid";
  const c = d.config;
  ["app_enabled","maintenance_mode","require_license","force_update"].forEach(k => document.getElementById(k).checked = !!c[k]);
  ["latest_version_code","latest_version_name","update_title","update_message","download_url","support_url"].forEach(k => document.getElementById(k).value = c[k] ?? "");
  renderKeys(d.keys || []);
}
async function saveConfig() {
  const body = {
    app_enabled: document.getElementById("app_enabled").checked,
    maintenance_mode: document.getElementById("maintenance_mode").checked,
    require_license: document.getElementById("require_license").checked,
    force_update: document.getElementById("force_update").checked,
    latest_version_code: Number(document.getElementById("latest_version_code").value || 1),
    latest_version_name: document.getElementById("latest_version_name").value,
    update_title: document.getElementById("update_title").value,
    update_message: document.getElementById("update_message").value,
    download_url: document.getElementById("download_url").value,
    support_url: document.getElementById("support_url").value
  };
  await fetch("/api/admin/config", { method:"PUT", headers: h(), body: JSON.stringify(body) });
  loadDash();
}
async function issueKey() {
  await fetch("/api/admin/keys", { method:"POST", headers: h(), body: JSON.stringify({ label: document.getElementById("klabel").value, days_valid: Number(document.getElementById("kdays").value || 30) }) });
  loadDash();
}
async function keyAct(url, method="POST") { await fetch(url, { method, headers: h() }); loadDash(); }
function renderKeys(list) {
  document.getElementById("keys").innerHTML = list.map(k => \`
    <div class="key-item">
      <div class="row" style="justify-content:space-between">
        <div><b>\${k.label}</b> • <span class="mono">\${k.key}</span></div>
        <div>\${k.revoked ? "REVOKED" : (k.bound_device_id ? "LOCKED: " + k.bound_device_id : "UNBOUND")}</div>
      </div>
      <div class="row" style="margin-top:8px">
        \${k.bound_device_id ? \`<button class="sec" onclick="keyAct('/api/admin/keys/\${k.key}/reset-device')">Reset Device</button>\` : ""}
        <button class="warn" onclick="keyAct('/api/admin/keys/\${k.key}/toggle-revoke')">\${k.revoked ? "Restore" : "Revoke"}</button>
        <button class="err" onclick="keyAct('/api/admin/keys/\${k.key}','DELETE')">Delete</button>
      </div>
    </div>\`).join("");
}
</script>
</body>
</html>`);
});

// Public Gate Config Endpoint (called by PranavGate.check)
app.get("/api/gate/config", (_req, res) => {
  res.json(gateConfig);
});

// Public One-Device License Key Verification
app.post("/api/gate/verify", (req, res) => {
  const { license_key, device_id, device_model } = req.body || {};
  if (!license_key || !device_id) {
    return res.status(400).json({
      valid: false,
      status: "BAD_REQUEST",
      message: "license_key and device_id are required."
    });
  }

  const normalizedKey = String(license_key).trim().toUpperCase();
  const record = licenseKeys.find((k) => k.key === normalizedKey);

  if (!record) {
    return res.status(404).json({
      valid: false,
      status: "NOT_FOUND",
      message: "Invalid PRANAV license key."
    });
  }

  if (record.revoked) {
    return res.status(403).json({
      valid: false,
      status: "REVOKED",
      message: "This license key has been revoked by Admin."
    });
  }

  if (record.expires_at && new Date(record.expires_at).getTime() < Date.now()) {
    return res.status(403).json({
      valid: false,
      status: "EXPIRED",
      message: "License key expired on " + record.expires_at.slice(0, 10)
    });
  }

  // Enforce strict 1-device binding
  if (!record.bound_device_id) {
    record.bound_device_id = device_id;
    record.bound_device_model = device_model || "Android Device";
    record.last_check_at = new Date().toISOString();
    saveKeys();
    return res.json({
      valid: true,
      status: "BOUND_NEW",
      message: "License bound exclusively to this device.",
      bound_device_id: record.bound_device_id,
      expires_at: record.expires_at
    });
  }

  if (record.bound_device_id !== device_id) {
    return res.status(403).json({
      valid: false,
      status: "DEVICE_MISMATCH",
      message: "Key is already locked to another device (" + (record.bound_device_model || "Unknown") + "). Ask Admin to reset device lock."
    });
  }

  record.last_check_at = new Date().toISOString();
  saveKeys();
  return res.json({
    valid: true,
    status: "ACTIVE",
    message: "One-device license verified.",
    bound_device_id: record.bound_device_id,
    expires_at: record.expires_at
  });
});

// Admin Endpoints
app.get("/api/admin/dashboard", requireAdmin, (_req, res) => {
  res.json({
    ok: true,
    config: gateConfig,
    keys: licenseKeys,
    github_sync_enabled: Boolean(GITHUB_TOKEN && GITHUB_REPO)
  });
});

app.put("/api/admin/config", requireAdmin, (req, res) => {
  gateConfig = {
    ...gateConfig,
    ...req.body,
    updated_at: new Date().toISOString()
  };
  saveConfig();
  res.json({ ok: true, message: "Remote config updated.", config: gateConfig });
});

app.post("/api/admin/keys", requireAdmin, (req, res) => {
  const { label = "User", days_valid = 30, custom_key = "" } = req.body || {};
  const keyStr = custom_key && custom_key.trim() ? custom_key.trim().toUpperCase() : generatePranavKey();
  if (licenseKeys.some((k) => k.key === keyStr)) {
    return res.status(409).json({ ok: false, message: "Key already exists." });
  }
  const expiresAt =
    Number(days_valid) > 0
      ? new Date(Date.now() + Number(days_valid) * 86400000).toISOString()
      : null;

  const newKey = {
    key: keyStr,
    label: String(label).trim() || "User",
    bound_device_id: null,
    bound_device_model: null,
    expires_at: expiresAt,
    revoked: false,
    created_at: new Date().toISOString(),
    last_check_at: null
  };
  licenseKeys.unshift(newKey);
  saveKeys();
  res.json({ ok: true, message: "Issued key " + keyStr, key: newKey });
});

app.post("/api/admin/keys/:key/reset-device", requireAdmin, (req, res) => {
  const record = licenseKeys.find((k) => k.key === req.params.key);
  if (!record) return res.status(404).json({ ok: false, message: "Key not found." });
  record.bound_device_id = null;
  record.bound_device_model = null;
  saveKeys();
  res.json({ ok: true, message: "Device lock cleared for " + record.key, key: record });
});

app.post("/api/admin/keys/:key/toggle-revoke", requireAdmin, (req, res) => {
  const record = licenseKeys.find((k) => k.key === req.params.key);
  if (!record) return res.status(404).json({ ok: false, message: "Key not found." });
  record.revoked = !record.revoked;
  saveKeys();
  res.json({ ok: true, message: (record.revoked ? "Revoked " : "Restored ") + record.key, key: record });
});

app.delete("/api/admin/keys/:key", requireAdmin, (req, res) => {
  const before = licenseKeys.length;
  licenseKeys = licenseKeys.filter((k) => k.key !== req.params.key);
  if (licenseKeys.length === before) return res.status(404).json({ ok: false, message: "Key not found." });
  saveKeys();
  res.json({ ok: true, message: "Deleted key " + req.params.key });
});

loadFromGitHubIfAvailable().then(() => {
  gateConfig = readJson(CONFIG_FILE, gateConfig);
  licenseKeys = readJson(KEYS_FILE, licenseKeys);
  app.listen(PORT, () => console.log(`PRANAV Admin Server listening on port ${PORT}`));
});
