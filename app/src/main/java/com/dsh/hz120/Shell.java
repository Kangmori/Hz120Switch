package com.dsh.hz120;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;

import java.util.ArrayList;
import java.util.List;

import rikka.shizuku.Shizuku;

/**
 * Process-wide Shizuku shell channel.
 *
 * All components (activity, tile, shortcut activity) share one bound user
 * service, so the tile and the shortcut do not each pay for a new connection.
 */
public final class Shell {

    public interface Callback {
        void onReady(boolean ready);
    }

    private static final Object LOCK = new Object();

    private static IUserService service;
    private static boolean binding;
    private static boolean initialized;
    private static String packageName;
    private static final List<Callback> WAITERS = new ArrayList<>();

    private Shell() {
    }

    public static void init(Context context) {
        synchronized (LOCK) {
            if (initialized) {
                return;
            }
            initialized = true;
            packageName = context.getPackageName();
        }
        Shizuku.addBinderReceivedListenerSticky(Shell::onBinderReceived);
        Shizuku.addBinderDeadListener(Shell::onBinderDead);
    }

    private static void onBinderReceived() {
        if (hasPermission()) {
            bind();
        } else {
            flush(false);
        }
    }

    private static void onBinderDead() {
        synchronized (LOCK) {
            service = null;
            binding = false;
        }
        flush(false);
    }

    public static boolean hasPermission() {
        try {
            return !Shizuku.isPreV11()
                    && Shizuku.checkSelfPermission() == android.content.pm.PackageManager.PERMISSION_GRANTED;
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean ready() {
        synchronized (LOCK) {
            return service != null;
        }
    }

    public static void ensure(Callback callback) {
        synchronized (LOCK) {
            if (service != null) {
                callback.onReady(true);
                return;
            }
            WAITERS.add(callback);
        }
        if (!Shizuku.pingBinder()) {
            flush(false);
            return;
        }
        if (hasPermission()) {
            bind();
        } else {
            flush(false);
        }
    }

    private static void bind() {
        synchronized (LOCK) {
            if (binding || service != null) {
                return;
            }
            binding = true;
        }
        try {
            Shizuku.bindUserService(userServiceArgs(), CONNECTION);
        } catch (Throwable t) {
            synchronized (LOCK) {
                binding = false;
            }
            flush(false);
        }
    }

    private static Shizuku.UserServiceArgs userServiceArgs() {
        return new Shizuku.UserServiceArgs(
                new ComponentName(packageName, UserService.class.getName()))
                .daemon(false)
                .processNameSuffix("shell")
                .debuggable(false)
                .version(1);
    }

    /** Blocking; call from a worker thread. Returns null when the channel is down. */
    public static String exec(String command) {
        IUserService current;
        synchronized (LOCK) {
            current = service;
        }
        if (current == null) {
            return null;
        }
        try {
            return current.exec(command).trim();
        } catch (Throwable t) {
            return null;
        }
    }

    public static int uid() {
        IUserService current;
        synchronized (LOCK) {
            current = service;
        }
        if (current == null) {
            return -1;
        }
        try {
            return current.getUid();
        } catch (Throwable t) {
            return -1;
        }
    }

    private static void flush(boolean ready) {
        List<Callback> copy;
        synchronized (LOCK) {
            copy = new ArrayList<>(WAITERS);
            WAITERS.clear();
        }
        for (Callback callback : copy) {
            callback.onReady(ready);
        }
    }

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (LOCK) {
                service = IUserService.Stub.asInterface(binder);
                binding = false;
            }
            flush(service != null);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (LOCK) {
                service = null;
                binding = false;
            }
            flush(false);
        }
    };
}
