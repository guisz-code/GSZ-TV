package com.gsztv.launcher;

import android.app.ActivityManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Conservative, user-controlled cleanup for ordinary apps only. */
final class PerformanceManager {
    private static final String PREFS = "performance";
    private static final String TIMEOUT = "background_timeout_minutes";
    private static final long NEVER = Long.MAX_VALUE;
    private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
            "com.gsztv.launcher", "com.nuvio.tv", "com.hisilicon.tv.service", "com.hisilicon.tvsetting",
            "com.hisilicon.tvui", "com.hisilicon.rmservice", "com.softwinner.tcorrection",
            "com.htc.htcsettings", "com.jm.jmlauncher", "com.htc.htclauncherhighenglishd08",
            "com.tcl.movieark.launcher", "com.android.systemui", "com.android.settings",
            "com.android.tv.settings", "com.android.providers.tv", "com.google.android.gms"));

    private PerformanceManager() {}

    static int timeout(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(TIMEOUT, 0);
    }

    static void setTimeout(Context c, int minutes) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(TIMEOUT, minutes).apply();
    }

    static void schedule(Context c) {
        Handler h = new Handler(Looper.getMainLooper());
        Runnable r = new Runnable() {
            @Override public void run() {
                cleanupExpired(c);
                h.postDelayed(this, 60_000L);
            }
        };
        h.post(r);
    }

    static int cleanup(Context c) {
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return 0;
        int count = 0;
        for (ActivityManager.RunningAppProcessInfo p : am.getRunningAppProcesses()) {
            if (p == null || p.pkgList == null || p.importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_BACKGROUND) continue;
            for (String pkg : p.pkgList) {
                if (PROTECTED.contains(pkg) || pkg.startsWith("android") || pkg.startsWith("com.android.")) continue;
                try {
                    am.killBackgroundProcesses(pkg);
                    count++;
                } catch (SecurityException ignored) {}
            }
        }
        return count;
    }

    static void cleanupExpired(Context c) {
        int minutes = timeout(c);
        if (minutes <= 0) return;
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        if (usm == null || am == null) return;
        long now = System.currentTimeMillis();
        try {
            Map<String, UsageStats> stats = usm.queryAndAggregateUsageStats(now - 24L * 60 * 60 * 1000, now);
            for (UsageStats s : stats.values()) {
                String pkg = s.getPackageName();
                if (pkg == null || PROTECTED.contains(pkg) || pkg.startsWith("android") || pkg.startsWith("com.android.")) continue;
                if (now - s.getLastTimeUsed() >= minutes * 60_000L) am.killBackgroundProcesses(pkg);
            }
        } catch (SecurityException ignored) {}
    }
}
