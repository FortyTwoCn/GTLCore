package org.gtlcore.gtlcore.mixin.gtm.machine;

import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.slot.ItemSlot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native rotor lifecycle, interaction safety and GUI are kept; Core's filter and failure reasons are applied. */
@Mixin(RotorHolderPartMachine.class)
public abstract class RotorHolderPartMachineMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$filterRotors(BlockEntityCreationInfo info, int tier, CallbackInfo ci) {
        ((RotorHolderPartMachine) (Object) this).inventory.setFilter(stack -> stack.is(GTItems.TURBINE_ROTOR.asItem()));
    }
    @Inject(method = "modifyRecipe", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$rotorFailure(GTRecipe recipe, CallbackInfoReturnable<GTRecipe> cir) {
        var holder = (RotorHolderPartMachine) (Object) this;
        if (holder.hasRotor() && holder.isFrontFaceFree()) return;
        var failure = RecipeResult.fail(Component.translatable(holder.hasRotor() ? "gtceu.multiblock.universal.rotor_obstructed" : "gtceu.recipe.fail.rotor.isEmpty"));
        for (var controller : holder.getControllers()) if (controller instanceof IRecipeLogicMachine machine) RecipeResult.of(machine, failure);
        cir.setReturnValue(null);
    }
    @Inject(method = "buildMainUI", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$rotorTooltip(ParentWidget<?> main, PosGuiData data, PanelSyncManager sync, UISettings settings, CallbackInfo ci) {
        for (var child : main.getChildren()) if (child instanceof ItemSlot slot)
            slot.tooltip(tooltip -> tooltip.add(Text.comp(Component.translatable("tooltip.gtlcore.turbine_rotor_only"))));
    }
}
