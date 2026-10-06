package org.gtlcore.gtlcore.common.machine.multiblock.generator;

import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import org.gtlcore.gtlcore.common.machine.trait.CombustionEngineRecipeLogic;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Core's engine rates on the native recipe/energy lifecycle and ModularUI. */
public class GTLLargeCombustionEngineMachine extends WorkableElectricMultiblockMachine {
    private final int engineTier;
    @SaveField protected boolean isOxygenBoosted;

    public GTLLargeCombustionEngineMachine(BlockEntityCreationInfo info, int tier) {
        super(info, new CombustionEngineRecipeLogic());
        engineTier = tier;
        getRecipeLogic().setRegressWhenWaiting(false);
    }
    @Override public int getTier() { return engineTier; }
    public boolean isExtreme() { return getTier() > GTValues.EV; }
    protected int intakeRadius() { return 1; }
    public boolean isIntakesObstructed() {
        int radius = intakeRadius();
        for (int x = -radius; x <= radius; x++) for (int y = -radius; y <= radius; y++) {
            if (x == 0 && y == 0) continue;
            var position = RelativeDirection.offsetPos(getBlockPos(), getFrontFacing(), getUpwardsFacing(), isFlipped(), x, y, 1);
            if (!getLevel().getBlockState(position).isAir()) return true;
        }
        return false;
    }
    public boolean isBoostAllowed() { return getMaxVoltage() >= GTValues.V[getTier() + 1]; }
    public boolean isOxygenBoosted() { return isOxygenBoosted; }
    @Override public long getOverclockVoltage() { return GTValues.V[getTier()] * (isOxygenBoosted ? 8 : 4); }
    public GTRecipe getLubricantRecipe() { return GTRecipeBuilder.ofRaw().inputFluids(GTMaterials.Lubricant.getFluid(1)).build(); }
    protected GTRecipe getBoostRecipe() {
        return GTRecipeBuilder.ofRaw().inputFluids(isExtreme() ? GTMaterials.Oxygen.getFluid(FluidStorageKeys.LIQUID, 80) :
                GTMaterials.Oxygen.getFluid(20)).build();
    }
    protected double getProductionBoost() { return isOxygenBoosted ? (isExtreme() ? 2 : 1.5) : 1; }
    public void updateBoost() {
        var boost = getBoostRecipe();
        isOxygenBoosted = isBoostAllowed() && RecipeRunnerHelper.matchRecipeInput(this, boost) && RecipeRunnerHelper.handleRecipeInput(this, boost);
        setChanged();
    }
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof GTLLargeCombustionEngineMachine engine)) return ModifierFunction.NULL;
        if (engine.isIntakesObstructed()) return ModifierFunction.cancel(Component.translatable("gtceu.multiblock.large_combustion_engine.obstructed"));
        if (!RecipeRunnerHelper.matchRecipeInput(engine, engine.getLubricantRecipe()))
            return ModifierFunction.cancel(Component.translatable("gtceu.multiblock.large_combustion_engine.no_lubricant"));
        long energy = recipe.getOutputEUt().getTotalEU();
        if (energy <= 0) return ModifierFunction.NULL;
        int limit = (int) Math.min(Integer.MAX_VALUE, engine.getOverclockVoltage() / energy);
        double boost = engine.getProductionBoost();
        return source -> {
            var result = CoreRecipeModifiers.fastParallel(engine, source, limit, false).getFirst();
            if (result == null) return null;
            return ModifierFunction.builder().eutMultiplier(boost).build().apply(result);
        };
    }
    @Override public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = super.getWidgetsForDisplay(sync);
        var allowed = new BooleanSyncValue(this::isBoostAllowed); sync.syncValue("engineBoostAllowed", allowed);
        var boosted = new BooleanSyncValue(this::isOxygenBoosted); sync.syncValue("engineOxygenBoost", boosted);
        var obstructed = new BooleanSyncValue(this::isIntakesObstructed); sync.syncValue("engineIntakeObstructed", obstructed);
        boolean extreme = isExtreme();
        widgets.add(Text.dynamic(() -> boostStatus(allowed.getBoolValue(), boosted.getBoolValue(), extreme))
                .asWidget().setEnabledIf(w -> isFormed()));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.multiblock.large_combustion_engine.obstructed").withStyle(ChatFormatting.RED))
                .asWidget().setEnabledIf(w -> isFormed() && obstructed.getBoolValue()));
        return widgets;
    }
    protected Component boostStatus(boolean allowed, boolean boosted, boolean extreme) {
        return Component.translatable(!allowed ? "gtceu.multiblock.large_combustion_engine.boost_disallowed" :
                boosted ? (extreme ? "gtceu.multiblock.large_combustion_engine.liquid_oxygen_boosted" : "gtceu.multiblock.large_combustion_engine.oxygen_boosted") :
                        (extreme ? "gtceu.multiblock.large_combustion_engine.supply_liquid_oxygen_to_boost" : "gtceu.multiblock.large_combustion_engine.supply_oxygen_to_boost"));
    }
}
