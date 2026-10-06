package org.gtlcore.gtlcore.mixin.gtm.ae.slot;

import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.api.recipe.ingredient.AEItemRecipeHandler;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import appeng.api.stacks.AEItemKey;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenCustomHashMap;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import java.util.*;

/** Native buffered AE slots expose exact component keys and long amounts to recipe search. */
@Mixin(ExportOnlyAEItemList.class)
public abstract class ExportOnlyAEItemListMixin extends NotifiableItemStackHandler implements IOptimizedMEList {
    @Shadow(remap = false) protected ExportOnlyAEItemSlot[] inventory;
    @Unique private final Object2LongMap<ItemStack> gtlcore$amounts = new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
    @Unique private boolean gtlcore$changed = true;
    protected ExportOnlyAEItemListMixin(int slots, IO handlerIO, IO capabilityIO) { super(slots, handlerIO, capabilityIO); }
    @Override public boolean getChanged() { return gtlcore$changed; }
    @Override public void setChanged(boolean changed) { gtlcore$changed = changed; }
    @Override public @Nullable Object2LongMap<ItemStack> getMEItemMap() {
        if (gtlcore$changed) {
            gtlcore$changed = false; gtlcore$amounts.clear();
            for (var slot : inventory) {
                var stock = slot.getStock();
                if (stock == null || stock.amount() <= 0 || !(stock.what() instanceof AEItemKey key)) continue;
                var identity = key.toStack(); long current = gtlcore$amounts.getLong(identity);
                gtlcore$amounts.put(identity, current > Long.MAX_VALUE - stock.amount() ? Long.MAX_VALUE : current + stock.amount());
            }
        }
        return gtlcore$amounts.isEmpty() ? null : gtlcore$amounts;
    }
    @Override public List<SizedIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
        return AEItemRecipeHandler.handleBuffered(io, recipe, left, simulate, inventory, getMachine());
    }
    @Override public void onContentsChanged() { gtlcore$changed = true; super.onContentsChanged(); }
    @Override public List<Object> getContents() {
        var amounts = getMEItemMap(); return amounts == null ? List.of() : new ArrayList<>(amounts.keySet());
    }
}
