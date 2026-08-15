package dev.karton.spotifywidget.media;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.karton.spotifywidget.SpotifyWidgetClient;
import dev.karton.spotifywidget.config.HudConfig;
import dev.karton.spotifywidget.hud.CoverArt;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Reads the Windows "now playing" session (the same data the volume flyout shows) through a small
 * PowerShell helper. No Spotify account, app registration or login involved - if the Spotify
 * desktop app is playing, Windows already knows the title, artist and position.
 */
public final class WindowsMediaSource implements MediaSource {
    private static final String SCRIPT_RESOURCE = "/assets/spotifywidget/nowplaying.ps1";

    private final AtomicReference<Track> track = new AtomicReference<>();
    private final AtomicReference<String> status = new AtomicReference<>("Starting");

    private volatile boolean running;
    private volatile Process process;
    private Thread readerThread;

    private String lastKey = "";
    private long lastReportedPosition = -1;
    private boolean startedSpotifyOnly;
    private int startedInterval;

    public static boolean supported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    @Override
    public void start() {
        if (running) return;
        if (!supported()) {
            status.set("Windows only - switch the source to the Spotify Web API");
            return;
        }
        running = true;
        readerThread = new Thread(this::runLoop, "spotify-widget-smtc");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    @Override
    public void stop() {
        running = false;
        Process current = process;
        if (current != null) current.destroy();
        if (readerThread != null) readerThread.interrupt();
        track.set(null);
    }

    @Override
    public Track track() {
        return track.get();
    }

    @Override
    public String status() {
        return status.get();
    }

    private void runLoop() {
        String script;
        try (InputStream in = WindowsMediaSource.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) throw new IllegalStateException("helper script missing from the mod jar");
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            status.set("Could not load the helper script");
            SpotifyWidgetClient.LOGGER.error("Missing PowerShell helper", e);
            return;
        }
        // PowerShell -EncodedCommand takes base64 of UTF-16LE, which also sidesteps execution policy
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(script.getBytes(StandardCharsets.UTF_16LE));

        long backoffMillis = 2000;
        while (running) {
            try {
                ProcessBuilder builder = new ProcessBuilder(
                        powershell(), "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
                startedInterval = HudConfig.get().systemPollMillis;
                startedSpotifyOnly = HudConfig.get().spotifyAppOnly;
                builder.environment().put("SPW_INTERVAL", String.valueOf(startedInterval));
                builder.environment().put("SPW_PREFER_SPOTIFY", startedSpotifyOnly ? "1" : "0");
                builder.redirectErrorStream(false);
                Process started = builder.start();
                process = started;
                backoffMillis = 2000;

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(started.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while (running && (line = reader.readLine()) != null) {
                        handle(line);
                        if (settingsChanged()) break; // restart the helper with the new settings
                    }
                }
                started.destroy();
            } catch (Exception e) {
                if (!running) return;
                status.set("Windows media session unavailable");
                SpotifyWidgetClient.LOGGER.warn("Media session helper stopped: {}", e.toString());
            }
            if (!running) return;
            try {
                Thread.sleep(backoffMillis);
            } catch (InterruptedException e) {
                return;
            }
            backoffMillis = Math.min(backoffMillis * 2, 30_000);
        }
    }

    private boolean settingsChanged() {
        HudConfig config = HudConfig.get();
        return config.systemPollMillis != startedInterval || config.spotifyAppOnly != startedSpotifyOnly;
    }

    private static String powershell() {
        String root = System.getenv("SystemRoot");
        if (root != null) {
            java.io.File exe = new java.io.File(root, "System32\\WindowsPowerShell\\v1.0\\powershell.exe");
            if (exe.isFile()) return exe.getAbsolutePath();
        }
        return "powershell.exe";
    }

    private void handle(String line) {
        line = line.trim();
        if (line.isEmpty() || line.charAt(0) != '{') return;
        JsonObject json;
        try {
            json = JsonParser.parseString(line).getAsJsonObject();
        } catch (Exception e) {
            return;
        }
        String state = json.has("state") ? json.get("state").getAsString() : "error";
        if (!state.equals("ok")) {
            if (state.equals("none")) {
                status.set(HudConfig.get().spotifyAppOnly ? "Spotify is not playing" : "Nothing playing");
                track.set(null);
                lastKey = "";
                lastReportedPosition = -1;
            } else {
                status.set("Could not read the media session");
            }
            return;
        }

        String title = string(json, "title");
        String artist = string(json, "artist");
        String playback = string(json, "status");
        boolean playing = playback.equalsIgnoreCase("Playing");
        long position = number(json, "position");
        long duration = number(json, "duration");
        String app = string(json, "app");

        if (title.isEmpty() && artist.isEmpty()) {
            track.set(null);
            return;
        }

        String key = "sys_" + Integer.toHexString((artist + "|" + title).hashCode());
        boolean newTrack = !key.equals(lastKey);
        if (newTrack) {
            lastKey = key;
            lastReportedPosition = -1;
            CoverLookup.find(key, artist, title, url -> CoverArt.offerRemote(key, url));
        }

        Track existing = track.get();
        boolean positionMoved = position != lastReportedPosition;
        // Windows only refreshes the timeline every few seconds; keep the old timestamp while the
        // reported position stands still so the on screen clock keeps ticking smoothly.
        if (!newTrack && existing != null && !positionMoved && existing.playing == playing) {
            return;
        }
        lastReportedPosition = position;
        track.set(new Track(key, title, artist, key, duration, position, playing, System.nanoTime()));
        status.set(playing
                ? "Playing" + (app.toLowerCase(Locale.ROOT).contains("spotify") ? " on Spotify" : "")
                : "Paused");
    }

    private static String string(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
    }

    private static long number(JsonObject json, String key) {
        return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsLong() : 0L;
    }
}
