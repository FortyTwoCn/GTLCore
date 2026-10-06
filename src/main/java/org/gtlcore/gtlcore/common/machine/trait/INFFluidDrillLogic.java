package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.INFFluidDrillMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.FluidVeinWorldEntry;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A native attached recipe trait. Infinite rigs never deplete the underlying deposit. */
public class INFFluidDrillLogic extends RecipeLogic {
    public static final int MAX_PROGRESS = 20;

    public INFFluidDrillLogic() { super(); }

    @Override
    public INFFluidDrillMachine getMachine() { return (INFFluidDrillMachine) super.getMachine(); }

    @Override
    protected List<Class<?>> validMachineClasses() { return List.of(INFFluidDrillMachine.class); }

    private @Nullable FluidVeinWorldEntry vein() {
        if (!(getMachine().getLevel() instanceof ServerLevel level)) return null;
        return BedrockFluidVeinSavedData.getOrCreate(level).getFluidVeinWorldEntry(
                SectionPos.blockToSectionCoord(getMachine().getBlockPos().getX()),
                SectionPos.blockToSectionCoord(getMachine().getBlockPos().getZ()));
    }

    public @Nullable Fluid getVeinFluid() {
        var entry = vein();
        return entry == null || entry.getDefinition() == null ? null : entry.getDefinition().value().getStoredFluid();
    }

    @Override
    public void findAndHandleRecipe() {
        if (isSuspend()) return;
        lastRecipe = null;
        if (getVeinFluid() == null) {
            if (subscription != null) { subscription.unsubscribe(); subscription = null; }
            return;
        }
        var recipe = getFluidDrillRecipe();
        if (recipe != null) setupRecipe(recipe);
    }

    private @Nullable GTRecipe getFluidDrillRecipe() {
        var machine = getMachine();
        var recipe = BedrockFluidProduction.recipe(vein(), INFFluidDrillMachine.getRigMultiplier(machine.getTier()),
                isOverclocked(), machine.getEnergyTier());
        return recipe != null && RecipeHelper.matchContents(machine, recipe).isSuccess() ? recipe : null;
    }

    public long getFluidToProduce() {
        return BedrockFluidProduction.amount(vein(), INFFluidDrillMachine.getRigMultiplier(getMachine().getTier()), isOverclocked());
    }

    @Override
    public void onRecipeFinish() {
        // Finishing may retry after an output is blocked. Do not discard production
        // or advance another cycle until the entire real output can be committed.
        if (lastRecipe != null) {
            if (!RecipeRunnerHelper.matchRecipeOutput(getMachine(), lastRecipe)) {
                waitForOutput(RecipeResult.FAIL_OUTPUT.reason()); return;
            }
            var output = RecipeHelper.handleRecipeIO(getMachine(), lastRecipe, IO.OUT, chanceCaches);
            if (!output.isSuccess()) { waitForOutput(output.reason()); return; }
        }
        getMachine().afterWorking();
        boolean pause = suspendAfterFinish;
        if (getMachine() instanceof ISuspendableMachine suspend && suspend.gtlcore$isSuspendAfterFinish()) {
            pause = true; suspend.gtlcore$setSuspendAfterFinish(false);
        }
        if (!pause) {
            var next = getFluidDrillRecipe();
            if (next != null) { setupRecipe(next); return; }
        }
        setStatus(pause ? Status.SUSPEND : Status.IDLE);
        setSuspendAfterFinish(false);
        progress = 0; duration = 0; isActive = false;
        lastRecipe = null; lastUnrolledRecipe = null;
        syncDataHolder.resyncAllFields();
    }

    protected void waitForOutput(@Nullable Component reason) {
        // A power-button finish pause must survive a full output, then take effect after delivery.
        boolean pause = suspendAfterFinish;
        suspendAfterFinish = false;
        try { setWaiting(reason); }
        finally { suspendAfterFinish = pause; }
    }

    protected boolean isOverclocked() { return getMachine().getEnergyTier() > getMachine().getTier(); }
}
