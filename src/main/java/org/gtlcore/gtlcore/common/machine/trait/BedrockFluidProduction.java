package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/** Native bedrock holders and Core's exact production quantities share one calculation. */
public final class BedrockFluidProduction {
    public static long amount(@Nullable FluidVeinWorldEntry entry, int rigMultiplier, boolean overclocked) {
        if (entry == null || entry.getDefinition() == null) return 0;
        var definition = entry.getDefinition().value();
        long remainingYield = (long) entry.getFluidYield() * entry.getOperationsRemaining() /
                BedrockFluidVeinSavedData.MAXIMUM_VEIN_OPERATIONS;
        long produced = Math.max(definition.getDepletedYield(), remainingYield) * rigMultiplier;
        return overclocked ? produced * 3 / 2 : produced;
    }

    public static @Nullable GTRecipe recipe(@Nullable FluidVeinWorldEntry entry, int rigMultiplier,
                                            boolean overclocked, int energyTier) {
        long amount = amount(entry, rigMultiplier, overclocked);
        if (amount <= 0) return null;
        return GTRecipeBuilder.ofRaw().duration(INFFluidDrillLogic.MAX_PROGRESS).EUt(GTValues.VA[energyTier])
                .outputFluids(LongFluidIngredient.create(new FluidStack(entry.getDefinition().value().getStoredFluid(), 1), amount))
                .build();
    }

    private BedrockFluidProduction() {}
}
