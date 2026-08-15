package dev.karton.spotifywidget.media;

/** One snapshot of what the media player reports as playing. */
public final class Track {
    public final String id;
    public final String title;
    public final String artist;
    /** Cache key for the album art, see CoverArt. */
    public final String artKey;
    public final long durationMs;
    public final long progressMs;
    public final boolean playing;
    /** {@link System#nanoTime()} when this snapshot was taken, used to interpolate progress. */
    public final long stampNanos;

    public Track(String id, String title, String artist, String artKey,
                 long durationMs, long progressMs, boolean playing, long stampNanos) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.artKey = artKey;
        this.durationMs = durationMs;
        this.progressMs = progressMs;
        this.playing = playing;
        this.stampNanos = stampNanos;
    }

    /** Playback position right now, extrapolated from the last poll. */
    public long currentProgressMs() {
        if (!playing) return Math.min(progressMs, durationMs);
        long elapsed = (System.nanoTime() - stampNanos) / 1_000_000L;
        return Math.min(progressMs + elapsed, durationMs);
    }

    public long remainingMs() {
        return Math.max(0, durationMs - currentProgressMs());
    }

    public static String formatTime(long millis) {
        long totalSeconds = Math.max(0, millis) / 1000L;
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }
}
