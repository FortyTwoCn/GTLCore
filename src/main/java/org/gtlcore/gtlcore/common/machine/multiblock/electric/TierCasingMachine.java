package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.pattern.PatternMatchData;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;

import net.minecraft.network.chat.Component;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TierCasingMachine extends WorkableElectricMultiblockMachine {
    @Getter
    protected final String tierType;
    @SyncToClient
    protected int casingTier;

    public int getCasingTier() { return casingTier; }

    public TierCasingMachine(BlockEntityCreationInfo info, String tierType) {
        this(info, tierType, new RecipeLogic());
    }

    public TierCasingMachine(BlockEntityCreationInfo info, String tierType, RecipeLogic logic) {
        super(info, logic);
        this.tierType = Objects.requireNonNull(tierType);
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        if (DEFAULT_STRUCTURE.equals(name)) {
            casingTier = isFormed() ? PatternMatchData.of(getDefaultPatternState().getContext())
                    .tier(tierType).orElse(0) : 0;
            getSyncDataHolder().markClientSyncFieldDirty("casingTier");
        }
    }

    @Override
    public void invalidateStructure(String name) {
        super.invalidateStructure(name);
        if (DEFAULT_STRUCTURE.equals(name)) {
            casingTier = 0;
            getSyncDataHolder().markClientSyncFieldDirty("casingTier");
        }
    }

    @Override
    public boolean beforeWorking(@Nullable GTRecipe recipe) {
        if (recipe != null && recipe.data.contains(tierType) && recipe.data.getInt(tierType) > casingTier) {
            RecipeResult.of(this, RecipeResult.FAIL_NO_ENOUGH_TIER);
            getRecipeLogic().interruptRecipe();
            RecipeLogic.putFailureReason(this, recipe, RecipeResult.FAIL_NO_ENOUGH_TIER.reason());
            return false;
        }
        return super.beforeWorking(recipe);
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        List<IWidget> widgets = new ArrayList<>(super.getWidgetsForDisplay(syncManager));
        var tier = syncManager.getOrCreateSyncHandler("gtlCasingTier", IntSyncValue.class,
                () -> new IntSyncValue(this::getCasingTier));
        var formed = syncManager.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isFormed));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.casings.tier", tier.getIntValue()))
                .asWidget().setEnabledIf(widget -> formed.getBoolValue()));
        return widgets;
    }
}
