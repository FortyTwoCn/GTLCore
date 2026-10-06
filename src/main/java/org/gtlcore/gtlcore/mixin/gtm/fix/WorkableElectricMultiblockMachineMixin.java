package org.gtlcore.gtlcore.mixin.gtm.fix;

import org.gtlcore.gtlcore.api.machine.trait.IBatchMachine;
import org.gtlcore.gtlcore.api.machine.trait.ILockRecipe;
import org.gtlcore.gtlcore.api.recipe.BatchProcessing;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.RecipeText;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationReceiver;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifierList;
import com.gregtechceu.gtceu.common.data.GTRecipeModifiers;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.research.DataBankMachine;
import com.gregtechceu.gtceu.common.mui.GTByteBufAdapters;
import com.gregtechceu.gtceu.utils.GTUtil;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.*;

/** Native batch persistence and ModularUI; Core time-window batching complements GT's own batch modifier. */
@Mixin(WorkableElectricMultiblockMachine.class)
public abstract class WorkableElectricMultiblockMachineMixin extends WorkableMultiblockMachine implements IBatchMachine {
    public WorkableElectricMultiblockMachineMixin(BlockEntityCreationInfo info) { super(info); }
    @Shadow(remap = false) protected EnergyContainerList energyContainer;
    @Shadow(remap = false) public abstract EnergyContainerList getEnergyContainer();
    @Shadow(remap = false) public abstract boolean isGenerator();

    /** @author GTLCore @reason Keep the controller's one-amp voltage policy with native energy containers. */
    @Overwrite(remap = false)
    public long getOverclockVoltage() {
        if (energyContainer == null) energyContainer = getEnergyContainer();
        return Math.max(energyContainer.getInputVoltage(), energyContainer.getOutputVoltage());
    }
    /** @author GTLCore @reason Preserve the voltage tier increase from two highest-tier input hatches. */
    @Overwrite(remap = false)
    public long getMaxVoltage() {
        if (energyContainer == null) energyContainer = getEnergyContainer();
        return isGenerator() ? energyContainer.getOutputVoltage() : energyContainer.getNumHighestInputContainers() > 1 ?
                GTValues.V[Math.min(GTUtil.getTierByVoltage(energyContainer.getHighestInputVoltage()) + 1, GTValues.MAX)] :
                energyContainer.getHighestInputVoltage();
    }

    @Inject(method = "setBatchEnabled", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$checkBatchMode(boolean enabled, CallbackInfo ci) {
        if (enabled && !gtlcore$nativeBatchModifier() && !supportsBatchProcessing()) ci.cancel();
    }
    @Inject(method = "setBatchEnabled", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$batchChanged(boolean enabled, CallbackInfo ci) {
        setChanged();
        getRecipeLogic().markLastRecipeDirty();
        getRecipeLogic().updateTickSubscription();
    }
    @Unique private boolean gtlcore$nativeBatchModifier() {
        return getDefinition().getRecipeModifier() instanceof RecipeModifierList list &&
                Arrays.stream(list.getModifiers()).anyMatch(modifier -> modifier == GTRecipeModifiers.BATCH_MODE);
    }
    @Unique private boolean gtlcore$baseBatchSupport() {
        return !isGenerator() && !(self() instanceof IOpticalComputationReceiver) &&
                !(self() instanceof IOpticalComputationProvider) && !(self() instanceof DataBankMachine) &&
                Arrays.stream(getRecipeTypes()).anyMatch(type -> type != GTRecipeTypes.DUMMY_RECIPES.value()) && !gtlcore$nativeBatchModifier();
    }
    @Override public boolean supportsBatchProcessing() {
        return gtlcore$baseBatchSupport() && !BatchProcessing.isBatchDisabledDefinition(this) && !BatchProcessing.isCrossRecipeParallel(this);
    }
    @Override public boolean canConfigureBatchProcessing() {
        return gtlcore$baseBatchSupport() && !BatchProcessing.isBatchDisabledDefinition(this) && BatchProcessing.canConfigureBatchProcessing(this);
    }

    @Inject(method = "getWidgetsForDisplay", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$batchAndLockDisplay(PanelSyncManager syncManager, CallbackInfoReturnable<List<IWidget>> cir) {
        IntSyncValue batch = syncManager.getOrCreateSyncHandler("gtlcoreBatchSize", IntSyncValue.class,
                () -> new IntSyncValue(() -> {
                    var recipe = getRecipeLogic().getLastUnrolledRecipe();
                    return recipe == null ? 1 : IGTRecipe.of(recipe).getBatchSize();
                }));
        cir.getReturnValue().add(Text.dynamic(() -> Component.translatable("gui.gtlcore.batch_processing.active", batch.getIntValue()))
                .asWidget().setEnabledIf(widget -> batch.getIntValue() > 1));
        GenericSyncValue<FriendlyByteBuf, Component> lockText = syncManager.getOrCreateSyncHandler("gtlcoreRecipeLock", GenericSyncValue.class,
                () -> GenericSyncValue.<FriendlyByteBuf, Component>builder(Component.class).nullable()
                        .adapter(GTByteBufAdapters.COMPONENT).getter(() -> {
                            if (!(getRecipeLogic() instanceof ILockRecipe lock) || !lock.isLock()) return null;
                            var recipe = lock.getLockRecipe();
                            if (recipe == null) return Component.translatable("gui.gtlcore.recipe_lock.no_recipe");
                            return Component.translatable("gui.gtlcore.recipe_lock.recipe").withStyle(style -> style.withHoverEvent(
                                    new HoverEvent(HoverEvent.Action.SHOW_TEXT, RecipeText.getRecipeInputText(recipe).append(RecipeText.getRecipeOutputText(recipe)))));
                        }).build());
        cir.getReturnValue().add(Text.dynamic(() -> lockText.getValue() == null ? Component.empty() : lockText.getValue())
                .asWidget().setEnabledIf(widget -> lockText.getValue() != null));
    }
}
