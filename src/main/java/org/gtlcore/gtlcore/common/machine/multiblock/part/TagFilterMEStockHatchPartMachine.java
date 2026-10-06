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
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine;
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
public class TagFilterMEStockHatchPartMachine extends MEInputHatchPartMachine implements IModifiableSyncOffset {
    @SaveField @SyncToClient protected String tagWhite = "";
    @SaveField @SyncToClient protected String tagBlack = "";
    @SaveField @SyncToClient private boolean isCountSort;
    @SaveField private int syncOffset;
    public TagFilterMEStockHatchPartMachine(BlockEntityCreationInfo info) { super(info); setOffsetBound(50); }
    @Override protected NotifiableFluidTank createTank(int capacity, int slots) {
        aeFluidHandler = new StockingList(this); return aeFluidHandler;
    }
    public ExportOnlyAEFluidList getStockingInventory() { return aeFluidHandler; }
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
            ((IOptimizedMEList) aeFluidHandler).onConfigChanged(); notifyControllers();
        }
    }
    @Override public void setWorkingEnabled(boolean enabled) {
        super.setWorkingEnabled(enabled);
        if (aeFluidHandler != null) {
            if (!enabled) clearVisualStock();
            ((IOptimizedMEList) aeFluidHandler).setChanged(true);
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
        if (updateMEStatus()) { refreshList(); syncME(); updateTankSubscription(); }
        else { clearVisualStock(); }
    }
    private void clearVisualStock() {
        for (var slot : aeFluidHandler.getInventory()) slot.setStock(null);
        ((IOptimizedMEList) aeFluidHandler).setChanged(true);
    }
    public void refreshList() {
        var storage = network(); if (storage == null) { aeFluidHandler.clearInventory(0); return; }
        var selected = TagFilteredStocking.select(storage, actionSource, tagWhite, tagBlack, isCountSort, AEFluidKey.class::isInstance, CONFIG_SIZE);
        var slots = aeFluidHandler.getInventory(); int index = 0;
        for (var stack : selected) {
            ((IMESlot) slots[index]).setConfigWithoutNotify(new GenericStack(stack.what(), 1));
            slots[index++].setStock(stack);
        }
        aeFluidHandler.clearInventory(index); ((IOptimizedMEList) aeFluidHandler).onConfigChanged();
    }
    @Override protected void syncME() {
        var storage = network(); if (storage == null) { clearVisualStock(); return; }
        for (var slot : aeFluidHandler.getInventory()) {
            var config = slot.getConfig(); long available = config == null ? 0 : storage.extract(config.what(), Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
            slot.setStock(available <= 0 ? null : new GenericStack(config.what(), available));
        }
        ((IOptimizedMEList) aeFluidHandler).setChanged(true);
    }
    @Override protected void flushInventory() { /* Virtual stock already belongs to the ME network. */ }
    @Override public void onLoad() {
        super.onLoad(); setOffsetBound(syncOffset == 0 ? 50 : syncOffset);
        if (getLevel() instanceof ServerLevel level) level.getServer().tell(new TickTask(1, () -> ((IOptimizedMEList) aeFluidHandler).onConfigChanged()));
    }
    public TagFilterConfigurator createTagFilterConfigurator() { return new TagFilterConfigurator(this::getTagWhite, this::setTagWhite, this::getTagBlack, this::setTagBlack); }
    @Override public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        return createTagFilterConfigurator().attachTo(super.getPanelBuilder(data, sync, settings), sync, this::isCountSort, this::setCountSort);
    }
    @Override protected void registerConfigActions(PanelSyncManager sync) {
        // Configuration slots are derived from the tag filters, so they have no manual edit actions.
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
    private static class StockingList extends ExportOnlyAEFluidList implements IOptimizedMEList {
        private final TagFilterMEStockHatchPartMachine owner;
        StockingList(TagFilterMEStockHatchPartMachine owner) { super(owner, CONFIG_SIZE, () -> new StockingSlot(owner)); this.owner = owner; onConfigChanged(); }
        private boolean changed = true; private long snapshotTick = Long.MIN_VALUE;
        private final Object2LongMap<FluidStack> amounts = LongFluidParallelLogic.newFluidAmountMap();
        private final List<FluidStack> display = new ArrayList<>();
        @Override public List<FluidStack> getFluidList() { return display; }
        @Override public List<FluidStack> getMEFluidList() { getMEFluidMap(); return display; }
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
        @Override public List<SizedFluidIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate) {
            var storage = owner == null ? null : owner.network();
            return storage == null ? left : AEFluidRecipeHandler.handleNetwork(io, recipe, left, simulate, inventory, owner, storage, owner.actionSource);
        }
        @Override public @Nullable Object2LongMap<FluidStack> getMEFluidMap() {
            var storage = owner == null ? null : owner.network(); if (storage == null) { amounts.clear(); display.clear(); return amounts; }
            long tick = owner.getOffsetTimer();
            if (changed || ConfigHolder.INSTANCE.enableUltimateMEStocking && snapshotTick != tick) {
                changed = false; snapshotTick = tick; amounts.clear(); display.clear();
                Set<AEFluidKey> queried = new HashSet<>();
                for (var slot : inventory) {
                    var config = slot.getConfig(); if (config == null || !(config.what() instanceof AEFluidKey key) || !queried.add(key)) continue;
                    long amount = storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, owner.actionSource);
                    if (amount > 0) { amounts.put(key.toStack(1), amount); display.add(key.toStack((int) Math.min(Integer.MAX_VALUE, amount))); }
                }
            }
            return amounts;
        }
    }
    private static class StockingSlot extends ExportOnlyAEConfigureFluidSlot {
        private TagFilterMEStockHatchPartMachine owner;
        StockingSlot(TagFilterMEStockHatchPartMachine owner) { this.owner = owner; }
        @Override public FluidStack drain(FluidStack fluid, FluidAction action) {
            return !fluid.isEmpty() && config != null && config.what().equals(AEFluidKey.of(fluid)) ? drain(fluid.getAmount(), action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            if (amount <= 0 || owner == null || config == null || !(config.what() instanceof AEFluidKey key)) return FluidStack.EMPTY;
            var storage = owner.network(); if (storage == null) return FluidStack.EMPTY;
            long extracted = storage.extract(key, amount, action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE, owner.actionSource);
            if (extracted <= 0) return FluidStack.EMPTY;
            if (action.execute()) TagFilteredStocking.afterExtraction(this, key, extracted);
            return key.toStack((int) extracted);
        }
        @Override public StockingSlot copy() {
            var copy = new StockingSlot(owner); copy.owner = owner; copy.config = config; copy.stock = stock; return copy;
        }
    }
}
