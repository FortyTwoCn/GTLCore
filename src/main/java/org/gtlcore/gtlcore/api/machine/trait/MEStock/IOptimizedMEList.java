package org.gtlcore.gtlcore.api.machine.trait.MEStock;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import javax.annotation.Nullable;

public interface IOptimizedMEList {

    default @Nullable Object2LongMap<ItemStack> getMEItemMap() {
        return null;
    }

    /** Native fluid stacks expose an int display amount; AE quantities remain in the map value. */
    default @Nullable Object2LongMap<FluidStack> getMEFluidMap() {
        return null;
    }

    default @NotNull List<FluidStack> getMEFluidList() {
        return List.of();
    }

    default Object2LongOpenHashMap<ItemStack> getItemMap() {
        return null;
    }

    default @NotNull List<FluidStack> getFluidList() {
        return List.of();
    }

    default void setChanged(boolean value) {}

    default boolean getChanged() {
        return false;
    }

    default void onConfigChanged() {}

    default boolean isStocking() {
        return false;
    }
}
