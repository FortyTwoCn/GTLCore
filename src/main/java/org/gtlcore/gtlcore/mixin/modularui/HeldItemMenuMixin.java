package org.gtlcore.gtlcore.mixin.modularui;

import brachy.modularui.factory.GuiData;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.factory.inventory.InventoryTypes;
import brachy.modularui.screen.ModularContainerMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** MUI protects hotbar swaps; its vanilla fallback also needs to protect the offhand UI holder. */
@Mixin(value = ModularContainerMenu.class, remap = false)
public abstract class HeldItemMenuMixin {
    @Shadow public abstract GuiData getGuiData();

    @Inject(method = "clicked", at = @At("HEAD"), cancellable = true)
    private void gtlcore$protectOffhandHolder(int slot, int button, ClickType type, Player player, CallbackInfo callback) {
        if (type == ClickType.SWAP && button == Inventory.SLOT_OFFHAND &&
                getGuiData() instanceof PlayerInventoryGuiData<?> data &&
                data.getInventoryType() == InventoryTypes.PLAYER && data.getSlotIndex() == Inventory.SLOT_OFFHAND) {
            callback.cancel();
        }
    }
}
