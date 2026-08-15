package dev.karton.spotifywidget.compat;

import com.mojang.blaze3d.platform.NativeImage;
import dev.karton.spotifywidget.SpotifyWidgetClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** Turns decoded ARGB pixels into a registered game texture. Render thread only. */
public final class Textures {
    private Textures() {
    }

    public static Identifier upload(String name, int[] argb, int size) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) return null;
        try {
            NativeImage image = new NativeImage(size, size, false);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int color = argb[y * size + x];
                    //? if >=1.21.2 {
                    image.setPixel(x, y, color);
                    //?} else
                    /*image.setPixelRGBA(x, y, toAbgr(color));*/
                }
            }
            //? if >=1.21.5 {
            DynamicTexture texture = new DynamicTexture(() -> name, image);
            //?} else
            /*DynamicTexture texture = new DynamicTexture(image);*/
            Identifier id = Identifier.fromNamespaceAndPath(SpotifyWidgetClient.MOD_ID, name);
            minecraft.getTextureManager().register(id, texture);
            return id;
        } catch (Exception e) {
            SpotifyWidgetClient.LOGGER.warn("Could not upload album art texture", e);
            return null;
        }
    }

    public static void release(Identifier id) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft != null) minecraft.getTextureManager().release(id);
    }

    /** Pre-1.21.2 NativeImage stores pixels as ABGR. */
    private static int toAbgr(int argb) {
        return (argb & 0xFF00FF00)
                | ((argb >> 16) & 0xFF)
                | ((argb & 0xFF) << 16);
    }
}
