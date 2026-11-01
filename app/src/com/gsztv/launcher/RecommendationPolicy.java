package com.gsztv.launcher;

/** Timing shared by catalogue sources. Rotation uses cached data; it never triggers a network request. */
final class RecommendationPolicy {
    static final long HOUR = 3_600_000L;
    static final long CATALOG_TTL = 6 * HOUR;

    static boolean expired(long savedAt, long now) {
        return savedAt <= 0 || now < savedAt || now - savedAt >= CATALOG_TTL;
    }

    static long rotation(long now) { return Math.floorDiv(now, HOUR); }

    static long retryDelay(int failures) {
        return 60_000L * (1L << Math.min(4, Math.max(0, failures - 1)));
    }

    static boolean canRetry(long failedAt, int failures, long now) {
        return failedAt == 0 || now < failedAt || now - failedAt >= Math.min(15 * 60_000L, retryDelay(failures));
    }
}
