package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.GenericStack;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.function.Predicate;

@Mixin(MEStockingHatchPartMachine.class)
public abstract class MEStockingHatchPartMachineMixin extends MEInputHatchPartMachine implements IModifiableSyncOffset {
    @Shadow(remap = false) public abstract void setAutoPullTest(Predicate<GenericStack> predicate);
    @Shadow(remap = false) public abstract boolean isAutoPull();
    @Shadow(remap = false) public abstract int getTicksPerCycle();
    @Shadow(remap = false) private void refreshList() { throw new AssertionError(); }
    protected MEStockingHatchPartMachineMixin(BlockEntityCreationInfo info) { super(info); }
    @Override public void setWorkingEnabled(boolean enabled) {
        super.setWorkingEnabled(enabled);
        if (aeFluidHandler instanceof IOptimizedMEList list) list.onConfigChanged();
        if (aeFluidHandler != null) aeFluidHandler.notifyListeners();
    }
    @Inject(method = "<init>(Lcom/gregtechceu/gtceu/api/blockentity/BlockEntityCreationInfo;)V", at = @At("TAIL"), remap = false)
    private void gtlcore$standalone(BlockEntityCreationInfo info, CallbackInfo ci) { setAutoPullTest(stack -> true); }
    @Inject(method = "removedFromController", at = @At("RETURN"), remap = false)
    private void gtlcore$standaloneAfterRemoval(MultiblockControllerMachine controller, CallbackInfo ci) { setAutoPullTest(stack -> true); }
    @Override public void onLoad() {
        super.onLoad(); scheduleForNextServerTick(this::gtlcore$configurationChanged);
    }
    @Unique private void gtlcore$configurationChanged() {
        if (aeFluidHandler instanceof IOptimizedMEList list) list.onConfigChanged();
    }
    @Override public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason); gtlcore$configurationChanged();
    }
    @Override public boolean shouldSyncME() {
        int interval = getMEUpdateInterval();
        return getOffsetTimer() % Math.max(1, interval == 0 ? getTicksPerCycle() : interval) == 0;
    }
    /** @author GTLCore @reason One bounded refresh with working/online gates; no removed constant injection. */
    @Overwrite(remap = false) public void autoIO() {
        if (!isWorkingEnabled() || !shouldSyncME() || !updateMEStatus() || getMainNode().getGrid() == null) return;
        if (isAutoPull()) refreshList();
        syncME(); gtlcore$configurationChanged(); updateTankSubscription();
    }
    @Inject(method = "writeConfigToTag", at = @At("RETURN"), remap = false)
    private void gtlcore$writeInterval(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putInt("SyncOffset", getMEUpdateInterval());
    }
    @Inject(method = "readConfigFromTag", at = @At("RETURN"), remap = false)
    private void gtlcore$readInterval(HolderLookup.Provider provider, CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("SyncOffset")) setMEUpdateInterval(Math.max(0, tag.getInt("SyncOffset")));
        gtlcore$configurationChanged();
    }
}
