package dev.karton.spotifywidget.media;

/** Owns the media source for the running operating system. */
public final class Media {
    private static MediaSource active;

    private Media() {
    }

    public static synchronized void start() {
        if (active != null) return;
        active = create();
        active.start();
    }

    public static synchronized void stop() {
        if (active != null) active.stop();
        active = null;
    }

    private static MediaSource create() {
        if (WindowsMediaSource.supported()) return new WindowsMediaSource();
        if (LinuxMediaSource.supported()) return new LinuxMediaSource();
        return new UnsupportedSource();
    }

    public static Track track() {
        MediaSource source = active;
        return source == null ? null : source.track();
    }

    public static String status() {
        MediaSource source = active;
        return source == null ? "Not started" : source.status();
    }

    private static final class UnsupportedSource implements MediaSource {
        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public Track track() {
            return null;
        }

        @Override
        public String status() {
            return "Unsupported system - the widget reads the player on Windows and Linux";
        }
    }
}
