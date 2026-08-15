package dev.karton.spotifywidget.mixin;

import dev.karton.spotifywidget.game.JukeboxWatcher;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A jukebox keeps its song on the server, so the client only finds out through level events 1010
 * (record started) and 1011 (record stopped). This picks those up as the game handles them.
 */
@Mixin(ClientPacketListener.class)
public class LevelEventMixin {
    @Inject(method = "handleLevelEvent", at = @At("TAIL"))
    private void spotifywidget$jukeboxEvent(ClientboundLevelEventPacket packet, CallbackInfo info) {
        JukeboxWatcher.onLevelEvent(packet.getType(), packet.getPos(), packet.getData());
    }
}
