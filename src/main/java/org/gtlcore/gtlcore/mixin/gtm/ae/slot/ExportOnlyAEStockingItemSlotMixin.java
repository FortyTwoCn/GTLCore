package org.gtlcore.gtlcore.mixin.gtm.ae.slot;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IMESlot;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;
import net.minecraft.world.item.ItemStack;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine$ExportOnlyAEStockingItemSlot", remap = false)
public abstract class ExportOnlyAEStockingItemSlotMixin extends ExportOnlyAEItemSlot implements IMESlot {
    @Shadow @Nullable private MEStockingBusPartMachine stockingBusPartMachine;
    @Unique private Runnable gtlcore$configChanged = () -> {};
    @Override public Runnable getOnConfigChanged() { return gtlcore$configChanged; }
    @Override public void setOnConfigChanged(Runnable callback) { gtlcore$configChanged = callback == null ? () -> {} : callback; }
    @Override public void setConfig(@Nullable GenericStack config) { super.setConfig(config); gtlcore$configChanged.run(); }
    @Override public void setConfigWithoutNotify(@Nullable GenericStack config) { this.config = config; }
    /** @author GTLCore @reason Native three-argument capability, working gates and nonnegative visual quantities. */
    @Overwrite public ItemStack extractItem(int slot, int amount, boolean simulate) {
        var owner = stockingBusPartMachine;
        if (slot != 0 || amount <= 0 || owner == null || !owner.isWorkingEnabled() || !owner.isOnline() ||
                config == null || !(config.what() instanceof AEItemKey key)) return ItemStack.EMPTY;
        var grid = owner.getMainNode().getGrid(); if (grid == null) return ItemStack.EMPTY;
        long extracted = grid.getStorageService().getInventory().extract(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, owner.getActionSource());
        if (extracted <= 0) return ItemStack.EMPTY;
        if (!simulate) {
            boolean emptyDisplay = stock == null;
            long remaining = stock == null || !stock.what().equals(key) ? 0 : Math.max(0, stock.amount() - extracted);
            setStock(remaining == 0 ? null : new GenericStack(key, remaining));
            if (emptyDisplay) getOnContentsChanged().run();
        }
        return key.toStack((int) extracted);
    }
}
