package org.gtlcore.gtlcore.mixin.gtmt;

import org.gtlcore.gtlcore.integration.gtmt.InfinityLaserContainer;
import org.gtlcore.gtlcore.integration.gtmt.NativeCreativeEnergyUI;
import org.gtlcore.gtlcore.utils.NumberUtils;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableLaserContainer;
import com.hepdd.gtmthings.common.block.machine.multiblock.part.CreativeLaserHatchPartMachine;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(CreativeLaserHatchPartMachine.class)
public abstract class CreativeLaserHatchPartMachineMixin extends TieredIOPartMachine {
    @Shadow(remap = false) private NotifiableLaserContainer buffer;
    @Shadow(remap = false) private long voltage;
    @Shadow(remap = false) private Long maxEnergy;
    @Shadow(remap = false) private int setTier;
    @Shadow(remap = false) private int amps;

    protected CreativeLaserHatchPartMachineMixin(BlockEntityCreationInfo info, int tier, IO io) { super(info, tier, io); }

    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lcom/hepdd/gtmthings/common/block/machine/multiblock/part/CreativeLaserHatchPartMachine;voltage:J", opcode = Opcodes.PUTFIELD), remap = false, require = 1)
    private void gtlcore$maximumVoltage(CreativeLaserHatchPartMachine machine, long original) {
        setTier = GTValues.MAX_TRUE;
        voltage = GTValues.VEX[setTier];
    }

    @Redirect(method = "<init>", at = @At(value = "FIELD", target = "Lcom/hepdd/gtmthings/common/block/machine/multiblock/part/CreativeLaserHatchPartMachine;maxEnergy:Ljava/lang/Long;", opcode = Opcodes.PUTFIELD), remap = false, require = 1)
    private void gtlcore$capacity(CreativeLaserHatchPartMachine machine, Long original) {
        maxEnergy = NumberUtils.saturatedMultiply(voltage, amps);
    }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/api/machine/trait/notifiable/NotifiableLaserContainer;receiverContainer(JJJ)Lcom/gregtechceu/gtceu/api/machine/trait/notifiable/NotifiableLaserContainer;"), remap = false, require = 1)
    private NotifiableLaserContainer gtlcore$creativeLaser(long capacity, long inputVoltage, long inputAmperage) {
        return new InfinityLaserContainer(maxEnergy, voltage, amps, 0, 0);
    }

    /** @author GTLCore @reason Preserve exact voltage input and all twenty-six laser tiers in ModularUI. */
    @Overwrite(remap = false)
    public void buildMainUI(ParentWidget<?> parent, PosGuiData data, PanelSyncManager manager, UISettings settings) {
        NativeCreativeEnergyUI.build(parent, manager, () -> voltage, value -> {
            if (value >= GTValues.VEX[GTValues.IV]) { voltage = value; gtlcore$update(); }
        }, () -> amps, value -> {
            if (value >= 0) { amps = value; gtlcore$update(); }
        }, GTValues.IV);
    }

    @Unique
    private void gtlcore$reset() {
        voltage = Math.max(GTValues.VEX[GTValues.IV], voltage);
        amps = Math.max(0, amps);
        setTier = NumberUtils.getFakeVoltageTier(voltage);
        maxEnergy = NumberUtils.saturatedMultiply(voltage, amps);
        buffer.resetBasicInfo(maxEnergy, voltage, amps, 0, 0);
        buffer.setEnergyStored(maxEnergy);
    }

    @Unique
    private void gtlcore$update() {
        gtlcore$reset();
        markAsChanged();
        if (!isRemote()) for (var controller : getControllers()) controller.invalidateAllStructures();
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        gtlcore$reset();
    }

    /** @author GTLCore @reason The creative trait supplies recipes without a separate refill tick. */
    @Overwrite(remap = false)
    protected void AddEngerySubscription() {}

    /** @author GTLCore @reason The creative trait remains full after every accepted recipe. */
    @Overwrite(remap = false)
    protected void addEng() {}
}
