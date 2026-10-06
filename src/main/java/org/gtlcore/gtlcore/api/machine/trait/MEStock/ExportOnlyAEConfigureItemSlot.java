package org.gtlcore.gtlcore.api.machine.trait.MEStock;

import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEItemSlot;

import appeng.api.stacks.GenericStack;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Nullable;



public class ExportOnlyAEConfigureItemSlot extends ExportOnlyAEItemSlot implements IMESlot {

    @Setter
    @Getter
    private Runnable onConfigChanged = () -> {};

    public ExportOnlyAEConfigureItemSlot(@Nullable GenericStack config, @Nullable GenericStack stock) {
        super(config, stock);
    }

    public ExportOnlyAEConfigureItemSlot() {
        super();
    }

    @Override
    public void setConfigWithoutNotify(@Nullable GenericStack config) {
        this.config = config;
    }

    @Override
    public void setConfig(@Nullable GenericStack config) {
        super.setConfig(config);
        onConfigChanged.run();
    }
}
