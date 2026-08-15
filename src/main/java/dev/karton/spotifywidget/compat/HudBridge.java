package dev.karton.spotifywidget.compat;

import dev.karton.spotifywidget.SpotifyWidgetClient;
import dev.karton.spotifywidget.hud.WidgetRenderer;
import net.minecraft.resources.Identifier;
//? if >=1.21.6 {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
//?} else
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;*/

/** Hooks the widget into whichever HUD event the running game version offers. */
public final class HudBridge {
    private HudBridge() {
    }

    public static void register() {
        //? if >=1.21.6 {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(SpotifyWidgetClient.MOD_ID, "now_playing"),
                (context, tickCounter) -> WidgetRenderer.render(new Canvas(context)));
        //?} else
        /*HudRenderCallback.EVENT.register((context, tickCounter) -> WidgetRenderer.render(new Canvas(context)));*/
    }
}
