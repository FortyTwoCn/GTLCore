package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.NativeLargeBoilerFuel;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.LargeBoilerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeLargeBoilerFuelTest {
    @Test void originalNativeBoilerModifierAppliesThermalScalingOnceAndKeepsTheFuelRecipeImmutable() {
        var entry = GTMultiMachines.LARGE_BOILER_BRONZE;
        var machine = (LargeBoilerMachine) entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
        var source = GTRecipeTypes.LARGE_BOILER_RECIPES.recipeBuilder("native_boiler_pure_fuel").inputItems(Items.COAL).duration(10).build();
        int expected = 10 * 6400 / machine.getMaxTemperature();
        for (int attempt = 0; attempt < 10; attempt++) {
            var modified = LargeBoilerMachine.recipeModifier(machine, source).apply(source);
            assertNotSame(source, modified); assertEquals(expected, modified.duration);
        }
        assertEquals(10, source.duration); assertEquals(100, machine.getThrottle());
    }

    @Test void subTickThermalFuelUsesExactRationalQuantitiesWithoutMutatingSourceContents() {
        var source = GTRecipeTypes.LARGE_BOILER_RECIPES.recipeBuilder("native_boiler_fractional_fuel").inputItems(Items.COAL, 6400).duration(1).build();
        var modified = NativeLargeBoilerFuel.thermalModifier(6401).apply(source);
        assertEquals(1, modified.duration);
        assertEquals(6401, ItemRecipeCapability.CAP.of(modified.getInputContents(ItemRecipeCapability.CAP).getFirst().content()).count());
        assertEquals(6400, ItemRecipeCapability.CAP.of(source.getInputContents(ItemRecipeCapability.CAP).getFirst().content()).count());
    }

    @Test void throttleSwitchPreservesBurnedFuelFractionAndNeverWrapsDurationOrProgress() {
        assertEquals(160, NativeLargeBoilerFuel.burnTicks(80, 50));
        assertEquals(320, NativeLargeBoilerFuel.burnTicks(80, 25));
        assertEquals(80, NativeLargeBoilerFuel.progress(40, 100, 50, 160));
        assertEquals(40, NativeLargeBoilerFuel.progress(80, 50, 100, 80));
        assertEquals(Integer.MAX_VALUE, NativeLargeBoilerFuel.burnTicks(Integer.MAX_VALUE, 25));
        assertEquals(Integer.MAX_VALUE, NativeLargeBoilerFuel.progress(Integer.MAX_VALUE, 100, 25, Integer.MAX_VALUE));
    }

    @Test void invalidServerThrottleAndMalformedThermalParametersAreBounded() {
        assertEquals(25, NativeLargeBoilerFuel.throttle(Integer.MIN_VALUE));
        assertEquals(100, NativeLargeBoilerFuel.throttle(Integer.MAX_VALUE));
        var source = GTRecipeTypes.LARGE_BOILER_RECIPES.recipeBuilder("native_boiler_invalid_fuel").duration(1).build();
        assertNull(NativeLargeBoilerFuel.thermalModifier(0).apply(source));
        source.duration = 0;
        assertNull(NativeLargeBoilerFuel.thermalModifier(6400).apply(source));
        assertEquals(4, NativeLargeBoilerFuel.burnTicks(1, 0));
    }
}
