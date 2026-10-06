package org.gtlcore.gtlcore.common.machine.multiblock.generator;

import org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Chemical engine: 7x7 intake, two boosters and the original IV production multipliers. */
public class ChemicalEnergyDevourerMachine extends GTLLargeCombustionEngineMachine {
    @SaveField private boolean isDinitrogenTetroxideBoosted;
    public ChemicalEnergyDevourerMachine(BlockEntityCreationInfo info) { super(info, GTValues.IV); }
    @Override protected int intakeRadius() { return 3; }
    @Override public boolean isBoostAllowed() { return getMaxVoltage() >= GTValues.V[getTier() + 3]; }
    @Override public long getOverclockVoltage() {
        return GTValues.V[getTier()] * (isOxygenBoosted && isDinitrogenTetroxideBoosted ? 128 : isOxygenBoosted ? 64 : 32);
    }
    @Override public GTRecipe getLubricantRecipe() { return GTRecipeBuilder.ofRaw().inputFluids(GTMaterials.Lubricant.getFluid(2)).build(); }
    @Override protected GTRecipe getBoostRecipe() { return GTRecipeBuilder.ofRaw().inputFluids(GTMaterials.Oxygen.getFluid(FluidStorageKeys.LIQUID, 120)).build(); }
    protected GTRecipe getBoostRecipea() { return GTRecipeBuilder.ofRaw().inputFluids(GTMaterials.DinitrogenTetroxide.getFluid(80)).build(); }
    @Override protected double getProductionBoost() { return isOxygenBoosted ? (isDinitrogenTetroxideBoosted ? 4 : 2) : 1; }
    public boolean isDinitrogenTetroxideBoosted() { return isDinitrogenTetroxideBoosted; }
    @Override public void updateBoost() {
        super.updateBoost();
        var boost = getBoostRecipea();
        isDinitrogenTetroxideBoosted = isBoostAllowed() && RecipeRunnerHelper.matchRecipeInput(this, boost) && RecipeRunnerHelper.handleRecipeInput(this, boost);
        setChanged();
    }
    @Override public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = super.getWidgetsForDisplay(sync);
        var oxygen = new BooleanSyncValue(this::isOxygenBoosted); sync.syncValue("chemicalEngineOxygen", oxygen);
        var nitro = new BooleanSyncValue(this::isDinitrogenTetroxideBoosted); sync.syncValue("chemicalEngineNitro", nitro);
        widgets.add(Text.dynamic(() -> Component.translatable(nitro.getBoolValue() ?
                        "gtceu.multiblock.large_combustion_engine.Joint_boosted" : "gtceu.multiblock.large_combustion_engine.supply_dinitrogen_tetroxide_to_boost"))
                .asWidget().setEnabledIf(w -> isFormed() && oxygen.getBoolValue()));
        return widgets;
    }
}
