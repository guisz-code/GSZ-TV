package com.gsztv.launcher;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.net.URL;
import java.net.URLStreamHandler;
import java.net.URLConnection;
import java.net.HttpURLConnection;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

/** Run the production classes on Android's runtime, with metadata-only fixtures and no private key. */
public final class RecommendationChecks {
    private static int checks;
    private static final Map<String, String> responses = new HashMap<>();
    private static final Map<String, String> redirects = new HashMap<>();
    private static int calls;
    private static boolean oversized;
    private static final class FixtureConnection extends HttpURLConnection {
        FixtureConnection(URL url) { super(url); }
        @Override public void connect() {}
        @Override public void disconnect() {}
        @Override public boolean usingProxy() { return false; }
        @Override public int getResponseCode() { calls++; return redirects.containsKey(url.toString()) ? 302 : responses.containsKey(url.toString()) ? 200 : 503; }
        @Override public String getHeaderField(String name) { return "Location".equals(name) ? redirects.get(url.toString()) : null; }
        @Override public long getContentLengthLong() { return oversized ? (2 << 20) : -1; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(responses.get(url.toString()).getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    }
    private static void check(boolean value, String name) {
        if (!value) throw new AssertionError(name);
        checks++;
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        if (Looper.getMainLooper() == null) Looper.prepareMainLooper();
        long now = 1_800_000_000_000L;
        check(!RecommendationPolicy.expired(now - 6 * RecommendationPolicy.HOUR + 1, now), "reuse catalogue before six hours");
        check(RecommendationPolicy.expired(now - 6 * RecommendationPolicy.HOUR, now), "refresh at six hours");
        check(RecommendationPolicy.expired(now + 100, now), "clock moved back");
        check(!RecommendationPolicy.expired(now - RecommendationPolicy.HOUR, now), "rotation must not fetch catalogue");
        check(RecommendationPolicy.rotation(2 * RecommendationPolicy.HOUR - 1) == 1, "hour before boundary");
        check(RecommendationPolicy.rotation(2 * RecommendationPolicy.HOUR) == 2, "hour at boundary");
        check(!RecommendationPolicy.canRetry(now - 59_999, 1, now), "first failure waits one minute");
        check(RecommendationPolicy.canRetry(now - 60_000, 1, now), "first retry after one minute");
        check(!RecommendationPolicy.canRetry(now - 14 * 60_000, 20, now), "repeated errors back off");
        check(RecommendationPolicy.canRetry(now - 15 * 60_000, 20, now), "backoff capped at fifteen minutes");
        check(RecommendationPolicy.canRetry(now + 100, 4, now), "retry after clock rollback");
        check("".equals(NuvioCatalog.cleanSources(" \n ")), "clear configured sources");
        check("https://catalog.example/manifest.json".equals(NuvioCatalog.cleanSources("stremio://catalog.example/manifest.json")), "addon install links");
        check(NuvioCatalog.cleanSources("http://catalog.example/manifest.json") == null, "reject unencrypted metadata source");
        check(NuvioCatalog.cleanSources("https://catalog.example/stream/movie/tt1.json") == null, "only addon manifests");
        check(NuvioCatalog.cleanSources("https://user:secret@catalog.example/manifest.json") == null, "reject URL credentials");
        check(NuvioCatalog.cleanSources("https://catalog.example/manifest.json\nhttps://catalog.example/manifest.json").indexOf('\n') < 0, "deduplicate source URLs");
        check(NuvioCatalog.cleanSources("https://1.example/manifest.json https://2.example/manifest.json https://3.example/manifest.json https://4.example/manifest.json https://5.example/manifest.json") == null, "bounded source count");
        Constructor<NuvioCatalog> constructor = NuvioCatalog.class.getDeclaredConstructor(Context.class);
        constructor.setAccessible(true);
        NuvioCatalog catalog = constructor.newInstance(new Object[] {null});
        Method parse = NuvioCatalog.class.getDeclaredMethod("parse", JSONArray.class); parse.setAccessible(true);
        JSONArray fixture = new JSONArray()
                .put(new JSONObject().put("type", "movie").put("id", "tt123").put("name", "Filme teste").put("background", "https://images.example/art.jpg"))
                .put(new JSONObject().put("type", "series").put("id", "catalog:abc/season%20").put("name", "Série teste").put("poster", "https://images.example/poster.jpg"))
                .put(new JSONObject().put("type", "channel").put("id", "live").put("name", "Ignore live"))
                .put(new JSONObject().put("type", "movie").put("name", "No ID"));
        List<Recs.Pick> result = (List<Recs.Pick>) parse.invoke(catalog, fixture);
        check(result.size() == 2, "only usable movie/series metadata");
        check("movie".equals(result.get(0).kind) && "tv".equals(result.get(1).kind), "correct home tabs");
        check("nuvio://detail/movie/tt123".equals(result.get(0).link), "official Nuvio detail link");
        check("catalog:abc/season%20".equals(Uri.parse(result.get(1).link).getLastPathSegment()), "ID survives path encoding");
        check("com.nuvio.tv".equals(result.get(0).pkg), "link constrained to Nuvio package");
        check("Nuvio".equals(Recs.serviceName(result.get(0))), "accessible source label");
        check(result.get(1).externalArt.pictureUri != null, "poster fallback");
        JSONArray huge = new JSONArray();
        for (int i=0;i<300;i++) huge.put(new JSONObject().put("type","movie").put("id","tt"+i).put("name","Movie "+i));
        check(((List<?>) parse.invoke(catalog, huge)).size() == 48, "bound cached metadata");
        Recs.Pick duplicate = new Recs.Pick(); duplicate.kind="movie"; duplicate.title="FILME TESTE"; duplicate.id=123;
        List<Recs.Pick> mixed = NuvioCatalog.mix(Collections.singletonList(duplicate), result, 6);
        check(mixed.size() == 2 && mixed.get(0).externalArt != null, "deduplicate titles and keep Nuvio destination");
        check(NuvioCatalog.mix(Arrays.asList(duplicate), result, 1).size() == 1, "respect configured maximum");
        URL.setURLStreamHandlerFactory(protocol -> "https".equals(protocol) ? new URLStreamHandler() {
            @Override protected URLConnection openConnection(URL u) { return new FixtureConnection(u); }
        } : null);
        Method getJson = NuvioCatalog.class.getDeclaredMethod("getJson", String.class); getJson.setAccessible(true);
        String address="https://catalog.fixture/manifest.json";
        responses.put(address,"{\"catalogs\":[{\"id\":\"top\",\"type\":\"movie\"}]}");
        check(((JSONObject)getJson.invoke(null,address)).getJSONArray("catalogs").length()==1,"read addon manifest metadata");
        responses.put("https://catalog.fixture/catalog/movie/top.json","{\"metas\":[{\"id\":\"tt123\",\"type\":\"movie\",\"name\":\"Fixture film\"}]}");
        JSONObject response=(JSONObject)getJson.invoke(null,"https://catalog.fixture/catalog/movie/top.json");
        check(((List<?>)parse.invoke(catalog,response.getJSONArray("metas"))).size()==1,"read and parse catalogue response");
        redirects.put("https://catalog.fixture/moved",address);
        check(((JSONObject)getJson.invoke(null,"https://catalog.fixture/moved")).has("catalogs"),"follow HTTPS redirects");
        redirects.put("https://catalog.fixture/insecure","http://catalog.fixture/manifest.json");
        try { getJson.invoke(null,"https://catalog.fixture/insecure"); throw new AssertionError("reject HTTP downgrade"); }
        catch(InvocationTargetException expected) { check(expected.getCause() instanceof java.io.IOException,"reject HTTP downgrade"); }
        oversized=true;
        try { getJson.invoke(null,address); throw new AssertionError("limit response bytes"); }
        catch(InvocationTargetException expected) { check(expected.getCause() instanceof java.io.IOException,"limit response bytes"); }
        oversized=false;
        try { getJson.invoke(null,"https://catalog.fixture/offline"); throw new AssertionError("HTTP failures are not empty success"); }
        catch(InvocationTargetException expected) { check(expected.getCause() instanceof java.io.IOException,"HTTP failures are not empty success"); }
        Method required=NuvioCatalog.class.getDeclaredMethod("requiredExtra",JSONObject.class);required.setAccessible(true);
        check((Boolean)required.invoke(null,new JSONObject("{\"extra\":[{\"name\":\"search\",\"isRequired\":true}]}")),"skip addons requiring a search query");
        check(!(Boolean)required.invoke(null,new JSONObject("{\"extra\":[{\"name\":\"genre\"}]}")),"allow optional catalogue filters");
        System.out.println("PASS: " + checks + " recommendation checks (Android runtime, no TMDB key).");
        System.exit(0);
    }
}
