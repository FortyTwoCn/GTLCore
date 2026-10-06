package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.pattern.PatternMatchData;
import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.api.recipe.IAdvancedContentModifier;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeCacheStrategy;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import org.gtlcore.gtlcore.common.machine.trait.FissionReactorThermal;
import org.gtlcore.gtlcore.common.machine.trait.FissionReactorRecipeLogic;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.ConditionalSubscriptionHandler;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;
import java.util.Collections;
import java.util.List;

/** Native reactor lifecycle, accepted structure accounting and preflighted coolant exchanges. */
public class FissionReactorMachine extends WorkableElectricMultiblockMachine {
    @SaveField @SyncToClient private int heat = FissionReactorThermal.AMBIENT;
    @SaveField @SyncToClient private int damaged;
    @SaveField private int parallel;
    @SaveField private int recipeHeat;
    @SyncToClient private int fuel, cooler, heatAdjacent = 1, coolerAdjacent;
    protected final ConditionalSubscriptionHandler heatSubs;
    private boolean thermalReady;

    public FissionReactorMachine(BlockEntityCreationInfo info) {
        super(info, new FissionReactorRecipeLogic());
        getRecipeLogic().setRegressWhenWaiting(false);
        heatSubs = new ConditionalSubscriptionHandler(this, this::heatUpdate, () -> isFormed() && thermalReady);
    }
    public int getHeat() { return heat; }
    public int getDamage() { return damaged; }
    public int getFuelCount() { return fuel; }
    public int getCoolerCount() { return cooler; }
    public int getFuelAdjacency() { return heatAdjacent - 1; }
    public int getCoolerAdjacency() { return coolerAdjacent; }
    public int getRunningParallel() { return parallel; }
    public int getRecipeHeat() { return recipeHeat; }
    public BlockPos getCentrePos() { return getBlockPos().relative(getFrontFacing().getOpposite(), 4).above(4); }

    public static int adjacent(Level level, BlockPos pos, Block block) {
        int count = 0;
        for (var direction : Direction.values()) if (level.getBlockState(pos.relative(direction)).is(block)) count++;
        return count;
    }
    @Override public void formStructure(String name) {
        super.formStructure(name);
        if (!getPatternState(name).isFormed()) return;
        var accepted = PatternMatchData.of(getPatternState(name).getContext());
        fuel = accepted.blockCount("FuelAssembly"); cooler = accepted.blockCount("Cooler");
        heatAdjacent = FissionReactorThermal.adjacentPairs(accepted.blockPositions("FuelAssembly")) + 1;
        coolerAdjacent = FissionReactorThermal.adjacentPairs(accepted.blockPositions("Cooler"));
        thermalReady = true; syncStructure(); heatSubs.initialize(getLevel()); getRecipeLogic().updateTickSubscription();
    }
    private void syncStructure() {
        for (var field : List.of("fuel", "cooler", "heatAdjacent", "coolerAdjacent")) getSyncDataHolder().markClientSyncFieldDirty(field);
    }
    private void clearThermalParts() {
        thermalReady = false; heatSubs.unsubscribe(); fuel = cooler = coolerAdjacent = 0; heatAdjacent = 1; syncStructure();
    }
    @Override public void invalidateStructure(String name) {
        super.invalidateStructure(name); clearThermalParts(); parallel = recipeHeat = 0; markAsChanged();
    }
    @Override public void onPartUnload() {
        clearThermalParts(); super.onPartUnload();
        // Rebind current block entities after a part reload without resetting the paid recipe.
        for (var state : patternStates.values()) {
            state.getCache().clear(); state.setState(com.gregtechceu.gtceu.api.multiblock.pattern.PatternState.CheckState.UNINITIALIZED);
        }
    }
    @Override public void onUnload() { thermalReady = false; heatSubs.unsubscribe(); super.onUnload(); }
    @Override public boolean isRecipeLogicAvailable() { return thermalReady && super.isRecipeLogicAvailable(); }

    @Override public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (recipe == null || recipe.data.getInt("FRheat") < 0 || !isFormed()) return false;
        long effective = IGTRecipe.of(recipe).getRealParallels();
        if (effective <= 0 || effective > fuel || !super.beforeWorking(recipe)) return false;
        parallel = Math.toIntExact(effective); recipeHeat = recipe.data.getInt("FRheat"); markAsChanged(); return true;
    }
    @Override public void afterWorking() { parallel = recipeHeat = 0; markAsChanged(); super.afterWorking(); }

    public void doExplosion(BlockPos pos, float explosionPower) {
        var level = getLevel();
        if (level != null && !level.isClientSide()) {
            level.removeBlock(getBlockPos(), false);
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    explosionPower, Level.ExplosionInteraction.BLOCK);
        }
    }
    private static Fluid requiredCoolantOutput(String name) {
        var id = ResourceLocation.fromNamespaceAndPath("gtceu", name);
        var material = GTRegistries.MATERIALS.get(id);
        if (material == null) throw new IllegalStateException("Required Core coolant material is not registered: " + id);
        return material.getFluid();
    }
    private boolean exchangeCoolant(Fluid input, long inputAmount, Fluid output, long outputAmount) {
        if (inputAmount <= 0 || outputAmount <= 0) return false;
        var recipe = GTRecipeBuilder.ofRaw()
                .inputFluids(LongFluidIngredient.create(new FluidStack(input, 1), inputAmount))
                .outputFluids(LongFluidIngredient.create(new FluidStack(output, 1), outputAmount)).build();
        if (!coolantIO(recipe, IO.OUT, true) || !coolantIO(recipe, IO.IN, true)) return false;
        // Native handler simulation and execution happen on the same server tick, without a yield.
        if (!coolantIO(recipe, IO.IN, false)) return false;
        if (!coolantIO(recipe, IO.OUT, false)) throw new IllegalStateException("Preflighted reactor coolant output changed during native transfer");
        return true;
    }
    private boolean coolantIO(GTRecipe recipe, IO io, boolean simulate) {
        return RecipeRunnerHelper.handleRecipe(io, this, io == IO.IN ? recipe.inputs : recipe.outputs,
                Collections.emptyMap(), false, recipe, simulate, RecipeCacheStrategy.NO_CACHE).isSuccess();
    }
    private boolean inputWater(long amount) {
        try { return exchangeCoolant(GTMaterials.DistilledWater.getFluid(), FissionReactorThermal.waterAmount(amount),
                GTMaterials.Steam.getFluid(), FissionReactorThermal.steamAmount(amount, heat)); }
        catch (ArithmeticException overflow) { return false; }
    }
    private boolean inputSodiumPotassium(long amount) {
        try {
            long quantity = FissionReactorThermal.sodiumPotassiumAmount(amount);
            return exchangeCoolant(GTMaterials.SodiumPotassium.getFluid(), quantity,
                    requiredCoolantOutput(heat > 825 ? "supercritical_sodium_potassium" : "hot_sodium_potassium"), quantity);
        } catch (ArithmeticException overflow) { return false; }
    }
    protected void heatUpdate() {
        if (!thermalReady || !isFormed() || isRemote() || getOffsetTimer() % 20 != 0) return;
        int oldHeat = heat, oldDamage = damaged;
        if (heat > FissionReactorThermal.MAX_HEAT) {
            if (damaged > 99) { doExplosion(getCentrePos(), fuel); return; }
            damaged = (int) Math.min(Integer.MAX_VALUE, (long) damaged + Math.max(1, heatAdjacent / 6));
        }
        if (getRecipeLogic().isWorking()) {
            long required = FissionReactorThermal.requiredCooling(recipeHeat, parallel, heat);
            long surplus = FissionReactorThermal.coolerCapacity(cooler, coolerAdjacent) - required;
            boolean cooled = false;
            if (required > 0 && surplus >= 0) {
                if (inputWater(required)) { accelerate(required, surplus, true); cooled = true; }
                else if (inputSodiumPotassium(required)) { accelerate(required, surplus, false); cooled = true; }
            }
            if (!cooled) heat = FissionReactorThermal.heated(heat, recipeHeat, heatAdjacent);
        } else if (heat > FissionReactorThermal.AMBIENT) heat--;
        else if (damaged > 0) damaged--;
        if (oldHeat != heat || oldDamage != damaged) {
            getSyncDataHolder().markClientSyncFieldDirty("heat"); getSyncDataHolder().markClientSyncFieldDirty("damaged"); markAsChanged();
        }
    }
    private void accelerate(long required, long surplus, boolean water) {
        var logic = getRecipeLogic();
        while (surplus >= required && logic.getProgress() < logic.getDuration()) {
            if (!(water ? inputWater(required) : inputSodiumPotassium(required))) break;
            surplus -= required;
            logic.setProgress((int) Math.min(logic.getDuration(), (long) logic.getProgress() + 20));
            logic.getSyncDataHolder().markClientSyncFieldDirty("progress"); markAsChanged();
        }
        if (heat > FissionReactorThermal.AMBIENT && surplus >= required &&
                (water ? inputWater(required) : inputSodiumPotassium(required))) heat--;
    }
    @Override public int getOutputSignal(@Nullable Direction side) { return FissionReactorThermal.signal(heat); }
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof FissionReactorMachine reactor) || !reactor.isFormed() || recipe.data.getInt("FRheat") < 0) return ModifierFunction.NULL;
        long base = IGTRecipe.of(recipe).getRealParallels();
        if (base <= 0 || base > reactor.fuel) return ModifierFunction.NULL;
        int amount = CoreRecipeModifiers.parallelAmount(reactor, recipe, Math.toIntExact(reactor.fuel / base));
        return amount <= 0 ? ModifierFunction.NULL : ModifierFunction.builder()
                .modifyAllContents(IAdvancedContentModifier.preciseMultiplier(amount)).eutMultiplier(amount).parallels(amount).build();
    }
    public void addDisplayText(List<Component> text) {
        if (!isFormed() || !thermalReady) return;
        text.add(Component.translatable("gtceu.machine.fission_reactor.fuel", fuel, heatAdjacent - 1));
        text.add(Component.translatable("gtceu.machine.fission_reactor.cooler", cooler, coolerAdjacent));
        text.add(Component.translatable("gtceu.machine.fission_reactor.heat", heat));
        text.add(Component.translatable("gtceu.machine.fission_reactor.damaged", damaged).append("%"));
    }
    @Override public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = super.getWidgetsForDisplay(sync);
        widgets.addAll(GTLMultiblockDisplays.syncedLines("fissionThermal", 166,
                (machine, rows) -> ((FissionReactorMachine) machine).addDisplayText(rows)).apply(this, sync));
        return widgets;
    }
}
