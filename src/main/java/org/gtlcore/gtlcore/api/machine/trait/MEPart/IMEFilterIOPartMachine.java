package org.gtlcore.gtlcore.api.machine.trait.MEPart;

import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;

import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import net.neoforged.neoforge.fluids.FluidStack;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import it.unimi.dsi.fastutil.Pair;
import org.jetbrains.annotations.NotNull;

public interface IMEFilterIOPartMachine extends IMEIOPartMachine {

    Pair<IMERecipeHandlerTrait<SizedIngredient, ItemStack>, IMERecipeHandlerTrait<SizedFluidIngredient, FluidStack>> getMERecipeHandlerTraits();

    @NotNull
    IMEFilterIOTrait getMETrait();
}
