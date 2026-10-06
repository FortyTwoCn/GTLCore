package org.gtlcore.gtlcore.common.machine.multiblock.part;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.*;
import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import org.gtlcore.gtlcore.client.gui.widget.AEDualConfigWidget;
import org.gtlcore.gtlcore.config.ConfigHolder;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import com.gregtechceu.gtceu.integration.ae2.machine.MEBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.*;
import com.gregtechceu.gtceu.utils.ItemStackHashStrategy;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.config.Actionable;
import appeng.api.networking.IGridNodeListener;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.*;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import it.unimi.dsi.fastutil.objects.*;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** 64 paired configurations reference the live ME network; visual stock never owns items or fluids. */
public class MEDualHatchStockPartMachine extends MEBusPartMachine implements IDataStickInteractable, IModifiableSyncOffset {
    protected static final int CONFIG_SIZE = 64;
    protected static final int AUTO_PULL_OFF = 0, AUTO_PULL_ALL = 1, AUTO_PULL_ITEM = 2, AUTO_PULL_FLUID = 3;
    public final ExportOnlyAEItemList aeItemHandler;
    public final ExportOnlyAEFluidList aeFluidHandler;
    @SaveField protected final NotifiableFluidTank fluidTank;
    @SaveField @SyncToClient protected int page = 1;
    @SaveField @SyncToClient private int autoPullMode;
    @SaveField private int syncOffset;

    public MEDualHatchStockPartMachine(BlockEntityCreationInfo info, Object... args) {
        super(info, IO.IN, new StockingItemList(CONFIG_SIZE));
        aeItemHandler = (ExportOnlyAEItemList) getInventory();
        ((StockingItemList) aeItemHandler).setOwner(this);
        aeFluidHandler = attachTrait(new StockingFluidList(this, CONFIG_SIZE)); fluidTank = aeFluidHandler;
    }
    public int getAutoPullMode() { return autoPullMode; }
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
    @Override public void setWorkingEnabled(boolean enabled) {
        super.setWorkingEnabled(enabled);
        if (aeItemHandler != null && aeFluidHandler != null) markMEStockChanged();
    }
    @Override public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (aeItemHandler == null || aeFluidHandler == null) return;
        if (!isOnline()) clearVisualStock();
        markMEStockChanged();
    }
    @Override public void onLoad() {
        super.onLoad(); scheduleForNextServerTick(() -> {
            ((IOptimizedMEList) aeItemHandler).onConfigChanged(); ((IOptimizedMEList) aeFluidHandler).onConfigChanged();
        });
    }
    @Override public void autoIO() {
        if (!isWorkingEnabled() || !shouldSyncME()) return;
        if (!updateMEStatus()) { clearVisualStock(); return; }
        if (autoPullMode != AUTO_PULL_OFF) refreshList(); else syncME();
        updateInventorySubscription();
    }
    private @Nullable MEStorage network() {
        var grid = getMainNode().getGrid();
        return isWorkingEnabled() && isOnline() && grid != null ? grid.getStorageService().getInventory() : null;
    }
    private void clearVisualStock() {
        for (var slot : aeItemHandler.getInventory()) slot.setStock(null);
        for (var slot : aeFluidHandler.getInventory()) slot.setStock(null);
    }
    private void refreshList() {
        var network = network(); var before = configuration();
        if (network == null) { aeItemHandler.clearInventory(0); aeFluidHandler.clearInventory(0); finishRefresh(before); return; }
        int index = 0;
        for (var entry : network.getAvailableStacks()) {
            if (index >= CONFIG_SIZE) break;
            var key = entry.getKey(); boolean item = key instanceof AEItemKey;
            if (!(item || key instanceof AEFluidKey) || entry.getLongValue() <= 0 ||
                    autoPullMode == AUTO_PULL_ITEM && !item || autoPullMode == AUTO_PULL_FLUID && item) continue;
            long available = network.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
            if (available <= 0) continue;
            var selected = item ? aeItemHandler.getInventory()[index] : aeFluidHandler.getInventory()[index];
            var opposite = item ? aeFluidHandler.getInventory()[index] : aeItemHandler.getInventory()[index];
            ((IMESlot) opposite).setConfigWithoutNotify(null); opposite.setStock(null);
            ((IMESlot) selected).setConfigWithoutNotify(new GenericStack(key, 1)); selected.setStock(new GenericStack(key, available)); index++;
        }
        aeItemHandler.clearInventory(index); aeFluidHandler.clearInventory(index); finishRefresh(before);
    }
    private List<AEKey> configuration() {
        List<AEKey> result = new ArrayList<>(CONFIG_SIZE);
        for (int i = 0; i < CONFIG_SIZE; i++) {
            var config = aeItemHandler.getInventory()[i].getConfig();
            if (config == null) config = aeFluidHandler.getInventory()[i].getConfig();
            result.add(config == null ? null : config.what());
        }
        return result;
    }
    private void finishRefresh(List<AEKey> before) {
        if (!before.equals(configuration())) {
            ((IOptimizedMEList) aeItemHandler).onConfigChanged(); ((IOptimizedMEList) aeFluidHandler).onConfigChanged();
        }
        markMEStockChanged();
    }
    protected void syncME() {
        var network = network();
        if (network == null) { clearVisualStock(); return; }
        for (int i = 0; i < CONFIG_SIZE; i++) {
            updateVisual(aeItemHandler.getInventory()[i], network); updateVisual(aeFluidHandler.getInventory()[i], network);
        }
    }
    private void updateVisual(ExportOnlyAESlot slot, MEStorage network) {
        var config = slot.getConfig(); long available = config == null ? 0 :
                network.extract(config.what(), Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
        slot.setStock(available <= 0 ? null : new GenericStack(config.what(), available));
    }
    private void markMEStockChanged() {
        ((IOptimizedMEList) aeItemHandler).setChanged(true); ((IOptimizedMEList) aeFluidHandler).setChanged(true);
        aeItemHandler.notifyListeners(); aeFluidHandler.notifyListeners();
    }
    protected void setAutoPullMode(int mode) {
        autoPullMode = Math.clamp(mode, AUTO_PULL_OFF, AUTO_PULL_FLUID);
        if (!isRemote()) {
            if (autoPullMode == AUTO_PULL_OFF) { aeItemHandler.clearInventory(0); aeFluidHandler.clearInventory(0); }
            else if (updateMEStatus()) refreshList();
            ((IOptimizedMEList) aeItemHandler).onConfigChanged(); ((IOptimizedMEList) aeFluidHandler).onConfigChanged();
            markAsChanged(); syncDataHolder.markClientSyncFieldDirty("autoPullMode"); updateInventorySubscription();
        }
    }
    public void setTerminalAutoPullMode(int mode) { setAutoPullMode(mode); }

    @Override public void buildMainUI(ParentWidget<?> contents, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var online = new BooleanSyncValue(this::isOnline); sync.syncValue("dualStockOnline", online);
        contents.child(Flow.col().coverChildren().center()
                .child(Text.dynamic(() -> Component.translatable(online.getBoolValue() ? "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline")).asWidget().marginBottom(4))
                .child(new AEDualConfigWidget(0, 0, aeItemHandler, aeFluidHandler, this::setPage, getPage()).syncManager(sync)));
    }
    @Override public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var builder = super.getPanelBuilder(data, sync, settings); var inherited = builder.leftConfigurators();
        var mode = SyncHandlers.intNumber(this::getAutoPullMode, this::setAutoPullMode).allowC2S(); sync.syncValue("dualStockAutoPullMode", mode);
        var interval = sync.syncedPanel("dualStockInterval", true, (sm, handler) -> {
            var value = SyncHandlers.string(() -> Integer.toString(getMEUpdateInterval()), text -> {
                try { setMEUpdateInterval(Integer.parseInt(text)); } catch (NumberFormatException ignored) {}
            }).allowC2S(); sm.syncValue("dualStockUpdateInterval", value);
            return PopupPanel.createPopupPanel("dualStockIntervalPanel", 160, 54)
                    .child(Text.lang("gtceu.gui.title.adv_me_config.ticks_per_cycle").asWidget().pos(4, 4))
                    .child(new TextFieldWidget().value(value).setNumbersLong(number -> Math.clamp(number, 0, Integer.MAX_VALUE)).pos(4, 28).size(152, 18));
        });
        return builder.leftConfigurators(flow -> {
            inherited.accept(flow);
            flow.child(new CycleButtonWidget().value(mode).stateOverlay(0, Text.str("OFF")).stateOverlay(1, Text.str("ALL"))
                    .stateOverlay(2, Text.str("I")).stateOverlay(3, Text.str("F")).size(18)
                    .tooltipDynamic(tooltip -> tooltip.addLine(Component.translatable("gtlcore.machine.me_dual_hatch_stock.turns." + mode.getIntValue()))));
            flow.child(new ButtonWidget<>().size(18).overlay(Text.str("T")).onMousePressed((context, button) -> {
                if (button != 0) return false; interval.openPanel(); return true;
            }));
        });
    }
    protected CompoundTag writeConfigToTag(HolderLookup.Provider provider) {
        var tag = new CompoundTag(); tag.putInt("AutoPullMode", autoPullMode);
        var configs = new CompoundTag();
        if (autoPullMode == AUTO_PULL_OFF) for (int i = 0; i < CONFIG_SIZE; i++) {
            var config = aeItemHandler.getInventory()[i].getConfig();
            if (config == null) config = aeFluidHandler.getInventory()[i].getConfig();
            if (config != null) configs.put(Integer.toString(i), GenericStack.writeTag(provider, config));
        }
        tag.put("ConfigStacks", configs); tag.putByte("GhostCircuit", (byte) circuitSlot.getCurrentCircuit());
        tag.putInt("SyncOffset", syncOffset); return tag;
    }
    protected void readConfigFromTag(HolderLookup.Provider provider, CompoundTag tag) {
        setAutoPullMode(tag.getInt("AutoPullMode"));
        if (autoPullMode == AUTO_PULL_OFF && tag.contains("ConfigStacks")) {
            var configs = tag.getCompound("ConfigStacks"); Set<AEKey> configured = new HashSet<>();
            for (int i = 0; i < CONFIG_SIZE; i++) {
                GenericStack config = GenericStack.readTag(provider, configs.getCompound(Integer.toString(i)));
                ((IMESlot) aeItemHandler.getInventory()[i]).setConfigWithoutNotify(null);
                ((IMESlot) aeFluidHandler.getInventory()[i]).setConfigWithoutNotify(null);
                if (config == null || config.amount() <= 0 || !configured.add(config.what())) continue;
                if (config.what() instanceof AEItemKey) ((IMESlot) aeItemHandler.getInventory()[i]).setConfigWithoutNotify(new GenericStack(config.what(), 1));
                else if (config.what() instanceof AEFluidKey) ((IMESlot) aeFluidHandler.getInventory()[i]).setConfigWithoutNotify(new GenericStack(config.what(), 1));
            }
            ((IOptimizedMEList) aeItemHandler).onConfigChanged(); ((IOptimizedMEList) aeFluidHandler).onConfigChanged();
        }
        if (tag.contains("GhostCircuit")) circuitSlot.setCurrentCircuit(Math.clamp(tag.getByte("GhostCircuit"), 0, 32));
        if (tag.contains("SyncOffset")) setMEUpdateInterval(tag.getInt("SyncOffset")); syncME(); markAsChanged();
    }
    @Override public InteractionResult onDataStickUse(Player player, ItemStack item) {
        var data = item.get(GTDataComponents.DATA_COPY_TAG);
        if (data == null || !data.contains("MEDualHatchStock")) return InteractionResult.PASS;
        if (!isRemote()) { readConfigFromTag(player.registryAccess(), data.copyTag().getCompound("MEDualHatchStock"));
            player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_paste_settings")); }
        return InteractionResult.sidedSuccess(isRemote());
    }
    @Override public InteractionResult onDataStickShiftUse(Player player, ItemStack item) {
        if (!isRemote()) {
            var tag = item.getOrDefault(GTDataComponents.DATA_COPY_TAG, CustomData.EMPTY).copyTag();
            tag.put("MEDualHatchStock", writeConfigToTag(player.registryAccess())); item.set(GTDataComponents.DATA_COPY_TAG, CustomData.of(tag));
            item.set(DataComponents.CUSTOM_NAME, Component.translatable("gtceu.machine.me.me_dual_hatch_stock.data_stick.name"));
            player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_copy_settings"));
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    private static class StockingItemList extends ExportOnlyAEItemList implements IOptimizedMEList {
        private MEDualHatchStockPartMachine owner;
        private final Object2LongMap<ItemStack> amounts = new Object2LongOpenCustomHashMap<>(ItemStackHashStrategy.comparingAllButCount());
        private boolean changed = true; private long snapshotTick = Long.MIN_VALUE;
        private StockingItemList(int size) { super(size, StockingItemSlot::new); }
        private void setOwner(MEDualHatchStockPartMachine owner) {
            this.owner = owner;
            for (var slot : inventory) { ((StockingItemSlot) slot).owner = owner; ((IMESlot) slot).setOnConfigChanged(this::onConfigChanged); }
        }
        @Override public boolean isAutoPull() { return owner != null && owner.getAutoPullMode() > 0; }
        @Override public boolean isStocking() { return true; }
        @Override public boolean getChanged() { return changed; }
        @Override public void setChanged(boolean changed) { this.changed = changed; }
        @Override public void onConfigChanged() { changed = true; snapshotTick = Long.MIN_VALUE; onContentsChanged(); }
        @Override public void clearInventory(int first) {
            for (int i = Math.max(0, first); i < inventory.length; i++) { ((IMESlot) inventory[i]).setConfigWithoutNotify(null); inventory[i].setStock(null); }
            onConfigChanged();
        }
        @Override public List<SizedIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe, List<SizedIngredient> left, boolean simulate) {
            var network = owner == null ? null : owner.network();
            return network == null ? left : AEItemRecipeHandler.handleNetwork(io, recipe, left, simulate, inventory, owner, network, owner.actionSource);
        }
        @Override public @Nullable Object2LongMap<ItemStack> getMEItemMap() {
            var network = owner == null ? null : owner.network();
            if (network == null) { amounts.clear(); return null; }
            long tick = owner.getOffsetTimer();
            if (changed || ConfigHolder.INSTANCE.enableUltimateMEStocking && snapshotTick != tick) {
                changed = false; snapshotTick = tick; amounts.clear(); Set<AEItemKey> queried = new HashSet<>();
                for (var slot : inventory) {
                    var config = slot.getConfig();
                    if (config == null || !(config.what() instanceof AEItemKey key) || !queried.add(key)) continue;
                    long available = network.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, owner.actionSource);
                    if (available > 0) amounts.put(key.toStack(), available);
                }
            }
            return amounts.isEmpty() ? null : amounts;
        }
        @Override public void onContentsChanged() { changed = true; super.onContentsChanged(); }
    }
    private static class StockingItemSlot extends ExportOnlyAEConfigureItemSlot {
        private MEDualHatchStockPartMachine owner;
        private StockingItemSlot() {}
        private StockingItemSlot(MEDualHatchStockPartMachine owner, GenericStack config, GenericStack stock) { super(config, stock); this.owner = owner; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) { return extractItem(slot, amount, simulate, true); }
        public ItemStack extractItem(int slot, int amount, boolean simulate, boolean notify) {
            if (slot != 0 || amount <= 0 || owner == null || config == null || !(config.what() instanceof AEItemKey key)) return ItemStack.EMPTY;
            var network = owner.network(); if (network == null) return ItemStack.EMPTY;
            long extracted = network.extract(key, amount, simulate ? Actionable.SIMULATE : Actionable.MODULATE, owner.actionSource);
            if (extracted <= 0) return ItemStack.EMPTY;
            if (!simulate) { stock = stock == null || stock.amount() <= extracted ? null : new GenericStack(key, stock.amount() - extracted); if (notify) onContentsChanged(); }
            return key.toStack((int) extracted);
        }
        @Override public StockingItemSlot copy() { return new StockingItemSlot(owner, config, stock); }
    }
    private static class StockingFluidList extends ExportOnlyAEFluidList implements IOptimizedMEList {
        private final MEDualHatchStockPartMachine owner;
        private final Object2LongMap<FluidStack> amounts = LongFluidParallelLogic.newFluidAmountMap();
        private final List<FluidStack> display = new ArrayList<>();
        private boolean changed = true; private long snapshotTick = Long.MIN_VALUE;
        private StockingFluidList(MEDualHatchStockPartMachine owner, int size) {
            super(owner, size, () -> new StockingFluidSlot(owner)); this.owner = owner;
            for (var slot : inventory) ((IMESlot) slot).setOnConfigChanged(this::onConfigChanged);
        }
        @Override public boolean isAutoPull() { return owner.getAutoPullMode() > 0; }
        @Override public boolean isStocking() { return true; }
        @Override public boolean getChanged() { return changed; }
        @Override public void setChanged(boolean changed) { this.changed = changed; }
        @Override public void onConfigChanged() { changed = true; snapshotTick = Long.MIN_VALUE; onContentsChanged(); }
        @Override public void clearInventory(int first) {
            for (int i = Math.max(0, first); i < inventory.length; i++) { ((IMESlot) inventory[i]).setConfigWithoutNotify(null); inventory[i].setStock(null); }
            onConfigChanged();
        }
        @Override public List<SizedFluidIngredient> handleRecipeInner(IO io, @Nullable GTRecipe recipe, List<SizedFluidIngredient> left, boolean simulate) {
            var network = owner.network(); return network == null ? left :
                    AEFluidRecipeHandler.handleNetwork(io, recipe, left, simulate, inventory, owner, network, owner.actionSource);
        }
        @Override public Object2LongMap<FluidStack> getMEFluidMap() {
            var network = owner.network();
            if (network == null) { amounts.clear(); display.clear(); return amounts; }
            long tick = owner.getOffsetTimer();
            if (changed || ConfigHolder.INSTANCE.enableUltimateMEStocking && snapshotTick != tick) {
                changed = false; snapshotTick = tick; amounts.clear(); display.clear(); Set<AEFluidKey> queried = new HashSet<>();
                for (var slot : inventory) {
                    var config = slot.getConfig();
                    if (config == null || !(config.what() instanceof AEFluidKey key) || !queried.add(key)) continue;
                    long available = network.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, owner.actionSource);
                    if (available > 0) { amounts.put(key.toStack(1), available); display.add(key.toStack((int) Math.min(Integer.MAX_VALUE, available))); }
                }
            }
            return amounts;
        }
        @Override public List<FluidStack> getMEFluidList() { getMEFluidMap(); return display; }
        @Override public List<FluidStack> getFluidList() { return display; }
        @Override public void onContentsChanged() { changed = true; super.onContentsChanged(); }
    }
    private static class StockingFluidSlot extends ExportOnlyAEConfigureFluidSlot {
        private final MEDualHatchStockPartMachine owner;
        private StockingFluidSlot(MEDualHatchStockPartMachine owner) { this.owner = owner; }
        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            return !resource.isEmpty() && config != null && config.what().equals(AEFluidKey.of(resource)) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }
        @Override public FluidStack drain(int amount, FluidAction action) {
            if (amount <= 0 || config == null || !(config.what() instanceof AEFluidKey key)) return FluidStack.EMPTY;
            var network = owner.network(); if (network == null) return FluidStack.EMPTY;
            long extracted = network.extract(key, amount, action.simulate() ? Actionable.SIMULATE : Actionable.MODULATE, owner.actionSource);
            if (extracted <= 0) return FluidStack.EMPTY;
            if (action.execute()) { stock = stock == null || stock.amount() <= extracted ? null : new GenericStack(key, stock.amount() - extracted); onContentsChanged(); }
            return key.toStack((int) extracted);
        }
        @Override public StockingFluidSlot copy() { var copy = new StockingFluidSlot(owner); copy.setConfigWithoutNotify(config); copy.stock = stock; return copy; }
    }
}
