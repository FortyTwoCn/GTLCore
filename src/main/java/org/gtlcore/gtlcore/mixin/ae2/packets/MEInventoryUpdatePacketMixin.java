package org.gtlcore.gtlcore.mixin.ae2.packets;

import org.gtlcore.gtlcore.integration.ae2.common.IConfirmStartMenu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import appeng.core.AELog;
import appeng.core.network.clientbound.MEInventoryUpdatePacket;
import appeng.menu.me.common.GridInventoryEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.ArrayList;

@OnlyIn(Dist.CLIENT)
@Mixin(MEInventoryUpdatePacket.class)
public class MEInventoryUpdatePacketMixin {

    @Shadow(remap = false)
    @Final
    private int containerId;

    @Shadow(remap = false)
    @Final
    private boolean fullUpdate;

    @Final
    @Shadow(remap = false)
    private List<GridInventoryEntry> entries;

    @Final
    @Shadow(remap = false)
    private int encodedEntryCount;

    @Final
    @Shadow(remap = false)
    private RegistryFriendlyByteBuf encodedEntries;

    @Shadow(remap = false)
    private static ArrayList<GridInventoryEntry> decodeEntriesPayload(int count, RegistryFriendlyByteBuf buffer) {
        throw new AssertionError("Mixin shadow");
    }

    @Inject(method = "handleOnClient", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private void gtlcore$handleConfirmInventory(Player player, CallbackInfo ci) {
        if (player.containerMenu.containerId != containerId) return;
        if (!(player.containerMenu instanceof IConfirmStartMenu menu)) return;
        var clientRepo = menu.gtlcore$getClientRepo();
        if (clientRepo == null) {
            AELog.info("Ignoring ME inventory update packet because no client repo is available.");
            return;
        }
        var actualEntries = entries;
        if (actualEntries == null && encodedEntries != null) {
            actualEntries = decodeEntriesPayload(encodedEntryCount, encodedEntries);
        }
        clientRepo.handleUpdate(fullUpdate, actualEntries != null ? actualEntries : List.of());
        ci.cancel();
    }
}
