package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.machine.trait.BedrockFluidProduction;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FluidDrillMachine;
import com.gregtechceu.gtceu.common.machine.trait.FluidDrillLogic;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native GT owns search, ticking, power handling and depletion. Core preserves long outputs and tool pauses. */
@Mixin(FluidDrillLogic.class)
public abstract class FluidDrillLogicMixin extends RecipeLogic {
    @Shadow(remap = false) private @Nullable Fluid veinFluid;
    @Shadow(remap = false) public abstract FluidDrillMachine getMachine();
    @Unique private boolean gtlcore$finishingFluidCycle;

    @Inject(method = "getFluidDrillRecipe", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$nativeLongProduction(CallbackInfoReturnable<GTRecipe> cir) {
        // Native GT attempts another cycle before testing its finish-pause flag.
        if (gtlcore$finishingFluidCycle && suspendAfterFinish) { cir.setReturnValue(null); return; }
        if (!(getMachine().getLevel() instanceof ServerLevel level) || veinFluid == null) {
            cir.setReturnValue(null); return;
        }
        var machine = getMachine();
        var entry = BedrockFluidVeinSavedData.getOrCreate(level).getFluidVeinWorldEntry(
                SectionPos.blockToSectionCoord(machine.getBlockPos().getX()),
                SectionPos.blockToSectionCoord(machine.getBlockPos().getZ()));
        var recipe = BedrockFluidProduction.recipe(entry, FluidDrillMachine.getRigMultiplier(machine.getTier()),
                machine.getEnergyTier() > machine.getTier(), machine.getEnergyTier());
        cir.setReturnValue(recipe != null && RecipeHelper.matchContents(machine, recipe).isSuccess() ? recipe : null);
    }

    @Inject(method = "getFluidToProduce(Lcom/gregtechceu/gtceu/api/data/worldgen/bedrockfluid/FluidVeinWorldEntry;)I",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$boundedNativeDisplay(FluidVeinWorldEntry entry, CallbackInfoReturnable<Integer> cir) {
        long produced = BedrockFluidProduction.amount(entry, FluidDrillMachine.getRigMultiplier(getMachine().getTier()),
                getMachine().getEnergyTier() > getMachine().getTier());
        // The pinned native public accessor returns int. Production recipes above
        // that range retain their exact amount in the native long Ingredient.
        cir.setReturnValue((int) Math.min(Integer.MAX_VALUE, produced));
    }

    @WrapMethod(method = "onRecipeFinish", remap = false)
    private void gtlcore$finishBeforeRestart(Operation<Void> original) {
        if (lastRecipe != null && !RecipeRunnerHelper.matchRecipeOutput(getMachine(), lastRecipe)) {
            boolean pause = suspendAfterFinish;
            suspendAfterFinish = false;
            try { setWaiting(RecipeResult.FAIL_OUTPUT.reason()); }
            finally { suspendAfterFinish = pause; }
            return;
        }
        if (getMachine() instanceof ISuspendableMachine suspend && suspend.gtlcore$isSuspendAfterFinish()) {
            setSuspendAfterFinish(true); suspend.gtlcore$setSuspendAfterFinish(false);
        }
        gtlcore$finishingFluidCycle = true;
        try { original.call(); }
        finally { gtlcore$finishingFluidCycle = false; }
    }
}
