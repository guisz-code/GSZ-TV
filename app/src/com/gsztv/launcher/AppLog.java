package com.gsztv.launcher;

import android.util.Log;

/** The app's log (adb logcat -s GSZTV). Never logs keys. */
final class AppLog {
    private AppLog() {}

    static void w(String message) {
        Log.i("GSZTV", message);
    }
}
