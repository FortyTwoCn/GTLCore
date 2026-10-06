package org.gtlcore.gtlcore.api.machine.trait;

import org.gtlcore.gtlcore.api.capability.IMERecipeHandler;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEFilterIOTrait;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

public class MEIORecipeHandlePart<T extends IMEFilterIOTrait> {

    public static final Comparator<MEIORecipeHandlePart<?>> COMPARATOR = Comparator.comparingInt(MEIORecipeHandlePart::getTotalPriority);

    protected final @NotNull T meTrait;
    protected final @NotNull IMERecipeHandler<SizedIngredient, ItemStack> itemHandler;
    protected final @NotNull IMERecipeHandler<SizedFluidIngredient, FluidStack> fluidHandler;
    protected final IMERecipeHandler<?, ?>[] handlers;

    public MEIORecipeHandlePart(@NotNull T meTrait, @NotNull IMERecipeHandler<SizedIngredient, ItemStack> itemHandler, @NotNull IMERecipeHandler<SizedFluidIngredient, FluidStack> fluidHandler) {
        this.itemHandler = itemHandler;
        this.fluidHandler = fluidHandler;
        this.handlers = new IMERecipeHandler[] { itemHandler, fluidHandler };
        this.meTrait = meTrait;
    }

    public static MEIORecipeHandlePart<IMEFilterIOTrait> of(IMEFilterIOPartMachine machine) {
        var meTrait = machine.getMETrait();
        var pair = machine.getMERecipeHandlerTraits();
        return new MEIORecipeHandlePart<>(meTrait, pair.left(), pair.right());
    }

    public boolean hasItemFilter() {
        return meTrait.hasItemFilter();
    }

    public boolean hasFluidFilter() {
        return meTrait.hasFluidFilter();
    }

    @SuppressWarnings("unchecked")
    public @NotNull <I, S> IMERecipeHandler<I, S> getMECapability(RecipeCapability<?> cap) {
        if (cap == ItemRecipeCapability.CAP) return (IMERecipeHandler<I, S>) itemHandler;
        else if (cap == FluidRecipeCapability.CAP) return (IMERecipeHandler<I, S>) fluidHandler;
        else throw new AssertionError("Invalid recipe capability");
    }

    public Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> meHandleOutput(Reference2ObjectOpenHashMap<RecipeCapability<?>, List<Object>> contents, boolean simulate) {
        boolean hasOutput = false;
        for (var it = Reference2ObjectMaps.fastIterator(contents); it.hasNext();) {
            var entry = it.next();
            var content = entry.getValue();
            if (content.isEmpty()) {
                it.remove();
                continue;
            }
            var cap = entry.getKey();
            if (cap != ItemRecipeCapability.CAP && cap != FluidRecipeCapability.CAP) continue;
            var result = meHandleOutput(cap, content, simulate);
            if (!result.equals(content)) {
                hasOutput = true;
                if (result.isEmpty()) it.remove();
                else entry.setValue(result);
            }
        }
        if (!simulate && hasOutput) meTrait.notifySelfIO();
        return contents;
    }

    @SuppressWarnings("unchecked")
    public <I> List<Object> meHandleOutput(RecipeCapability<?> cap, List<I> content, boolean simulate) {
        var meHandler = getMECapability(cap);
        return (List<Object>) (Object) meHandler.meHandleRecipeOutput(content, simulate);
    }

    private int getTotalPriority() {
        return itemHandler.getPriority() + fluidHandler.getPriority();
    }
}
