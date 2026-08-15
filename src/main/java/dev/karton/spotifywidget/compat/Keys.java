package dev.karton.spotifywidget.compat;

import com.mojang.blaze3d.platform.InputConstants;
import dev.karton.spotifywidget.SpotifyWidgetClient;
import net.minecraft.client.KeyMapping;
//? if >=1.21.9 {
import net.minecraft.resources.Identifier;
//?}
//? if >=26.1 {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;*/

/**
 * Key mappings moved from a category string to a {@code KeyMapping.Category} in 1.21.9, and the
 * Fabric helper was renamed in 26.1. Both are handled here.
 */
public final class Keys {
    //? if >=1.21.9 {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(SpotifyWidgetClient.MOD_ID, "controls"));
    //?} else
    /*private static final String CATEGORY = "key.categories.spotifywidget";*/

    private Keys() {
    }

    /** Creates and registers a binding. Pass {@link InputConstants#UNKNOWN} style -1 for unbound. */
    public static KeyMapping register(String translationKey, int glfwKey) {
        KeyMapping mapping = new KeyMapping(translationKey, InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
        //? if >=26.1 {
        return KeyMappingHelper.registerKeyMapping(mapping);
        //?} else
        /*return KeyBindingHelper.registerKeyBinding(mapping);*/
    }
}
