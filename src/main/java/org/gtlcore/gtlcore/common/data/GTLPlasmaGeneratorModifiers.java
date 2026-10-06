package org.gtlcore.gtlcore.common.data;

import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/** Optional reactor plasma is consumed by native recipe setup, never by parallel previews. */
public final class GTLPlasmaGeneratorModifiers {
    private GTLPlasmaGeneratorModifiers() {}

    public static RecipeModifier tiered(Map<Long, Supplier<FluidStack>> plasmas) {
        var choices = Map.copyOf(plasmas);
        return (machine, recipe) -> {
            if (!(machine instanceof WorkableElectricMultiblockMachine electric)) return ModifierFunction.NULL;
            return source -> {
                var plasma = choices.get(source.getOutputEUt().getTotalEU());
                var boosts = new ArrayList<FluidStack>();
                if (plasma != null) {
                    var fluid = plasma.get();
                    if (available(electric, fluid)) boosts.add(fluid.copy());
                }
                return apply(electric, source, boosts.isEmpty() ? 1 : 16, boosts);
            };
        };
    }

    public static RecipeModifier advanced(Supplier<FluidStack> starmetal, Supplier<FluidStack> denseNeutron) {
        return (machine, recipe) -> {
            if (!(machine instanceof WorkableElectricMultiblockMachine electric)) return ModifierFunction.NULL;
            return source -> {
                int parallel = 1;
                var boosts = new ArrayList<FluidStack>();
                var first = starmetal.get();
                if (available(electric, first)) { parallel = 8; boosts.add(first.copy()); }
                var second = denseNeutron.get();
                if (available(electric, second)) { parallel = 16; boosts.add(second.copy()); }
                return apply(electric, source, parallel, boosts);
            };
        };
    }

    private static boolean available(WorkableElectricMultiblockMachine machine, FluidStack fluid) {
        return !fluid.isEmpty() && RecipeRunnerHelper.matchRecipeInput(machine,
                GTRecipeBuilder.ofRaw().inputFluids(plasma(fluid)).build());
    }

    private static SizedFluidIngredient plasma(FluidStack fluid) {
        // MachineIO originally matched the exact plasma, including data components.
        return new SizedFluidIngredient(fluid.isComponentsPatchEmpty() ? FluidIngredient.single(fluid) :
                DataComponentFluidIngredient.of(true, fluid), fluid.getAmount());
    }

    private static GTRecipe apply(WorkableElectricMultiblockMachine machine, GTRecipe source,
                                  int limit, List<FluidStack> boosts) {
        var parallel = CoreRecipeModifiers.fastParallel(machine, source, limit, false).getFirst();
        if (parallel == null) return null;
        var result = GTLRecipeModifiers.standardOverclocking(machine, parallel);
        if (!boosts.isEmpty()) {
            // Add after parallel/OC: the original rate is one mB of each selected plasma per run.
            var inputs = new ArrayList<>(result.getInputContents(FluidRecipeCapability.CAP));
            inputs.addAll(GTRecipeBuilder.ofRaw().inputFluids(boosts.stream().map(GTLPlasmaGeneratorModifiers::plasma)
                            .toArray(SizedFluidIngredient[]::new)).build()
                    .getInputContents(FluidRecipeCapability.CAP));
            result.inputs.put(FluidRecipeCapability.CAP, inputs);
        }
        return result;
    }
}
