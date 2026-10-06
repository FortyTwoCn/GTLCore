package org.gtlcore.gtlcore.api.machine.trait.MEStock;

import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidSlot;

import appeng.api.stacks.GenericStack;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;



public class ExportOnlyAEConfigureFluidSlot extends ExportOnlyAEFluidSlot implements IMESlot {

    @Getter
    @Setter
    private Runnable onConfigChanged = () -> {};

    public ExportOnlyAEConfigureFluidSlot(@Nullable GenericStack config, @Nullable GenericStack stock) {
        super(config, stock);
    }

    public ExportOnlyAEConfigureFluidSlot() {
        super();
    }

    @Override
    public void setConfig(@Nullable GenericStack config) {
        super.setConfig(config);
        onConfigChanged.run();
    }

    @Override
    public void setConfigWithoutNotify(@Nullable GenericStack config) {
        this.config = config;
    }
}
