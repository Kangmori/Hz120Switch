package com.dsh.hz120;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import rikka.shizuku.Shizuku;

/**
 * Invisible activity behind the home-screen shortcut: flips the switch and
 * finishes. If Shizuku is missing or not authorized it opens the main screen
 * instead of failing silently.
 */
public class ToggleActivity extends Activity {

    private static final String KEY = "peak_refresh_rate";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Shell.init(this);

        if (!Shizuku.pingBinder() || !Shell.hasPermission()) {
            openMain();
            return;
        }

        Shell.ensure(ready -> {
            if (!ready) {
                toast(getString(R.string.shizuku_not_running));
                finish();
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
                runOnUiThread(() -> {
                    toast(getString("1".equals(after) ? R.string.toast_on : R.string.toast_off));
                    finish();
                });
            }).start();
        });
    }

    private void openMain() {
        startActivity(new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

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
}
