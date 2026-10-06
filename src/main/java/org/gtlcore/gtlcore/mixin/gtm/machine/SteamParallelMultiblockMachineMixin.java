package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.machine.ISteamMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeInputHandlerPolicy;
import org.gtlcore.gtlcore.api.machine.trait.RecipeInputExclusion;
import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IRecipeHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.steam.SteamEnergyRecipeHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.common.machine.multiblock.steam.SteamParallelMultiblockMachine;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static com.gregtechceu.gtceu.common.data.machines.GTMultiMachines.STEAM_OVEN;

@Mixin(SteamParallelMultiblockMachine.class)
public abstract class SteamParallelMultiblockMachineMixin implements ISteamMachine, IRecipeInputHandlerPolicy {
    @Shadow(remap = false) private SteamEnergyRecipeHandler steamEnergy;

    @Inject(method = "<init>(Lcom/gregtechceu/gtceu/api/blockentity/BlockEntityCreationInfo;I)V", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$ovenLimit(BlockEntityCreationInfo info, int maximum, CallbackInfo ci) {
        var machine = (SteamParallelMultiblockMachine) (Object) this;
        if (machine.getDefinition() == STEAM_OVEN.get()) machine.setMaxParallels(256);
    }

    @Inject(method = "recipeModifier", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private static void gtlcore$oven(MetaMachine machine, GTRecipe recipe, CallbackInfoReturnable<ModifierFunction> cir) {
        if (machine.getDefinition() != STEAM_OVEN.get()) return;
        int parallels = CoreRecipeModifiers.parallelAmount(machine, recipe, 256);
        cir.setReturnValue(parallels == 0 ? ModifierFunction.NULL : ModifierFunction.builder()
                .modifyAllContents(IAdvancedContentModifier.preciseMultiplier(parallels)).eutMultiplier(parallels)
                .parallels(parallels).durationMultiplier(.5).build());
    }

    @Inject(method = "formStructure", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$reserveSteam(String structure, CallbackInfo ci) {
        if (steamEnergy == null) return;
        var machine = (SteamParallelMultiblockMachine) (Object) this;
        RecipeInputExclusion.exclude(machine, steamEnergy.getSteamTank());
        ((IRecipeCapabilityMachine) (Object) this).upDate();
    }

    @Override public boolean isRecipeInputHandlerExcluded(IRecipeHandler<?> handler) { return steamEnergy != null && handler == steamEnergy.getSteamTank(); }
    @Override public double getSteamPerEU() { return ((SteamParallelMultiblockMachine) (Object) this).getConversionRate(); }
}
