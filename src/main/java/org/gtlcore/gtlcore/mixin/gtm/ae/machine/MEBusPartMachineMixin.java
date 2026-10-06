package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.integration.ae2.AEGridMachineSettings;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.integration.ae2.machine.MEBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MEBusPartMachine.class)
public abstract class MEBusPartMachineMixin extends ItemBusPartMachine implements IGridConnectedMachine, IModifiableSyncOffset {
    @Unique @SaveField
    private int gtlcore$syncOffset;

    public MEBusPartMachineMixin(BlockEntityCreationInfo info, int tier, IO io) { super(info, tier, io); }

    @Override public void onLoad() {
        AEGridMachineSettings.applyExposedSides(this, getFrontFacing());
        super.onLoad();
    }

    @Inject(method = "onRotated(Lnet/minecraft/core/Direction;Lnet/minecraft/core/Direction;)V",
            at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$restoreExposedSides(Direction oldFacing, Direction newFacing, CallbackInfo ci) {
        AEGridMachineSettings.applyExposedSides(this, newFacing);
    }

    @Override public boolean shouldSyncME() {
        int interval = gtlcore$syncOffset == 0 ? ConfigHolder.INSTANCE.compat.ae2.updateIntervals : gtlcore$syncOffset;
        return getOffsetTimer() % Math.max(1, interval) == 0;
    }

    @Override public int getMEUpdateInterval() { return gtlcore$syncOffset; }

    @Override public void setMEUpdateInterval(int offset) {
        if (offset < 0) throw new IllegalArgumentException("ME refresh interval must not be negative");
        gtlcore$syncOffset = offset;
        markAsChanged();
    }
}
