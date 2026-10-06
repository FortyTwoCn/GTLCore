package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.pattern.PatternMatchData;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;

import net.minecraft.network.chat.Component;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class AdvancedAssemblyLineMachine extends WorkableElectricMultiblockMachine {
    @Getter
    @SyncToClient
    private int speed;

    public AdvancedAssemblyLineMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof AdvancedAssemblyLineMachine line)) {
            return RecipeModifier.nullWrongType(AdvancedAssemblyLineMachine.class, machine);
        }
        return GTRecipeModifiers.hatchParallel(machine, recipe)
                .andThen(ModifierFunction.builder().durationMultiplier(line.speed / 100.0).build())
                .andThen(modified -> OverclockingLogic.NON_PERFECT_OVERCLOCK
                        .getModifier(machine, modified, line.getOverclockVoltage()).apply(modified));
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        if (DEFAULT_STRUCTURE.equals(name)) {
            speed = isFormed() ? 100 - PatternMatchData.of(getDefaultPatternState().getContext())
                    .blockCount("Unit") : 0;
            getSyncDataHolder().markClientSyncFieldDirty("speed");
        }
    }

    @Override
    public void invalidateStructure(String name) {
        super.invalidateStructure(name);
        if (DEFAULT_STRUCTURE.equals(name)) {
            speed = 0;
            getSyncDataHolder().markClientSyncFieldDirty("speed");
        }
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        List<IWidget> widgets = new ArrayList<>(super.getWidgetsForDisplay(syncManager));
        var value = syncManager.getOrCreateSyncHandler("gtlAssemblySpeed", IntSyncValue.class,
                () -> new IntSyncValue(this::getSpeed));
        var formed = syncManager.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isFormed));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.machine.duration_multiplier.tooltip",
                value.getIntValue()).append("%"))
                .asWidget().setEnabledIf(widget -> formed.getBoolValue()));
        return widgets;
    }
}
