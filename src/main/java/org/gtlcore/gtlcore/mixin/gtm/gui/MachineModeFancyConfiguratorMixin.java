package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.api.gui.MachineModeConfigurator;

import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.mui.GTMuiWidgets;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.ButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replace the native mode button factory; the removed 1.20 fancy configurator is never targeted. */
@Mixin(value = GTMuiWidgets.class, remap = false)
public abstract class MachineModeFancyConfiguratorMixin {
    @Inject(method = "createRecipeTypeButton", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtlcore$recipeModeSelector(WorkableElectricMultiblockMachine machine, PanelSyncManager sync,
                                                 CallbackInfoReturnable<ButtonWidget<?>> cir) {
        cir.setReturnValue(MachineModeConfigurator.createButton(machine, sync));
    }
}
