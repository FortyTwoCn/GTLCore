package org.gtlcore.gtlcore.api.machine.trait;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import brachy.modularui.drawable.ItemDrawable;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;
import net.minecraft.network.chat.Component;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.dust;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.dustSmall;
import static com.gregtechceu.gtceu.common.data.GTMaterials.Gold;

public interface IBatchMachine {
    boolean isBatchEnabled();
    void setBatchEnabled(boolean enabled);
    default boolean supportsBatchProcessing() { return true; }
    default boolean canConfigureBatchProcessing() { return supportsBatchProcessing(); }

    static void attachBatchConfigurator(Flow configurators, WorkableElectricMultiblockMachine machine) {
        if (!(machine instanceof IBatchMachine batch) || !batch.canConfigureBatchProcessing()) return;
        var value = new BooleanSyncValue(() -> batch.supportsBatchProcessing() && batch.isBatchEnabled(), batch::setBatchEnabled).allowC2S();
        configurators.child(new ToggleButton().value(value)
                .background(GTGuiTextures.BUTTON)
                .overlay(new ItemDrawable(ChemicalHelper.get(dustSmall, Gold)))
                .overlay(true, new ItemDrawable(ChemicalHelper.get(dust, Gold)))
                .tooltipAutoUpdate(true)
                .tooltipBuilder(tooltip -> tooltip.addLine(Component.translatable(
                        !batch.supportsBatchProcessing() ? "gui.gtlcore.batch_processing.unsupported_mode" :
                                value.getBoolValue() ? "gui.gtlcore.batch_processing.enabled" : "gui.gtlcore.batch_processing.disabled"))));
    }
}
