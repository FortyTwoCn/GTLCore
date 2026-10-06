package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.machine.trait.IFusionHeatTransaction;
import org.gtlcore.gtlcore.api.recipe.NativeFusionStartup;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.gregtechceu.gtceu.api.recipe.ActionResult;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.FusionReactorMachine;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import java.util.List;
import java.util.function.Supplier;

@Mixin(value = FusionReactorMachine.class, remap = false)
public abstract class FusionReactorMachineMixin extends WorkableElectricMultiblockMachine implements IFusionHeatTransaction {
    @Shadow protected long heat;
    @Shadow @Final protected NotifiableEnergyContainer energyContainer;
    @Shadow protected abstract void updatePreHeatSubscription();
    protected FusionReactorMachineMixin(BlockEntityCreationInfo info) { super(info); }

    /** @author GTLCore @reason Native modifiers are pure; accepted recipe IO owns startup heat payment. */
    @Overwrite
    public static ModifierFunction recipeModifier(MetaMachine machine, GTRecipe recipe) {
        if (!(machine instanceof FusionReactorMachine fusion))
            return RecipeModifier.nullWrongType(FusionReactorMachine.class, machine);
        var transaction = (IFusionHeatTransaction) fusion;
        long required = NativeFusionStartup.requiredHeat(recipe);
        if (RecipeHelper.getRecipeEUtTier(recipe) > fusion.getTier() || !NativeFusionStartup.canStart(
                transaction.gtlcore$getFusionEnergy(), transaction.gtlcore$getFusionHeat(), required))
            return ModifierFunction.cancel(NativeFusionStartup.failureReason());
        return FusionReactorMachine.FUSION_OC.getModifier(machine, recipe, fusion.getMaxVoltage(), false);
    }

    @Override public long gtlcore$getFusionHeat() { return heat; }
    @Override public NotifiableEnergyContainer gtlcore$getFusionEnergy() { return energyContainer; }
    @Override public ActionResult gtlcore$withFusionHeat(GTRecipe recipe, Supplier<ActionResult> operation) {
        return NativeFusionStartup.run(energyContainer, heat, NativeFusionStartup.requiredHeat(recipe), required -> {
            heat = required;
            setChanged();
            updatePreHeatSubscription();
        }, operation);
    }

    @Override public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        var widgets = super.getWidgetsForDisplay(syncManager);
        widgets.addAll(GTLMultiblockDisplays.syncedLines("fusionHeat", 164, (controller, lines) -> {
            if (!controller.isFormed()) return;
            var fusion = (IFusionHeatTransaction) controller;
            var energy = fusion.gtlcore$getFusionEnergy();
            lines.add(Component.translatable("gtceu.multiblock.fusion_reactor_energy", energy.getEnergyStored() / 1_000_000,
                    energy.getEnergyCapacity() / 1_000_000));
            lines.add(Component.translatable("gtceu.multiblock.fusion_reactor.heat", fusion.gtlcore$getFusionHeat()));
        }).apply(this, syncManager));
        return widgets;
    }
}
