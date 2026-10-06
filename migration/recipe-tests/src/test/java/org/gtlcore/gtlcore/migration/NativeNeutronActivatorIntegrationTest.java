package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.PatternMatchData;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.noenergy.NeutronActivatorMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeNeutronActivatorIntegrationTest {
    private static NeutronActivatorMachine machine() {
        var entry = NativeMachineFixtures.NEUTRON;
        return (NeutronActivatorMachine) entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
    }

    private static void charge(NeutronActivatorMachine machine, int charge) {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var saved = machine.saveWithoutMetadata(registries); saved.putInt("eV", charge);
        machine.loadAdditional(saved, registries);
        assertEquals(charge, machine.getEV());
    }

    private static GTRecipe recipe() {
        var recipe = GTLRecipeTypes.NEUTRON_ACTIVATOR_RECIPES.recipeBuilder("native_neutron_bounds").duration(20).build();
        recipe.data.putInt("ev_min", 10); recipe.data.putInt("ev_max", 20); recipe.data.putInt("evt", 1);
        return recipe;
    }

    @Test void originalStrictEnergyRangeUsesNativeFailuresAndPureModifiers() {
        var machine = machine(); var recipe = recipe();
        charge(machine, 10_000_000);
        assertNull(NeutronActivatorMachine.recipeModifier(machine, recipe).apply(recipe));
        charge(machine, 10_000_001);
        var result = NeutronActivatorMachine.recipeModifier(machine, recipe).apply(recipe);
        assertNotNull(result); assertEquals(20, result.duration);
        charge(machine, 20_000_000);
        assertNull(NeutronActivatorMachine.recipeModifier(machine, recipe).apply(recipe));
        assertEquals(20, recipe.duration); assertEquals(1, IGTRecipe.of(recipe).getRealParallels());
        // Multiplication of recipe MeV boundaries must not overflow an int into an accepted interval.
        recipe.data.putInt("ev_max", 5000); charge(machine, 20_000_000);
        assertNotNull(NeutronActivatorMachine.recipeModifier(machine, recipe).apply(recipe));
    }

    @Test void nativePipeCounterControlsSpeedAndClearsOnInvalidation() {
        var machine = machine(); var state = machine.getDefaultPatternState();
        state.setState(PatternState.CheckState.VALID_UNCACHED);
        var counter = PatternMatchData.of(state.getContext());
        for (int i = 0; i < 8; i++) counter.countBlock("SpeedPipe", new BlockPos(i, 0, 0));
        counter.countBlock("SpeedPipe", BlockPos.ZERO);
        machine.formStructure("main");
        assertTrue(machine.isFormed()); assertEquals(Math.pow(.95, 4), machine.getEfficiencyFactor());
        charge(machine, 15_000_000);
        var recipe = recipe();
        assertEquals(16, NeutronActivatorMachine.recipeModifier(machine, recipe).apply(recipe).duration);
        assertEquals(20, recipe.duration);
        machine.invalidateStructure("main");
        assertFalse(machine.isFormed()); assertEquals(1, machine.getEfficiencyFactor());
    }

    @Test void tickEnergyNeverWrapsForLargeParallelismAndInsufficientEnergyDoesNotDrain() {
        var machine = machine(); var recipe = recipe(); charge(machine, 1_000_000_000);
        IGTRecipe.of(recipe).setRealParallels(1_000_000_000L);
        assertTrue(machine.requiredEV(recipe) > Integer.MAX_VALUE);
        assertFalse(machine.beforeWorking(recipe));
        assertFalse(machine.getRecipeLogic().handleTickRecipe(recipe).isSuccess());
        assertEquals(1_000_000_000, machine.getEV());
        IGTRecipe.of(recipe).setRealParallels(1);
        assertEquals(1000, machine.requiredEV(recipe));
        assertTrue(machine.beforeWorking(recipe));
        assertEquals(1_000_000_000, machine.getEV());
    }
}
