package org.gtlcore.gtlcore.network.packet;

import org.gtlcore.gtlcore.common.player.NoClipManager;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;

import com.glodblock.github.glodium.network.packet.IMessage;

public class SSetNoClip implements IMessage {

    private boolean enabled;

    public SSetNoClip() {}

    public SSetNoClip(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void toBytes(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(enabled);
    }

    @Override
    public void fromBytes(RegistryFriendlyByteBuf buf) {
        enabled = buf.readBoolean();
    }

    @Override
    public void onMessage(Player player) {
        NoClipManager.setEnabled(player, enabled);
    }

    @Override
    public net.minecraft.resources.ResourceLocation id() {
        return net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(org.gtlcore.gtlcore.network.GTLNetworkHandler.MOD_ID, "s_set_no_clip");
    }

    @Override
    public boolean isClient() {
        return true;
    }
}
