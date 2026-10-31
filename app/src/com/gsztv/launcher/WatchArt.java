package com.gsztv.launcher;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The "Continue watching" cards' pictures, decoded in the background once, at the card's size on screen (16:9,
 * centre-cropped), and kept in memory. Drawing only reuses them. Only pictures already on the TV are read (content:// and
 * android.resource://, from the apps themselves): the app never downloads an address another app wrote (http, https),
 * since it only talks to TMDB, Open-Meteo and Binance, and never opens files (file://). Anything else, or a picture that
 * http/https artwork is also read when an app publishes a remote poster (for example Nuvio); it is bounded and cached,
 * and when it can't be read the app's TV banner is shown instead.
 */
final class WatchArt {
    /** The card in the 1920×1080 layout; the pictures are made at this times the UI's scale (setScale). */
    static final int W = 520, H = 292;
    private static final int MAX_BYTES = 8 << 20;
    /** At most this many pixels decoded (~8 MB): a 60000×300 picture is sampled down before it's decoded. */
    private static final long MAX_PIXELS = 2_000_000;

    private final Context app;
    private final Runnable onLoaded;
    private final Handler main = new Handler(Looper.getMainLooper());
    /** One thread for the whole process: a home screen started over (new settings) doesn't leave one behind. */
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private volatile boolean closed;
    private volatile int pw = W, ph = H;   // the pictures' size in pixels
    private static final long RETRY_MS = 10 * 60_000L;
    private final Set<String> pending = new HashSet<>();
    private final Map<String, Long> failed = new HashMap<>();
    private final Map<String, Integer> colors = new HashMap<>();   // first dominant colour
    private final Map<String, Integer> secondColors = new HashMap<>(); // second dominant colour
    private final Map<Bitmap, BitmapShader> shaders = new HashMap<>();   // for the rounded corners, made with the picture
    private final Map<String, Bitmap> cache = new LinkedHashMap<String, Bitmap>(16, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Bitmap> eldest) {
            if (size() <= 16) return false;   // ~600 KB each at 1080p: the row and a little more
            shaders.remove(eldest.getValue());
            return true;
        }
    };

    WatchArt(Context c, Runnable onLoaded) {
        app = c.getApplicationContext();
        this.onLoaded = onLoaded;
    }

    /**
     * The card's picture, or the app's banner if it has none or it failed; null while loading (in the background;
     * onLoaded runs on the main thread when it's in).
     */
    Bitmap get(Uri uri, String key, String bannerKey, String pkg) {
        if (uri != null) {
            Bitmap b = fetch(key, uri, pkg);
            if (b != null || !failedRecently(key)) return b;
        }
        return fetch(bannerKey, null, pkg);
    }

    /** The UI's scale (width / 1920): pictures already made at another size are made again. */
    void setScale(float k) {
        int width = Math.max(1, Math.round(W * k)), height = Math.max(1, Math.round(H * k));
        if (width == pw && height == ph) return;
        pw = width;
        ph = height;
        synchronized (cache) {
            cache.clear();
            shaders.clear();
        }
    }

    /** The picture's shader (get's pictures only). */
    BitmapShader shader(Bitmap b) {
        synchronized (cache) {
            return shaders.get(b);
        }
    }

    /** The colour of the card's picture (or of the banner in its place), or null until it's decoded. */
    Integer color(String key, String bannerKey) {
        synchronized (cache) {
            if (key != null) {
                Integer c = colors.get(key);
                Long at = failed.get(key);
                if (c != null || at == null || System.currentTimeMillis() - at >= RETRY_MS) return c;
            }
            return colors.get(bannerKey);
        }
    }

    /** The second dominant colour, or the first one when the artwork is effectively monochrome. */
    Integer secondColor(String key, String bannerKey) {
        synchronized (cache) {
            if (key != null && secondColors.containsKey(key)) return secondColors.get(key);
            return secondColors.get(bannerKey);
        }
    }

    /** The home screen went away: nothing more is posted to it, and the pictures go. */
    void close() {
        closed = true;
        synchronized (cache) {
            cache.clear();
            shaders.clear();
            colors.clear();
        }
    }

    private boolean failedRecently(String uri) {
        synchronized (cache) {
            Long at = failed.get(uri);
            return at != null && System.currentTimeMillis() - at < RETRY_MS;
        }
    }

    private Bitmap fetch(String key, Uri uri, String pkg) {
        if (closed) return null;
        synchronized (cache) {
            Bitmap b = cache.get(key);
            if (b != null || pending.contains(key)) return b;
            Long at = failed.get(key);
            if (at != null && System.currentTimeMillis() - at < RETRY_MS) return null;
            pending.add(key);
        }
        IO.execute(() -> {
            if (closed) return;
            Bitmap b = null;
            try {
                b = uri == null ? banner(pkg) : load(uri);
            } catch (IOException | RuntimeException | OutOfMemoryError e) {
                AppLog.w("continue watching picture: " + e.getMessage());
            }
            int[] palette = b == null ? null : paletteOf(b);
            Integer color = palette == null ? null : palette[0];
            Integer second = palette == null ? null : palette[1];
            BitmapShader shader = b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            synchronized (cache) {
                pending.remove(key);
                if (closed) return;
                if (b != null) {
                    shaders.put(b, shader);
                    cache.put(key, b);
                    colors.put(key, color);
                    secondColors.put(key, second);
                } else {
                    failed.put(key, System.currentTimeMillis());
                }
            }
            main.post(() -> { if (!closed) onLoaded.run(); });   // the picture, or the banner in its place
        });
        return null;
    }

    private Bitmap load(Uri uri) throws IOException {
        byte[] data = read(uri);
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("unreadable picture");
        int cw = pw, ch = ph, bw = bounds.outWidth, bh = bounds.outHeight;
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inSampleSize = 1;
        // Halved while both sides stay at least the card's, or while it's still too many pixels (either side too big).
        for (int s = 1; bw / (s * 2) >= cw && bh / (s * 2) >= ch || (long) (bw / s) * (bh / s) > MAX_PIXELS; s *= 2) {
            opts.inSampleSize = s * 2;
        }
        Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        if (src == null) throw new IOException("unreadable picture");
        // Centre crop to 16:9.
        int sw = src.getWidth(), sh = src.getHeight();
        Rect from = (long) sw * ch > (long) sh * cw ? new Rect((sw - sh * cw / ch) / 2, 0, (sw + sh * cw / ch) / 2, sh)
                : new Rect(0, (sh - sw * ch / cw) / 2, sw, (sh + sw * ch / cw) / 2);
        Bitmap out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, from, new Rect(0, 0, cw, ch), new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG));
        src.recycle();
        return out;
    }

    /** Two dominant, deliberately softened colours for a subtle two-colour background gradient. */
    private static int[] paletteOf(Bitmap b) {
        Bitmap tiny = Bitmap.createScaledBitmap(b, 8, 5, true);
        int[] px = new int[40];
        tiny.getPixels(px, 0, 8, 0, 0, 8, 5);
        tiny.recycle();
        int[] count = new int[512], sumR = new int[512], sumG = new int[512], sumB = new int[512];
        for (int p : px) {
            int r = (p >> 16) & 255, g = (p >> 8) & 255, bl = p & 255;
            int bin = ((r >> 5) << 6) | ((g >> 5) << 3) | (bl >> 5);
            count[bin]++;
            sumR[bin] += r;
            sumG[bin] += g;
            sumB[bin] += bl;
        }
        int first = -1, second = -1;
        for (int i = 0; i < count.length; i++) {
            if (count[i] > (first < 0 ? 0 : count[first])) { second = first; first = i; }
            else if (i != first && count[i] > (second < 0 ? 0 : count[second])) second = i;
        }
        int primary = soften(sumR[first] / count[first], sumG[first] / count[first], sumB[first] / count[first]);
        if (second < 0 || colorDistance(first, second) < 2) return new int[] {primary, primary};
        int alternate = soften(sumR[second] / count[second], sumG[second] / count[second], sumB[second] / count[second]);
        return new int[] {primary, alternate};
    }

    private static int colorDistance(int a, int b) {
        int ar = a >> 6, ag = (a >> 3) & 7, ab = a & 7;
        int br = b >> 6, bg = (b >> 3) & 7, bb = b & 7;
        return Math.abs(ar - br) + Math.abs(ag - bg) + Math.abs(ab - bb);
    }

    private static int soften(int r, int g, int b) {
        float[] hsv = new float[3];
        android.graphics.Color.RGBToHSV(r, g, b, hsv);
        hsv[1] = Math.min(1, hsv[1] * 1.25f + 0.05f);
        hsv[2] = Math.max(0.38f, Math.min(0.84f, hsv[2]));
        return android.graphics.Color.HSVToColor(hsv);
    }

    /** The app's TV banner (or its icon) on a dark card, as the shelf shows apps without a logo. */
    private Bitmap banner(String pkg) throws IOException {
        PackageManager pm = app.getPackageManager();
        Drawable d;
        try {
            d = pm.getApplicationBanner(pkg);
            if (d == null) d = pm.getApplicationIcon(pkg);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException("no app " + pkg);
        }
        int cw = pw, ch = ph;
        Bitmap out = Bitmap.createBitmap(cw, ch, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(0xFF15161C);
        // TV banners are 16:9 already: edge to edge; an icon goes in the middle.
        boolean wide = d.getIntrinsicWidth() > d.getIntrinsicHeight();
        int s = Math.round(ch * 0.5f);
        if (wide) d.setBounds(0, 0, cw, ch);
        else d.setBounds((cw - s) / 2, (ch - s) / 2, (cw + s) / 2, (ch + s) / 2);
        d.draw(c);
        return out;
    }

    /** Reads local provider artwork and bounded remote artwork published by apps such as Nuvio. */
    private byte[] read(Uri uri) throws IOException {
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
            HttpURLConnection connection = (HttpURLConnection) new URL(uri.toString()).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(7000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "GSZ-TV/1.3");
            try {
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                    throw new IOException("artwork HTTP " + connection.getResponseCode());
                }
                if (connection.getContentLengthLong() > MAX_BYTES) throw new IOException("picture too large");
                try (InputStream in = connection.getInputStream()) {
                    return readAll(in);
                }
            } finally {
                connection.disconnect();
            }
        }
        try (InputStream in = app.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("no picture there");
            return readAll(in);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16384];
        int n;
        while ((n = in.read(buffer)) > 0) {
            out.write(buffer, 0, n);
            if (out.size() > MAX_BYTES) throw new IOException("picture too large");
        }
        return out.toByteArray();
    }
}
