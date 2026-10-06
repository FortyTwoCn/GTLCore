package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import java.util.Map;

/** The original advanced rig's thermal factors and exact long production. */
public final class AdvancedFluidDrillProduction {
    private static final Map<ResourceLocation, Integer> COILS = Map.of(
            id("titansteel"), 0, id("adamantine"), 1, id("naquadriatictaranium"), 2,
            id("starmetal"), 3, id("infinity"), 4, id("hypogen"), 6, id("eternity"), 8);

    public static int maxHeat(@Nullable ResourceLocation material) {
        Integer extra = material == null ? null : COILS.get(material);
        return extra == null ? -1 : 8000 + 1000 * extra;
    }

    public static int rate(int drillHeadRate, int heat, int maxHeat, int voltageTier) {
        if (drillHeadRate <= 0 || heat < 2000 || maxHeat <= 2000) return 0;
        double temperature = (heat - 2000.0) / (maxHeat - 2000.0);
        double thermal = temperature < 0.4 ? 0.3 * Math.sqrt(temperature / 0.4) :
                0.3 + 0.7 * Math.pow((temperature - 0.4) / 0.6, 2.5);
        double voltage = Math.max(0, Math.min((voltageTier - GTValues.UHV) / 5.0, 1.0));
        double voltageFactor = Math.pow(voltage, 0.6 * (1 - 0.2 * temperature)) * (0.95 + 0.05 * temperature);
        double overclock = (maxHeat - 12000) / 1000.0 * 0.125;
        return (int) Math.max(0, drillHeadRate * (1 + overclock) * thermal * voltageFactor);
    }

    public static @Nullable GTRecipe recipe(Map<Fluid, Long> fluids, int rate) {
        return recipe(fluids, (long) rate);
    }

    public static @Nullable GTRecipe recipe(Map<Fluid, Long> fluids, long rate) {
        if (fluids.isEmpty() || rate <= 0) return null;
        try {
            long total = 0;
            for (long amount : fluids.values()) {
                if (amount <= 0) return null;
                total = Math.addExact(total, amount);
            }
            long eut = Math.multiplyExact(Math.addExact(GTValues.V[GTValues.UHV], total), rate);
            Math.multiplyExact(eut, AdvancedInfiniteDrillLogic.MAX_PROGRESS);
            var builder = GTRecipeBuilder.ofRaw().duration(AdvancedInfiniteDrillLogic.MAX_PROGRESS).EUt(eut);
            for (var entry : fluids.entrySet()) {
                builder.outputFluids(LongFluidIngredient.create(new FluidStack(entry.getKey(), 1),
                        Math.multiplyExact(entry.getValue(), rate)));
            }
            return builder.build();
        } catch (ArithmeticException overflow) {
            return null;
        }
    }

    private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath("gtceu", name); }
    private AdvancedFluidDrillProduction() {}
}
