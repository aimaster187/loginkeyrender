package com.pranav.guard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PRANAV Unified Gate Engine.
 * Combines Admin Remote Control (Maintenance / Kill Switch), Force App Update Dialog,
 * and One-Device License Key Login into a single self-contained entry point:
 *
 *   PranavGate.check(activity);
 *   // Bytecode signature: Lcom/pranav/guard/PranavGate;->check(Landroid/app/Activity;)V
 */
public final class PranavGate {

    private static final String PREFS_NAME = "pranav_gate_prefs";
    private static final String KEY_RENDER_URL = "render_base_url";
    private static final String KEY_GITHUB_URL = "github_fallback_url";
    private static final String KEY_LICENSE = "saved_license_key";

    public static volatile String DEFAULT_RENDER_URL = "";
    public static volatile String DEFAULT_GITHUB_FALLBACK_URL = "";

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public enum PreviewMode {
        FORCE_UPDATE,
        LICENSE_GATE,
        MAINTENANCE_BLOCK
    }

    public interface GateCallback {
        void onGateStatus(boolean allowed, String statusMessage);
    }

    private PranavGate() {}

    public static void check(final Activity activity) {
        check(activity, null, null, null);
    }

    public static void check(
            final Activity activity,
            final String renderBaseUrl,
            final String githubFallbackUrl
    ) {
        check(activity, renderBaseUrl, githubFallbackUrl, null);
    }

    public static void check(
            final Activity activity,
            final String renderBaseUrl,
            final String githubFallbackUrl,
            final GateCallback callback
    ) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        final Context appCtx = activity.getApplicationContext();
        final SharedPreferences prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        final String resolvedRenderUrl = normalizeUrl(
                !isBlank(renderBaseUrl) ? renderBaseUrl
                        : !isBlank(prefs.getString(KEY_RENDER_URL, "")) ? prefs.getString(KEY_RENDER_URL, "")
                        : DEFAULT_RENDER_URL
        );
        final String resolvedGithubUrl = normalizeUrl(
                !isBlank(githubFallbackUrl) ? githubFallbackUrl
                        : !isBlank(prefs.getString(KEY_GITHUB_URL, "")) ? prefs.getString(KEY_GITHUB_URL, "")
                        : DEFAULT_GITHUB_FALLBACK_URL
        );

        if (isBlank(resolvedRenderUrl) && isBlank(resolvedGithubUrl)) {
            notifyCallback(callback, false, "No Render Admin URL or GitHub config URL configured.");
            return;
        }

        EXECUTOR.execute(() -> {
            try {
                final long currentVersionCode = getAppVersionCode(appCtx);
                final String currentVersionName = getAppVersionName(appCtx);
                final String pkgName = appCtx.getPackageName();
                JSONObject config = null;

                if (!isBlank(resolvedRenderUrl)) {
                    String configUrl = resolvedRenderUrl
                            + "/api/gate/config?pkg=" + Uri.encode(pkgName)
                            + "&vc=" + currentVersionCode;
                    config = httpGetJson(configUrl);
                }

                if (config == null && !isBlank(resolvedGithubUrl)) {
                    config = httpGetJson(resolvedGithubUrl);
                }

                if (config == null) {
                    notifyCallback(callback, false, "Unable to reach Render Admin or GitHub fallback.");
                    return;
                }

                final boolean appEnabled = config.optBoolean("app_enabled", true);
                final boolean maintenanceMode = config.optBoolean("maintenance_mode", false);
                final String supportUrl = config.optString("support_url", "");

                if (!appEnabled || maintenanceMode) {
                    final String title = config.optString("maintenance_title", "Service Paused");
                    final String msg = config.optString(
                            "maintenance_message",
                            "Access is temporarily paused by PRANAV Admin. Please check back soon."
                    );
                    MAIN_HANDLER.post(() -> showBlockingNoticeDialog(activity, title, msg, supportUrl, true));
                    notifyCallback(callback, false, "Blocked by Admin kill-switch / maintenance mode.");
                    return;
                }

                final long latestVersionCode = config.optLong("latest_version_code", currentVersionCode);
                final String latestVersionName = config.optString("latest_version_name", String.valueOf(latestVersionCode));
                final boolean forceUpdate = config.optBoolean("force_update", false);
                final String updateTitle = config.optString("update_title", "New Update Available");
                final String updateMessage = config.optString(
                        "update_message",
                        "• Improved performance & security\n• New PRANAV Admin controls"
                );
                final String downloadUrl = config.optString("download_url", "");
                final boolean requireLicense = config.optBoolean("require_license", true);

                if (latestVersionCode > currentVersionCode) {
                    MAIN_HANDLER.post(() -> showUpdateDialog(
                            activity,
                            updateTitle,
                            currentVersionName,
                            latestVersionName,
                            updateMessage,
                            downloadUrl,
                            forceUpdate,
                            () -> {
                                if (requireLicense) {
                                    verifySavedOrPromptLicense(activity, resolvedRenderUrl, supportUrl, callback);
                                } else {
                                    notifyCallback(callback, true, "Gate passed (update skipped).");
                                }
                            }
                    ));
                    notifyCallback(
                            callback,
                            !forceUpdate,
                            forceUpdate ? "Force Update required (v" + latestVersionName + ")."
                                    : "Optional update available (v" + latestVersionName + ")."
                    );
                    return;
                }

                if (requireLicense) {
                    verifySavedOrPromptLicense(activity, resolvedRenderUrl, supportUrl, callback);
                } else {
                    notifyCallback(callback, true, "Gate passed (open access mode).");
                }
            } catch (Exception e) {
                notifyCallback(callback, false, "Gate check error: " + e.getMessage());
            }
        });
    }

    public static void configure(Context context, String renderBaseUrl, String githubFallbackUrl) {
        if (context == null) return;
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_RENDER_URL, normalizeUrl(renderBaseUrl))
                .putString(KEY_GITHUB_URL, normalizeUrl(githubFallbackUrl))
                .apply();
    }

    public static void saveLicenseKey(Context context, String licenseKey) {
        if (context == null) return;
        context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LICENSE, licenseKey == null ? "" : licenseKey.trim())
                .apply();
    }

    public static String getSavedLicenseKey(Context context) {
        if (context == null) return "";
        return context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LICENSE, "");
    }

    @SuppressLint("HardwareIds")
    public static String getDeviceId(Context context) {
        String androidId = "";
        try {
            androidId = Settings.Secure.getString(
                    context.getContentResolver(),
                    Settings.Secure.ANDROID_ID
            );
        } catch (Exception ignored) {}
        String rawSeed = (androidId == null ? "unknown" : androidId)
                + "|" + Build.BRAND
                + "|" + Build.MODEL
                + "|" + Build.MANUFACTURER
                + "|" + Build.HARDWARE;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawSeed.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < 8 && i < hash.length; i++) {
                hex.append(String.format(Locale.US, "%02X", hash[i]));
            }
            String s = hex.toString();
            return "PRNV-" + s.substring(0, 4) + "-" + s.substring(4, 8)
                    + "-" + s.substring(8, 12) + "-" + s.substring(12, 16);
        } catch (Exception e) {
            return "PRNV-" + Integer.toHexString(rawSeed.hashCode()).toUpperCase(Locale.US);
        }
    }

    public static void showLocalPreviewDialog(final Activity activity, final PreviewMode mode) {
        if (activity == null || activity.isFinishing()) return;
        MAIN_HANDLER.post(() -> {
            switch (mode) {
                case FORCE_UPDATE:
                    showUpdateDialog(
                            activity,
                            "New Update Available",
                            getAppVersionName(activity),
                            "2.0.0",
                            "• Mandatory security & stability upgrade\n• Faster Render + GitHub sync\n• Updated 1-Device License protection",
                            "https://github.com/releases",
                            false,
                            null
                    );
                    break;
                case MAINTENANCE_BLOCK:
                    showBlockingNoticeDialog(
                            activity,
                            "Admin Maintenance Active",
                            "This application is currently locked from the PRANAV Render Admin console. Check back soon or contact Admin.",
                            "https://t.me/",
                            false
                    );
                    break;
                case LICENSE_GATE:
                    showLicensePromptDialog(
                            activity,
                            "",
                            "https://t.me/",
                            "Enter your PRANAV 1-Device License Key. First login locks the key to this hardware ID.",
                            null,
                            true
                    );
                    break;
            }
        });
    }

    private static void verifySavedOrPromptLicense(
            final Activity activity,
            final String renderBaseUrl,
            final String supportUrl,
            final GateCallback callback
    ) {
        final Context appCtx = activity.getApplicationContext();
        final String savedKey = getSavedLicenseKey(appCtx);

        if (isBlank(savedKey)) {
            MAIN_HANDLER.post(() -> showLicensePromptDialog(
                    activity,
                    renderBaseUrl,
                    supportUrl,
                    "Enter your PRANAV single-device license key to unlock this device.",
                    callback,
                    false
            ));
            notifyCallback(callback, false, "License key required.");
            return;
        }

        if (isBlank(renderBaseUrl)) {
            MAIN_HANDLER.post(() -> showLicensePromptDialog(
                    activity,
                    "",
                    supportUrl,
                    "Render Admin URL missing. License verification requires an active Render server.",
                    callback,
                    false
            ));
            notifyCallback(callback, false, "Render URL required for license check.");
            return;
        }

        VerifyResult result = verifyKeyWithServer(appCtx, renderBaseUrl, savedKey);
        if (result.valid) {
            String expiryInfo = isBlank(result.expiresAt) ? "" : " (Expires " + result.expiresAt.substring(0, Math.min(10, result.expiresAt.length())) + ")";
            notifyCallback(callback, true, "One-device license verified: " + savedKey + expiryInfo);
        } else {
            MAIN_HANDLER.post(() -> showLicensePromptDialog(
                    activity,
                    renderBaseUrl,
                    supportUrl,
                    result.message,
                    callback,
                    false
            ));
            notifyCallback(callback, false, result.message);
        }
    }

    private static VerifyResult verifyKeyWithServer(Context context, String renderBaseUrl, String key) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("license_key", key.trim());
            payload.put("device_id", getDeviceId(context));
            payload.put("device_model", Build.MANUFACTURER + " " + Build.MODEL);
            payload.put("package_name", context.getPackageName());

            JSONObject resp = httpPostJson(renderBaseUrl + "/api/gate/verify", payload);
            if (resp == null) {
                return new VerifyResult(false, "Cannot reach Render Admin server to verify license.", "");
            }
            boolean valid = resp.optBoolean("valid", false);
            String msg = resp.optString("message", valid ? "Verified" : "License rejected");
            String exp = resp.optString("expires_at", "");
            return new VerifyResult(valid, msg, exp);
        } catch (Exception e) {
            return new VerifyResult(false, "Verification error: " + e.getMessage(), "");
        }
    }

    private static void showUpdateDialog(
            final Activity activity,
            final String title,
            final String currentVersionName,
            final String latestVersionName,
            final String message,
            final String downloadUrl,
            final boolean forceUpdate,
            final Runnable onDismissedOptional
    ) {
        if (isActivityDead(activity)) return;

        LinearLayout root = buildDialogContainer(activity);
        root.addView(buildBadge(
                activity,
                forceUpdate ? "PRANAV FORCE UPDATE" : "PRANAV UPDATE AVAILABLE",
                forceUpdate ? "#FFB300" : "#00E5FF",
                forceUpdate ? "#2E2000" : "#08242C"
        ));
        root.addView(buildTitleText(activity, title));

        // Version comparison pill box
        TextView versionChip = new TextView(activity);
        versionChip.setText("Installed v" + currentVersionName + "  →  Latest v" + latestVersionName);
        versionChip.setTextColor(Color.parseColor("#00E5FF"));
        versionChip.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        versionChip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        versionChip.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 12), dp(activity, 8));
        GradientDrawable verBg = new GradientDrawable();
        verBg.setColor(Color.parseColor("#070B14"));
        verBg.setCornerRadius(dp(activity, 10));
        verBg.setStroke(dp(activity, 1), Color.parseColor("#1E3A5F"));
        versionChip.setBackground(verBg);
        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        verLp.bottomMargin = dp(activity, 12);
        root.addView(versionChip, verLp);

        // Changelog card
        TextView changelog = new TextView(activity);
        changelog.setText("WHAT'S NEW\n" + message);
        changelog.setTextColor(Color.parseColor("#CBD5E1"));
        changelog.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        changelog.setLineSpacing(dp(activity, 4), 1f);
        changelog.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        GradientDrawable logBg = new GradientDrawable();
        logBg.setColor(Color.parseColor("#0B1220"));
        logBg.setCornerRadius(dp(activity, 12));
        logBg.setStroke(dp(activity, 1), Color.parseColor("#243247"));
        changelog.setBackground(logBg);
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        logLp.bottomMargin = dp(activity, 16);
        root.addView(changelog, logLp);

        LinearLayout btnRow = buildButtonRow(activity);

        if (!forceUpdate) {
            Button laterBtn = buildSecondaryButton(activity, "Later");
            btnRow.addView(laterBtn);
            addSpacer(activity, btnRow, 10);
            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(root)
                    .setCancelable(true)
                    .create();
            laterBtn.setOnClickListener(v -> {
                dialog.dismiss();
                if (onDismissedOptional != null) onDismissedOptional.run();
            });
            Button updateBtn = buildPrimaryButton(activity, "Update Now", "#00E5FF", "#04131A");
            updateBtn.setOnClickListener(v -> openUrlSafe(activity, downloadUrl));
            btnRow.addView(updateBtn);
            root.addView(btnRow);
            styleDialogWindow(dialog);
            dialog.show();
        } else {
            Button exitBtn = buildSecondaryButton(activity, "Exit App");
            exitBtn.setOnClickListener(v -> activity.finishAffinity());
            btnRow.addView(exitBtn);
            addSpacer(activity, btnRow, 10);

            Button updateBtn = buildPrimaryButton(activity, "Update Now", "#FFB300", "#1A1100");
            updateBtn.setOnClickListener(v -> openUrlSafe(activity, downloadUrl));
            btnRow.addView(updateBtn);
            root.addView(btnRow);

            AlertDialog dialog = new AlertDialog.Builder(activity)
                    .setView(root)
                    .setCancelable(false)
                    .create();
            styleDialogWindow(dialog);
            dialog.show();
        }
    }

    private static void showBlockingNoticeDialog(
            final Activity activity,
            final String title,
            final String message,
            final String supportUrl,
            final boolean lockExit
    ) {
        if (isActivityDead(activity)) return;

        LinearLayout root = buildDialogContainer(activity);
        root.addView(buildBadge(activity, "PRANAV ADMIN CONTROL", "#FF4D6D", "#2B0B14"));
        root.addView(buildTitleText(activity, title));
        root.addView(buildBodyText(activity, message));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setCancelable(!lockExit)
                .create();

        LinearLayout btnRow = buildButtonRow(activity);
        if (!isBlank(supportUrl)) {
            Button contactBtn = buildSecondaryButton(activity, "Contact Admin");
            contactBtn.setOnClickListener(v -> openUrlSafe(activity, supportUrl));
            btnRow.addView(contactBtn);
            addSpacer(activity, btnRow, 10);
        }

        Button actionBtn = buildPrimaryButton(
                activity,
                lockExit ? "Exit App" : "Close Preview",
                "#FF4D6D",
                "#FFFFFF"
        );
        actionBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (lockExit) {
                activity.finishAffinity();
            }
        });
        btnRow.addView(actionBtn);
        root.addView(btnRow);
        styleDialogWindow(dialog);
        dialog.show();
    }

    private static void showLicensePromptDialog(
            final Activity activity,
            final String renderBaseUrl,
            final String supportUrl,
            final String statusMessage,
            final GateCallback callback,
            final boolean previewOnly
    ) {
        if (isActivityDead(activity)) return;

        final String deviceId = getDeviceId(activity);
        LinearLayout root = buildDialogContainer(activity);
        root.addView(buildBadge(activity, "PRANAV 1-DEVICE LICENSE", "#00E5FF", "#08242C"));
        root.addView(buildTitleText(activity, "License Login"));

        final TextView statusView = buildBodyText(activity, statusMessage);
        root.addView(statusView);

        TextView deviceBox = new TextView(activity);
        deviceBox.setText("DEVICE ID: " + deviceId + "  (Tap to Copy)");
        deviceBox.setTextColor(Color.parseColor("#94A3B8"));
        deviceBox.setTypeface(Typeface.MONOSPACE);
        deviceBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        deviceBox.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(Color.parseColor("#0B1220"));
        boxBg.setCornerRadius(dp(activity, 10));
        boxBg.setStroke(dp(activity, 1), Color.parseColor("#283548"));
        deviceBox.setBackground(boxBg);
        deviceBox.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) {
                cm.setPrimaryClip(ClipData.newPlainText("PRANAV Device ID", deviceId));
                Toast.makeText(activity, "Device ID copied", Toast.LENGTH_SHORT).show();
            }
        });
        LinearLayout.LayoutParams devLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        devLp.bottomMargin = dp(activity, 12);
        root.addView(deviceBox, devLp);

        // Key input + Paste button row
        LinearLayout keyRow = new LinearLayout(activity);
        keyRow.setOrientation(LinearLayout.HORIZONTAL);
        keyRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams keyRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        keyRowLp.bottomMargin = dp(activity, 14);

        final EditText keyInput = new EditText(activity);
        keyInput.setHint("PRANAV-XXXX-XXXX-XXXX");
        keyInput.setText(getSavedLicenseKey(activity));
        keyInput.setTextColor(Color.WHITE);
        keyInput.setHintTextColor(Color.parseColor("#64748B"));
        keyInput.setTypeface(Typeface.MONOSPACE);
        keyInput.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        keyInput.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        GradientDrawable inputBg = new GradientDrawable();
        inputBg.setColor(Color.parseColor("#070B14"));
        inputBg.setCornerRadius(dp(activity, 10));
        inputBg.setStroke(dp(activity, 1), Color.parseColor("#00E5FF"));
        keyInput.setBackground(inputBg);
        keyRow.addView(keyInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        addSpacer(activity, keyRow, 8);
        Button pasteBtn = buildSecondaryButton(activity, "Paste");
        pasteBtn.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip() && cm.getPrimaryClip() != null && cm.getPrimaryClip().getItemCount() > 0) {
                CharSequence txt = cm.getPrimaryClip().getItemAt(0).coerceToText(activity);
                if (txt != null) {
                    keyInput.setText(txt.toString().trim().toUpperCase(Locale.US));
                }
            }
        });
        keyRow.addView(pasteBtn);
        root.addView(keyRow, keyRowLp);

        final ProgressBar spinner = new ProgressBar(activity);
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(
                dp(activity, 28),
                dp(activity, 28)
        );
        spinLp.gravity = Gravity.CENTER_HORIZONTAL;
        spinLp.bottomMargin = dp(activity, 10);
        root.addView(spinner, spinLp);

        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setView(root)
                .setCancelable(previewOnly)
                .create();

        LinearLayout btnRow = buildButtonRow(activity);
        if (previewOnly) {
            Button closeBtn = buildSecondaryButton(activity, "Close");
            closeBtn.setOnClickListener(v -> dialog.dismiss());
            btnRow.addView(closeBtn);
            addSpacer(activity, btnRow, 8);
        } else if (!isBlank(supportUrl)) {
            Button getKeyBtn = buildSecondaryButton(activity, "Get Key");
            getKeyBtn.setOnClickListener(v -> openUrlSafe(activity, supportUrl));
            btnRow.addView(getKeyBtn);
            addSpacer(activity, btnRow, 8);
        }

        final Button verifyBtn = buildPrimaryButton(activity, "Login & Bind Device", "#00E5FF", "#04131A");
        verifyBtn.setOnClickListener(v -> {
            final String enteredKey = keyInput.getText().toString().trim();
            if (isBlank(enteredKey)) {
                statusView.setText("Please enter a valid PRANAV license key.");
                statusView.setTextColor(Color.parseColor("#FF4D6D"));
                return;
            }
            if (previewOnly || isBlank(renderBaseUrl)) {
                Toast.makeText(
                        activity,
                        "Configure Render Admin URL to verify live keys.",
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }

            spinner.setVisibility(View.VISIBLE);
            verifyBtn.setEnabled(false);

            EXECUTOR.execute(() -> {
                VerifyResult result = verifyKeyWithServer(activity.getApplicationContext(), renderBaseUrl, enteredKey);
                MAIN_HANDLER.post(() -> {
                    spinner.setVisibility(View.GONE);
                    verifyBtn.setEnabled(true);
                    if (result.valid) {
                        saveLicenseKey(activity.getApplicationContext(), enteredKey);
                        Toast.makeText(activity, "License bound to this device!", Toast.LENGTH_SHORT).show();
                        if (dialog.isShowing()) dialog.dismiss();
                        notifyCallback(callback, true, "One-device license verified (" + enteredKey + ").");
                    } else {
                        statusView.setText(result.message);
                        statusView.setTextColor(Color.parseColor("#FF4D6D"));
                        notifyCallback(callback, false, result.message);
                    }
                });
            });
        });
        btnRow.addView(verifyBtn);
        root.addView(btnRow);

        styleDialogWindow(dialog);
        dialog.show();
    }

    private static JSONObject httpGetJson(String urlStr) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return new JSONObject(readStream(conn.getInputStream()));
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static JSONObject httpPostJson(String urlStr, JSONObject body) {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            conn.setRequestProperty("Accept", "application/json");

            byte[] out = body.toString().getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }

            int code = conn.getResponseCode();
            InputStream stream = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
            if (stream != null) {
                return new JSONObject(readStream(stream));
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return null;
    }

    private static String readStream(InputStream is) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    @SuppressWarnings("deprecation")
    private static long getAppVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (Exception e) {
            return 1L;
        }
    }

    private static String getAppVersionName(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName != null ? info.versionName : "1.0";
        } catch (Exception e) {
            return "1.0";
        }
    }

    private static void openUrlSafe(Activity activity, String url) {
        if (isBlank(url)) {
            Toast.makeText(activity, "Link not configured in Admin.", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(activity, "Invalid URL: " + url, Toast.LENGTH_SHORT).show();
        }
    }

    private static void notifyCallback(GateCallback callback, boolean allowed, String message) {
        if (callback != null) {
            MAIN_HANDLER.post(() -> callback.onGateStatus(allowed, message));
        }
    }

    private static boolean isActivityDead(Activity activity) {
        return activity == null || activity.isFinishing() || activity.isDestroyed();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static String normalizeUrl(String url) {
        if (url == null) return "";
        String trimmed = url.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static LinearLayout buildDialogContainer(Context ctx) {
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(ctx, 22), dp(ctx, 22), dp(ctx, 22), dp(ctx, 20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#0F1624"));
        bg.setCornerRadius(dp(ctx, 20));
        bg.setStroke(dp(ctx, 1), Color.parseColor("#243247"));
        root.setBackground(bg);
        return root;
    }

    private static TextView buildBadge(Context ctx, String text, String fgHex, String bgHex) {
        TextView badge = new TextView(ctx);
        badge.setText(text);
        badge.setTextColor(Color.parseColor(fgHex));
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(ctx, 10), dp(ctx, 4), dp(ctx, 10), dp(ctx, 4));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(dp(ctx, 50));
        badge.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(ctx, 12);
        badge.setLayoutParams(lp);
        return badge;
    }

    private static TextView buildTitleText(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#F8FAFC"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(ctx, 8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static TextView buildBodyText(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#CBD5E1"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tv.setLineSpacing(dp(ctx, 3), 1f);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(ctx, 14);
        tv.setLayoutParams(lp);
        return tv;
    }

    private static LinearLayout buildButtonRow(Context ctx) {
        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.END);
        return row;
    }

    private static Button buildPrimaryButton(Context ctx, String label, String bgHex, String fgHex) {
        Button btn = new Button(ctx);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.parseColor(fgHex));
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setMinimumHeight(dp(ctx, 48));
        btn.setPadding(dp(ctx, 18), dp(ctx, 10), dp(ctx, 18), dp(ctx, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(dp(ctx, 12));
        btn.setBackground(bg);
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return btn;
    }

    private static Button buildSecondaryButton(Context ctx, String label) {
        Button btn = new Button(ctx);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.parseColor("#CBD5E1"));
        btn.setMinimumHeight(dp(ctx, 48));
        btn.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#1E293B"));
        bg.setCornerRadius(dp(ctx, 12));
        btn.setBackground(bg);
        return btn;
    }

    private static void addSpacer(Context ctx, LinearLayout row, int widthDp) {
        View spacer = new View(ctx);
        row.addView(spacer, new LinearLayout.LayoutParams(dp(ctx, widthDp), 1));
    }

    private static void styleDialogWindow(AlertDialog dialog) {
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
    }

    private static int dp(Context ctx, int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                ctx.getResources().getDisplayMetrics()
        ));
    }

    private static final class VerifyResult {
        final boolean valid;
        final String message;
        final String expiresAt;

        VerifyResult(boolean valid, String message, String expiresAt) {
            this.valid = valid;
            this.message = message;
            this.expiresAt = expiresAt;
        }
    }
}
