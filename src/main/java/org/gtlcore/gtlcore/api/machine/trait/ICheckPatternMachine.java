package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;

import net.minecraft.network.chat.Component;

/**
 * 代码参考自gto
 * &#064;line <a href="https://github.com/GregTech-Odyssey/GTOCore">...</a>
 */

public interface ICheckPatternMachine {

    UITexture STRUCTURE_CHECK = UITexture.fullImage(GTCEu.id("textures/gui/structure_check.png"));

    default void setTime(int time) {}

    default int getTime() {
        return 0;
    }

    default boolean hasButton() {
        return false;
    }

    static void attachConfigurators(Flow configuratorPanel, PanelSyncManager sync, MetaMachine machine) {
        if (machine instanceof ICheckPatternMachine checkPatternMachine) {
            var ready = SyncHandlers.bool(() -> checkPatternMachine.getTime() < 1, pressed -> {
                if (checkPatternMachine.getTime() > 0) checkPatternMachine.setTime(0);
            }).allowC2S();
            sync.syncValue("gtlcoreStructureCheck", ready);
            configuratorPanel.child(new ToggleButton().name("gtlcoreStructureCheckButton").size(18).value(ready)
                    .overlay(false, STRUCTURE_CHECK.getSubArea(0, 0, 1, 0.5f))
                    .overlay(true, STRUCTURE_CHECK.getSubArea(0, 0.5f, 1, 1))
                    .tooltip(tooltip -> tooltip.addLine(Component.translatable("gtceu.machine.structure_check"))));
        }
    }
}
