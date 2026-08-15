package dev.karton.spotifywidget.input;

import dev.karton.spotifywidget.compat.Keys;
import dev.karton.spotifywidget.media.MediaControl;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * Playback controls, listed under "Spotify Widget" in the vanilla Controls screen.
 * Arrow keys by default; play/pause ships unbound so it cannot clash with anything.
 */
public final class MediaKeys {
    /** Volume keys repeat while held, every 4 ticks. */
    private static final int VOLUME_REPEAT_TICKS = 4;

    private static KeyMapping next;
    private static KeyMapping previous;
    private static KeyMapping volumeUp;
    private static KeyMapping volumeDown;
    private static KeyMapping playPause;

    private static int volumeCooldown;

    private MediaKeys() {
    }

    public static void register() {
        next = Keys.register("key.spotifywidget.next", GLFW.GLFW_KEY_RIGHT);
        previous = Keys.register("key.spotifywidget.previous", GLFW.GLFW_KEY_LEFT);
        volumeUp = Keys.register("key.spotifywidget.volume_up", GLFW.GLFW_KEY_UP);
        volumeDown = Keys.register("key.spotifywidget.volume_down", GLFW.GLFW_KEY_DOWN);
        playPause = Keys.register("key.spotifywidget.play_pause", GLFW.GLFW_KEY_UNKNOWN);
    }

    public static void tick() {
        if (next == null) return;

        while (next.consumeClick()) MediaControl.next();
        while (previous.consumeClick()) MediaControl.previous();
        while (playPause.consumeClick()) MediaControl.playPause();

        // Volume repeats while the key stays down
        if (volumeCooldown > 0) volumeCooldown--;
        boolean up = volumeUp.isDown();
        boolean down = volumeDown.isDown();
        if (up || down) {
            if (volumeCooldown == 0) {
                if (up) MediaControl.volumeUp();
                else MediaControl.volumeDown();
                volumeCooldown = VOLUME_REPEAT_TICKS;
            }
        } else {
            volumeCooldown = 0;
            // Drop any clicks that piled up while a screen was open
            while (volumeUp.consumeClick()) {
                // consumed
            }
            while (volumeDown.consumeClick()) {
                // consumed
            }
        }
    }
}
