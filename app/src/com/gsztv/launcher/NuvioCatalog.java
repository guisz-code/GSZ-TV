package com.gsztv.launcher;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reads metadata from the same catalogue addons configured by the person in Nuvio.
 * Nuvio's private settings are not exported: no attempt is made to read another app's files.
 * Only manifest/catalog resources are requested. No stream lookup, account login or playback request.
 */
final class NuvioCatalog {
    static final String PACKAGE = "com.nuvio.tv";
    private static final int MAX_BYTES = 1 << 20, MAX_ITEMS = 48, MAX_CATALOGS = 6;
    private static NuvioCatalog instance;
    private final Context app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<Recs.Listener> listeners = new ArrayList<>();
    private volatile List<Recs.Pick> picks = new ArrayList<>();
    private boolean busy;
    private volatile String sources = "", error = "";
    private volatile long savedAt, failedAt;
    private int failures;
    private boolean diskRead;

    static synchronized NuvioCatalog get(Context c) {
        if (instance == null) instance = new NuvioCatalog(c.getApplicationContext());
        return instance;
    }

    private NuvioCatalog(Context c) { app = c; }
    void addListener(Recs.Listener l) { if (!listeners.contains(l)) listeners.add(l); }
    void removeListener(Recs.Listener l) { listeners.remove(l); }
    private File file() { return new File(app.getFilesDir(), "nuvio-catalog.json"); }

    /** Accept the addon installation link as copied from Nuvio, one URL per line. */
    static String cleanSources(String text) {
        StringBuilder out = new StringBuilder();
        Set<String> seen = new HashSet<>();
        for (String raw : text.trim().split("\\s+")) {
            if (raw.isEmpty()) continue;
            String url = raw.replaceFirst("(?i)^(stremio|nuvio)://", "https://");
            try {
                URL u = new URL(url);
                if (!"https".equalsIgnoreCase(u.getProtocol()) || u.getHost().isEmpty()
                        || u.getUserInfo() != null || u.getRef() != null
                        || !u.getPath().endsWith("/manifest.json") || url.length() > 4096) return null;
            } catch (Exception e) { return null; }
            if (!seen.add(url)) continue;
            if (seen.size() > 4) return null;
            if (out.length() != 0) out.append('\n');
            out.append(url);
        }
        return out.toString();
    }

    void refreshIfStale() {
        String configured = Prefs.nuvioCatalogs();
        if (busy || configured.isEmpty()) return;
        long now = System.currentTimeMillis();
        boolean changed = !configured.equals(sources);
        if (!changed && !RecommendationPolicy.canRetry(failedAt, failures, now)) return;
        if (!changed && diskRead && !RecommendationPolicy.expired(savedAt, now)) return;
        busy = true;
        if (changed) { sources = configured; savedAt = failedAt = 0; failures = 0; picks = new ArrayList<>(); }
        io.execute(() -> {
            try {
                if (!diskRead) {
                    diskRead = true;
                    try (InputStream in = new FileInputStream(file())) {
                        JSONObject stored = new JSONObject(read(in));
                        if (configured.equals(stored.optString("sources"))) {
                            picks = parse(stored.optJSONArray("metas"));
                            savedAt = stored.optLong("at");
                            notifyListeners();
                        }
                    } catch (Exception ignored) {}
                }
                if (!RecommendationPolicy.expired(savedAt, System.currentTimeMillis())) return;
                List<Recs.Pick> loaded = new ArrayList<>();
                Set<String> seen = new HashSet<>();
                int count = 0, succeeded = 0, movieCatalogs = 0, seriesCatalogs = 0;
                boolean partialFailure = false;
                for (String manifestUrl : configured.split("\n")) {
                    if (count >= MAX_CATALOGS) break;
                    try {
                        JSONArray catalogs = getJson(manifestUrl).optJSONArray("catalogs");
                        URL manifest = new URL(manifestUrl);
                        String base = new URL(manifest, ".").toExternalForm();
                        for (int i = 0; catalogs != null && i < Math.min(catalogs.length(), 100) && count < MAX_CATALOGS; i++) {
                            JSONObject cat = catalogs.optJSONObject(i);
                            if (cat == null || !supported(cat.optString("type")) || requiredExtra(cat) || cat.optString("id").isEmpty()) continue;
                            boolean movie = "movie".equals(cat.optString("type"));
                            if (movie ? movieCatalogs >= 3 : seriesCatalogs >= 3) continue;
                            if (movie) movieCatalogs++; else seriesCatalogs++;
                            count++;
                            try {
                                String endpoint = base + "catalog/" + Uri.encode(cat.getString("type")) + "/" + Uri.encode(cat.getString("id")) + ".json"
                                        + (manifest.getQuery() == null ? "" : "?" + manifest.getQuery());
                                List<Recs.Pick> part = parse(getJson(endpoint).optJSONArray("metas"));
                                succeeded++;
                                int accepted = 0;
                                for (Recs.Pick p : part) {
                                    if (accepted >= 12 || loaded.size() >= MAX_ITEMS) break;
                                    if (seen.add(p.externalId)) { loaded.add(p); accepted++; }
                                }
                            } catch (Exception e) { partialFailure = true; }
                        }
                    } catch (Exception e) { partialFailure = true; }
                }
                if (loaded.isEmpty()) throw new IOException(succeeded > 0 ? "O addon não retornou títulos de filmes ou séries." : "Não foi possível ler um catálogo de filmes ou séries deste addon.");
                if (!configured.equals(Prefs.nuvioCatalogs())) return; // source changed while the requests were running
                // A temporary failure in one catalogue must not erase its cached entries.
                if (partialFailure) for (Recs.Pick p : picks)
                    if (loaded.size() < MAX_ITEMS && seen.add(p.externalId)) loaded.add(p);
                picks = loaded;
                savedAt = System.currentTimeMillis();
                failedAt = 0; failures = 0;
                error = partialFailure ? "Alguns catálogos não responderam; usando os demais e o cache." : "";
                save(configured);
            } catch (Exception e) {
                // Never log a configured URL: addon links may include private account tokens.
                error = "Não foi possível atualizar o catálogo. Confira o link e a conexão; o cache foi mantido.";
                failedAt = System.currentTimeMillis(); failures++;
            } finally {
                main.post(() -> { busy = false; for (Recs.Listener l : new ArrayList<>(listeners)) l.onRecs(); });
            }
        });
    }

    private static boolean supported(String type) { return "movie".equals(type) || "series".equals(type); }
    private static boolean requiredExtra(JSONObject catalog) {
        JSONArray extra = catalog.optJSONArray("extra");
        for (int i = 0; extra != null && i < extra.length(); i++) {
            JSONObject field = extra.optJSONObject(i);
            if (field != null && field.optBoolean("isRequired")) return true;
        }
        return false;
    }

    private List<Recs.Pick> parse(JSONArray metas) {
        List<Recs.Pick> out = new ArrayList<>();
        for (int i = 0; metas != null && i < Math.min(metas.length(), 200) && out.size() < MAX_ITEMS; i++) {
            JSONObject o = metas.optJSONObject(i);
            if (o == null || !supported(o.optString("type")) || o.optString("id").isEmpty() || o.optString("name").trim().isEmpty()) continue;
            Recs.Pick p = new Recs.Pick();
            String type = o.optString("type"), id = o.optString("id");
            if (id.length() > 512) continue;
            p.externalId = type + ":" + id;
            p.kind = "movie".equals(type) ? "movie" : "tv";
            p.title = o.optString("name").trim();
            if (p.title.length() > 300) p.title = p.title.substring(0, 300);
            p.app = "nuvio"; p.pkg = PACKAGE; p.detailed = true;
            p.meta = "Nuvio · " + ("movie".equals(type) ? "Filme" : "Série");
            p.link = "nuvio://detail/" + type + "/" + Uri.encode(id);
            String poster = safeArt(o.optString("poster")), background = safeArt(o.optString("background"));
            p.externalArt = new WatchNext.Item(PACKAGE, poster, background);
            out.add(p);
        }
        return out;
    }

    private static String safeArt(String url) {
        return url.startsWith("https://") && url.length() <= 4096 ? url : null;
    }

    /** Preserve source order within each type; hourly selection rotates the cached list. */
    List<Recs.Pick> now(String kind) {
        List<Recs.Pick> filtered = new ArrayList<>(), out = new ArrayList<>();
        if (Prefs.nuvioCatalogs().isEmpty()) return out;
        for (Recs.Pick p : picks) if (kind == null || kind.equals(p.kind)) filtered.add(p);
        if (filtered.isEmpty()) return out;
        int start = (int) Math.floorMod(RecommendationPolicy.rotation(System.currentTimeMillis()) * Prefs.cards(), filtered.size());
        for (int i = 0; i < Math.min(Prefs.cards(), filtered.size()); i++) out.add(filtered.get((start + i) % filtered.size()));
        return out;
    }

    static List<Recs.Pick> mix(List<Recs.Pick> tmdb, List<Recs.Pick> nuvio, int max) {
        List<Recs.Pick> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < Math.max(tmdb.size(), nuvio.size()) && out.size() < max; i++) {
            // Nuvio first: duplicate titles keep the link into the person's configured player.
            if (i < nuvio.size()) addUnique(out, seen, nuvio.get(i), max);
            if (i < tmdb.size()) addUnique(out, seen, tmdb.get(i), max);
        }
        return out;
    }

    private static void addUnique(List<Recs.Pick> out, Set<String> seen, Recs.Pick p, int max) {
        String identity = p.kind + ":" + p.title.trim().toLowerCase(Locale.ROOT);
        if (out.size() < max && seen.add(identity)) out.add(p);
    }

    String summary() {
        if (Prefs.nuvioCatalogs().isEmpty()) return "Adicionar link do addon";
        if (!error.isEmpty()) return error;
        return picks.isEmpty() ? "Carregando catálogos…" : picks.size() + " títulos em cache";
    }

    private void save(String configured) throws Exception {
        JSONArray metas = new JSONArray();
        for (Recs.Pick p : picks) {
            String id = Uri.parse(p.link).getLastPathSegment();
            WatchNext.Item art = p.externalArt;
            metas.put(new JSONObject().put("type", "movie".equals(p.kind) ? "movie" : "series")
                    .put("id", id).put("name", p.title).put("background", art.picture() == null ? "" : art.picture()));
        }
        File temporary = new File(file().getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temporary)) {
            out.write(new JSONObject().put("sources", configured).put("at", savedAt).put("metas", metas).toString().getBytes(StandardCharsets.UTF_8));
        }
        if (!temporary.renameTo(file())) throw new IOException("cache");
    }

    private static JSONObject getJson(String address) throws Exception {
        URL url = new URL(address);
        for (int redirects = 0; redirects <= 3; redirects++) {
            if (!"https".equalsIgnoreCase(url.getProtocol()) || url.getUserInfo() != null) throw new IOException("HTTPS required");
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(5000); c.setReadTimeout(8000); c.setInstanceFollowRedirects(false);
            c.setRequestProperty("Accept", "application/json");
            try {
                int code = c.getResponseCode();
                if (code >= 300 && code < 400) {
                    String to = c.getHeaderField("Location");
                    if (to == null) throw new IOException("redirect");
                    url = new URL(url, to); continue;
                }
                if (code != 200 || c.getContentLengthLong() > MAX_BYTES) throw new IOException("catalog response");
                try (InputStream in = c.getInputStream()) { return new JSONObject(read(in)); }
            } finally { c.disconnect(); }
        }
        throw new IOException("redirect limit");
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192]; int n;
        while ((n = in.read(buffer)) != -1) {
            if (out.size() + n > MAX_BYTES) throw new IOException("catalog size");
            out.write(buffer, 0, n);
        }
        return out.toString("UTF-8");
    }

    private void notifyListeners() { main.post(() -> { for (Recs.Listener l : new ArrayList<>(listeners)) l.onRecs(); }); }
}
