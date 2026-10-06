package org.gtlcore.gtlcore.api.machine.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidRecipeHandler;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntProviderFluidIngredient;
import com.gregtechceu.gtceu.api.transfer.fluid.CustomFluidTank;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Fixed native tanks with exact long storage; NeoForge capabilities expose bounded int-sized transfers. */
public class NativeLongFluidTank extends NotifiableFluidTank implements ILongFluidStorage {
    public NativeLongFluidTank(int slots, long capacity, IO io) {
        super(createStorages(slots, capacity), io);
        allowSameFluids = false;
    }

    private static List<CustomFluidTank> createStorages(int slots, long capacity) {
        if (slots <= 0 || capacity < 0) throw new IllegalArgumentException("Invalid long tank size");
        var tanks = new ArrayList<CustomFluidTank>(slots);
        for (int i = 0; i < slots; i++) tanks.add(new LongTank(capacity));
        return tanks;
    }

    public LongTank longTank(int tank) { return (LongTank) storages[tank]; }

    @Override public long gtlcore$getStoredAmount(int tank) { return longTank(tank).amount; }

    @Override public double getTotalContentAmount() {
        double total = 0;
        for (int i = 0; i < getTanks(); i++) total += gtlcore$getStoredAmount(i);
        return total;
    }

    @Override
    public List<SizedFluidIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe,
                                                       List<SizedFluidIngredient> left, boolean simulate) {
        if (io != handlerIO || (io != IO.IN && io != IO.OUT)) return left;
        FluidStack[] types = new FluidStack[getTanks()];
        long[] amounts = new long[getTanks()];
        for (int i = 0; i < getTanks(); i++) {
            types[i] = longTank(i).getFluid();
            amounts[i] = gtlcore$getStoredAmount(i);
        }
        boolean changed = false;
        for (var iterator = left.listIterator(); iterator.hasNext();) {
            var ingredient = iterator.next();
            var matching = LongFluidIngredient.matchingIngredient(ingredient);
            FluidStack[] fluids;
            long remaining;
            if (matching instanceof IntProviderFluidIngredient provider && simulate) {
                fluids = new FluidStack[] { provider.getMaxSizeStack() };
                remaining = provider.getMaxRoll();
            } else {
                fluids = ingredient.getFluids();
                remaining = LongFluidIngredient.amount(ingredient);
            }
            if (fluids.length == 0 || fluids[0].isEmpty()) { iterator.remove(); continue; }
            FluidStack output = fluids[0].copy();
            if (io == IO.OUT && recipe != null) recipe.mutateOutput(output);
            int existing = -1;
            if (io == IO.OUT && !allowSameFluids) {
                for (int i = 0; i < getTanks(); i++) {
                    if (amounts[i] > 0 && FluidStack.isSameFluidSameComponents(types[i], output)) {
                        existing = i;
                        break;
                    }
                }
            }
            for (int i = 0; i < getTanks() && remaining > 0; i++) {
                if (existing >= 0 && i != existing) continue;
                long moved;
                if (io == IO.IN) {
                    if (amounts[i] == 0 || !matching.test(types[i])) continue;
                    moved = Math.min(amounts[i], remaining);
                    amounts[i] -= moved;
                    if (!simulate && getMachine() != null) LongFluidRecipeHandler.recordConsumed(getMachine(), recipe,
                            LongFluidIngredient.create(types[i], moved));
                } else {
                    if (!longTank(i).isFluidValid(output) ||
                            (amounts[i] > 0 && !FluidStack.isSameFluidSameComponents(types[i], output))) continue;
                    moved = Math.min(longTank(i).longCapacity - amounts[i], remaining);
                    if (moved <= 0) continue;
                    types[i] = output;
                    amounts[i] += moved;
                }
                remaining -= moved;
                changed |= moved > 0;
                if (io == IO.OUT && !allowSameFluids) break;
            }
            if (remaining <= 0) iterator.remove();
            else if (matching instanceof IntProviderFluidIngredient provider)
                iterator.set(new SizedFluidIngredient(provider.getInner(), Math.toIntExact(remaining)));
            else iterator.set(LongFluidIngredient.withAmount(ingredient, remaining));
        }
        if (!simulate && changed) {
            for (int i = 0; i < getTanks(); i++) longTank(i).setFluidAmount(types[i], amounts[i], false);
            onContentsChanged();
        }
        return left;
    }

    /** Native component-bearing fluid identity and the long count are saved together. */
    public static final class LongTank extends CustomFluidTank {
        private final long longCapacity;
        private long amount;

        public LongTank(long capacity) {
            super((int) Math.min(Integer.MAX_VALUE, capacity));
            if (capacity < 0) throw new IllegalArgumentException("Negative fluid capacity");
            longCapacity = capacity;
        }

        public long getLongCapacity() { return longCapacity; }
        public long getLongAmount() { return amount; }

        @Override public FluidStack getFluid() {
            return amount == 0 ? FluidStack.EMPTY : fluid.copyWithAmount((int) Math.min(Integer.MAX_VALUE, amount));
        }
        @Override public int getFluidAmount() { return (int) Math.min(Integer.MAX_VALUE, amount); }
        @Override public int getSpace() { return (int) Math.min(Integer.MAX_VALUE, longCapacity - amount); }
        @Override public boolean isEmpty() { return amount == 0; }
        @Override public void setFluid(FluidStack stack) { setFluidAmount(stack, stack.getAmount(), true); }

        public void setFluidAmount(FluidStack stack, long count) { setFluidAmount(stack, count, true); }

        private void setFluidAmount(FluidStack stack, long count, boolean notify) {
            if (count < 0 || count > longCapacity || (count > 0 && stack.isEmpty()))
                throw new IllegalArgumentException("Invalid stored fluid amount " + count);
            fluid = count == 0 ? FluidStack.EMPTY : stack.copyWithAmount(1);
            amount = count;
            if (notify) onContentsChanged();
        }

        public long fillLong(FluidStack resource, long requested, FluidAction action) {
            if (requested <= 0 || resource.isEmpty() || !isFluidValid(resource) ||
                    (amount > 0 && !FluidStack.isSameFluidSameComponents(fluid, resource))) return 0;
            long filled = Math.min(requested, longCapacity - amount);
            if (filled > 0 && action.execute()) setFluidAmount(resource, amount + filled, true);
            return filled;
        }

        @Override public int fill(FluidStack resource, FluidAction action) {
            return (int) fillLong(resource, resource.getAmount(), action);
        }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !FluidStack.isSameFluidSameComponents(fluid, resource)) return FluidStack.EMPTY;
            return drain(resource.getAmount(), action);
        }

        @Override public FluidStack drain(int maximum, FluidAction action) {
            int drained = (int) Math.min(amount, Math.max(0, maximum));
            if (drained == 0) return FluidStack.EMPTY;
            FluidStack result = fluid.copyWithAmount(drained);
            if (action.execute()) setFluidAmount(fluid, amount - drained, true);
            return result;
        }

        @Override public CompoundTag writeToNBT(HolderLookup.Provider registries, CompoundTag tag) {
            if (amount > 0) tag.put("Fluid", fluid.save(registries));
            else tag.remove("Fluid");
            tag.putLong("LongAmount", amount);
            return tag;
        }

        @Override public LongTank readFromNBT(HolderLookup.Provider registries, CompoundTag tag) {
            FluidStack saved = FluidStack.parseOptional(registries, tag.getCompound("Fluid"));
            setFluidAmount(saved, tag.getLong("LongAmount"), false);
            onContentsChanged();
            return this;
        }
    }
}
