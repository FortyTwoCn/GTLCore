package org.gtlcore.gtlcore.common.data;

import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.recipe.*;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.steam.LargeSteamParallelMultiblockMachine;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IOverclockMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.*;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.recipe.modifier.*;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GCYMRecipeTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import java.math.BigInteger;
import java.util.*;

/** Production machine modifiers use GTCEu 8's native two-argument modifier API. */
public final class GTLRecipeModifiers {
    private GTLRecipeModifiers() {}
    public static final RecipeModifier GCYM_REDUCTION = (machine, recipe) -> input -> reduction(machine, input, .8, .6);
    public static final RecipeModifier GENERATOR_OC = (machine, recipe) ->
            machine instanceof WorkableElectricMultiblockMachine electric ?
                    input -> standardOverclocking(electric, input) : ModifierFunction.NULL;
    public static final RecipeModifier LARGE_STEAM_OC = (machine, recipe) -> LargeSteamParallelMultiblockMachine.recipeModifier(machine, recipe, .5);
    public static final RecipeModifier STEAM_OC = (machine, recipe) -> LargeSteamParallelMultiblockMachine.recipeModifier(machine, recipe, 1);
    public static final RecipeModifier COIL_PARALLEL = (machine, recipe) -> {
        if (!(machine instanceof CoilWorkableElectricMultiblockMachine coil)) return ModifierFunction.NULL;
        int limit = (int) Math.min(Integer.MAX_VALUE, Math.pow(2, coil.getCoilType().getCoilTemperature() / 900.0));
        return input -> CoreRecipeModifiers.accurateParallel(machine, input, limit, false).getFirst();
    };

    /** Apply the fixed speed reduction before calculating coil parallelism and native overclocking. */
    public static final RecipeModifier VACUUM_DRYING = (machine, recipe) -> {
        if (!(machine instanceof CoilWorkableElectricMultiblockMachine coil)) return ModifierFunction.NULL;
        int limit = (int) Math.min(Integer.MAX_VALUE, Math.pow(2, coil.getCoilType().getCoilTemperature() / 900.0));
        return input -> {
            var reduced = input.copy();
            reduced.duration = (int) Math.max(1, input.duration * .5);
            var parallel = CoreRecipeModifiers.accurateParallel(machine, reduced, limit, false).getFirst();
            if (parallel == null) return null;
            return parallel.data.contains("ebf_temp") ? GTRecipeModifiers.ebfOverclock(machine, parallel).apply(parallel) :
                    OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK.getModifier(machine, parallel, coil.getOverclockVoltage()).apply(parallel);
        };
    };

    public static final RecipeModifier ENGRAVING_LASER = (machine, recipe) -> {
        if (!(machine instanceof WorkableElectricMultiblockMachine electric)) return ModifierFunction.NULL;
        boolean ordinary = electric.getRecipeType() == GTRecipeTypes.LASER_ENGRAVER_RECIPES.get();
        return input -> {
            var parallel = ordinary ? CoreRecipeModifiers.hatchParallel(machine, input).apply(input) : input;
            if (parallel == null) return null;
            var logic = ordinary ? OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK : OverclockingLogic.PERFECT_OVERCLOCK;
            return GTRecipeModifiers.ELECTRIC_OVERCLOCK.apply(logic).applyModifier(machine, parallel);
        };
    };

    public static final RecipeModifier MEGA_ALLOY_OVERCLOCK = (machine, recipe) ->
            machine instanceof CoilWorkableElectricMultiblockMachine coil &&
                    coil.getRecipeType() == GCYMRecipeTypes.ALLOY_BLAST_RECIPES.get() ?
                            GTRecipeModifiers.ebfOverclock(machine, recipe) : ModifierFunction.IDENTITY;

    public static final RecipeModifier DIMENSIONAL_MIXER = (machine, recipe) ->
            machine instanceof WorkableElectricMultiblockMachine electric &&
                    electric.getRecipeType() == GTRecipeTypes.MIXER_RECIPES.get() ?
                            input -> reduction(machine, input, 1, .2) : ModifierFunction.IDENTITY;
    public static ModifierFunction chemicalPlantOverclock(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof CoilWorkableElectricMultiblockMachine coil)) return ModifierFunction.NULL;
        return input -> {
            double factor = 1 - coil.getCoilTier() * .05;
            var reduced = reduction(machine, input, factor * .8, factor * .6);
            if (reduced == null) return null;
            var parallel = GTRecipeModifiers.hatchParallel(machine, reduced).apply(reduced);
            return parallel == null ? null : OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK.getModifier(machine, parallel, coil.getOverclockVoltage()).apply(parallel);
        };
    }
    public static ModifierFunction processingPlantOverclock(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof WorkableElectricMultiblockMachine electric)) return ModifierFunction.NULL;
        return input -> {
            var reduced = reduction(machine, input, .9, .6);
            if (reduced == null) return null;
            var parallel = CoreRecipeModifiers.accurateParallel(machine, reduced, 4 * electric.getTier() - 1, false).getFirst();
            return parallel == null ? null : OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK.getModifier(machine, parallel, electric.getOverclockVoltage()).apply(parallel);
        };
    }
    public static ModifierFunction nanoForgeOverclock(MetaMachine machine, GTRecipe recipe, int tier) {
        if (!(machine instanceof StorageMachine storage)) return ModifierFunction.NULL;
        int required = recipe.data.getInt("nano_forge_tier");
        if (required > tier) return ModifierFunction.cancel(Component.translatable("gtceu.recipe.fail.nano_forge.tier"));
        var catalyst = storage.getMachineStorageItem();
        String id = BuiltInRegistries.ITEM.getKey(catalyst.getItem()).toString();
        String expected = switch (tier) {
            case 1 -> "gtceu:carbon_nanoswarm";
            case 2 -> "gtceu:neutronium_nanoswarm";
            case 3 -> "gtceu:draconium_nanoswarm";
            default -> null;
        };
        if (expected != null && !expected.equals(id)) return ModifierFunction.cancel(Component.translatable(switch (tier) {
            case 1 -> "message.gtlcore.need_carbon_nano_swarm";
            case 2 -> "message.gtlcore.need_neutronium_nano_swarm";
            default -> "message.gtlcore.need_dragon_nano_swarm";
        }));
        int maximum = (int) Math.min(Integer.MAX_VALUE, Math.min(catalyst.getCount(), 64) * Math.pow(2, tier - required));
        return input -> {
            var parallel = CoreRecipeModifiers.accurateParallel(machine, input, maximum, false).getFirst();
            var logic = OverclockingLogic.create(1 / Math.pow(2, 1 + tier - required), 4, false);
            return parallel == null ? null : logic.getModifier(machine, parallel, storage.getOverclockVoltage()).apply(parallel);
        };
    }
    public static ModifierFunction dissolvingTankOverclock(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof WorkableElectricMultiblockMachine electric)) return ModifierFunction.NULL;
        var inputs = recipe.getInputContents(FluidRecipeCapability.CAP);
        if (inputs.size() < 2) return ModifierFunction.NULL;
        var first = FluidRecipeCapability.CAP.of(inputs.get(0).content());
        var second = FluidRecipeCapability.CAP.of(inputs.get(1).content());
        var firstFluids = first.getFluids(); var secondFluids = second.getFluids();
        if (firstFluids.length == 0 || secondFluids.length == 0) return ModifierFunction.NULL;
        if (!RecipeRunnerHelper.matchRecipeInput(electric, recipe)) return ModifierFunction.NULL;
        var amounts = countFluidAmounts(electric, recipe, firstFluids[0].getFluid(), secondFluids[0].getFluid());
        if (amounts.first == 0) return ModifierFunction.NULL;
        boolean ratio = BigInteger.valueOf(amounts.second).multiply(BigInteger.valueOf(LongFluidIngredient.amount(first))).equals(
                BigInteger.valueOf(amounts.first).multiply(BigInteger.valueOf(LongFluidIngredient.amount(second))));
        return input -> {
            var parallel = GTRecipeModifiers.hatchParallel(machine, input).apply(input);
            if (parallel == null) return null;
            var result = OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK.getModifier(machine, parallel, electric.getOverclockVoltage()).apply(parallel);
            if (result == null) return null;
            if (!ratio) {
                RecipeResult.ofWorking(electric, RecipeResult.fail(Component.translatable("gtceu.recipe.fail.no.ratio")));
                result.outputs.clear();
            } else RecipeResult.ofWorking(electric, RecipeResult.SUCCESS);
            return result;
        };
    }
    public static GTRecipe reduction(MetaMachine machine, GTRecipe recipe, double energy, double duration) {
        if (!(machine instanceof IOverclockMachine overclock)) return recipe;
        if (RecipeHelper.getRecipeEUtTier(recipe) > overclock.getMaxOverclockTier()) return null;
        var result = recipe.copy();
        if (energy != 1) EURecipeCapability.putEUContent(result.tickInputs, new EnergyStack((long) Math.max(1, recipe.getInputEUt().getTotalEU() * energy)));
        if (duration != 1) result.duration = (int) Math.max(1, recipe.duration * duration);
        RecipeMultiplierTracker.captureReduction(machine, recipe, energy, duration);
        return result;
    }
    public static int getHatchParallel(MetaMachine machine) {
        return machine instanceof MultiblockControllerMachine controller && controller.isFormed() ?
                controller.getParallelHatch().map(hatch -> hatch.getCurrentParallel()).orElse(1) : 1;
    }
    public static GTRecipe standardOverclocking(WorkableElectricMultiblockMachine machine, GTRecipe recipe) {
        double duration = recipe.duration, voltage = recipe.getOutputEUt().getTotalEU();
        for (int remaining = 16; remaining > 0; remaining--) {
            double nextVoltage = voltage * 4, nextDuration = duration / 4;
            if (nextVoltage > machine.getOverclockVoltage() || nextDuration < 1) break;
            duration = nextDuration; voltage = nextVoltage;
        }
        var result = recipe.copy(); result.duration = (int) duration;
        EURecipeCapability.putEUContent(result.tickOutputs, new EnergyStack((long) voltage));
        return result;
    }
    private static FluidAmounts countFluidAmounts(WorkableElectricMultiblockMachine machine, GTRecipe recipe, Fluid first, Fluid second) {
        var core = (IRecipeCapabilityMachine) machine;
        var handle = core.getActiveRecipeHandle(recipe);
        if (handle instanceof MEPatternRecipeHandlePart me) return count(me.getMEContent(FluidRecipeCapability.CAP, recipe), first, second);
        if (handle instanceof RecipeHandlePart part) {
            var amounts = count(part.getContentWithShared(FluidRecipeCapability.CAP, false), first, second);
            var shared = core.getSharedRecipeHandlePart();
            if (core.isDistinct() && shared != null && shared != part) return amounts.add(count(shared.getSelfContent(FluidRecipeCapability.CAP, false), first, second));
            return amounts;
        }
        long a = 0, b = 0;
        for (var handler : machine.getCapabilitiesFlat(IO.IN, FluidRecipeCapability.CAP)) for (var content : handler.getContents()) if (content instanceof FluidStack stack) {
            if (stack.getFluid() == first) a = saturatingAdd(a, stack.getAmount());
            if (stack.getFluid() == second) b = saturatingAdd(b, stack.getAmount());
        }
        return new FluidAmounts(a, b);
    }
    private static FluidAmounts count(it.unimi.dsi.fastutil.objects.Object2LongMap<FluidStack> contents, Fluid first, Fluid second) {
        long a = 0, b = 0;
        for (var entry : contents.object2LongEntrySet()) {
            if (entry.getKey().getFluid() == first) a = saturatingAdd(a, entry.getLongValue());
            if (entry.getKey().getFluid() == second) b = saturatingAdd(b, entry.getLongValue());
        }
        return new FluidAmounts(a, b);
    }
    private static long saturatingAdd(long a, long b) { return a > Long.MAX_VALUE - b ? Long.MAX_VALUE : a + b; }
    private record FluidAmounts(long first, long second) { FluidAmounts add(FluidAmounts other) { return new FluidAmounts(saturatingAdd(first, other.first), saturatingAdd(second, other.second)); } }
}
