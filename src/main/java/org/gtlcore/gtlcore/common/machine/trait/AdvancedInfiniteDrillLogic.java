package org.gtlcore.gtlcore.common.machine.trait;

import org.gtlcore.gtlcore.api.machine.ISuspendableMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.AdvancedInfiniteDrillMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.material.Fluid;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Native attached advanced-rig trait. Searches are pure; only working ticks pay energy. */
public class AdvancedInfiniteDrillLogic extends RecipeLogic {
    public static final int MAX_PROGRESS = 20;
    private final Map<Fluid, Long> veinFluids = new LinkedHashMap<>();
    private int range;

    public AdvancedInfiniteDrillLogic(int range) { super(); setRange(range); }
    public int getRange() { return range; }
    public void setRange(int range) {
        if (range <= 0 || (range & 1) == 0) throw new IllegalArgumentException("Drilling range must be a positive odd number");
        this.range = range; veinFluids.clear();
    }
    @Override public AdvancedInfiniteDrillMachine getMachine() { return (AdvancedInfiniteDrillMachine) super.getMachine(); }
    @Override protected List<Class<?>> validMachineClasses() { return List.of(AdvancedInfiniteDrillMachine.class); }
    public Map<Fluid, Long> getVeinFluids() {
        if (veinFluids.isEmpty() && getMachine().getLevel() instanceof ServerLevel level)
            getGridFluid(BedrockFluidVeinSavedData.getOrCreate(level));
        return Collections.unmodifiableMap(veinFluids);
    }

    @Override public void findAndHandleRecipe() {
        if (isSuspend()) return;
        lastRecipe = null;
        var recipe = nextRecipe();
        if (recipe != null) setupRecipe(recipe);
    }

    protected @Nullable GTRecipe nextRecipe() {
        var machine = getMachine();
        if (!(machine.getLevel() instanceof ServerLevel level)) return null;
        getGridFluid(BedrockFluidVeinSavedData.getOrCreate(level));
        if (machine.isEmpty() || !machine.canRunnable()) return null;
        var recipe = AdvancedFluidDrillProduction.recipe(veinFluids, machine.getRate());
        if (recipe == null || !RecipeHelper.matchContents(machine, recipe).isSuccess()) return null;
        // The original rig admits a cycle only if all twenty energy ticks are available.
        long fullCost = Math.multiplyExact(recipe.getInputEUt().getTotalEU(), MAX_PROGRESS);
        var gate = com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder.ofRaw().duration(1).EUt(fullCost).build();
        return RecipeHelper.matchTickRecipe(machine, gate).isSuccess() ? recipe : null;
    }

    protected void getGridFluid(BedrockFluidVeinSavedData data) {
        veinFluids.clear();
        int x = SectionPos.blockToSectionCoord(getMachine().getBlockPos().getX());
        int z = SectionPos.blockToSectionCoord(getMachine().getBlockPos().getZ());
        int mid = range / 2;
        try {
            for (int dx = -mid; dx <= mid; dx++) for (int dz = -mid; dz <= mid; dz++) {
                var entry = data.getFluidVeinWorldEntry(x + dx, z + dz);
                long amount = BedrockFluidProduction.amount(entry, 10, false);
                if (entry == null || entry.getDefinition() == null || amount <= 0) continue;
                Fluid fluid = entry.getDefinition().value().getStoredFluid();
                veinFluids.put(fluid, Math.addExact(veinFluids.getOrDefault(fluid, 0L), amount));
            }
        } catch (ArithmeticException overflow) { veinFluids.clear(); }
    }

    @Override public void onRecipeFinish() {
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
        if (!pause) { var next = nextRecipe(); if (next != null) { setupRecipe(next); return; } }
        setStatus(pause ? Status.SUSPEND : Status.IDLE);
        setSuspendAfterFinish(false);
        progress = 0; duration = 0; isActive = false;
        lastRecipe = null; lastUnrolledRecipe = null;
        syncDataHolder.resyncAllFields();
    }

    private void waitForOutput(@Nullable Component reason) {
        boolean pause = suspendAfterFinish; suspendAfterFinish = false;
        try { setWaiting(reason); } finally { suspendAfterFinish = pause; }
    }
}
