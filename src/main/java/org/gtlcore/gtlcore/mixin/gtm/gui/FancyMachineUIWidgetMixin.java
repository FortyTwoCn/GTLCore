package org.gtlcore.gtlcore.mixin.gtm.gui;

import org.gtlcore.gtlcore.client.gui.MEStorageConfiguratorTabLayout;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanel;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.integration.ae2.machine.MEHatchPartMachine;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Core's four-controls-per-column layout over the actual native panel builder. */
@Mixin(value = MachineUIPanelBuilder.class, remap = false)
public abstract class FancyMachineUIWidgetMixin {
    @Shadow @Final private MetaMachine machine;

    @Inject(method = "build", at = @At("RETURN"), require = 1)
    private void gtlcore$configureMEStorageColumns(PanelSyncManager sync, UISettings settings,
                                                  CallbackInfoReturnable<MachineUIPanel> cir) {
        var nativePanel = cir.getReturnValue();
        nativePanel.getLeftConfiguratorPanel().child(org.gtlcore.gtlcore.api.gui.NativeCoverUI.button(machine, nativePanel, sync));
        if (machine instanceof MEHatchPartMachine || machine instanceof MEPatternBufferPartMachineBase) {
            var panel = cir.getReturnValue();
            MEStorageConfiguratorTabLayout.arrange(panel.getLeftConfiguratorPanel(), true);
            MEStorageConfiguratorTabLayout.arrange(panel.getRightConfiguratorPanel(), false);
        }
    }
}
