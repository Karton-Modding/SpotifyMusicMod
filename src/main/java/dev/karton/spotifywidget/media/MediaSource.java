package dev.karton.spotifywidget.media;

/** Something that can tell the widget what is playing. */
public interface MediaSource {
    void start();

    void stop();

    /** Latest known track, or null when nothing is playing. */
    Track track();

    /** Short line shown in the settings screen. */
    String status();
}
