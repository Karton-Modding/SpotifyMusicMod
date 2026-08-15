package dev.karton.spotifywidget;

import dev.karton.spotifywidget.compat.HudBridge;
import dev.karton.spotifywidget.config.HudConfig;
import dev.karton.spotifywidget.game.JukeboxWatcher;
import dev.karton.spotifywidget.hud.CoverArt;
import dev.karton.spotifywidget.input.MediaKeys;
import dev.karton.spotifywidget.media.Media;
import dev.karton.spotifywidget.media.MediaControl;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpotifyWidgetClient implements ClientModInitializer {
    public static final String MOD_ID = "spotifywidget";
    public static final Logger LOGGER = LoggerFactory.getLogger("Spotify Widget");

    @Override
    public void onInitializeClient() {
        HudConfig.get();
        HudBridge.register();
        MediaKeys.register();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            CoverArt.tick();
            MediaKeys.tick();
            JukeboxWatcher.tick();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            MediaControl.shutdown();
            Media.stop();
        });
        Media.start();
    }
}
