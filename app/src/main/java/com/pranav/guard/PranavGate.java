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
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
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
 * PRANAV 3D Unified Gate Engine (Single-DEX Standalone).
 * Combines Admin Remote Control (Kill-Switch / Maintenance), 3D Force App Update Pop-Up,
 * and One-Device Hardware License Key Login in one dependency-free class.
 *
 * Hook in Activity.onCreate():
 *   PranavGate.check(this);
 *   // Bytecode: invoke-static {p0}, Lcom/pranav/guard/PranavGate;->check(Landroid/app/Activity;)V
 */
public final class PranavGate {

    // =========================================================================
    // SET YOUR LIVE RENDER ADMIN URL HERE BEFORE COMPILING STANDALONE DEX
    // =========================================================================
    public static volatile String DEFAULT_RENDER_URL = "";
    public static volatile String DEFAULT_GITHUB_FALLBACK_URL = "";

    private static final String PREFS_NAME = "pranav_gate_prefs";
    private static final String KEY_RENDER_URL = "render_base_url";
    private static final String KEY_GITHUB_URL = "github_fallback_url";
    private static final String KEY_LICENSE = "saved_license_key";

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
                        "• Mandatory performance & security upgrade\n• Latest GitHub release package"
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
                            "• 3D Glassmorphic PRANAV Pop-up Engine\n• Faster Render + GitHub sync\n• Updated 1-Device License protection",
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

        final String accentHex = forceUpdate ? "#FFB300" : "#00E5FF";
        final String lipHex = forceUpdate ? "#996500" : "#008EA3";
        final LinearLayout card = build3DDialogCard(activity, accentHex);

        card.addView(build3DBadge(
                activity,
                forceUpdate ? "★ PRANAV 3D • FORCE UPDATE" : "★ PRANAV 3D • UPDATE READY",
                accentHex,
                forceUpdate ? "#2E2000" : "#08242C"
        ));
        card.addView(buildTitleText(activity, title));

        TextView versionChip = build3DRecessedBox(
                activity,
                "INSTALLED v" + currentVersionName + "   ▸   LATEST v" + latestVersionName,
                "#00E5FF",
                true
        );
        LinearLayout.LayoutParams verLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        verLp.bottomMargin = dp(activity, 12);
        card.addView(versionChip, verLp);

        TextView changelog = build3DRecessedBox(
                activity,
                "WHAT'S NEW\n" + message,
                "#CBD5E1",
                false
        );
        LinearLayout.LayoutParams logLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        logLp.bottomMargin = dp(activity, 16);
        card.addView(changelog, logLp);

        LinearLayout btnRow = buildButtonRow(activity);

        if (!forceUpdate) {
            Button laterBtn = build3DSecondaryButton(activity, "Later");
            btnRow.addView(laterBtn);
            addSpacer(activity, btnRow, 10);
            AlertDialog dialog = create3DDialog(activity, card, true);
            laterBtn.setOnClickListener(v -> {
                dialog.dismiss();
                if (onDismissedOptional != null) onDismissedOptional.run();
            });
            Button updateBtn = build3DPrimaryButton(activity, "Update Now", "#00E5FF", "#008EA3", "#04131A");
            updateBtn.setOnClickListener(v -> openUrlSafe(activity, downloadUrl));
            btnRow.addView(updateBtn);
            card.addView(btnRow);
            show3DDialogAnimated(dialog, card);
        } else {
            Button exitBtn = build3DSecondaryButton(activity, "Exit App");
            exitBtn.setOnClickListener(v -> activity.finishAffinity());
            btnRow.addView(exitBtn);
            addSpacer(activity, btnRow, 10);

            Button updateBtn = build3DPrimaryButton(activity, "Update Now", "#FFB300", lipHex, "#1A1100");
            updateBtn.setOnClickListener(v -> openUrlSafe(activity, downloadUrl));
            btnRow.addView(updateBtn);
            card.addView(btnRow);

            AlertDialog dialog = create3DDialog(activity, card, false);
            show3DDialogAnimated(dialog, card);
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

        LinearLayout card = build3DDialogCard(activity, "#FF4D6D");
        card.addView(build3DBadge(activity, "★ PRANAV 3D • ADMIN LOCK", "#FF4D6D", "#2B0B14"));
        card.addView(buildTitleText(activity, title));
        card.addView(buildBodyText(activity, message));

        AlertDialog dialog = create3DDialog(activity, card, !lockExit);

        LinearLayout btnRow = buildButtonRow(activity);
        if (!isBlank(supportUrl)) {
            Button contactBtn = build3DSecondaryButton(activity, "Contact Admin");
            contactBtn.setOnClickListener(v -> openUrlSafe(activity, supportUrl));
            btnRow.addView(contactBtn);
            addSpacer(activity, btnRow, 10);
        }

        Button actionBtn = build3DPrimaryButton(
                activity,
                lockExit ? "Exit App" : "Close Preview",
                "#FF4D6D",
                "#9E1B34",
                "#FFFFFF"
        );
        actionBtn.setOnClickListener(v -> {
            dialog.dismiss();
            if (lockExit) {
                activity.finishAffinity();
            }
        });
        btnRow.addView(actionBtn);
        card.addView(btnRow);
        show3DDialogAnimated(dialog, card);
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
        LinearLayout card = build3DDialogCard(activity, "#00E5FF");
        card.addView(build3DBadge(activity, "★ PRANAV 3D • 1-DEVICE KEY", "#00E5FF", "#08242C"));
        card.addView(buildTitleText(activity, "License Login"));

        final TextView statusView = buildBodyText(activity, statusMessage);
        card.addView(statusView);

        TextView deviceBox = build3DRecessedBox(
                activity,
                "DEVICE ID: " + deviceId + "  (Tap to Copy)",
                "#38BDF8",
                true
        );
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
        card.addView(deviceBox, devLp);

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
        keyInput.setBackground(build3DInputBg(activity));
        keyRow.addView(keyInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        addSpacer(activity, keyRow, 8);
        Button pasteBtn = build3DSecondaryButton(activity, "Paste");
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
        card.addView(keyRow, keyRowLp);

        final ProgressBar spinner = new ProgressBar(activity);
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams spinLp = new LinearLayout.LayoutParams(
                dp(activity, 28),
                dp(activity, 28)
        );
        spinLp.gravity = Gravity.CENTER_HORIZONTAL;
        spinLp.bottomMargin = dp(activity, 10);
        card.addView(spinner, spinLp);

        final AlertDialog dialog = create3DDialog(activity, card, previewOnly);

        LinearLayout btnRow = buildButtonRow(activity);
        if (previewOnly) {
            Button closeBtn = build3DSecondaryButton(activity, "Close");
            closeBtn.setOnClickListener(v -> dialog.dismiss());
            btnRow.addView(closeBtn);
            addSpacer(activity, btnRow, 8);
        } else if (!isBlank(supportUrl)) {
            Button getKeyBtn = build3DSecondaryButton(activity, "Get Key");
            getKeyBtn.setOnClickListener(v -> openUrlSafe(activity, supportUrl));
            btnRow.addView(getKeyBtn);
            addSpacer(activity, btnRow, 8);
        }

        final Button verifyBtn = build3DPrimaryButton(activity, "Login & Bind Device", "#00E5FF", "#008EA3", "#04131A");
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
        card.addView(btnRow);

        show3DDialogAnimated(dialog, card);
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

    // =========================================================================
    // 3D PROGRAMMATIC UI ENGINE (Beveled Depth, Specular Rim, Press Physics)
    // =========================================================================

    private static LinearLayout build3DDialogCard(Context ctx, String glowHex) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(ctx, 22), dp(ctx, 20), dp(ctx, 22), dp(ctx, 22));
        card.setElevation(dp(ctx, 24));

        // Base 3D dark extrusion layer
        GradientDrawable baseLip = new GradientDrawable();
        baseLip.setColor(Color.parseColor("#04070E"));
        baseLip.setCornerRadius(dp(ctx, 24));
        baseLip.setStroke(dp(ctx, 2), Color.parseColor(glowHex));

        // Top 3D metallic gradient face
        GradientDrawable topFace = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{
                        Color.parseColor("#18243E"),
                        Color.parseColor("#0E1628"),
                        Color.parseColor("#090E1B")
                }
        );
        topFace.setCornerRadius(dp(ctx, 22));
        topFace.setStroke(dp(ctx, 1), Color.parseColor("#33FFFFFF"));

        LayerDrawable layered = new LayerDrawable(new android.graphics.drawable.Drawable[]{baseLip, topFace});
        layered.setLayerInset(1, dp(ctx, 1), dp(ctx, 1), dp(ctx, 1), dp(ctx, 6));
        card.setBackground(layered);

        // Top specular glass highlight strip
        View sheen = new View(ctx);
        GradientDrawable sheenBg = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.TRANSPARENT, Color.parseColor(glowHex), Color.TRANSPARENT}
        );
        sheen.setBackground(sheenBg);
        LinearLayout.LayoutParams sheenLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(ctx, 3)
        );
        sheenLp.bottomMargin = dp(ctx, 14);
        card.addView(sheen, sheenLp);

        return card;
    }

    private static AlertDialog create3DDialog(Context ctx, View cardView, boolean cancelable) {
        FrameLayout wrapper = new FrameLayout(ctx);
        wrapper.setPadding(dp(ctx, 12), dp(ctx, 12), dp(ctx, 12), dp(ctx, 18));
        wrapper.setClipToPadding(false);
        wrapper.addView(cardView);

        return new AlertDialog.Builder(ctx)
                .setView(wrapper)
                .setCancelable(cancelable)
                .create();
    }

    private static void show3DDialogAnimated(AlertDialog dialog, View card) {
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.76f);
        }
        card.setScaleX(0.84f);
        card.setScaleY(0.84f);
        card.setRotationX(14f);
        card.setAlpha(0f);
        dialog.show();
        card.animate()
                .scaleX(1f)
                .scaleY(1f)
                .rotationX(0f)
                .alpha(1f)
                .setDuration(320)
                .setInterpolator(new OvershootInterpolator(1.1f))
                .start();
    }

    private static TextView build3DBadge(Context ctx, String text, String fgHex, String bgHex) {
        TextView badge = new TextView(ctx);
        badge.setText(text);
        badge.setTextColor(Color.parseColor(fgHex));
        badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        badge.setTypeface(Typeface.DEFAULT_BOLD);
        badge.setPadding(dp(ctx, 12), dp(ctx, 6), dp(ctx, 12), dp(ctx, 6));
        badge.setElevation(dp(ctx, 6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor(bgHex));
        bg.setCornerRadius(dp(ctx, 50));
        bg.setStroke(dp(ctx, 1), Color.parseColor(fgHex));
        badge.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        lp.bottomMargin = dp(ctx, 12);
        badge.setLayoutParams(lp);
        return badge;
    }

    private static TextView build3DRecessedBox(Context ctx, String text, String textHex, boolean mono) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor(textHex));
        tv.setTypeface(mono ? Typeface.MONOSPACE : Typeface.DEFAULT, mono ? Typeface.BOLD : Typeface.NORMAL);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, mono ? 12f : 13.5f);
        tv.setLineSpacing(dp(ctx, 4), 1f);
        tv.setPadding(dp(ctx, 13), dp(ctx, 11), dp(ctx, 13), dp(ctx, 11));

        GradientDrawable outerRim = new GradientDrawable();
        outerRim.setColor(Color.parseColor("#04070E"));
        outerRim.setCornerRadius(dp(ctx, 12));
        outerRim.setStroke(dp(ctx, 1), Color.parseColor("#2A3B58"));

        GradientDrawable innerWell = new GradientDrawable();
        innerWell.setColor(Color.parseColor("#080D18"));
        innerWell.setCornerRadius(dp(ctx, 11));

        LayerDrawable well = new LayerDrawable(new android.graphics.drawable.Drawable[]{outerRim, innerWell});
        well.setLayerInset(1, dp(ctx, 1), dp(ctx, 3), dp(ctx, 1), dp(ctx, 1));
        tv.setBackground(well);
        return tv;
    }

    private static GradientDrawable build3DInputBg(Context ctx) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.parseColor("#050912"));
        bg.setCornerRadius(dp(ctx, 12));
        bg.setStroke(dp(ctx, 1.5f), Color.parseColor("#00E5FF"));
        return bg;
    }

    private static TextView buildTitleText(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(Color.parseColor("#F8FAFC"));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 21);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setShadowLayer(dp(ctx, 4), 0, dp(ctx, 2), Color.parseColor("#80000000"));
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

    private static Button build3DPrimaryButton(Context ctx, String label, String topHex, String lipHex, String fgHex) {
        Button btn = createExtrudedButton(ctx, label, topHex, lipHex, fgHex);
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return btn;
    }

    private static Button build3DSecondaryButton(Context ctx, String label) {
        return createExtrudedButton(ctx, label, "#24324A", "#0F172A", "#E2E8F0");
    }

    @SuppressLint("ClickableViewAccessibility")
    private static Button createExtrudedButton(Context ctx, String label, String topHex, String lipHex, String fgHex) {
        Button btn = new Button(ctx);
        btn.setText(label);
        btn.setAllCaps(false);
        btn.setTextColor(Color.parseColor(fgHex));
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setMinimumHeight(dp(ctx, 50));
        btn.setPadding(dp(ctx, 16), dp(ctx, 10), dp(ctx, 16), dp(ctx, 14));
        btn.setElevation(dp(ctx, 8));

        GradientDrawable lip = new GradientDrawable();
        lip.setColor(Color.parseColor(lipHex));
        lip.setCornerRadius(dp(ctx, 13));

        GradientDrawable face = new GradientDrawable();
        face.setColor(Color.parseColor(topHex));
        face.setCornerRadius(dp(ctx, 13));
        face.setStroke(dp(ctx, 1), Color.parseColor("#44FFFFFF"));

        LayerDrawable layered = new LayerDrawable(new android.graphics.drawable.Drawable[]{lip, face});
        layered.setLayerInset(1, 0, 0, 0, dp(ctx, 5));
        btn.setBackground(layered);

        btn.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN) {
                v.animate().translationY(dp(ctx, 3)).scaleX(0.98f).scaleY(0.98f)
                        .setDuration(70).setInterpolator(new DecelerateInterpolator()).start();
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.animate().translationY(0f).scaleX(1f).scaleY(1f)
                        .setDuration(110).setInterpolator(new DecelerateInterpolator()).start();
            }
            return false;
        });
        return btn;
    }

    private static void addSpacer(Context ctx, LinearLayout row, int widthDp) {
        View spacer = new View(ctx);
        row.addView(spacer, new LinearLayout.LayoutParams(dp(ctx, widthDp), 1));
    }

    private static int dp(Context ctx, float value) {
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
