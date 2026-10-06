package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.recipe.*;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.CWURecipeCapability;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.feature.IAttachConfiguratorsTrait;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.layout.Flow;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.*;

/** Core locking/batching around native recipe search, working, waiting, prerolls, tick IO and pause lifecycle. */
@Mixin(RecipeLogic.class)
public abstract class RecipeLogicMixin implements ILockRecipe, IRecipeStatus, IAttachConfiguratorsTrait {
    @SaveField private boolean isLock;
    @SaveField private @Nullable GTRecipe lockRecipe;
    @Unique private boolean gtlcore$preparingBatch, gtlcore$retryComputation;
    @Unique private @Nullable RecipeResult gtlcore$recipeStatus, gtlcore$workingStatus;
    @Shadow(remap = false) protected @Nullable GTRecipe lastRecipe;
    @Shadow(remap = false) protected @Nullable GTRecipe lastOriginRecipe;
    @Shadow(remap = false) public abstract IRecipeLogicMachine getRLMachine();
    @Shadow(remap = false) public abstract RecipeLogic.Status getStatus();
    @Shadow(remap = false) public abstract void markLastRecipeDirty();
    @Shadow(remap = false) public abstract void updateTickSubscription();
    @Shadow(remap = false) public abstract void setWaiting(@Nullable Component reason);
    @Shadow(remap = false) public abstract void setSuspendAfterFinish(boolean suspend);

    @Override public boolean isLock() { return isLock; }
    @Override public GTRecipe getLockRecipe() { return lockRecipe; }
    @Override public void setLockRecipe(GTRecipe recipe) { lockRecipe = recipe == null ? null : recipe.copy(); }
    @Override public void setLock(boolean locked) {
        if (isLock == locked) return;
        isLock = locked;
        lockRecipe = locked && lastOriginRecipe != null ? lastOriginRecipe.copy() : null;
        getRLMachine().self().setChanged();
        markLastRecipeDirty();
        updateTickSubscription();
    }
    @Override public RecipeResult getRecipeStatus() { return gtlcore$recipeStatus; }
    @Override public void setRecipeStatus(RecipeResult result) { gtlcore$recipeStatus = result; }
    @Override public RecipeResult getWorkingStatus() { return gtlcore$workingStatus; }
    @Override public void setWorkingStatus(RecipeResult result) { gtlcore$workingStatus = result; }

    @Inject(method = "searchRecipe", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$searchLocked(CallbackInfoReturnable<Iterator<GTRecipe>> cir) {
        if (isLock && lockRecipe != null) cir.setReturnValue(Collections.singleton(lockRecipe).iterator());
    }
    @ModifyArg(method = "checkMatchedRecipeAvailable", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/machine/feature/IRecipeLogicMachine;fullModifyRecipe(Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;)Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;"),
            index = 0, remap = false, require = 1)
    private GTRecipe gtlcore$copyBeforeModifiers(GTRecipe recipe) { return recipe.copy(); }
    @Inject(method = "checkMatchedRecipeAvailable", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$captureLock(GTRecipe match, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && isLock && lockRecipe == null) setLockRecipe(match);
    }

    @Inject(method = "setupRecipe", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$prepareBatch(GTRecipe recipe, CallbackInfo ci) {
        var machine = getRLMachine();
        if (gtlcore$preparingBatch || !BatchProcessing.isEnabled(machine.self()) || IGTRecipe.of(recipe).isBatchProcessed()) return;
        // Work on a copy before native prerolls and consumption. The processed flag makes this re-entry run once.
        var prepared = BatchProcessing.apply(machine.self(), recipe.copy(), IGTRecipe.of(recipe).isSubTickParallelized());
        if (prepared != null) {
            gtlcore$preparingBatch = true;
            try { ((RecipeLogic) (Object) this).setupRecipe(prepared); }
            finally { gtlcore$preparingBatch = false; }
        } else {
            ((RecipeLogic) (Object) this).resetRecipeLogic();
            gtlcore$recipeStatus = RecipeResult.FAIL_INPUT;
        }
        ci.cancel();
    }
    @Inject(method = "onRecipeFinish", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$waitForBatchOutput(CallbackInfo ci) {
        if (lastRecipe != null && IGTRecipe.of(lastRecipe).getBatchSize() > 1) {
            // At finish, consumed inputs cannot be matched again. Only output capacity determines this wait.
            var output = RecipeRunnerHelper.handleRecipe(IO.OUT, getRLMachine(), lastRecipe.outputs, Map.of(), false, lastRecipe, true);
            if (!output.isSuccess()) { setWaiting(output.reason()); ci.cancel(); return; }
        }
        if (getRLMachine() instanceof ISuspendableMachine suspend && suspend.gtlcore$isSuspendAfterFinish()) {
            setSuspendAfterFinish(true);
            suspend.gtlcore$setSuspendAfterFinish(false);
        }
    }
    @Inject(method = "findAndHandleRecipe", at = @At("HEAD"), remap = false, require = 1)
    private void gtlcore$clearSearchStatus(CallbackInfo ci) { gtlcore$recipeStatus = gtlcore$workingStatus = null; gtlcore$retryComputation = false; }
    @Inject(method = "checkRecipe", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$matchStatus(GTRecipe recipe, CallbackInfoReturnable<ActionResult> cir) {
        var result = cir.getReturnValue();
        gtlcore$recipeStatus = result.isSuccess() ? RecipeResult.SUCCESS : RecipeResult.fail(result.reason());
        gtlcore$retryComputation |= !result.isSuccess() && result.io() == IO.IN && result.capability() == CWURecipeCapability.CAP;
    }
    @Inject(method = "setWaiting", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$waitingStatus(Component reason, CallbackInfo ci) { gtlcore$workingStatus = RecipeResult.fail(reason); }
    @Inject(method = "setStatus", at = @At("HEAD"), remap = false, require = 1)
    private void gtlcore$workingStatus(RecipeLogic.Status status, CallbackInfo ci) {
        if (status == RecipeLogic.Status.WORKING) { gtlcore$workingStatus = null; gtlcore$retryComputation = false; }
    }
    @Inject(method = "resetRecipeLogic", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$resetStatus(CallbackInfo ci) { gtlcore$recipeStatus = gtlcore$workingStatus = null; gtlcore$retryComputation = false; }

    @ModifyExpressionValue(method = "serverTick", at = @At(value = "FIELD", target =
            "Lcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;keepSubscribing:Z"),
            remap = false, require = 2, expect = 2, allow = 2)
    private boolean gtlcore$retryOpticalSupply(boolean nativeKeepSubscribing) {
        return nativeKeepSubscribing || gtlcore$retryComputation || RecipeResult.FAIL_NO_ENOUGH_CWU_IN.equals(gtlcore$recipeStatus);
    }

    @Override public void attachRightConfigurators(Flow flow, ModularPanel<?> panel, PanelSyncManager syncManager) {
        if (!(getRLMachine() instanceof WorkableElectricMultiblockMachine electric)) return;
        ILockRecipe.attachRecipeLockable(flow, (RecipeLogic) (Object) this);
        if (!electric.isGenerator()) {
            IRecipeCapabilityMachine.attachConfigurators(flow, electric);
            IBatchMachine.attachBatchConfigurator(flow, electric);
        }
    }
}
