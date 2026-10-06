package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.jei.NativeRecipeEnergy;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeRecipeEnergyTest {
    @Test void maximumVoltageAmperageAndDurationDoNotOverflowTheNativeViewer() {
        var recipe = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_viewer_max_energy")
                .EUt(Long.MAX_VALUE, Long.MAX_VALUE).duration(Integer.MAX_VALUE).build();
        var summary = NativeRecipeEnergy.summarize(recipe);
        var rate = BigInteger.valueOf(Long.MAX_VALUE).pow(2);
        assertEquals(rate, summary.perTick());
        assertEquals(rate.multiply(BigInteger.valueOf(Integer.MAX_VALUE)), summary.total());
        assertTrue(summary.input()); assertTrue(summary.amperage().signum() > 0);
        assertEquals(Integer.MAX_VALUE, recipe.duration);
        assertFalse(NativeRecipeEnergy.compact(summary.total()).contains("∞"));
    }
    @Test void computationTotalIncludesDurationAndSumsLargeRequirementsWithoutIntOverflow() {
        var recipe = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_viewer_cwu_energy")
                .EUt(32, 2).duration(100).build();
        recipe.data.putBoolean("duration_is_total_cwu", true);
        recipe.tickInputs.put(CWURecipeCapability.CAP, List.of(new Content(5, 10000, 10000)));
        assertEquals(BigInteger.valueOf(1280), NativeRecipeEnergy.summarize(recipe).total());
        recipe.tickInputs.put(CWURecipeCapability.CAP, List.of(new Content(Integer.MAX_VALUE, 10000, 10000), new Content(Integer.MAX_VALUE, 10000, 10000)));
        assertEquals(BigInteger.ZERO, NativeRecipeEnergy.summarize(recipe).total());
        recipe.tickInputs.put(CWURecipeCapability.CAP, List.of(new Content(0, 10000, 10000)));
        assertEquals(BigInteger.valueOf(6400), NativeRecipeEnergy.summarize(recipe).total());
    }
    @Test void nativeGenerationDirectionAndExactCompactIntegersRemainDistinct() {
        var builder = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_viewer_generation")
                .EUt(-128, 3).duration(20);
        // Furnace builders begin with an input EU default; a generator has only the output route.
        builder.tickInput.remove(EURecipeCapability.CAP);
        var recipe = builder.build();
        var summary = NativeRecipeEnergy.summarize(recipe);
        assertFalse(summary.input()); assertFalse(summary.maximum());
        assertEquals(BigInteger.valueOf(7680), summary.total());
        assertEquals("999", NativeRecipeEnergy.compact(BigInteger.valueOf(999)));
        assertEquals("1K", NativeRecipeEnergy.compact(BigInteger.valueOf(1000)));
        assertEquals("-9.22E", NativeRecipeEnergy.compact(BigInteger.valueOf(Long.MIN_VALUE)));
    }
}
