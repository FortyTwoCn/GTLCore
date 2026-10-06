package org.gtlcore.gtlcore.mixin.gtm.ae.slot;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.ingredient.AEFluidRecipeHandler;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidParallelLogic;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import appeng.api.stacks.AEFluidKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

import java.util.*;

/** Direct AE quantities bypass the native delegate's int-sized display stack. */
@Mixin(ExportOnlyAEFluidList.class)
public abstract class ExportOnlyAEFluidListMixin extends NotifiableFluidTank implements IOptimizedMEList {
    @Shadow(remap = false) protected ExportOnlyAEFluidSlot[] inventory;
    @Unique private final Object2LongMap<FluidStack> gtlcore$amounts = LongFluidParallelLogic.newFluidAmountMap();
    @Unique private final List<FluidStack> gtlcore$displayFluids = new ArrayList<>();
    @Unique private boolean gtlcore$changed = true;

    protected ExportOnlyAEFluidListMixin(int slots, int capacity, IO io, IO capabilityIO) {
        super(slots, capacity, io, capabilityIO);
    }

    @Override public boolean getChanged() { return gtlcore$changed; }
    @Override public void setChanged(boolean changed) { gtlcore$changed = changed; }
    @Override public List<FluidStack> getFluidList() { return gtlcore$displayFluids; }

    @Override
    public Object2LongMap<FluidStack> getMEFluidMap() {
        if (gtlcore$changed) {
            gtlcore$changed = false;
            gtlcore$amounts.clear();
            gtlcore$displayFluids.clear();
            for (var slot : inventory) {
                var stock = slot.getStock();
                if (stock == null || stock.amount() <= 0 || !(stock.what() instanceof AEFluidKey key)) continue;
                var identity = key.toStack(1);
                long amount = gtlcore$amounts.getLong(identity);
                gtlcore$amounts.put(identity, amount > Long.MAX_VALUE - stock.amount() ?
                        Long.MAX_VALUE : amount + stock.amount());
            }
            for (var entry : gtlcore$amounts.object2LongEntrySet()) {
                gtlcore$displayFluids.add(entry.getKey().copyWithAmount((int) Math.min(Integer.MAX_VALUE, entry.getLongValue())));
            }
        }
        return gtlcore$amounts;
    }

    @Override public List<FluidStack> getMEFluidList() { getMEFluidMap(); return gtlcore$displayFluids; }

    @Override
    public List<SizedFluidIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe,
                                                        List<SizedFluidIngredient> left, boolean simulate) {
        return AEFluidRecipeHandler.handleBuffered(io, recipe, left, simulate, inventory, getMachine());
    }

    @Override
    public void onContentsChanged() {
        gtlcore$changed = true;
        super.onContentsChanged();
    }

    @Override public List<Object> getContents() { return new ArrayList<>(getMEFluidList()); }
}
