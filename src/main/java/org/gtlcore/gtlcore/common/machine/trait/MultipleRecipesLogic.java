package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.ParallelMachine;
import org.gtlcore.gtlcore.api.machine.trait.*;
import org.gtlcore.gtlcore.api.recipe.*;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.*;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.BiPredicate;
import static org.gtlcore.gtlcore.api.recipe.IParallelLogic.*;
import static org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.*;

/** Cross-recipe execution on native RecipeLogic. Consumed work remains pending until its outputs are delivered. */
public class MultipleRecipesLogic extends RecipeLogic implements ICrossRecipeLogic {
    private static final int MAX_THREADS = 64;
    private final @Nullable ParallelMachine configuredParallel;
    private final @Nullable BiPredicate<CompoundTag, IRecipeLogicMachine> dataCheck;
    private double reductionEUt, reductionDuration;
    @SaveField private @Nullable GTRecipe pendingRecipe;

    public MultipleRecipesLogic() { this(null, null, 1, 1); }
    public MultipleRecipesLogic(BiPredicate<CompoundTag, IRecipeLogicMachine> dataCheck) { this(null, dataCheck, 1, 1); }
    public MultipleRecipesLogic(ParallelMachine machine) { this(machine, null, 1, 1); }
    public MultipleRecipesLogic(ParallelMachine machine, BiPredicate<CompoundTag, IRecipeLogicMachine> dataCheck) { this(machine, dataCheck, 1, 1); }
    public MultipleRecipesLogic(ParallelMachine machine, BiPredicate<CompoundTag, IRecipeLogicMachine> dataCheck, double reductionEUt, double reductionDuration) {
        super(); this.configuredParallel = machine; this.dataCheck = dataCheck; setReduction(reductionEUt, reductionDuration);
    }
    public ParallelMachine getParallel() { return configuredParallel != null ? configuredParallel : (ParallelMachine) getMachine(); }
    public BiPredicate<CompoundTag, IRecipeLogicMachine> getDataCheck() { return dataCheck; }
    public double getReductionEUt() { return reductionEUt; }
    public double getReductionDuration() { return reductionDuration; }
    public void setReduction(double energy, double duration) {
        if (!(energy > 0) || !(duration > 0) || !Double.isFinite(energy) || !Double.isFinite(duration)) throw new IllegalArgumentException("Invalid cross-recipe reduction");
        reductionEUt = energy; reductionDuration = duration;
    }
    @Override public WorkableElectricMultiblockMachine getMachine() { return (WorkableElectricMultiblockMachine) super.getMachine(); }
    private ILockRecipe lock() { return (ILockRecipe) (Object) this; }
    private IRecipeStatus coreStatus() { return (IRecipeStatus) (Object) this; }

    @Override public void findAndHandleRecipe() {
        if (!usesMultipleRecipeExecution() && pendingRecipe == null) { super.findAndHandleRecipe(); return; }
        lastFailedMatches = null; clearFailureReason(); coreStatus().setRecipeStatus(null);
        if (pendingRecipe == null) pendingRecipe = collectRecipes();
        if (pendingRecipe == null) {
            lastRecipe = null; progress = duration = 0; isActive = false; setStatus(Status.IDLE);
            return;
        }
        RecipeResult.of(getRLMachine(), RecipeResult.SUCCESS);
        setupRecipe(pendingRecipe.copy());
    }
    protected double getTotalEuOfRecipe(GTRecipe recipe) { return (double) recipe.getInputEUt().getTotalEU() * recipe.duration; }
    protected double getEuMultiplier() {
        var maintenance = ((IRecipeCapabilityMachine) getParallel()).getMaintenanceMachine();
        return reductionEUt * reductionDuration * (maintenance == null ? 1 : maintenance.getDurationMultiplier());
    }

    protected boolean usesMultipleRecipeExecution() { return true; }
    protected boolean hasPendingRecipe() { return pendingRecipe != null; }
    protected int getMultipleThreads() { return MAX_THREADS; }

    /** Preserve extension payment state if native interruption later restarts already consumed work. */
    public void syncPendingRecipeData(GTRecipe runningRecipe) {
        if (pendingRecipe != null && lastRecipe == runningRecipe) {
            pendingRecipe.data = runningRecipe.data.copy();
            markAsChanged();
        }
    }

    @Override public void handleRecipeWorking() {
        // Finished work can wait for output space. It must not consume another native tick while doing so.
        if (pendingRecipe != null && progress >= duration) return;
        super.handleRecipeWorking();
    }

    protected GTRecipe collectRecipes() {
        var machine = getRLMachine();
        if (machine.getCapabilitiesProxy().isEmpty() && (!(machine instanceof IRecipeCapabilityMachine core) || core.emptyHandlePart())) return null;
        long maxEUt = getMachine().getOverclockVoltage();
        if (maxEUt <= 0) return null;
        var iterator = lock().isLock() && lock().getLockRecipe() != null ? Collections.singleton(lock().getLockRecipe()).iterator() : lookupRecipeIterator();
        var builder = machine.getRecipeType().recipeBuilder(ResourceLocation.fromNamespaceAndPath("gtlcore", "cross_recipe_work"));
        builder.input.clear(); builder.output.clear(); builder.tickInput.clear(); builder.tickOutput.clear();
        var output = builder.duration(20).build();
        double totalEu = 0, multiplier = getEuMultiplier();
        boolean consumed = false;
        long remain = (long) Math.max(1, getParallel().getMaxParallel()) * Math.max(1, getMultipleThreads());
        while (remain > 0 && iterator.hasNext()) {
            var source = iterator.next();
            if (source == null || !checkSource(source)) continue;
            long parallels = getMaxParallel(machine, source, Math.min(remain, Long.MAX_VALUE / IGTRecipe.of(source).getRealParallels()));
            if (parallels <= 0) continue;
            var selected = source.copy(IAdvancedContentModifier.preciseMultiplier(parallels), false);
            IGTRecipe.of(selected).setRealParallels(Math.multiplyExact(IGTRecipe.of(source).getRealParallels(), parallels));
            // Check representability without advancing probability caches before consuming any inputs.
            RecipeChancePreview.output(selected, machine, selected.outputs, getChanceCaches(), false);
            if (!handleRecipeInput(machine, selected)) continue;
            consumed = true;
            var rolled = getRecipeOutputChance(machine, selected);
            remain -= parallels; totalEu += getTotalEuOfRecipe(selected) * multiplier;
            for (var entry : rolled.outputs.entrySet()) output.outputs.computeIfAbsent(entry.getKey(), cap -> new ArrayList<>()).addAll(entry.getValue());
            lastOriginRecipe = source.copy();
            if (lock().isLock() && lock().getLockRecipe() == null) lock().setLockRecipe(source);
            if (totalEu / maxEUt > 20 * 500) break;
        }
        if (!consumed) {
            if (coreStatus().getRecipeStatus() == null || coreStatus().getRecipeStatus().isSuccess()) RecipeResult.of(machine, RecipeResult.FAIL_FIND);
            return null;
        }
        double ticks = totalEu / maxEUt;
        long eut = ticks > 20 ? maxEUt : (long) Math.max(1, totalEu / 20);
        EURecipeCapability.putEUContent(output.tickInputs, new EnergyStack(eut));
        output.duration = (int) Math.min(Integer.MAX_VALUE, Math.max(ticks, 20));
        IGTRecipe.of(output).setHasTick(true);
        return output;
    }
    protected Iterator<GTRecipe> lookupRecipeIterator() { return searchRecipe(); }
    private boolean checkSource(GTRecipe recipe) {
        return RecipeRunnerHelper.matchRecipe(getRLMachine(), recipe) && IGTRecipe.of(recipe).getEuTier() <= getMachine().getTier() &&
                RecipeHelper.checkConditions(recipe, this).isSuccess() && (dataCheck == null || dataCheck.test(recipe.data, getRLMachine()));
    }

    @Override public void onRecipeFinish() {
        if (!usesMultipleRecipeExecution() && pendingRecipe == null) { super.onRecipeFinish(); return; }
        if (lastRecipe == null) return;
        var result = handleRecipe(IO.OUT, getRLMachine(), lastRecipe.outputs, getChanceCaches(), false, lastRecipe, true);
        if (!result.isSuccess()) { setWaiting(result.reason()); return; }
        if (!handleRecipeOutput(getRLMachine(), lastRecipe)) { setWaiting(RecipeResult.FAIL_OUTPUT.reason()); return; }
        getRLMachine().afterWorking(); pendingRecipe = null;
        runAttempt = runDelay = progress = duration = 0; consecutiveRecipes++;
        lastRecipe = lastUnrolledRecipe = null; isActive = false;
        boolean pause = isSuspendAfterFinish();
        if (getRLMachine() instanceof ISuspendableMachine suspend && suspend.gtlcore$isSuspendAfterFinish()) {
            pause = true; suspend.gtlcore$setSuspendAfterFinish(false);
        }
        setSuspendAfterFinish(false); setStatus(pause ? Status.SUSPEND : Status.IDLE);
        syncDataHolder.resyncAllFields();
        if (!pause) findAndHandleRecipe();
    }
}
