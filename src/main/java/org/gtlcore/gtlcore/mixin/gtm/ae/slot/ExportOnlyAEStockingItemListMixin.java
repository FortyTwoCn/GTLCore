package org.gtlcore.gtlcore.mixin.gtm.ae.slot;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IMESlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.ingredient.AEItemRecipeHandler;
import org.gtlcore.gtlcore.config.ConfigHolder;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;

/** The pinned GT version uses a static list with an explicit owner, rather than this$0. */
@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine$ExportOnlyAEStockingItemList", remap = false)
public abstract class ExportOnlyAEStockingItemListMixin extends ExportOnlyAEItemList implements IOptimizedMEList {
    @Shadow @Nullable private MEStockingBusPartMachine stockingBusPartMachine;
    @Shadow public abstract void setStockingBusPartMachine(@Nullable MEStockingBusPartMachine machine);
    @Unique private final Object2LongMap<ItemStack> gtlcore$networkAmounts = new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
    @Unique private long gtlcore$snapshotTick = Long.MIN_VALUE;

    protected ExportOnlyAEStockingItemListMixin(int slots) { super(slots); }
    @Inject(method = "<init>(I)V", at = @At("TAIL"), require = 1)
    private void gtlcore$listenToConfiguration(int slots, CallbackInfo ci) {
        for (var slot : inventory) ((IMESlot) slot).setOnConfigChanged(this::onConfigChanged);
    }
    @Override public void clearInventory(int startIndex) {
        for (int i = Math.max(0, startIndex); i < inventory.length; i++) {
            ((IMESlot) inventory[i]).setConfigWithoutNotify(null); inventory[i].setStock(null);
        }
        onConfigChanged();
    }
    @Override public void onConfigChanged() {
        setChanged(true); gtlcore$snapshotTick = Long.MIN_VALUE;
        if (stockingBusPartMachine != null) setStockingBusPartMachine(stockingBusPartMachine);
        // SaveField decoding can restore new slot instances. Reattach their callbacks and their native owner.
        for (var slot : inventory) ((IMESlot) slot).setOnConfigChanged(this::onConfigChanged);
        onContentsChanged();
    }
    @Unique private @Nullable MEStorage gtlcore$network() {
        var owner = stockingBusPartMachine;
        var grid = owner == null ? null : owner.getMainNode().getGrid();
        return owner != null && owner.isWorkingEnabled() && owner.isOnline() && grid != null ? grid.getStorageService().getInventory() : null;
    }
    @Override public List<SizedIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
        var network = gtlcore$network();
        return network == null ? left : AEItemRecipeHandler.handleNetwork(io, recipe, left, simulate, inventory, getMachine(), network, stockingBusPartMachine.getActionSource());
    }
    @Override public @Nullable Object2LongMap<ItemStack> getMEItemMap() {
        var network = gtlcore$network();
        if (network == null) { gtlcore$networkAmounts.clear(); return null; }
        long tick = stockingBusPartMachine.getOffsetTimer();
        if (getChanged() || ConfigHolder.INSTANCE.enableUltimateMEStocking && gtlcore$snapshotTick != tick) {
            setChanged(false); gtlcore$snapshotTick = tick; gtlcore$networkAmounts.clear();
            Set<AEItemKey> queried = new HashSet<>();
            for (var slot : inventory) {
                var config = slot.getConfig();
                if (config == null || !(config.what() instanceof AEItemKey key) || !queried.add(key)) continue;
                long amount = network.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, stockingBusPartMachine.getActionSource());
                if (amount > 0) gtlcore$networkAmounts.put(key.toStack(), amount);
            }
        }
        return gtlcore$networkAmounts.isEmpty() ? null : gtlcore$networkAmounts;
    }
}
