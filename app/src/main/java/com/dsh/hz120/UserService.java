package com.dsh.hz120;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Runs in a process started by Shizuku, with the same UID as the Shizuku
 * server (uid 2000 / shell when Shizuku was started over ADB, uid 0 when it
 * was started with root).
 *
 * Shizuku instantiates this class and uses it as the service binder directly,
 * so it must itself be an IBinder -- hence extending the AIDL Stub instead of
 * android.app.Service.
 */
public class UserService extends IUserService.Stub {

    private static final long TIMEOUT_SECONDS = 20;

    @Override
    public String exec(String command) {
        return run(command);
    }

    @Override
    public int getUid() {
        return android.os.Process.myUid();
    }

    private static String run(String command) {
        StringBuilder out = new StringBuilder();
        java.lang.Process process = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("/system/bin/sh", "-c", command);
            builder.redirectErrorStream(true);
            process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }

            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                out.append("[timeout]\n");
            }
        } catch (Throwable t) {
            out.append("[exec error] ").append(t).append('\n');
            if (process != null) {
                process.destroyForcibly();
            }
        }
        return out.toString();
    }
}
