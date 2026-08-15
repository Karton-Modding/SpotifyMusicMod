package dev.karton.spotifywidget.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/** Opening a screen moved from Minecraft to Gui in 26.2. */
public final class Screens {
    private Screens() {
    }

    public static void open(Screen screen) {
        Minecraft minecraft = Minecraft.getInstance();
        //? if >=26.2 {
        minecraft.gui.setScreen(screen);
        //?} else
        /*minecraft.setScreen(screen);*/
    }
}
