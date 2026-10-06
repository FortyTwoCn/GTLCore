package org.gtlcore.gtlcore.common.recipe;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.math.BigInteger;
import java.util.Collections;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/** Original Core voltage rules attached to native GT recipe builders. */
public final class GTLRecipeCallbacks {
    private GTLRecipeCallbacks() {}

    private static boolean hasFluidInput(GTRecipeBuilder builder) {
        return !builder.input.getOrDefault(FluidRecipeCapability.CAP, Collections.emptyList()).isEmpty() ||
                !builder.tickInput.getOrDefault(FluidRecipeCapability.CAP, Collections.emptyList()).isEmpty();
    }

    private static SizedFluidIngredient fluid(Fluid fluid, long amount) {
        return amount > Integer.MAX_VALUE ? LongFluidIngredient.create(fluid, amount) :
                SizedFluidIngredient.of(fluid, Math.toIntExact(amount));
    }

    private static int cuttingAmount(GTRecipeBuilder builder, long divisor, int minimum, int maximum) {
        return BigInteger.valueOf(builder.EUt().getTotalEU()).multiply(BigInteger.valueOf(builder.duration))
                .divide(BigInteger.valueOf(divisor)).max(BigInteger.valueOf(minimum))
                .min(BigInteger.valueOf(maximum)).intValueExact();
    }

    private static int duration(int original, double multiplier) {
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, original * multiplier));
    }

    public static BiConsumer<GTRecipeBuilder, RecipeOutput> cutting(Supplier<Fluid> grade8, Supplier<Fluid> grade16) {
        return (builder, output) -> {
            if (hasFluidInput(builder)) return;
            long eu = builder.EUt().getTotalEU();
            if (eu < 524288) {
                builder.copy(builder.id.withSuffix("_water"))
                        .inputFluids(fluid(GTMaterials.Water.getFluid(), cuttingAmount(builder, 320, 4, 1000)))
                        .duration(duration(builder.duration, 2)).save(output);
                builder.copy(builder.id.withSuffix("_distilled_water"))
                        .inputFluids(fluid(GTMaterials.DistilledWater.getFluid(), cuttingAmount(builder, 426, 3, 750)))
                        .duration(duration(builder.duration, 1.5)).save(output);
                builder.copy(builder.id.withSuffix("_8_water"))
                        .inputFluids(fluid(grade8.get(), cuttingAmount(builder, 720, 1, 500)))
                        .duration(duration(builder.duration, 0.8)).save(output);
                builder.copy(builder.id.withSuffix("_16_water"))
                        .inputFluids(fluid(grade16.get(), cuttingAmount(builder, 960, 1, 250)))
                        .duration(duration(builder.duration, 0.5)).save(output);
                builder.inputFluids(fluid(GTMaterials.Lubricant.getFluid(), cuttingAmount(builder, 1280, 1, 250)))
                        .duration(Math.max(1, builder.duration));
            } else if (eu < GTValues.VA[GTValues.UEV]) {
                builder.copy(builder.id.withSuffix("_16_water"))
                        .inputFluids(fluid(grade16.get(), cuttingAmount(builder, 640, 1, 500)))
                        .duration(duration(builder.duration, 0.5)).save(output);
                builder.inputFluids(fluid(grade8.get(), cuttingAmount(builder, 320, 1, 1000)))
                        .duration(Math.max(1, builder.duration));
            } else {
                builder.inputFluids(fluid(grade16.get(), cuttingAmount(builder, 320, 1, 1000)))
                        .duration(Math.max(1, builder.duration));
            }
        };
    }

    public static BiConsumer<GTRecipeBuilder, RecipeOutput> circuitAssembly(Supplier<Fluid> mutated,
                                                                          Supplier<Fluid> superMutated) {
        return (builder, output) -> {
            if (hasFluidInput(builder)) return;
            long eu = builder.EUt().getTotalEU();
            if (eu < 160000) {
                long multiplier = builder.getSolderMultiplier();
                builder.copy(builder.id.withSuffix("_soldering_alloy"))
                        .inputFluids(fluid(GTMaterials.SolderingAlloy.getFluid(), Math.max(1, 72 * multiplier))).save(output);
                builder.inputFluids(fluid(GTMaterials.Tin.getFluid(), Math.max(1, 144 * multiplier)));
            } else {
                long multiplier = GTUtil.getFloorTierByVoltage(eu) - 6L;
                builder.copy(builder.id.withSuffix("_soldering_alloy"))
                        .inputFluids(fluid(superMutated.get(), Math.max(1, 72 * multiplier))).save(output);
                builder.inputFluids(fluid(mutated.get(), Math.max(1, 144 * multiplier)));
            }
        };
    }

    public static BiConsumer<GTRecipeBuilder, RecipeOutput> heatExchange(Supplier<GTRecipeType> exchanger,
                                                                       Supplier<Fluid> supercriticalSteam) {
        return (builder, output) -> {
            long eu = builder.duration * GTValues.V[GTValues.EV];
            var plasma = FluidRecipeCapability.CAP.of(builder.input.get(FluidRecipeCapability.CAP).getFirst().content());
            var cooled = FluidRecipeCapability.CAP.of(builder.output.get(FluidRecipeCapability.CAP).getFirst().content());
            exchanger.get().recipeBuilder(builder.id)
                    .inputFluids(LongFluidIngredient.copy(plasma), fluid(GTMaterials.DistilledWater.getFluid(), eu / 160))
                    .outputFluids(LongFluidIngredient.copy(cooled), fluid(supercriticalSteam.get(), eu))
                    .addData("eu", eu).duration(200).save(output);
        };
    }

    public static BiConsumer<GTRecipeBuilder, RecipeOutput> focusedEngraving(Supplier<GTRecipeType> array,
                                                                           Supplier<Fluid> photoresist,
                                                                           Supplier<Fluid> euvPhotoresist) {
        return (builder, output) -> {
            if (builder.output.containsKey(FluidRecipeCapability.CAP)) return;
            long eu = builder.EUt().getTotalEU();
            long scaledEu = eu > Long.MAX_VALUE / 4 ? Long.MAX_VALUE : eu * 4;
            var recipe = array.get().copyFrom(builder).duration(duration(builder.duration, 0.2)).EUt(scaledEu);
            double tier = Math.log10(eu) / Math.log10(4);
            recipe.inputFluids(tier > 10 ? fluid(euvPhotoresist.get(), Math.max(1, (long) (tier / 2))) :
                    fluid(photoresist.get(), Math.max(1, (long) tier))).save(output);
        };
    }

    public static String fusionTier(long startEuInMillions) {
        if (startEuInMillions > 1280L) return "V";
        if (startEuInMillions > 640L) return "IV";
        if (startEuInMillions > 320L) return "III";
        if (startEuInMillions > 160L) return "II";
        return "I";
    }
}
