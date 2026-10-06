package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.common.data.GTLRecipeModifiers;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.DoubleSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import java.util.List;

/** Native catalyst storage, selected-input parallel modifier, overclocking and synced multiplier display. */
public class PCBFactoryMachine extends StorageMachine {
    public PCBFactoryMachine(BlockEntityCreationInfo info) { super(info, 64); }
    public PCBFactoryMachine(BlockEntityCreationInfo info, RecipeLogic logic) { super(info, 64, logic); }
    private double reductionEUt = 1, reductionDuration = 1;

    private void getPCBReduction() {
        var stack = getMachineStorageItem();
        reductionEUt = 1; reductionDuration = 1;
        var id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (id.equals("gtceu:vibranium_nanoswarm")) {
            reductionDuration = Math.max((100.0 - stack.getCount()) / 100, .36);
            reductionEUt = .25;
        } else if (id.equals("gtceu:gold_nanoswarm")) {
            reductionDuration = Math.max((100.0 - stack.getCount() * .5) / 100, .68);
        }
    }
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof PCBFactoryMachine pcb)) return ModifierFunction.NULL;
        return source -> {
            pcb.getPCBReduction();
            var reduced = GTLRecipeModifiers.reduction(pcb, source, pcb.reductionEUt, pcb.reductionDuration);
            var parallel = GTRecipeModifiers.hatchParallel(pcb, reduced).apply(reduced);
            return parallel == null ? null : OverclockingLogic.PERFECT_OVERCLOCK_SUBTICK.getModifier(pcb, parallel, pcb.getOverclockVoltage(), true).apply(parallel);
        };
    }
    @Override public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = super.getWidgetsForDisplay(sync);
        var energy = new DoubleSyncValue(() -> { getPCBReduction(); return reductionEUt; });
        var duration = new DoubleSyncValue(() -> { getPCBReduction(); return reductionDuration; });
        sync.syncValue("pcbEnergyMultiplier", energy); sync.syncValue("pcbDurationMultiplier", duration);
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.machine.eut_multiplier.tooltip", energy.getDoubleValue())).asWidget().setEnabledIf(widget -> isFormed()));
        widgets.add(Text.dynamic(() -> Component.translatable("gtceu.machine.duration_multiplier.tooltip", duration.getDoubleValue())).asWidget().setEnabledIf(widget -> isFormed()));
        return widgets;
    }
}
