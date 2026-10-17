package com.dsh.hz120;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

/**
 * Material-You-ish single screen: big state, one card, one pill button.
 */
public class MainActivity extends Activity {

    private static final int REQ_PERMISSION = 1001;
    private static final String KEY = "peak_refresh_rate";
    private static final Pattern ACTIVE_MODE = Pattern.compile("mActiveModeId=(\\d+)");

    private TextView tvStatus;
    private TextView tvSub;
    private TextView tvPeak;
    private TextView tvMode;
    private TextView tvUid;
    private TextView tvShell;
    private Button btnToggle;
    private Button btnShortcut;

    private int accent;
    private int onAccent;
    private int cardBg;
    private int outline;
    private int textPrimary;
    private int textSecondary;
    private boolean busy;

    private final Shizuku.OnRequestPermissionResultListener permissionListener =
            (requestCode, grantResult) -> runOnUiThread(() -> {
                if (grantResult == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    toast(getString(R.string.permission_granted));
                    refreshUi();
                } else {
                    toast(getString(R.string.permission_denied));
                }
            });

    /** The binder often arrives after onResume, so re-read when it shows up. */
    private final Shizuku.OnBinderReceivedListener binderListener =
            () -> runOnUiThread(this::refreshUi);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shell.init(this);
        resolveColors();
        setContentView(buildUi());
        Shizuku.addRequestPermissionResultListener(permissionListener);
        Shizuku.addBinderReceivedListenerSticky(binderListener);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUi();
    }

    @Override
    protected void onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Shizuku.removeBinderReceivedListener(binderListener);
        super.onDestroy();
    }

    // ---------------------------------------------------------------- UI

    private void resolveColors() {
        accent = resolveColor(android.R.attr.colorAccent, 0xFF6750A4);
        onAccent = contrastOn(accent);
        cardBg = resolveColor(android.R.attr.colorBackgroundFloating, 0xFF1C1B1F);
        outline = resolveColor(android.R.attr.colorControlNormal, 0xFF8E8E93);
        textPrimary = resolveColor(android.R.attr.textColorPrimary, 0xFFFFFFFF);
        textSecondary = resolveColor(android.R.attr.textColorSecondary, 0xFFAAAAAA);
    }

    private static int contrastOn(int color) {
        double luminance = (0.299 * ((color >> 16) & 0xFF)
                + 0.587 * ((color >> 8) & 0xFF)
                + 0.114 * (color & 0xFF)) / 255.0;
        return luminance > 0.6 ? 0xFF1B1B1F : 0xFFFFFFFF;
    }

    private int resolveColor(int attr, int fallback) {
        TypedValue value = new TypedValue();
        if (getTheme().resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0) {
                return getColor(value.resourceId);
            }
            return value.data;
        }
        return fallback;
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(24), dp(28), dp(24), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // Android 15+ is edge-to-edge; keep content below the status bar.
        scroll.setFitsSystemWindows(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            scroll.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(0, bars.top, 0, bars.bottom);
                return insets;
            });
        }

        TextView label = new TextView(this);
        label.setText(R.string.screen_label);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setLetterSpacing(0.16f);
        label.setTextColor(textSecondary);
        root.addView(label, matchWrap());

        tvStatus = new TextView(this);
        tvStatus.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        tvStatus.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        tvStatus.setLetterSpacing(-0.02f);
        tvStatus.setTextColor(textPrimary);
        tvStatus.setPadding(0, dp(6), 0, 0);
        root.addView(tvStatus, matchWrap());

        tvSub = new TextView(this);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvSub.setTextColor(textSecondary);
        tvSub.setPadding(0, dp(6), 0, 0);
        root.addView(tvSub, matchWrap());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(20), dp(18), dp(20), dp(18));
        card.setBackground(cardDrawable());
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(24);
        root.addView(card, cardParams);

        TextView cardTitle = new TextView(this);
        cardTitle.setText(R.string.card_title);
        cardTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        cardTitle.setLetterSpacing(0.12f);
        cardTitle.setTextColor(textSecondary);
        card.addView(cardTitle, matchWrap());

        tvPeak = addRow(card, getString(R.string.row_peak), dp(14));
        tvMode = addRow(card, getString(R.string.row_mode), dp(10));
        tvUid = addRow(card, getString(R.string.row_shell), dp(10));

        btnToggle = new Button(this);
        btnToggle.setAllCaps(false);
        btnToggle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btnToggle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        btnToggle.setTextColor(onAccent);
        btnToggle.setBackground(pillDrawable(accent, 0));
        btnToggle.setOnClickListener(v -> toggle());
        LinearLayout.LayoutParams toggleParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72));
        toggleParams.topMargin = dp(24);
        root.addView(btnToggle, toggleParams);

        btnShortcut = new Button(this);
        btnShortcut.setText(R.string.add_shortcut);
        btnShortcut.setAllCaps(false);
        btnShortcut.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        btnShortcut.setTextColor(textPrimary);
        btnShortcut.setBackground(pillDrawable(0x00000000, outline));
        btnShortcut.setOnClickListener(v -> pinShortcut());
        LinearLayout.LayoutParams shortcutParams =
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        shortcutParams.topMargin = dp(12);
        root.addView(btnShortcut, shortcutParams);

        tvShell = new TextView(this);
        tvShell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvShell.setTextColor(textSecondary);
        tvShell.setPadding(0, dp(20), 0, 0);
        tvShell.setText(R.string.tile_hint);
        root.addView(tvShell, matchWrap());

        return scroll;
    }

    private TextView addRow(LinearLayout parent, String label, int topMarginDp) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(topMarginDp);
        parent.addView(row, params);

        TextView name = new TextView(this);
        name.setText(label);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setTextColor(textSecondary);
        row.addView(name, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView value = new TextView(this);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        value.setTypeface(Typeface.MONOSPACE);
        value.setTextColor(textPrimary);
        value.setGravity(Gravity.END);
        row.addView(value, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return value;
    }

    private GradientDrawable cardDrawable() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(cardBg);
        drawable.setCornerRadius(dp(28));
        drawable.setStroke(dp(1), (outline & 0x00FFFFFF) | 0x22000000);
        return drawable;
    }

    private GradientDrawable pillDrawable(int fill, int strokeColor) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(36));
        if (strokeColor != 0) {
            drawable.setStroke(dp(1), strokeColor);
        }
        return drawable;
    }

    // ------------------------------------------------------------ actions

    private void toggle() {
        if (busy) {
            return;
        }
        if (!Shell.hasPermission()) {
            requestPermission();
            return;
        }
        busy = true;
        btnToggle.setEnabled(false);
        Shell.ensure(ready -> {
            if (!ready) {
                busy = false;
                runOnUiThread(this::refreshUi);
                return;
            }
            new Thread(() -> {
                String peak = Shell.exec("settings get system " + KEY);
                boolean on = "1".equals(peak);
                if (on) {
                    Shell.exec("settings delete system " + KEY);
                } else {
                    Shell.exec("settings put system " + KEY + " 1");
                }
                sleep(500);
                String after = Shell.exec("settings get system " + KEY);
                String mode = readMode();
                String uid = String.valueOf(Shell.uid());
                busy = false;
                runOnUiThread(() -> {
                    applyState(after, mode, uid);
                    toast("1".equals(after)
                            ? getString(R.string.toast_on)
                            : getString(R.string.toast_off));
                });
            }).start();
        });
    }

    private void refreshUi() {
        if (!Shizuku.pingBinder()) {
            applyOffline(getString(R.string.shizuku_not_running),
                    getString(R.string.shizuku_not_running_sub));
            return;
        }
        if (!Shell.hasPermission()) {
            applyOffline(getString(R.string.not_authorized),
                    getString(R.string.not_authorized_sub));
            return;
        }
        Shell.ensure(ready -> {
            if (!ready) {
                runOnUiThread(() -> applyOffline(getString(R.string.shizuku_not_running),
                        getString(R.string.shizuku_not_running_sub)));
                return;
            }
            new Thread(() -> {
                String peak = Shell.exec("settings get system " + KEY);
                String mode = readMode();
                String uid = String.valueOf(Shell.uid());
                runOnUiThread(() -> applyState(peak, mode, uid));
            }).start();
        });
    }

    private String readMode() {
        // Grep on the device: sending the whole dumpsys over Binder can exceed
        // the transaction limit and kill the channel.
        String line = Shell.exec("dumpsys display | grep -m1 mActiveModeId");
        if (line == null) {
            return "-";
        }
        Matcher matcher = ACTIVE_MODE.matcher(line);
        return matcher.find() ? matcher.group(1) : "-";
    }

    private void applyState(String peak, String mode, String uid) {
        boolean on = "1".equals(peak);
        tvStatus.setText(on ? R.string.state_on : R.string.state_off);
        tvStatus.setTextColor(on ? accent : textPrimary);
        tvSub.setText(on ? R.string.state_on_sub : R.string.state_off_sub);

        tvPeak.setText(peak == null || "null".equals(peak) ? "—" : peak);
        tvMode.setText(mode == null ? "—" : mode);
        tvUid.setText("-1".equals(uid) ? "—" : uid);

        btnToggle.setText(on ? R.string.action_off : R.string.action_on);
        btnToggle.setBackground(pillDrawable(accent, 0));
        btnToggle.setEnabled(true);
        busy = false;
    }

    private void applyOffline(String status, String sub) {
        tvStatus.setText(status);
        tvStatus.setTextColor(textPrimary);
        tvSub.setText(sub);
        tvPeak.setText("—");
        tvMode.setText("—");
        tvUid.setText("—");
        btnToggle.setText(R.string.action_authorize);
        btnToggle.setEnabled(true);
        busy = false;
    }

    private void requestPermission() {
        if (!Shizuku.pingBinder()) {
            toast(getString(R.string.shizuku_not_running));
            return;
        }
        try {
            if (Shell.hasPermission()) {
                refreshUi();
                return;
            }
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                toast(getString(R.string.authorize_in_shizuku));
                return;
            }
            Shizuku.requestPermission(REQ_PERMISSION);
        } catch (Throwable t) {
            toast(getString(R.string.permission_failed, t.toString()));
        }
    }

    private void pinShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            toast(getString(R.string.pin_unsupported));
            return;
        }
        ShortcutManager manager = getSystemService(ShortcutManager.class);
        if (manager == null || !manager.isRequestPinShortcutSupported()) {
            toast(getString(R.string.pin_unsupported));
            return;
        }
        ShortcutInfo info = new ShortcutInfo.Builder(this, "toggle")
                .setShortLabel(getString(R.string.shortcut_short))
                .setLongLabel(getString(R.string.shortcut_long))
                .setIcon(Icon.createWithResource(this, R.drawable.ic_hz))
                .setIntent(new Intent(this, ToggleActivity.class).setAction(Intent.ACTION_VIEW))
                .build();
        boolean requested = manager.requestPinShortcut(info, null);
        toast(requested ? getString(R.string.pin_requested) : getString(R.string.pin_failed));
    }

    // -------------------------------------------------------------- utils

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }
}
