package org.gtlcore.gtlcore.api.machine.multiblock;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;

import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.drawable.Icon;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.utils.Alignment;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ListWidget;

/** A native recipe multiblock with a text panel and no electric energy trait. */
public class NoEnergyMultiblockMachine extends WorkableMultiblockMachine implements IMuiMachine {
    public NoEnergyMultiblockMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    public NoEnergyMultiblockMachine(BlockEntityCreationInfo info, RecipeLogic logic) {
        super(info, logic);
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData,
                            PanelSyncManager syncManager, UISettings settings) {
        var panel = new ParentWidget<>().size(182, 117).background(GuiTextures.DISPLAY);
        var text = new ListWidget<>().size(176, 111)
                .childSeparator(Icon.EMPTY_2PX)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .collapseDisabledChildren()
                .left(3).top(3);
        text.children(getWidgetsForDisplay(syncManager));
        panel.child(text);
        mainWidget.child(panel.margin(4, 2));
    }
}
