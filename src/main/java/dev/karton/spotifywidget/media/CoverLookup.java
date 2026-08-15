package dev.karton.spotifywidget.media;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.karton.spotifywidget.SpotifyWidgetClient;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Finds album art for a title/artist pair. Windows only reports the track name, so the cover is
 * looked up on Deezer and, failing that, the iTunes search endpoint. Neither needs a key or login.
 */
public final class CoverLookup {
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "spotify-widget-cover-lookup");
        thread.setDaemon(true);
        return thread;
    });

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();
    private static final String MISSING = "";

    private CoverLookup() {
    }

    /** Calls back with an artwork URL once found. Never calls back when nothing matches. */
    public static void find(String key, String artist, String title, Consumer<String> callback) {
        String cached = CACHE.get(key);
        if (cached != null) {
            if (!cached.equals(MISSING)) callback.accept(cached);
            return;
        }
        if (PENDING.putIfAbsent(key, Boolean.TRUE) != null) return;
        WORKER.submit(() -> {
            String url = MISSING;
            try {
                url = search(artist, title);
            } catch (Exception e) {
                SpotifyWidgetClient.LOGGER.debug("Cover lookup failed", e);
            } finally {
                CACHE.put(key, url == null ? MISSING : url);
                PENDING.remove(key);
            }
            if (url != null && !url.isEmpty()) callback.accept(url);
        });
    }

    private static String search(String artist, String title) throws Exception {
        String url = deezer("artist:\"" + artist + "\" track:\"" + title + "\"");
        if (url == null) url = deezer(artist + " " + title);
        if (url == null) url = itunes(artist + " " + title);

        String shortTitle = simplify(title);
        if (url == null && !shortTitle.equals(title)) {
            url = deezer(artist + " " + shortTitle);
            if (url == null) url = itunes(artist + " " + shortTitle);
        }
        return url;
    }

    /** Drops "- Remastered", "(feat. ...)" and similar tails that hurt matching. */
    private static String simplify(String title) {
        int dash = title.indexOf(" - ");
        if (dash > 0) title = title.substring(0, dash);
        int bracket = title.indexOf(" (");
        if (bracket > 0) title = title.substring(0, bracket);
        return title.trim();
    }

    private static String deezer(String query) throws Exception {
        String body = get("https://api.deezer.com/search?limit=1&q=" + encode(query));
        if (body == null) return null;
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!json.has("data")) return null;
        JsonArray data = json.getAsJsonArray("data");
        if (data.isEmpty()) return null;
        JsonObject album = data.get(0).getAsJsonObject().getAsJsonObject("album");
        for (String field : new String[]{"cover_big", "cover_medium", "cover"}) {
            if (album.has(field) && !album.get(field).isJsonNull()) {
                String value = album.get(field).getAsString();
                if (!value.isBlank()) return value;
            }
        }
        return null;
    }

    private static String itunes(String query) throws Exception {
        String body = get("https://itunes.apple.com/search?entity=song&limit=1&term=" + encode(query));
        if (body == null) return null;
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        if (!json.has("results")) return null;
        JsonArray results = json.getAsJsonArray("results");
        if (results.isEmpty()) return null;
        JsonObject first = results.get(0).getAsJsonObject();
        if (!first.has("artworkUrl100")) return null;
        // The 100x100 thumbnail URL also serves larger sizes
        return first.get("artworkUrl100").getAsString().replace("100x100bb", "512x512bb");
    }

    private static String get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "SpotifyWidget-Minecraft-Mod")
                .timeout(Duration.ofSeconds(12))
                .GET()
                .build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return response.statusCode() == 200 ? response.body() : null;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
