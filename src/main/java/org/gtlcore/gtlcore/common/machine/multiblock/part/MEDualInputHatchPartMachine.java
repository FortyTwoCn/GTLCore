package org.gtlcore.gtlcore.common.machine.multiblock.part;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.client.gui.widget.AEDualConfigWidget;
import org.gtlcore.gtlcore.integration.ae2.AEBufferedSlotTransactions;
import org.gtlcore.gtlcore.integration.ae2.IBufferedMEInventory;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.*;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Flow;
import java.util.Arrays;

/** Native 64-slot paired physical input buffers. Retained inventory is owned by the hatch. */
public class MEDualInputHatchPartMachine extends MEInputBusPartMachine implements IModifiableSyncOffset, IBufferedMEInventory {
    private static final int CONFIG_SIZE = 64;
    public final ExportOnlyAEFluidList aeFluidHandler;
    @SaveField protected final NotifiableFluidTank fluidTank;
    @SaveField @SyncToClient protected int page = 1;
    @SaveField private int syncOffset;

    public MEDualInputHatchPartMachine(BlockEntityCreationInfo info, Object... args) {
        super(info, new ExportOnlyAEItemList(CONFIG_SIZE));
        aeItemHandler = (ExportOnlyAEItemList) getInventory();
        aeFluidHandler = attachTrait(new ExportOnlyAEFluidList(this, CONFIG_SIZE));
        fluidTank = aeFluidHandler;
    }
    public int getPage() { return Math.clamp(page, 1, 4); }
    public void setPage(int page) {
        this.page = Math.clamp(page, 1, 4); markAsChanged(); syncDataHolder.markClientSyncFieldDirty("page");
    }
    @Override public int getMEUpdateInterval() { return syncOffset; }
    @Override public void setMEUpdateInterval(int interval) { syncOffset = Math.max(0, interval); markAsChanged(); }
    @Override public boolean shouldSyncME() {
        int interval = syncOffset == 0 ? com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.compat.ae2.updateIntervals : syncOffset;
        return getOffsetTimer() % Math.max(1, interval) == 0;
    }
    @Override public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (aeFluidHandler != null) aeFluidHandler.notifyListeners();
    }
    @Override protected void syncME() {
        var grid = getMainNode().getGrid();
        if (grid == null || !isOnline()) return;
        super.syncME();
        var storage = grid.getStorageService().getInventory();
        for (var slot : aeFluidHandler.getInventory()) AEBufferedSlotTransactions.balance(slot, storage, actionSource);
    }
    @Override protected void flushInventory() {
        super.flushInventory();
        var grid = getMainNode().getGrid();
        if (grid == null || !isOnline()) return;
        var storage = grid.getStorageService().getInventory();
        for (var slot : aeFluidHandler.getInventory()) AEBufferedSlotTransactions.flush(slot, storage, actionSource);
    }
    @Override public ExportOnlyAESlot[] getBufferedMESlots() {
        var slots = Arrays.copyOf(aeItemHandler.getInventory(), CONFIG_SIZE * 2, ExportOnlyAESlot[].class);
        System.arraycopy(aeFluidHandler.getInventory(), 0, slots, CONFIG_SIZE, CONFIG_SIZE);
        return slots;
    }
    @Override public void buildMainUI(ParentWidget<?> contents, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var online = new BooleanSyncValue(this::isOnline); sync.syncValue("dualInputOnline", online);
        contents.child(Flow.col().coverChildren().center()
                .child(Text.dynamic(() -> Component.translatable(online.getBoolValue() ? "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline")).asWidget().marginBottom(4))
                .child(new AEDualConfigWidget(0, 0, aeItemHandler, aeFluidHandler, this::setPage, getPage(), true).syncManager(sync)));
    }
    @Override protected CompoundTag writeConfigToTag(HolderLookup.Provider provider) {
        var tag = new CompoundTag(); var configs = new CompoundTag(); tag.put("ConfigStacks", configs);
        for (int i = 0; i < CONFIG_SIZE; i++) {
            var config = aeItemHandler.getInventory()[i].getConfig();
            if (config == null) config = aeFluidHandler.getInventory()[i].getConfig();
            if (config != null) configs.put(Integer.toString(i), GenericStack.writeTag(provider, config));
        }
        tag.putByte("GhostCircuit", (byte) circuitSlot.getCurrentCircuit());
        tag.putBoolean("DistinctBuses", isDistinct()); tag.putInt("SyncOffset", getMEUpdateInterval()); return tag;
    }
    @Override protected void readConfigFromTag(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("ConfigStacks")) {
            var configs = tag.getCompound("ConfigStacks");
            for (int i = 0; i < CONFIG_SIZE; i++) {
                var config = GenericStack.readTag(provider, configs.getCompound(Integer.toString(i)));
                aeItemHandler.getInventory()[i].setConfig(config != null && config.amount() > 0 && config.what() instanceof AEItemKey ? config : null);
                aeFluidHandler.getInventory()[i].setConfig(config != null && config.amount() > 0 && config.what() instanceof AEFluidKey ? config : null);
            }
        }
        if (tag.contains("GhostCircuit")) circuitSlot.setCurrentCircuit(Math.clamp(tag.getByte("GhostCircuit"), 0, 32));
        if (tag.contains("DistinctBuses")) setDistinct(tag.getBoolean("DistinctBuses"));
        if (tag.contains("SyncOffset")) setMEUpdateInterval(tag.getInt("SyncOffset"));
        markAsChanged();
    }
}
