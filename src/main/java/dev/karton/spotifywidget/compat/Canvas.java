package dev.karton.spotifywidget.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.resources.Identifier;
//? if >=26.1 {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else
/*import net.minecraft.client.gui.GuiGraphics;*/
//? if >=1.21.6 {
import net.minecraft.client.renderer.RenderPipelines;
//?} elif >=1.21.2 {
/*import net.minecraft.client.renderer.RenderType;
*///?}

/**
 * Thin wrapper over the vanilla draw context. Everything that Mojang renamed or reshaped between
 * 1.21.1 and 26.2 is handled here, so the widget code itself stays version independent.
 */
public final class Canvas {
    //? if >=26.1 {
    private final GuiGraphicsExtractor ctx;

    public Canvas(GuiGraphicsExtractor ctx) {
        this.ctx = ctx;
    }
    //?} else {
    /*private final GuiGraphics ctx;

    public Canvas(GuiGraphics ctx) {
        this.ctx = ctx;
    }
    *///?}

    private static Font font() {
        return Minecraft.getInstance().font;
    }

    public int screenWidth() {
        return ctx.guiWidth();
    }

    public int screenHeight() {
        return ctx.guiHeight();
    }

    public void rect(int x, int y, int width, int height, int argb) {
        if (width <= 0 || height <= 0) return;
        ctx.fill(x, y, x + width, y + height, argb);
    }

    public void text(String value, int x, int y, int argb, boolean shadow) {
        //? if >=26.1 {
        ctx.text(font(), value, x, y, argb, shadow);
        //?} else
        /*ctx.drawString(font(), value, x, y, argb, shadow);*/
    }

    /** Draws a square texture scaled into {@code width} x {@code height}. */
    public void texture(Identifier texture, int x, int y, int width, int height, int textureSize, float alpha) {
        int tint = tint(alpha);
        //? if >=1.21.6 {
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, 0.0F, 0.0F,
                width, height, textureSize, textureSize, textureSize, textureSize, tint);
        //?} elif >=1.21.2 {
        /*ctx.blit(RenderType::guiTextured, texture, x, y, 0.0F, 0.0F,
                width, height, textureSize, textureSize, textureSize, textureSize, tint);
        *///?} else {
        /*ctx.setColor(1.0F, 1.0F, 1.0F, alpha);
        ctx.blit(texture, x, y, width, height, 0.0F, 0.0F, textureSize, textureSize, textureSize, textureSize);
        ctx.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        *///?}
    }

    public void push(float scale) {
        //? if >=1.21.6 {
        ctx.pose().pushMatrix();
        if (scale != 1.0F) ctx.pose().scale(scale, scale);
        //?} else {
        /*ctx.pose().pushPose();
        if (scale != 1.0F) ctx.pose().scale(scale, scale, 1.0F);
        *///?}
    }

    public void pop() {
        //? if >=1.21.6 {
        ctx.pose().popMatrix();
        //?} else
        /*ctx.pose().popPose();*/
    }

    public int textWidth(String value) {
        return font().width(value);
    }

    /** Shortens a string with an ellipsis so it fits into {@code maxWidth}. */
    public String trim(String value, int maxWidth) {
        Font font = font();
        if (font.width(value) <= maxWidth) return value;
        int ellipsis = font.width("...");
        StringBuilder builder = new StringBuilder();
        int width = 0;
        for (int i = 0; i < value.length(); i++) {
            int charWidth = font.width(String.valueOf(value.charAt(i)));
            if (width + charWidth + ellipsis > maxWidth) break;
            width += charWidth;
            builder.append(value.charAt(i));
        }
        return builder.append("...").toString();
    }

    /**
     * False in creative and spectator, where the game hides the health, hunger and experience
     * rows and slides the held item name 14 pixels down.
     */
    public static boolean statusBarsVisible() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.gameMode != null && minecraft.gameMode.canHurtPlayer();
    }

    /** True while any screen (inventory, chat, menus) is open. */
    public static boolean guiOpen() {
        //? if >=26.2 {
        return Minecraft.getInstance().gui.screen() != null;
        //?} else
        /*return Minecraft.getInstance().screen != null;*/
    }

    private static int tint(float alpha) {
        int value = Math.max(0, Math.min(255, Math.round(alpha * 255f)));
        return (value << 24) | 0xFFFFFF;
    }
}
