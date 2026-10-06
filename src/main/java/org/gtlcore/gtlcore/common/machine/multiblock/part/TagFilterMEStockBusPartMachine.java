package org.gtlcore.gtlcore.common.machine.multiblock.part;

import org.gtlcore.gtlcore.api.gui.TagFilterConfigurator;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.*;
import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.TagFilteredStocking;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.*;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.config.Actionable;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Native filtered network view: only recipe/slot extraction removes stock from ME. */
public class TagFilterMEStockBusPartMachine extends MEInputBusPartMachine implements IModifiableSyncOffset {
    @SaveField @SyncToClient protected String tagWhite = "";
    @SaveField @SyncToClient protected String tagBlack = "";
    @SaveField @SyncToClient private boolean isCountSort;
    @SaveField private int syncOffset;
    public TagFilterMEStockBusPartMachine(BlockEntityCreationInfo info) { super(info, new StockingList()); aeItemHandler = (ExportOnlyAEItemList) getInventory(); ((StockingList) aeItemHandler).setOwner(this); setOffsetBound(50); }
    
    public ExportOnlyAEItemList getStockingInventory() { return aeItemHandler; }
    public String getTagWhite() { return tagWhite; }
    public String getTagBlack() { return tagBlack; }
    public boolean isCountSort() { return isCountSort; }
    public void setTagWhite(String value) { tagWhite = TagFilterConfigurator.normalize(value); settingsChanged("tagWhite"); }
    public void setTagBlack(String value) { tagBlack = TagFilterConfigurator.normalize(value); settingsChanged("tagBlack"); }
    public void setCountSort(boolean value) { isCountSort = value; settingsChanged("isCountSort"); }
    private void settingsChanged(String field) {
        if (!isRemote()) {
            markAsChanged(); syncDataHolder.markClientSyncFieldDirty(field);
            if (isWorkingEnabled() && updateMEStatus()) refreshList();
            ((IOptimizedMEList) aeItemHandler).onConfigChanged(); notifyControllers();
        }
    }
    @Override public void setWorkingEnabled(boolean enabled) {
        super.setWorkingEnabled(enabled);
        if (aeItemHandler != null) {
            if (!enabled) clearVisualStock();
            ((IOptimizedMEList) aeItemHandler).setChanged(true);
        }
        notifyControllers();
    }
    private void notifyControllers() {
        for (var controller : getControllers()) {
            if (controller instanceof com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine machine) machine.getRecipeLogic().updateTickSubscription();
        }
    }
    @Override public int getMEUpdateInterval() { return syncOffset; }
    @Override public void setMEUpdateInterval(int interval) {
        if (interval < 0) throw new IllegalArgumentException("ME refresh interval must not be negative");
        syncOffset = interval; setOffsetBound(interval == 0 ? 50 : interval); markAsChanged();
    }
    @Override public boolean shouldSyncME() { return getOffsetTimer() % Math.max(1, syncOffset == 0 ? 50 : syncOffset) == 0; }
    private @Nullable MEStorage network() {
        var grid = getMainNode().getGrid();
        return isWorkingEnabled() && isOnline() && grid != null ? grid.getStorageService().getInventory() : null;
    }
    @Override public void autoIO() {
        if (!isWorkingEnabled()) { clearVisualStock(); return; }
        if (!shouldSyncME()) return;
        if (updateMEStatus()) { refreshList(); syncME(); updateInventorySubscription(); }
        else { clearVisualStock(); }
    }
    private void clearVisualStock() {
        for (var slot : aeItemHandler.getInventory()) slot.setStock(null);
        ((IOptimizedMEList) aeItemHandler).setChanged(true);
    }
    public void refreshList() {
        var storage = network(); if (storage == null) { aeItemHandler.clearInventory(0); return; }
        var selected = TagFilteredStocking.select(storage, actionSource, tagWhite, tagBlack, isCountSort, AEItemKey.class::isInstance, CONFIG_SIZE);
        var slots = aeItemHandler.getInventory(); int index = 0;
        for (var stack : selected) {
            ((IMESlot) slots[index]).setConfigWithoutNotify(new GenericStack(stack.what(), 1));
            slots[index++].setStock(stack);
        }
        aeItemHandler.clearInventory(index); ((IOptimizedMEList) aeItemHandler).onConfigChanged();
    }
    @Override protected void syncME() {
        var storage = network(); if (storage == null) { clearVisualStock(); return; }
        for (var slot : aeItemHandler.getInventory()) {
            var config = slot.getConfig(); long available = config == null ? 0 : storage.extract(config.what(), Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
            slot.setStock(available <= 0 ? null : new GenericStack(config.what(), available));
        }
        ((IOptimizedMEList) aeItemHandler).setChanged(true);
    }
    @Override protected void flushInventory() { /* Virtual stock already belongs to the ME network. */ }
    @Override public void onLoad() {
        super.onLoad(); setOffsetBound(syncOffset == 0 ? 50 : syncOffset);
        if (getLevel() instanceof ServerLevel level) level.getServer().tell(new TickTask(1, () -> ((IOptimizedMEList) aeItemHandler).onConfigChanged()));
    }
    public TagFilterConfigurator createTagFilterConfigurator() { return new TagFilterConfigurator(this::getTagWhite, this::setTagWhite, this::getTagBlack, this::setTagBlack); }
    @Override public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return createTagFilterConfigurator().attachTo(super.getPanelBuilder(data, sync, settings), sync, this::isCountSort, this::setCountSort);
    }
    @Override protected void registerConfigActions(PanelSyncManager sync) {
        sync.registerServerSyncedAction("ae_stock_pickup", packet -> {
            int index = packet.readVarInt(); if (index < 0 || index >= CONFIG_SIZE) return;
            var player = sync.getPlayer(); if (!player.containerMenu.getCarried().isEmpty()) return;
            var slot = aeItemHandler.getInventory()[index]; var config = slot.getConfig();
            if (config != null && config.what() instanceof AEItemKey key) {
                player.containerMenu.setCarried(slot.extractItem(0, key.toStack().getMaxStackSize(), false));
            }
        });
    }
    @Override protected CompoundTag writeConfigToTag(HolderLookup.Provider provider) {
        var tag = new CompoundTag(); tag.putByte("GhostCircuit", (byte) circuitSlot.getCurrentCircuit());
        tag.putString("TagWhite", tagWhite); tag.putString("TagBlack", tagBlack);
        tag.putBoolean("CountSort", isCountSort); tag.putInt("SyncOffset", syncOffset); return tag;
    }
    @Override protected void readConfigFromTag(HolderLookup.Provider provider, CompoundTag tag) {
        if (tag.contains("GhostCircuit")) circuitSlot.setCurrentCircuit(tag.getByte("GhostCircuit"));
        if (tag.contains("TagWhite")) setTagWhite(tag.getString("TagWhite"));
        if (tag.contains("TagBlack")) setTagBlack(tag.getString("TagBlack"));
        if (tag.contains("CountSort")) setCountSort(tag.getBoolean("CountSort"));
        if (tag.contains("SyncOffset")) setMEUpdateInterval(Math.max(0, tag.getInt("SyncOffset")));
    }
    private static class StockingList extends ExportOnlyAEItemList implements IOptimizedMEList {
        private TagFilterMEStockBusPartMachine owner;
        StockingList() { super(CONFIG_SIZE, StockingSlot::new); }
        void setOwner(TagFilterMEStockBusPartMachine owner) { this.owner = owner; onConfigChanged(); }
        private boolean changed = true; private long snapshotTick = Long.MIN_VALUE;
        private final Object2LongMap<ItemStack> amounts = new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
        
        @Override public boolean isStocking() { return true; }
        @Override public boolean isAutoPull() { return true; }
        @Override public boolean getChanged() { return changed; }
        @Override public void setChanged(boolean value) { changed = value; }
        @Override public void onContentsChanged() { changed = true; super.onContentsChanged(); }
        @Override public void onConfigChanged() {
            for (var slot : inventory) { ((StockingSlot) slot).owner = owner; ((IMESlot) slot).setOnConfigChanged(this::onConfigChanged); slot.setOnContentsChanged(this::onContentsChanged); }
            snapshotTick = Long.MIN_VALUE; onContentsChanged();
        }
        @Override public void clearInventory(int first) {
            for (int i = Math.max(0, first); i < inventory.length; i++) { ((IMESlot) inventory[i]).setConfigWithoutNotify(null); inventory[i].setStock(null); }
            onConfigChanged();
        }
        @Override public List<SizedIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
            var storage = owner == null ? null : owner.network();
            return storage == null ? left : AEItemRecipeHandler.handleNetwork(io, recipe, left, simulate, inventory, owner, storage, owner.actionSource);
        }
        @Override public @Nullable Object2LongMap<ItemStack> getMEItemMap() {
            var storage = owner == null ? null : owner.network(); if (storage == null) { amounts.clear(); return null; }
            long tick = owner.getOffsetTimer();
            if (changed || ConfigHolder.INSTANCE.enableUltimateMEStocking && snapshotTick != tick) {
                changed = false; snapshotTick = tick; amounts.clear(); 
                Set<AEItemKey> queried = new HashSet<>();
                for (var slot : inventory) {
                    var config = slot.getConfig(); if (config == null || !(config.what() instanceof AEItemKey key) || !queried.add(key)) continue;
                    long amount = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, owner.actionSource);
                    if (amount > 0) { amounts.put(key.toStack(), amount); }
                }
            }
            return amounts.isEmpty() ? null : amounts;
        }
    }
    private static class StockingSlot extends ExportOnlyAEConfigureItemSlot {
        private TagFilterMEStockBusPartMachine owner;
        StockingSlot() {}
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || owner == null || config == null || !(config.what() instanceof AEItemKey key)) return ItemStack.EMPTY;
            var storage = owner.network(); if (storage == null) return ItemStack.EMPTY;
            long extracted = storage.extract(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, owner.actionSource);
            if (extracted <= 0) return ItemStack.EMPTY;
            if (!simulate) TagFilteredStocking.afterExtraction(this, key, extracted);
            return key.toStack((int) extracted);
        }
        @Override public StockingSlot copy() {
            var copy = new StockingSlot(); copy.owner = owner; copy.config = config; copy.stock = stock; return copy;
        }
    }
}
