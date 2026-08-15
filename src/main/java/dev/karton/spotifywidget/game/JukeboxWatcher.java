package dev.karton.spotifywidget.game;

import dev.karton.spotifywidget.SpotifyWidgetClient;
import dev.karton.spotifywidget.config.HudConfig;
import dev.karton.spotifywidget.media.MediaControl;
import dev.karton.spotifywidget.media.Track;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.JukeboxSong;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks records started by nearby jukeboxes. The song only exists on the server, so the client
 * finds out through level events; see {@code LevelEventMixin}. While a record plays the widget
 * shows the disc and the desktop player is paused, and it starts again once the record ends,
 * is stopped, or the player walks out of earshot.
 */
public final class JukeboxWatcher {
    /** Records play at volume 4, so vanilla can be heard 64 blocks away. */
    private static final int RANGE = 64;
    private static final int EVENT_RECORD_STARTED = 1010;
    private static final int EVENT_RECORD_STOPPED = 1011;

    private static final Map<BlockPos, Playing> ACTIVE = new ConcurrentHashMap<>();

    private static Track track;
    private static boolean pausedByUs;

    private JukeboxWatcher() {
    }

    /** The disc currently playing in earshot, or null. */
    public static Track track() {
        return track;
    }

    /** Called from the packet mixin on the client thread. */
    public static void onLevelEvent(int type, BlockPos pos, int data) {
        if (type == EVENT_RECORD_STOPPED) {
            ACTIVE.remove(pos);
            return;
        }
        if (type != EVENT_RECORD_STARTED) return;

        JukeboxSong song = songById(data);
        if (song == null) return;
        long durationMs = (long) (song.lengthInSeconds() * 1000f);
        ACTIVE.put(pos.immutable(), new Playing(song.description().getString(), durationMs, System.nanoTime()));
    }

    public static void clearAll() {
        ACTIVE.clear();
        stop(true);
    }

    public static void tick() {
        HudConfig config = HudConfig.get();
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.level == null) {
            ACTIVE.clear();
            stop(true);
            return;
        }
        if (!config.jukeboxWidget && !config.pauseForJukebox) {
            stop(true);
            return;
        }

        long now = System.nanoTime();
        ACTIVE.entrySet().removeIf(entry -> entry.getValue().finished(now));

        Playing nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        if (recordsAudible(minecraft)) {
            for (Map.Entry<BlockPos, Playing> entry : ACTIVE.entrySet()) {
                double distance = entry.getKey().distToCenterSqr(player.getX(), player.getY(), player.getZ());
                if (distance <= RANGE * RANGE && distance < nearestDistance) {
                    nearestDistance = distance;
                    nearest = entry.getValue();
                }
            }
        }

        if (nearest == null) {
            // Out of earshot, muted, finished or stopped - let the desktop player carry on
            stop(true);
            return;
        }

        String id = "jukebox:" + nearest.title + ":" + nearest.startNanos;
        boolean started = track == null || !track.id.equals(id);
        track = new Track(id, nearest.title, "Jukebox", null,
                nearest.durationMs, nearest.elapsedMs(now), true, System.nanoTime());
        if (started && config.pauseForJukebox) {
            MediaControl.pause();
            pausedByUs = true;
            SpotifyWidgetClient.LOGGER.debug("Jukebox started '{}', pausing the desktop player", nearest.title);
        }
    }

    /** Drops the disc and, when we paused the desktop player for it, starts that again. */
    private static void stop(boolean resume) {
        track = null;
        if (resume && pausedByUs) {
            pausedByUs = false;
            MediaControl.play();
        }
    }

    private static boolean recordsAudible(Minecraft minecraft) {
        return minecraft.options.getSoundSourceVolume(SoundSource.MASTER) > 0f
                && minecraft.options.getSoundSourceVolume(SoundSource.RECORDS) > 0f;
    }

    private static JukeboxSong songById(int id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return null;
        try {
            //? if >=1.21.2 {
            return minecraft.level.registryAccess().lookupOrThrow(Registries.JUKEBOX_SONG)
                    .get(id).map(holder -> holder.value()).orElse(null);
            //?} else {
            /*return minecraft.level.registryAccess().registryOrThrow(Registries.JUKEBOX_SONG)
                    .getHolder(id).map(holder -> holder.value()).orElse(null);
            *///?}
        } catch (Exception e) {
            SpotifyWidgetClient.LOGGER.debug("Unknown jukebox song id {}", id, e);
            return null;
        }
    }

    private static final class Playing {
        private final String title;
        private final long durationMs;
        private final long startNanos;

        private Playing(String title, long durationMs, long startNanos) {
            this.title = title;
            this.durationMs = durationMs;
            this.startNanos = startNanos;
        }

        private long elapsedMs(long now) {
            return (now - startNanos) / 1_000_000L;
        }

        private boolean finished(long now) {
            return durationMs > 0 && elapsedMs(now) > durationMs;
        }
    }
}
