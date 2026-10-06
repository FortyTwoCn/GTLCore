package org.gtlcore.gtlcore.mixin.gtm.ae.machine;

import org.gtlcore.gtlcore.api.gui.AdvancedMEConfigurator;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IOptimizedMEList;
import org.gtlcore.gtlcore.integration.ae2.AEBufferedSlotTransactions;
import org.gtlcore.gtlcore.integration.ae2.IBufferedMEInventory;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.integration.ae2.machine.MEHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAEFluidList;
import com.gregtechceu.gtceu.integration.ae2.slot.ExportOnlyAESlot;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import appeng.api.networking.IGridNodeListener;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.List;

@Mixin(MEInputHatchPartMachine.class)
public abstract class MEInputHatchPartMachineMixin extends MEHatchPartMachine
        implements IModifiableSyncOffset, IBufferedMEInventory {
    @Shadow(remap = false) protected ExportOnlyAEFluidList aeFluidHandler;
    @Shadow(remap = false) protected abstract CompoundTag writeConfigToTag(HolderLookup.Provider provider);
    @Shadow(remap = false) protected abstract void readConfigFromTag(HolderLookup.Provider provider, CompoundTag tag);
    @Unique private boolean gtlcore$stockInDroppedMachine;

    protected MEInputHatchPartMachineMixin(BlockEntityCreationInfo info, IO io) {
        super(info, io);
    }
    @Inject(method = "autoIO", at = @At(value = "INVOKE",
            target = "Lcom/gregtechceu/gtceu/integration/ae2/machine/MEInputHatchPartMachine;syncME()V", shift = At.Shift.AFTER), remap = false)
    private void gtlcore$changed(CallbackInfo ci) {
        if (aeFluidHandler instanceof IOptimizedMEList list) list.setChanged(true);
    }
    @Inject(method = "writeConfigToTag", at = @At("RETURN"), remap = false)
    private void gtlcore$writeInterval(HolderLookup.Provider provider, CallbackInfoReturnable<CompoundTag> cir) {
        cir.getReturnValue().putInt("SyncOffset", getMEUpdateInterval());
    }
    @Inject(method = "readConfigFromTag", at = @At("RETURN"), remap = false)
    private void gtlcore$readInterval(HolderLookup.Provider provider, CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("SyncOffset")) setMEUpdateInterval(Math.max(0, tag.getInt("SyncOffset")));
    }
    @Override public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (aeFluidHandler != null) aeFluidHandler.notifyListeners();
    }
    @Override public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return new AdvancedMEConfigurator(this::setMEUpdateInterval, this::getMEUpdateInterval)
                .attachTo(super.getPanelBuilder(data, sync, settings), sync);
    }
    /** @author GTLCore @reason Preserve refused returns and exact long quantities. */
    @Overwrite(remap = false) protected void syncME() {
        var grid = getMainNode().getGrid();
        if (grid == null || !isOnline()) return;
        var storage = grid.getStorageService().getInventory();
        for (var slot : aeFluidHandler.getInventory()) AEBufferedSlotTransactions.balance(slot, storage, actionSource);
    }
    /** @author GTLCore @reason Remove only the stock actually accepted by AE. */
    @Overwrite(remap = false) protected void flushInventory() {
        var grid = getMainNode().getGrid();
        if (grid == null || !isOnline()) return;
        var storage = grid.getStorageService().getInventory();
        for (var slot : aeFluidHandler.getInventory()) AEBufferedSlotTransactions.flush(slot, storage, actionSource);
    }
    @Override public ExportOnlyAESlot[] getBufferedMESlots() {
        return aeFluidHandler.isStocking() ? new ExportOnlyAESlot[0] : aeFluidHandler.getInventory();
    }
    @Override public void modifyDrops(List<ItemStack> drops) {
        super.modifyDrops(drops);
        flushInventory();
        // The block saves its item components after this hook. Do not return its retained stock twice.
        gtlcore$stockInDroppedMachine = drops.stream().anyMatch(stack ->
                stack.getItem() instanceof MetaMachineItem item && item.getBlock() == getBlockState().getBlock());
    }
    /** @author GTLCore @reason Stock retained in the dropped machine already belongs to that item. */
    @Overwrite(remap = false) public void onMachineDestroyed() {
        if (!gtlcore$stockInDroppedMachine) flushInventory();
        super.onMachineDestroyed();
    }
    @Override public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        super.saveToItem(stack, registries);
        var payload = new CompoundTag();
        payload.put("configuration", writeConfigToTag(registries));
        var slots = getBufferedMESlots();
        var states = new CompoundTag();
        for (int i = 0; i < slots.length; i++) states.put(Integer.toString(i), slots[i].serializeNBT(registries));
        payload.put("slots", states);
        var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        custom.put("gtlcoreBufferedME", payload);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }
    @Override protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        var custom = components.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!custom.contains("gtlcoreBufferedME", 10)) return;
        var payload = custom.getCompound("gtlcoreBufferedME");
        readConfigFromTag(getLevel().registryAccess(), payload.getCompound("configuration"));
        var states = payload.getCompound("slots"); var slots = getBufferedMESlots();
        for (int i = 0; i < slots.length; i++) {
            var slot = slots[i]; slot.setConfig(null); slot.setStock(null);
            slot.deserializeNBT(getLevel().registryAccess(), states.getCompound(Integer.toString(i)));
            slot.getOnContentsChanged().run();
        }
        markAsChanged();
    }
}
