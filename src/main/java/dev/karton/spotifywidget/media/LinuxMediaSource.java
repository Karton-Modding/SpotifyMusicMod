package dev.karton.spotifywidget.media;

import dev.karton.spotifywidget.SpotifyWidgetClient;
import dev.karton.spotifywidget.config.HudConfig;
import dev.karton.spotifywidget.hud.CoverArt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the MPRIS D-Bus player that desktop Linux exposes for Spotify (and every other media
 * player). Uses {@code gdbus}, which ships with glib, and falls back to {@code playerctl}.
 * No account setup, and Spotify even hands out the real cover URL through {@code xesam:artUrl}.
 */
public final class LinuxMediaSource implements MediaSource {
    private static final String BUS_PREFIX = "org.mpris.MediaPlayer2.";
    private static final String OBJECT_PATH = "/org/mpris/MediaPlayer2";
    private static final String PLAYER_INTERFACE = "org.mpris.MediaPlayer2.Player";
    /** Unit separator, kept out of any track title. */
    private static final String SEPARATOR = "\u001F";

    private final AtomicReference<Track> track = new AtomicReference<>();
    private final AtomicReference<String> status = new AtomicReference<>("Starting");

    private volatile boolean running;
    private Thread thread;

    private String tool;
    private String lastKey = "";
    private long lastReportedPosition = -1;

    public static boolean supported() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("linux") || os.contains("nix") || os.contains("bsd");
    }

    @Override
    public void start() {
        if (running) return;
        if (!supported()) {
            status.set("Linux only - switch the source to the Spotify Web API");
            return;
        }
        running = true;
        thread = new Thread(this::runLoop, "spotify-widget-mpris");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) thread.interrupt();
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
        tool = hasCommand("gdbus", "--version") ? "gdbus"
                : hasCommand("playerctl", "--version") ? "playerctl"
                : null;
        if (tool == null) {
            status.set("Install gdbus (glib2) or playerctl to read the player");
            return;
        }
        while (running) {
            try {
                if (tool.equals("gdbus")) pollGdbus();
                else pollPlayerctl();
            } catch (Exception e) {
                SpotifyWidgetClient.LOGGER.debug("MPRIS poll failed", e);
                status.set("Could not read the media player");
            }
            try {
                Thread.sleep(Math.max(250, HudConfig.get().systemPollMillis));
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void pollGdbus() throws Exception {
        String bus = pickBus();
        if (bus == null) {
            noPlayer();
            return;
        }
        String output = run(5, "gdbus", "call", "--session", "--dest", bus, "--object-path", OBJECT_PATH,
                "--method", "org.freedesktop.DBus.Properties.GetAll", PLAYER_INTERFACE);
        if (output == null || output.isBlank()) {
            noPlayer();
            return;
        }
        String title = variantString(output, "xesam:title");
        String artist = firstOfArray(output, "xesam:artist");
        String artUrl = variantString(output, "xesam:artUrl");
        long lengthMicros = variantNumber(output, "mpris:length");
        long positionMicros = variantNumber(output, "Position");
        String playback = plainString(output, "PlaybackStatus");
        publish(title, artist, artUrl, lengthMicros / 1000L, positionMicros / 1000L, playback, bus);
    }

    private void pollPlayerctl() throws Exception {
        List<String> command = new ArrayList<>(List.of("playerctl"));
        if (HudConfig.get().spotifyAppOnly) command.addAll(List.of("--player", "spotify"));
        command.addAll(List.of("metadata", "--format",
                String.join(SEPARATOR,
                "{{status}}", "{{xesam:title}}", "{{xesam:artist}}",
                "{{mpris:length}}", "{{position}}", "{{mpris:artUrl}}")));
        String output = run(5, command.toArray(new String[0]));
        if (output == null || output.isBlank()) {
            noPlayer();
            return;
        }
        String[] parts = output.trim().split(SEPARATOR, -1);
        if (parts.length < 5) {
            noPlayer();
            return;
        }
        long length = parseLong(parts[3]);
        long position = parseLong(parts[4]);
        String art = parts.length > 5 ? parts[5] : "";
        publish(parts[1], parts[2], art, length / 1000L, position / 1000L, parts[0], "playerctl");
    }

    /** Picks the Spotify bus name, or any other MPRIS player when that is allowed. */
    private String pickBus() throws Exception {
        String names = run(5, "gdbus", "call", "--session", "--dest", "org.freedesktop.DBus",
                "--object-path", "/org/freedesktop/DBus", "--method", "org.freedesktop.DBus.ListNames");
        if (names == null) return null;
        Matcher matcher = Pattern.compile("'(" + Pattern.quote(BUS_PREFIX) + "[^']+)'").matcher(names);
        String fallback = null;
        while (matcher.find()) {
            String bus = matcher.group(1);
            if (bus.toLowerCase(Locale.ROOT).contains("spotify")) return bus;
            if (fallback == null) fallback = bus;
        }
        return HudConfig.get().spotifyAppOnly ? null : fallback;
    }

    private void noPlayer() {
        status.set(HudConfig.get().spotifyAppOnly ? "Spotify is not running" : "No media player found");
        track.set(null);
        lastKey = "";
        lastReportedPosition = -1;
    }

    private void publish(String title, String artist, String artUrl,
                         long durationMs, long positionMs, String playback, String source) {
        if (title == null) title = "";
        if (artist == null) artist = "";
        if (title.isEmpty() && artist.isEmpty()) {
            noPlayer();
            return;
        }
        boolean playing = playback != null && playback.equalsIgnoreCase("Playing");
        String key = "mpris_" + Integer.toHexString((artist + "|" + title).hashCode());
        if (!key.equals(lastKey)) {
            lastKey = key;
            lastReportedPosition = -1;
            if (artUrl != null && artUrl.startsWith("http")) {
                CoverArt.offerRemote(key, artUrl);
            } else {
                String artistForLookup = artist;
                String titleForLookup = title;
                CoverLookup.find(key, artistForLookup, titleForLookup, url -> CoverArt.offerRemote(key, url));
            }
        }

        Track existing = track.get();
        if (existing != null && positionMs == lastReportedPosition && existing.playing == playing
                && existing.id.equals(key)) {
            return;
        }
        lastReportedPosition = positionMs;
        track.set(new Track(key, title, artist, key, durationMs, positionMs, playing, System.nanoTime()));
        status.set(playing
                ? "Playing" + (source.toLowerCase(Locale.ROOT).contains("spotify") ? " on Spotify" : "")
                : "Paused");
    }

    private static boolean hasCommand(String... command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            boolean done = process.waitFor(3, TimeUnit.SECONDS);
            if (!done) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private static String run(int timeoutSeconds, String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(false).start();
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line).append('\n');
        }
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return null;
        }
        return process.exitValue() == 0 ? builder.toString() : null;
    }

    // --- GVariant text parsing -------------------------------------------------
    // gdbus prints things like: {'xesam:title': <'Song'>, 'mpris:length': <uint64 199486000>, ...}

    private static String variantString(String output, String key) {
        Matcher matcher = Pattern.compile("'" + Pattern.quote(key) + "':\\s*<'((?:[^'\\\\]|\\\\.)*)'>").matcher(output);
        return matcher.find() ? unescape(matcher.group(1)) : "";
    }

    private static String plainString(String output, String key) {
        Matcher matcher = Pattern.compile("'" + Pattern.quote(key) + "':\\s*<?'((?:[^'\\\\]|\\\\.)*)'").matcher(output);
        return matcher.find() ? unescape(matcher.group(1)) : "";
    }

    private static long variantNumber(String output, String key) {
        Matcher matcher = Pattern.compile("'" + Pattern.quote(key) + "':\\s*<(?:[a-z0-9]+\\s+)?(-?\\d+)>").matcher(output);
        return matcher.find() ? parseLong(matcher.group(1)) : 0L;
    }

    private static String firstOfArray(String output, String key) {
        Matcher matcher = Pattern.compile("'" + Pattern.quote(key) + "':\\s*<\\[([^\\]]*)\\]>").matcher(output);
        if (!matcher.find()) return "";
        Matcher entry = Pattern.compile("'((?:[^'\\\\]|\\\\.)*)'").matcher(matcher.group(1));
        List<String> names = new ArrayList<>();
        while (entry.find()) names.add(unescape(entry.group(1)));
        return String.join(", ", names);
    }

    private static String unescape(String value) {
        return value.replace("\\'", "'").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception e) {
            return 0L;
        }
    }
}
