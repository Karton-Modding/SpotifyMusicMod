package dev.karton.spotifywidget.media;

import dev.karton.spotifywidget.SpotifyWidgetClient;
import dev.karton.spotifywidget.config.HudConfig;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sends playback commands to the player the system already talks to. On Windows a small PowerShell
 * helper drives the media session (and the volume keys); on Linux the MPRIS player is called
 * directly. Nothing here needs a Spotify account.
 */
public final class MediaControl {
    private static final String SCRIPT_RESOURCE = "/assets/spotifywidget/control.ps1";

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "spotify-widget-control");
        thread.setDaemon(true);
        return thread;
    });

    private static Process process;
    private static BufferedWriter commands;
    private static boolean unavailable;

    private MediaControl() {
    }

    public static void next() {
        send("next");
    }

    public static void previous() {
        send("prev");
    }

    public static void playPause() {
        send("playpause");
    }

    public static void play() {
        send("play");
    }

    public static void pause() {
        send("pause");
    }

    public static void volumeUp() {
        send("volup");
    }

    public static void volumeDown() {
        send("voldown");
    }

    public static void shutdown() {
        WORKER.submit(() -> {
            closeHelper();
            return null;
        });
    }

    private static void send(String command) {
        if (unavailable) return;
        WORKER.submit(() -> {
            try {
                if (WindowsMediaSource.supported()) sendWindows(command);
                else if (LinuxMediaSource.supported()) sendLinux(command);
                else unavailable = true;
            } catch (Exception e) {
                SpotifyWidgetClient.LOGGER.warn("Media command '{}' failed", command, e);
                closeHelper();
            }
            return null;
        });
    }

    // --- Windows ---------------------------------------------------------------

    private static void sendWindows(String command) throws Exception {
        if (process == null || !process.isAlive()) startWindowsHelper();
        if (commands == null) return;
        commands.write(command);
        commands.newLine();
        commands.flush();
    }

    private static void startWindowsHelper() throws Exception {
        closeHelper();
        String script;
        try (InputStream in = MediaControl.class.getResourceAsStream(SCRIPT_RESOURCE)) {
            if (in == null) throw new IllegalStateException("control helper missing from the mod jar");
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        String encoded = java.util.Base64.getEncoder()
                .encodeToString(script.getBytes(StandardCharsets.UTF_16LE));

        ProcessBuilder builder = new ProcessBuilder(
                powershell(), "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded);
        builder.environment().put("SPW_PREFER_SPOTIFY", HudConfig.get().spotifyAppOnly ? "1" : "0");
        builder.redirectErrorStream(true);
        process = builder.start();
        commands = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        // Drain the helper's replies so its pipe never fills up
        Thread drain = new Thread(() -> {
            try (InputStream out = process.getInputStream()) {
                while (out.read() != -1) {
                    // replies are only useful for debugging
                }
            } catch (Exception ignored) {
                // helper stopped
            }
        }, "spotify-widget-control-reader");
        drain.setDaemon(true);
        drain.start();
    }

    private static String powershell() {
        String root = System.getenv("SystemRoot");
        if (root != null) {
            java.io.File exe = new java.io.File(root, "System32\\WindowsPowerShell\\v1.0\\powershell.exe");
            if (exe.isFile()) return exe.getAbsolutePath();
        }
        return "powershell.exe";
    }

    private static void closeHelper() {
        try {
            if (commands != null) {
                commands.write("quit");
                commands.newLine();
                commands.flush();
                commands.close();
            }
        } catch (Exception ignored) {
            // helper already gone
        }
        commands = null;
        if (process != null) {
            process.destroy();
            process = null;
        }
    }

    // --- Linux -----------------------------------------------------------------

    private static void sendLinux(String command) throws Exception {
        boolean spotifyOnly = HudConfig.get().spotifyAppOnly;
        List<String> playerctl = new java.util.ArrayList<>(List.of("playerctl"));
        if (spotifyOnly) playerctl.addAll(List.of("--player", "spotify"));
        switch (command) {
            case "next" -> playerctl.add("next");
            case "prev" -> playerctl.add("previous");
            case "playpause" -> playerctl.add("play-pause");
            case "play" -> playerctl.add("play");
            case "pause" -> playerctl.add("pause");
            case "volup" -> playerctl.addAll(List.of("volume", "0.05+"));
            case "voldown" -> playerctl.addAll(List.of("volume", "0.05-"));
            default -> {
                return;
            }
        }
        if (run(playerctl.toArray(new String[0]))) return;

        // No playerctl: fall back to raw D-Bus for transport, and to the sink volume for the rest
        switch (command) {
            case "volup", "voldown" -> {
                String delta = command.equals("volup") ? "5%+" : "5%-";
                if (!run("wpctl", "set-volume", "@DEFAULT_AUDIO_SINK@", delta)) {
                    run("pactl", "set-sink-volume", "@DEFAULT_SINK@", command.equals("volup") ? "+5%" : "-5%");
                }
            }
            default -> {
                String method = switch (command) {
                    case "next" -> "Next";
                    case "prev" -> "Previous";
                    case "play" -> "Play";
                    case "pause" -> "Pause";
                    default -> "PlayPause";
                };
                run("gdbus", "call", "--session", "--dest", "org.mpris.MediaPlayer2.spotify",
                        "--object-path", "/org/mpris/MediaPlayer2",
                        "--method", "org.mpris.MediaPlayer2.Player." + method);
            }
        }
    }

    private static boolean run(String... command) {
        try {
            Process started = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!started.waitFor(4, java.util.concurrent.TimeUnit.SECONDS)) {
                started.destroyForcibly();
                return false;
            }
            return started.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Only used for log messages. */
    public static String platform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") ? "Windows media session" : os.contains("linux") ? "MPRIS" : "unsupported";
    }
}
