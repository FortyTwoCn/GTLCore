package org.gtlcore.gtlcore.mixin.modularui;

import brachy.modularui.network.ModularNetworkSide;
import brachy.modularui.value.sync.ModularSyncManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** A delayed close for the previous GUI must not close a newly opened cover menu. */
@Mixin(value = ModularNetworkSide.class, remap = false)
public abstract class MenuCloseMixin {
    @Shadow @Final
    private Int2ReferenceOpenHashMap<ModularSyncManager> activeScreens;

    @WrapOperation(method = "closeContainer(IZLnet/minecraft/world/entity/player/Player;Z)V",
            at = @At(value = "INVOKE", target = "Lbrachy/modularui/network/ModularNetworkSide;closeContainer(Lnet/minecraft/world/entity/player/Player;)V"),
            require = 1)
    private void gtlcore$closeMatchingMenu(ModularNetworkSide network, Player player, Operation<Void> original,
            int networkId, boolean dispose, Player requestedPlayer, boolean sync) {
        var manager = activeScreens.get(networkId);
        if (manager != null && manager.getMenu() == player.containerMenu) original.call(network, player);
    }
}
