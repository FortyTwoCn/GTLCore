package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

public final class NativeFusionStartup {
    private NativeFusionStartup() {}
    public static Component failureReason() { return Component.translatable("gtceu.recipe_modifier.insufficient_eu_to_start_fusion"); }
    public static long requiredHeat(GTRecipe recipe) {
        return recipe.data.contains("eu_to_start", Tag.TAG_ANY_NUMERIC) ? recipe.data.getLong("eu_to_start") : -1;
    }
    public static long missingHeat(long heat, long required) {
        return Math.max(0, required - Math.max(0, heat));
    }
    public static boolean canStart(IEnergyContainer energy, long heat, long required) {
        return required >= 0 && required <= energy.getEnergyCapacity() &&
                missingHeat(heat, required) <= energy.getEnergyStored();
    }

    /** Native server recipe IO is serial; an unsuccessful operation returns the entire heat reservation. */
    public static ActionResult run(IEnergyContainer energy, long heat, long required, LongConsumer commitHeat,
                                   Supplier<ActionResult> operation) {
        if (!canStart(energy, heat, required)) return ActionResult.fail(failureReason(), EURecipeCapability.CAP, IO.IN);
        long needed = missingHeat(heat, required);
        if (needed == 0) return operation.get();
        long reserved = energy.removeEnergy(needed);
        if (reserved != needed) {
            if (reserved > 0) energy.addEnergy(reserved);
            return ActionResult.fail(failureReason(), EURecipeCapability.CAP, IO.IN);
        }
        boolean accepted = false;
        try {
            var result = operation.get();
            if (result.isSuccess()) {
                commitHeat.accept(required);
                accepted = true;
            }
            return result;
        } finally {
            if (!accepted) energy.addEnergy(reserved);
        }
    }
}
