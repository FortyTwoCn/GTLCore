package org.gtlcore.gtlcore.mixin.gtm.ae.slot;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IMESlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.ingredient.AEFluidRecipeHandler;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidParallelLogic;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.mixin.gtm.ae.machine.MEHatchPartMachineAccessor;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.*;

@Mixin(targets = "com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine$ExportOnlyAEStockingFluidList", remap = false)
public abstract class ExportOnlyAEStockingFluidListMixin extends ExportOnlyAEFluidList implements IOptimizedMEList {
    @Shadow @Final private MEStockingHatchPartMachine this$0;
    @Unique private final Object2LongMap<FluidStack> gtlcore$networkAmounts = LongFluidParallelLogic.newFluidAmountMap();
    @Unique private long gtlcore$snapshotTick = Long.MIN_VALUE;

    protected ExportOnlyAEStockingFluidListMixin(MetaMachine machine, int slots) { super(machine, slots); }

    @Inject(method = "<init>(Lcom/gregtechceu/gtceu/integration/ae2/machine/MEStockingHatchPartMachine;Lcom/gregtechceu/gtceu/api/machine/MetaMachine;I)V",
            at = @At("TAIL"), require = 1)
    private void gtlcore$listenToConfiguration(MEStockingHatchPartMachine owner, MetaMachine machine, int slots,
                                              CallbackInfo ci) {
        for (var slot : inventory) ((IMESlot) slot).setOnConfigChanged(this::onConfigChanged);
    }

    @Override
    public void clearInventory(int startIndex) {
        for (int i = Math.max(0, startIndex); i < getConfigurableSlots(); i++) {
            ((IMESlot) inventory[i]).setConfigWithoutNotify(null);
            inventory[i].setStock(null);
        }
        onConfigChanged();
    }

    @Override public void onConfigChanged() {
        setChanged(true); gtlcore$snapshotTick = Long.MIN_VALUE;
        for (var slot : inventory) ((IMESlot) slot).setOnConfigChanged(this::onConfigChanged);
        onContentsChanged();
    }

    @Unique private @Nullable MEStorage gtlcore$network() {
        var grid = this$0.getMainNode().getGrid();
        return this$0.isWorkingEnabled() && this$0.isOnline() && grid != null ? grid.getStorageService().getInventory() : null;
    }

    @Override
    public List<SizedFluidIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe,
                                                        List<SizedFluidIngredient> left, boolean simulate) {
        if (io != IO.IN || left.isEmpty() || !this$0.isWorkingEnabled()) return left;
        var grid = this$0.getMainNode().getGrid();
        if (grid == null || !this$0.isOnline()) return left;
        return AEFluidRecipeHandler.handleNetwork(io, recipe, left, simulate, inventory, getMachine(),
                grid.getStorageService().getInventory(), ((MEHatchPartMachineAccessor) this$0).getActionSource());
    }

    @Override
    public Object2LongMap<FluidStack> getMEFluidMap() {
        var network = gtlcore$network();
        var display = getFluidList();
        if (network == null) { gtlcore$networkAmounts.clear(); display.clear(); return gtlcore$networkAmounts; }
        long tick = this$0.getOffsetTimer();
        if (getChanged() || ConfigHolder.INSTANCE.enableUltimateMEStocking && gtlcore$snapshotTick != tick) {
            setChanged(false); gtlcore$snapshotTick = tick;
            gtlcore$networkAmounts.clear(); display.clear();
            var source = ((MEHatchPartMachineAccessor) this$0).getActionSource();
            Set<AEFluidKey> queried = new HashSet<>();
            for (var slot : inventory) {
                var config = slot.getConfig();
                if (config == null || !(config.what() instanceof AEFluidKey key) || !queried.add(key)) continue;
                long amount = network.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, source);
                if (amount > 0) {
                    gtlcore$networkAmounts.put(key.toStack(1), amount);
                    display.add(key.toStack((int) Math.min(Integer.MAX_VALUE, amount)));
                }
            }
        }
        return gtlcore$networkAmounts;
    }

    @Override public List<FluidStack> getMEFluidList() { getMEFluidMap(); return getFluidList(); }
}
