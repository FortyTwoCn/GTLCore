package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.MolecularAssemblerMultiblockMachineBase;

import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import org.jetbrains.annotations.Nullable;

/** A queued craft is persisted as a pending output recipe until its entire buffer transaction succeeds. */
public class MolecularAssemblerRecipesLogic extends RecipeLogic {
    @SaveField
    protected boolean craftStarted;

    public MolecularAssemblerRecipesLogic() {
        super();
    }

    @Override
    public MolecularAssemblerMultiblockMachineBase getMachine() {
        return (MolecularAssemblerMultiblockMachineBase) super.getMachine();
    }

    protected @Nullable GTRecipe getRecipe() {
        var handler = getMachine().getMAHandler();
        return handler == null ? null : handler.extractGTRecipe(getMachine().getMaxParallel(), getMachine().getTickDuration());
    }

    @Override
    public void serverTick() {
        if (!isSuspend() && getRLMachine().isRecipeLogicAvailable()) {
            if (lastRecipe != null) {
                if (progress < duration) handleRecipeWorking();
                if (!isSuspend() && craftStarted && progress >= duration) onRecipeFinish();
            } else {
                findAndHandleRecipe();
            }
        }
        if (isSuspend() || !getRLMachine().isRecipeLogicAvailable() || (lastRecipe == null && isIdle() && !keepSubscribing)) {
            if (subscription != null) {
                subscription.unsubscribe();
                subscription = null;
            }
        }
    }

    @Override
    public void handleRecipeWorking() {
        if (lastRecipe == null || !getRLMachine().isRecipeLogicAvailable()) return;
        if (!craftStarted) {
            if (!getRLMachine().beforeWorking(lastRecipe)) {
                setWaiting(null);
                return;
            }
            craftStarted = true;
        }
        if (!getRLMachine().onWorking()) {
            setWaiting(null);
            return;
        }
        setStatus(Status.WORKING);
        ++progress;
        ++totalContinuousRunningTime;
        markAsChanged();
    }

    @Override
    public void findAndHandleRecipe() {
        if (lastRecipe != null || isSuspend() || !getRLMachine().isRecipeLogicAvailable()) return;
        var match = getRecipe();
        if (match != null) setupRecipe(match);
    }

    @Override
    public void setupRecipe(GTRecipe recipe) {
        if (lastRecipe != null) throw new IllegalStateException("Molecular assembler already has a pending craft");
        lastRecipe = recipe;
        lastUnrolledRecipe = recipe.copy();
        lastOriginRecipe = null;
        progress = 0;
        duration = Math.max(1, recipe.duration);
        craftStarted = false;
        isActive = true;
        // The queue has already been consumed; a rejected callback must keep this recipe pending.
        if (getRLMachine().beforeWorking(recipe)) {
            craftStarted = true;
            setStatus(Status.WORKING);
        } else {
            setWaiting(null);
        }
        markAsChanged();
        syncDataHolder.resyncAllFields();
    }

    @Override
    public void onRecipeFinish() {
        if (lastRecipe == null || !craftStarted || progress < duration || isSuspend() || !getRLMachine().isRecipeLogicAvailable()) return;
        var handler = getMachine().getMAHandler();
        if (handler == null || !handler.tryHandleRecipeOutput(lastRecipe)) {
            setWaiting(null);
            return;
        }
        // Clear the committed output before callbacks or prefetching another queued craft.
        lastRecipe = null;
        lastUnrolledRecipe = null;
        lastOriginRecipe = null;
        progress = 0;
        duration = 0;
        craftStarted = false;
        isActive = false;
        consecutiveRecipes++;
        boolean suspend = suspendAfterFinish;
        suspendAfterFinish = false;
        if (getMachine() instanceof ISuspendableMachine legacy && legacy.gtlcore$isSuspendAfterFinish()) {
            suspend = true;
            legacy.gtlcore$setSuspendAfterFinish(false);
        }
        setStatus(suspend ? Status.SUSPEND : Status.IDLE);
        markAsChanged();
        syncDataHolder.resyncAllFields();
        getRLMachine().afterWorking();
        if (!suspend) findAndHandleRecipe();
    }

    @Override
    public void resetRecipeLogic() {
        if (lastRecipe != null) {
            // Unforming a structure must not discard crafts already extracted from the IO queue.
            updateTickSubscription();
            syncDataHolder.resyncAllFields();
        } else {
            craftStarted = false;
            super.resetRecipeLogic();
        }
    }

    @Override
    public void interruptRecipe() {
        if (lastRecipe != null) {
            // The IO queue cannot recover the original AE inputs; retain its accepted craft for resume.
            setStatus(Status.SUSPEND);
            markAsChanged();
        }
    }
}
