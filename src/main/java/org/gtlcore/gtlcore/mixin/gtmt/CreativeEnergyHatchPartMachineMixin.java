package org.gtlcore.gtlcore.mixin.gtmt;

import org.gtlcore.gtlcore.integration.gtmt.InfinityEnergyContainer;
import org.gtlcore.gtlcore.integration.gtmt.NativeCreativeEnergyUI;
import org.gtlcore.gtlcore.utils.NumberUtils;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.CreativeEnergyHatchPartMachine;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import org.spongepowered.asm.mixin.*;

@Mixin(CreativeEnergyHatchPartMachine.class)
public abstract class CreativeEnergyHatchPartMachineMixin extends TieredIOPartMachine {
    @Shadow(remap = false) @Final public NotifiableEnergyContainer energyContainer;
    @Shadow(remap = false) private long voltage;
    @Shadow(remap = false) private long maxEnergy;
    @Shadow(remap = false) private int setTier;
    @Shadow(remap = false) private int amps;
    @Shadow(remap = false) protected abstract void updateMachine();

    protected CreativeEnergyHatchPartMachineMixin(BlockEntityCreationInfo info, int tier, IO io) { super(info, tier, io); }

    /** @author GTLCore @reason Preserve the original maximum creative voltage using a native attached trait. */
    @Overwrite(remap = false)
    protected NotifiableEnergyContainer createEnergyContainer() {
        setTier = GTValues.MAX_TRUE;
        voltage = GTValues.VEX[setTier];
        maxEnergy = NumberUtils.saturatedMultiply(voltage, amps);
        return new InfinityEnergyContainer(maxEnergy, voltage, amps, 0, 0);
    }

    /** @author GTLCore @reason Preserve exact voltage input and all thirty-one creative tiers in ModularUI. */
    @Overwrite(remap = false)
    public void buildMainUI(ParentWidget<?> parent, PosGuiData data, PanelSyncManager manager, UISettings settings) {
        NativeCreativeEnergyUI.build(parent, manager, () -> voltage, this::setVoltage, () -> amps, this::setAmps, GTValues.ULV);
    }

    /** @author GTLCore @reason Native voltage tiers extend through Long.MAX_VALUE. */
    @Overwrite(remap = false)
    private void setVoltage(long value) {
        if (value < GTValues.VEX[GTValues.ULV]) return;
        voltage = value;
        updateMachine();
    }

    /** @author GTLCore @reason Zero disables the supply; plus controls retain the original integer range. */
    @Overwrite(remap = false)
    private void setAmps(int value) {
        if (value < 0) return;
        amps = value;
        updateMachine();
    }

    /** @author GTLCore @reason Recompute saturated capacity after settings changes and native SaveField reload. */
    @Overwrite(remap = false)
    private void updateEnergyContainer() {
        voltage = Math.max(GTValues.VEX[GTValues.ULV], voltage);
        amps = Math.max(0, amps);
        setTier = NumberUtils.getFakeVoltageTier(voltage);
        maxEnergy = NumberUtils.saturatedMultiply(voltage, amps);
        energyContainer.resetBasicInfo(maxEnergy, voltage, amps, 0, 0);
        energyContainer.setEnergyStored(maxEnergy);
    }
}
