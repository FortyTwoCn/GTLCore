package org.gtlcore.gtlcore.mixin.ae2;

import org.gtlcore.gtlcore.integration.ae2.FastInfinityCell;
import org.gtlcore.gtlcore.integration.ae2.InfinityCell;
import org.gtlcore.gtlcore.integration.ae2.storage.CellItemData;
import org.gtlcore.gtlcore.integration.ae2.storage.InfinityCellDataStorage;
import org.gtlcore.gtlcore.utils.StorageManager;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(AbstractContainerMenu.class)
public abstract class CursedInternalSlotMixin {
    @Shadow @Final public NonNullList<Slot> slots;
    @Shadow public abstract ItemStack getCarried();
    @Shadow public abstract void setCarried(ItemStack stack);

    @Inject(method = "doClick", at = @At("HEAD"), cancellable = true)
    private void gtlcore$cloneDisk(int slotIndex, int button, ClickType actionType, Player player, CallbackInfo ci) {
        if (actionType != ClickType.CLONE || !player.hasInfiniteMaterials() || !getCarried().isEmpty() ||
                slotIndex < 0 || slotIndex >= slots.size() || !(player.level() instanceof ServerLevel level)) return;
        var source = slots.get(slotIndex).getItem();
        boolean fast = source.getItem() instanceof FastInfinityCell;
        if (!(source.getItem() instanceof InfinityCell) && !fast) return;
        UUID sourceId = CellItemData.getDiskUUID(source);
        if (sourceId == null) return;
        var manager = StorageManager.getInstance(level.getServer());
        var storage = manager.getOrCreateDisk(sourceId, fast);
        UUID clonedId = UUID.randomUUID();
        // Keep native upgrades, fuzzy settings and names, but give the new cell independent persisted data.
        var clone = source.copyWithCount(source.getMaxStackSize());
        CustomData.update(DataComponents.CUSTOM_DATA, clone, tag -> tag.putUUID("diskuuid", clonedId));
        CellItemData.setStoredCount(clone, storage.totalAmount);
        manager.updateDisk(clonedId, new InfinityCellDataStorage(storage.stackKeys.copy(), storage.amounts.copy(),
                storage.totalAmount, storage.isFastCell));
        setCarried(clone);
        ci.cancel();
    }
}
