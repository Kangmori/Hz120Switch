package com.dsh.hz120;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import rikka.shizuku.Shizuku;

/**
 * Quick Settings tile: tap to toggle, state reflects peak_refresh_rate.
 */
public class HzTileService extends TileService {

    private static final String KEY = "peak_refresh_rate";

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public void onStartListening() {
        super.onStartListening();
        Shell.init(this);
        refreshTile();
    }

    @Override
    public void onClick() {
        super.onClick();
        Shell.init(this);

        if (!Shizuku.pingBinder() || !Shell.hasPermission()) {
            openApp();
            return;
        }

        Shell.ensure(ready -> {
            if (!ready) {
                openApp();
                return;
            }
            new Thread(() -> {
                String peak = Shell.exec("settings get system " + KEY);
                if ("1".equals(peak)) {
                    Shell.exec("settings delete system " + KEY);
                } else {
                    Shell.exec("settings put system " + KEY + " 1");
                }
                sleep(400);
                handler.post(this::queryOnce);
            }).start();
        });
    }

    private void refreshTile() {
        Tile tile = getQsTile();
        if (tile == null) {
            return;
        }
        tile.setIcon(Icon.createWithResource(this, R.drawable.ic_hz_tile));
        tile.setLabel(getString(R.string.tile_label));

        if (!Shell.ready()) {
            tile.setState(Tile.STATE_INACTIVE);
            tile.updateTile();
            if (Shizuku.pingBinder() && Shell.hasPermission()) {
                Shell.ensure(ready -> {
                    if (ready) {
                        handler.post(this::queryOnce);
                    }
                });
            }
            return;
        }
        queryOnce();
    }

    private void queryOnce() {
        new Thread(() -> {
            String peak = Shell.exec("settings get system " + KEY);
            boolean on = "1".equals(peak);
            handler.post(() -> {
                Tile tile = getQsTile();
                if (tile == null) {
                    return;
                }
                tile.setState(on ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
                tile.setLabel(getString(on ? R.string.tile_on : R.string.tile_off));
                tile.updateTile();
            });
        }).start();
    }

    private void openApp() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (Build.VERSION.SDK_INT >= 34) {
            PendingIntent pending = PendingIntent.getActivity(this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            startActivityAndCollapse(pending);
        } else {
            startActivityAndCollapse(intent);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
