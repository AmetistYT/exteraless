package app.exteraless.player;

import android.text.TextUtils;
import android.util.LruCache;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.BuildVars;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Pattern;

public final class LrcLib {

    public static final int OK = 0;
    public static final int NOT_FOUND = 1;
    public static final int ERROR = 2;

    public interface Callback {
        void onResult(Lyrics lyrics, int status);
    }

    public static final class Query {
        public final String artist;
        public final String title;
        public final String album;
        public final int duration;

        public Query(String artist, String title, String album, int duration) {
            String a = clean(artist);
            String t = clean(title);
            if (t != null) {
                t = AUDIO_EXT.matcher(t).replaceFirst("").replace('_', ' ').trim();
            }
            if (TextUtils.isEmpty(a) && t != null) {
                int dash = t.indexOf(" - ");
                if (dash > 0) {
                    a = t.substring(0, dash).trim();
                    t = t.substring(dash + 3).trim();
                }
            }
            this.artist = a;
            this.title = stripDecorations(t);
            this.album = clean(album);
            this.duration = duration;
        }

        public String key() {
            return (artist == null ? "" : artist.toLowerCase(Locale.ROOT)) + "\u0001" + (title == null ? "" : title.toLowerCase(Locale.ROOT)) + "\u0001" + duration;
        }

        public boolean valid() {
            return !TextUtils.isEmpty(title);
        }
    }

    private static final String BASE = "https://lrclib.net/api/";
    private static final Pattern AUDIO_EXT = Pattern.compile("\\.(mp3|m4a|flac|ogg|oga|opus|wav|aac|alac|wma)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern DECORATION = Pattern.compile("\\s*[(\\[][^)\\]]*(official|lyric|video|audio|visualizer|remaster|hq|hd)[^)\\]]*[)\\]]", Pattern.CASE_INSENSITIVE);
    private static final DispatchQueue queue = new DispatchQueue("lrclib");
    private static final LruCache<String, Lyrics> memory = new LruCache<>(24);
    private static final HashSet<String> missing = new HashSet<>();

    private LrcLib() {
    }

    private static String clean(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.isEmpty() ? null : s;
    }

    private static String stripDecorations(String s) {
        if (s == null) {
            return null;
        }
        String out = DECORATION.matcher(s).replaceAll("").trim();
        return out.isEmpty() ? s : out;
    }

    public static Lyrics cached(Query query) {
        return memory.get(query.key());
    }

    public static boolean knownMissing(Query query) {
        return missing.contains(query.key());
    }

    public static void loadCached(Query query, Callback callback) {
        String key = query.key();
        Lyrics hit = memory.get(key);
        if (hit != null) {
            callback.onResult(hit, OK);
            return;
        }
        queue.postRunnable(() -> {
            Lyrics disk = readDisk(key);
            if (disk != null) {
                memory.put(key, disk);
            }
            AndroidUtilities.runOnUIThread(() -> callback.onResult(disk, disk != null ? OK : NOT_FOUND));
        });
    }

    public static void fetch(Query query, Callback callback) {
        String key = query.key();
        Lyrics hit = memory.get(key);
        if (hit != null) {
            callback.onResult(hit, OK);
            return;
        }
        queue.postRunnable(() -> {
            Lyrics result = readDisk(key);
            int status = OK;
            if (result == null) {
                try {
                    JSONObject best = request(query);
                    if (best == null) {
                        status = NOT_FOUND;
                    } else {
                        writeDisk(key, best);
                        result = fromJson(best);
                        if (result == null) {
                            status = NOT_FOUND;
                        }
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                    status = ERROR;
                }
            }
            final Lyrics lyrics = result;
            final int finalStatus = status;
            AndroidUtilities.runOnUIThread(() -> {
                if (lyrics != null) {
                    memory.put(key, lyrics);
                    missing.remove(key);
                } else if (finalStatus == NOT_FOUND) {
                    missing.add(key);
                }
                callback.onResult(lyrics, finalStatus);
            });
        });
    }

    private static JSONObject request(Query q) throws Exception {
        if (!TextUtils.isEmpty(q.artist)) {
            StringBuilder url = new StringBuilder(BASE).append("get?artist_name=").append(enc(q.artist))
                    .append("&track_name=").append(enc(q.title));
            if (!TextUtils.isEmpty(q.album)) {
                url.append("&album_name=").append(enc(q.album));
            }
            if (q.duration > 0) {
                url.append("&duration=").append(q.duration);
            }
            String body = get(url.toString());
            if (body != null) {
                JSONObject obj = new JSONObject(body);
                if (hasLyrics(obj)) {
                    return obj;
                }
            }
        }
        StringBuilder url = new StringBuilder(BASE).append("search?track_name=").append(enc(q.title));
        if (!TextUtils.isEmpty(q.artist)) {
            url.append("&artist_name=").append(enc(q.artist));
        }
        String body = get(url.toString());
        if (body == null) {
            return null;
        }
        JSONArray arr = new JSONArray(body);
        JSONObject best = null;
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null || !hasLyrics(o)) {
                continue;
            }
            int score = 0;
            if (!TextUtils.isEmpty(o.optString("syncedLyrics", null)) && !o.isNull("syncedLyrics")) {
                score += 40;
            }
            if (q.duration > 0 && o.has("duration")) {
                int diff = (int) Math.abs(Math.round(o.optDouble("duration", 0)) - q.duration);
                score -= diff <= 3 ? diff : 20 + Math.min(diff, 60);
            }
            if (score > bestScore) {
                bestScore = score;
                best = o;
            }
        }
        return best;
    }

    private static boolean hasLyrics(JSONObject o) {
        return o.optBoolean("instrumental", false)
                || !o.isNull("syncedLyrics") && !TextUtils.isEmpty(o.optString("syncedLyrics", null))
                || !o.isNull("plainLyrics") && !TextUtils.isEmpty(o.optString("plainLyrics", null));
    }

    private static Lyrics fromJson(JSONObject o) {
        if (o.optBoolean("instrumental", false)) {
            return Lyrics.instrumental(Lyrics.SOURCE_LRCLIB);
        }
        Lyrics synced = o.isNull("syncedLyrics") ? null : Lyrics.parse(o.optString("syncedLyrics", null), Lyrics.SOURCE_LRCLIB);
        if (synced != null && synced.synced) {
            return synced;
        }
        return o.isNull("plainLyrics") ? synced : Lyrics.parse(o.optString("plainLyrics", null), Lyrics.SOURCE_LRCLIB);
    }

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8").replace("+", "%20");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(15_000);
            connection.setRequestProperty("User-Agent", "exteraless/" + BuildVars.BUILD_VERSION_STRING + " (https://github.com/exteraless/exteraless)");
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code == 404) {
                return null;
            }
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("lrclib http " + code);
            }
            try (InputStream in = connection.getInputStream()) {
                return readAll(in);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            out.write(buf, 0, n);
        }
        return out.toString("UTF-8");
    }

    private static File cacheFile(String key) {
        File dir = new File(ApplicationLoader.applicationContext.getCacheDir(), "lrclib");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        return new File(dir, Utilities.MD5(key) + ".json");
    }

    private static Lyrics readDisk(String key) {
        try {
            File f = cacheFile(key);
            if (f == null || !f.exists()) {
                return null;
            }
            try (FileInputStream in = new FileInputStream(f)) {
                return fromJson(new JSONObject(readAll(in)));
            }
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static void writeDisk(String key, JSONObject o) {
        try {
            File f = cacheFile(key);
            if (f == null) {
                return;
            }
            JSONObject slim = new JSONObject();
            slim.put("instrumental", o.optBoolean("instrumental", false));
            if (!o.isNull("syncedLyrics")) {
                slim.put("syncedLyrics", o.optString("syncedLyrics", null));
            }
            if (!o.isNull("plainLyrics")) {
                slim.put("plainLyrics", o.optString("plainLyrics", null));
            }
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(slim.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }
}
