package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.ExportOnlyAEConfigureFluidSlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.ExportOnlyAEConfigureItemSlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IMESlot;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.slot.*;


import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import com.gregtechceu.gtceu.integration.ae2.gui.AEConfigWidget;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.textfield.TextFieldWidget;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.crafting.pattern.EncodedPatternItem;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static org.gtlcore.gtlcore.utils.NumberUtils.saturatedAdd;

public class MEStockingPatternBufferPartMachine extends MEPatternBufferPartMachine implements IModifiableSyncOffset {

    protected static final int CONFIG_SIZE = 32;

    @SaveField
    protected final ExportOnlyAEStockingItemList stockItemHandler;

    @SaveField
    protected final ExportOnlyAEStockingFluidList stockFluidHandler;

    @SaveField
    private int syncOffset;

    @SaveField
    @Setter
    protected int page = 1;

    public MEStockingPatternBufferPartMachine(BlockEntityCreationInfo holder, int maxPatternCount, IO io) {
        super(holder, maxPatternCount, io);
        this.stockItemHandler = attachTrait(new ExportOnlyAEStockingItemList(this, CONFIG_SIZE));
        this.stockFluidHandler = attachTrait(new ExportOnlyAEStockingFluidList(this, CONFIG_SIZE));
    }

    @Override
    protected MEPatternBufferRecipeHandlerTrait createRecipeHandler(IO io) {
        return super.createRecipeHandler(io);
    }

    @Override
    protected InternalSlot createInternalSlot(int slotIndex) {
        return new StockingPatternBufferInternalSlot(slotIndex);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            scheduleForNextServerTick(() -> {
                if (isRemoved()) return;
                stockItemHandler.onConfigChanged();
                stockFluidHandler.onConfigChanged();
                syncStockInput();
            });
        }
    }

    @Override
    protected void update() {
        super.update();
        int offset = getMEUpdateInterval();
        if (getOffsetTimer() % Math.max(1, offset == 0 ? com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.compat.ae2.updateIntervals : offset) == 0) {
            syncStockInput();
        }
    }

    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var builder = super.getPanelBuilder(data, sync, settings);
        var inherited = builder.leftConfigurators();
        var itemPanel = sync.syncedPanel("patternStockItems", true, (manager, handler) -> stockPanel(manager, false));
        var fluidPanel = sync.syncedPanel("patternStockFluids", true, (manager, handler) -> stockPanel(manager, true));
        var intervalPanel = sync.syncedPanel("patternStockInterval", true, (manager, handler) -> {
            var value = SyncHandlers.string(() -> Integer.toString(getMEUpdateInterval()), text -> {
                try { setMEUpdateInterval(Integer.parseInt(text)); } catch (NumberFormatException ignored) {}
            }).allowC2S();
            manager.syncValue("stockUpdateInterval", value);
            return PopupPanel.createPopupPanel("patternStockIntervalPanel", 150, 54)
                    .child(Text.lang("gtceu.gui.title.adv_me_config.ticks_per_cycle").asWidget().pos(4, 4))
                    .child(new TextFieldWidget().value(value).size(140, 18).pos(4, 28));
        });
        return builder.leftConfigurators(flow -> {
            inherited.accept(flow);
            flow.child(new ButtonWidget<>().size(18).overlay(Text.str("I"))
                            .tooltipDynamic(t -> t.addLine(Component.translatable("gui.gtlcore.stock_input_config")))
                            .onMousePressed((context, button) -> { if (button != 0) return false; itemPanel.openPanel(); return true; }))
                    .child(new ButtonWidget<>().size(18).overlay(Text.str("F"))
                            .tooltipDynamic(t -> t.addLine(Component.translatable("gui.gtlcore.stock_input_config")))
                            .onMousePressed((context, button) -> { if (button != 0) return false; fluidPanel.openPanel(); return true; }))
                    .child(new ButtonWidget<>().size(18).overlay(Text.str("T"))
                            .tooltipDynamic(t -> t.addLine(Component.translatable("gtceu.gui.adv_me_config.ticks_per_cycle")))
                            .onMousePressed((context, button) -> { if (button != 0) return false; intervalPanel.openPanel(); return true; }));
        });
    }

    private PopupPanel stockPanel(PanelSyncManager manager, boolean fluid) {
        registerStockConfigActions(manager, fluid);
        return PopupPanel.createPopupPanel(fluid ? "patternStockFluidsPanel" : "patternStockItemsPanel", 170, 194)
                .child(Text.lang("gui.gtlcore.stock_input_config").asWidget().pos(4, 4))
                .child(new AEConfigWidget(fluid ? stockFluidHandler : stockItemHandler, CONFIG_SIZE, fluid)
                        .syncManager(manager).size(144, 152).pos(12, 22));
    }

    private void registerStockConfigActions(PanelSyncManager manager, boolean fluid) {
        manager.registerServerSyncedAction("ae_config_set", packet -> {
            int slot = packet.readVarInt(); if (slot < 0 || slot >= CONFIG_SIZE) return;
            var carried = manager.getPlayer().containerMenu.getCarried();
            if (carried.isEmpty()) return;
            if (fluid) net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(carried).ifPresent(value ->
                    configureStock(slot, GenericStack.fromFluidStack(value)));
            else configureStock(slot, GenericStack.fromItemStack(carried));
        });
        manager.registerServerSyncedAction("ae_config_clear", packet -> {
            int slot = packet.readVarInt(); if (slot < 0 || slot >= CONFIG_SIZE) return;
            if (fluid) stockFluidHandler.getInventory()[slot].setConfig(null);
            else stockItemHandler.getInventory()[slot].setConfig(null);
        });
        manager.registerServerSyncedAction("ae_config_set_ghost", packet -> {
            int slot = packet.readVarInt(); if (slot < 0 || slot >= CONFIG_SIZE) return;
            boolean fluidPayload = packet.readBoolean(); if (fluidPayload != fluid) return;
            GenericStack config = fluid ? GenericStack.fromFluidStack(FluidStack.STREAM_CODEC.decode(packet)) :
                    GenericStack.fromItemStack(ItemStack.STREAM_CODEC.decode(packet));
            if (config != null) configureStock(slot, config);
        });
    }

    /** Item/fluid configuration share the original 32 positions. They never duplicate network availability. */
    public void configureStock(int slot, @Nullable GenericStack config) {
        if (slot < 0 || slot >= CONFIG_SIZE || config != null && config.amount() <= 0) return;
        if (config == null || config.what() instanceof AEItemKey) {
            stockFluidHandler.getInventory()[slot].setConfig(null);
            stockItemHandler.getInventory()[slot].setConfig(config == null ? null : new GenericStack(config.what(), 1));
        } else if (config.what() instanceof AEFluidKey) {
            stockItemHandler.getInventory()[slot].setConfig(null);
            stockFluidHandler.getInventory()[slot].setConfig(new GenericStack(config.what(), 1000));
        }
        markAsChanged();
    }

    public @Nullable GenericStack getConfiguredStock(int slot) {
        if (slot < 0 || slot >= CONFIG_SIZE) return null;
        var item = stockItemHandler.getInventory()[slot].getConfig();
        return item != null ? item : stockFluidHandler.getInventory()[slot].getConfig();
    }

    @Override
    public int getMEUpdateInterval() {
        return syncOffset;
    }

    @Override
    public void setMEUpdateInterval(int offset) {
        this.syncOffset = Math.max(0, offset);
        markAsChanged();
    }

    protected void onStockInputConfigChanged(boolean removedConfig) {
        if (!isRemote()) {
            markAsChanged();
            syncStockInput();
            if (removedConfig) {
                invalidateRecipeCaches();
            }
        }
    }

    protected void invalidateRecipeCaches() {
        for (int slot = 0; slot < maxPatternCount; slot++) {
            removeSlotFromGTRecipeCache(slot);
        }
    }

    protected void syncStockInput() {
        // No active pattern slots means no recipe can currently run here or in bound proxies.
        // Sync stock for the UI, but skip the expensive before/after comparison and notification.
        if (!hasActiveSlot()) {
            performStockSync();
            return;
        }

        var oldItemStock = new Object2LongOpenHashMap<>(stockItemHandler.stockMap);
        var oldFluidStock = new Object2LongOpenHashMap<>(stockFluidHandler.stockMap);

        performStockSync();

        boolean changed = !oldItemStock.equals(stockItemHandler.stockMap) || !oldFluidStock.equals(stockFluidHandler.stockMap);
        if (changed) {
            recipeHandler.getMeItemHandler().notifyListeners();
            recipeHandler.getMeFluidHandler().notifyListeners();
        }
    }

    private boolean hasActiveSlot() {
        int count = getInternalSlotCount();
        for (int i = 0; i < count; i++) {
            if (getInternalSlot(i).isActive()) {
                return true;
            }
        }
        return false;
    }

    private void performStockSync() {
        IGrid grid = this.getMainNode().getGrid();
        if (grid == null) {
            stockItemHandler.clearStocks();
            stockFluidHandler.clearStocks();
            return;
        }

        IStorageService storageService = grid.getStorageService();
        MEStorage networkStorage = storageService.getInventory();
        stockItemHandler.syncStock(networkStorage);
        stockFluidHandler.syncStock(networkStorage);
    }

    private <K extends AEKey, T> boolean handleStockedInputs(Object2LongMap<T> demands, Object2LongMap<K> owned,
            Object2LongMap<K> catalysts, List<K> configured, java.util.function.BiPredicate<T, K> matches,
            java.util.function.ToLongFunction<K> availableInNetwork, boolean simulate) {
        var grid = getMainNode().getGrid();
        return org.gtlcore.gtlcore.integration.ae2.StockingInputTransactions.handle(demands, owned, catalysts, configured,
                matches, availableInNetwork, simulate, grid == null ? null : grid.getStorageService().getInventory(), actionSource,
                buffer, () -> { markAsChanged(); getMETrait().notifySelfIO(); syncStockInput(); });
    }

    protected void appendStockItemContents(List<ItemStack> inputs) {
        for (var entry : Object2LongMaps.fastIterable(stockItemHandler.stockMap)) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                inputs.add(entry.getKey().toStack((int) Math.min(amount, Integer.MAX_VALUE)));
            }
        }
    }

    protected void appendStockFluidContents(List<FluidStack> inputs) {
        for (var entry : Object2LongMaps.fastIterable(stockFluidHandler.stockMap)) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                inputs.add(entry.getKey().toStack((int) Math.min(amount, Integer.MAX_VALUE)));
            }
        }
    }

    protected void addStockItemMap(Object2LongOpenHashMap<ItemStack> map) {
        for (var entry : Object2LongMaps.fastIterable(stockItemHandler.stockMap)) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                map.addTo(entry.getKey().toStack(), amount);
            }
        }
    }

    public @NotNull Object2LongMap<ItemStack> getStockingItemMap() {
        var map = new Object2LongOpenHashMap<ItemStack>();
        addStockItemMap(map);
        return map;
    }

    protected void addStockFluidMap(Object2LongOpenHashMap<FluidStack> map) {
        for (var entry : Object2LongMaps.fastIterable(stockFluidHandler.stockMap)) {
            long amount = entry.getLongValue();
            if (amount > 0) {
                map.addTo(entry.getKey().toStack(1), amount);
            }
        }
    }

    public @NotNull Object2LongMap<FluidStack> getStockingFluidMap() {
        var map = new Object2LongOpenHashMap<FluidStack>();
        addStockFluidMap(map);
        return map;
    }

    protected class StockingPatternBufferInternalSlot extends PatternBufferInternalSlot {

        public StockingPatternBufferInternalSlot(int slotIndex) {
            super(slotIndex);
        }

        @Override
        public boolean isItemActive(boolean simulate) {
            return hasPatternInSlot(getSlotIndex()) && (simulate ?
                    (!getItemInventory().isEmpty() || !sharedCatalystInventory.isEmpty() ||
                            !getCircuitForRecipe(getSlotIndex()).isEmpty() || hasItemCatalystInventory() ||
                            hasVirtualItemSupply() || stockItemHandler.hasConfig()) :
                    (!getItemInventory().isEmpty() || stockItemHandler.hasConfig()));
        }

        @Override
        public boolean isFluidActive(boolean simulate) {
            return hasPatternInSlot(getSlotIndex()) && (simulate ?
                    (!getFluidInventory().isEmpty() || !sharedCatalystTank.isEmpty() ||
                            hasFluidCatalystInventory() || hasVirtualFluidSupply() ||
                            stockFluidHandler.hasConfig()) :
                    (!getFluidInventory().isEmpty() || stockFluidHandler.hasConfig()));
        }

        @Override
        public ObjectList<ItemStack> getLimitItemStackInput() {
            var inputs = super.getLimitItemStackInput();
            appendStockItemContents(inputs);
            return inputs;
        }

        @Override
        public ObjectList<FluidStack> getLimitFluidStackInput() {
            var inputs = super.getLimitFluidStackInput();
            appendStockFluidContents(inputs);
            return inputs;
        }

        @Override
        public Object2LongMap<ItemStack> getItemStackInputMap() {
            var map = new Object2LongOpenHashMap<ItemStack>();
            for (var entry : Object2LongMaps.fastIterable(super.getItemStackInputMap())) {
                map.addTo(entry.getKey(), entry.getLongValue());
            }
            addStockItemMap(map);
            return map;
        }

        @Override
        public Object2LongMap<FluidStack> getFluidStackInputMap() {
            var map = new Object2LongOpenHashMap<FluidStack>();
            for (var entry : Object2LongMaps.fastIterable(super.getFluidStackInputMap())) {
                map.addTo(entry.getKey(), entry.getLongValue());
            }
            addStockFluidMap(map);
            return map;
        }

        @Override
        public boolean handleItemInternal(Object2LongMap<SizedIngredient> left, int leftCircuit, boolean simulate) {
            if (!stockItemHandler.hasConfig()) return super.handleItemInternal(left, leftCircuit, simulate);
            if (simulate && leftCircuit >= 0 && leftCircuit != getCacheManager().getCircuitCache()) return false;
            return handleStockedInputs(left, getItemInventory(), getItemCatalystInventory(), stockItemHandler.configList,
                    (ingredient, key) -> org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient.matchingIngredient(ingredient).test(key.toStack(1)),
                    stockItemHandler::getAvailableAmount, simulate);
        }

        @Override
        public boolean handleFluidInternal(Object2LongMap<SizedFluidIngredient> left, boolean simulate) {
            if (!stockFluidHandler.hasConfig()) return super.handleFluidInternal(left, simulate);
            return handleStockedInputs(left, getFluidInventory(), getFluidCatalystInventory(), stockFluidHandler.configList,
                    AEUtils::testFluidIngredient, stockFluidHandler::getAvailableAmount, simulate);
        }
    }

    protected class ExportOnlyAEStockingItemList extends ExportOnlyAEItemList {

        protected final ObjectArrayList<AEItemKey> configList = new ObjectArrayList<>();
        protected final IntArrayList configIndexList = new IntArrayList();
        protected final Object2LongOpenHashMap<AEItemKey> stockMap = new Object2LongOpenHashMap<>();

        public ExportOnlyAEStockingItemList(MetaMachine holder, int slots) {
            super(slots, ExportOnlyAEStockingItemSlot::new);
            stockMap.defaultReturnValue(0);
            for (ExportOnlyAEItemSlot slot : inventory) {
                ((IMESlot) slot).setOnConfigChanged(() -> {
                    onStockInputConfigChanged(onConfigChanged());
                });
            }
        }

        @Override public boolean isStocking() { return true; }
        @Override public IO getHandlerIO() { return IO.NONE; }

        public void clearInventory(int startIndex) {
            for (int i = startIndex; i < this.getConfigurableSlots(); ++i) {
                IConfigurableSlot slot = this.getConfigurableSlot(i);
                ((IMESlot) slot).setConfigWithoutNotify(null);
                slot.setStock(null);
            }
            onStockInputConfigChanged(onConfigChanged());
        }

        public void clearStocks() {
            stockMap.clear();
            for (ExportOnlyAEItemSlot slot : inventory) {
                slot.setStock(null);
            }
        }

        public void syncStock(MEStorage networkStorage) {
            stockMap.clear();
            for (ExportOnlyAEItemSlot slot : inventory) {
                GenericStack config = slot.getConfig();
                if (config != null && config.what() instanceof AEItemKey key) {
                    long amount = networkStorage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
                    if (amount > 0) {
                        slot.setStock(new GenericStack(key, amount));
                        stockMap.put(key, amount);
                        continue;
                    }
                }
                slot.setStock(null);
            }
        }

        public boolean onConfigChanged() {
            var previousConfig = new ObjectOpenHashSet<>(configList);
            configList.clear();
            configIndexList.clear();
            for (int i = 0; i < inventory.length; i++) {
                GenericStack config = inventory[i].getConfig();
                if (config != null && config.what() instanceof AEItemKey key) {
                    if (!configList.contains(key)) configList.add(key);
                    configIndexList.add(i);
                    previousConfig.remove(key);
                }
            }
            return !previousConfig.isEmpty();
        }

        public boolean hasConfig() {
            return !configList.isEmpty();
        }

        public long getAvailableAmount(AEItemKey key) {
            IGrid grid = getMainNode().getGrid();
            return grid == null ? 0 : configList.contains(key) ? grid.getStorageService().getInventory()
                    .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource) : 0;
        }

        public long extractStock(AEItemKey key, long amount) {
            if (amount <= 0 || getMainNode().getGrid() == null) return 0;
            long extracted = getMainNode().getGrid().getStorageService().getInventory()
                    .extract(key, amount, Actionable.MODULATE, actionSource);
            if (extracted <= 0) return 0;

            long left = getAvailableAmount(key);
            if (left <= 0) stockMap.removeLong(key);
            else stockMap.put(key, left);
            for (int index : configIndexList) {
                ExportOnlyAEItemSlot slot = inventory[index];
                GenericStack config = slot.getConfig();
                if (config != null && key.equals(config.what())) {
                    slot.setStock(left > 0 ? new GenericStack(key, left) : null);
                    break;
                }
            }
            return extracted;
        }
    }

    protected static class ExportOnlyAEStockingItemSlot extends ExportOnlyAEConfigureItemSlot {

        public ExportOnlyAEStockingItemSlot() {
            super();
        }

        public ExportOnlyAEStockingItemSlot(@Nullable GenericStack config, @Nullable GenericStack stock) {
            super(config, stock);
        }

        @Override
        public @NotNull ExportOnlyAEStockingItemSlot copy() {
            return new ExportOnlyAEStockingItemSlot(this.config == null ? null : copy(this.config), this.stock == null ? null : copy(this.stock));
        }
    }

    protected class ExportOnlyAEStockingFluidList extends ExportOnlyAEFluidList {

        protected final ObjectArrayList<AEFluidKey> configList = new ObjectArrayList<>();
        protected final IntArrayList configIndexList = new IntArrayList();
        protected final Object2LongOpenHashMap<AEFluidKey> stockMap = new Object2LongOpenHashMap<>();

        public ExportOnlyAEStockingFluidList(MetaMachine holder, int slots) {
            super(holder, slots, ExportOnlyAEStockingFluidSlot::new);
            stockMap.defaultReturnValue(0);
            for (ExportOnlyAEFluidSlot slot : inventory) {
                ((IMESlot) slot).setOnConfigChanged(() -> {
                    onStockInputConfigChanged(onConfigChanged());
                });
            }
        }

        @Override public boolean isStocking() { return true; }
        @Override public IO getHandlerIO() { return IO.NONE; }

        public void clearInventory(int startIndex) {
            for (int i = startIndex; i < this.getConfigurableSlots(); ++i) {
                IConfigurableSlot slot = this.getConfigurableSlot(i);
                ((IMESlot) slot).setConfigWithoutNotify(null);
                slot.setStock(null);
            }
            onStockInputConfigChanged(onConfigChanged());
        }

        public void clearStocks() {
            stockMap.clear();
            for (ExportOnlyAEFluidSlot slot : inventory) {
                slot.setStock(null);
            }
        }

        public void syncStock(MEStorage networkStorage) {
            stockMap.clear();
            for (ExportOnlyAEFluidSlot slot : inventory) {
                GenericStack config = slot.getConfig();
                if (config != null && config.what() instanceof AEFluidKey key) {
                    long amount = networkStorage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource);
                    if (amount > 0) {
                        slot.setStock(new GenericStack(key, amount));
                        stockMap.put(key, amount);
                        continue;
                    }
                }
                slot.setStock(null);
            }
        }

        public boolean onConfigChanged() {
            var previousConfig = new ObjectOpenHashSet<>(configList);
            configList.clear();
            configIndexList.clear();
            for (int i = 0; i < inventory.length; i++) {
                GenericStack config = inventory[i].getConfig();
                if (config != null && config.what() instanceof AEFluidKey key) {
                    if (!configList.contains(key)) configList.add(key);
                    configIndexList.add(i);
                    previousConfig.remove(key);
                }
            }
            return !previousConfig.isEmpty();
        }

        public boolean hasConfig() {
            return !configList.isEmpty();
        }

        public long getAvailableAmount(AEFluidKey key) {
            IGrid grid = getMainNode().getGrid();
            return grid == null ? 0 : configList.contains(key) ? grid.getStorageService().getInventory()
                    .extract(key, Long.MAX_VALUE, Actionable.SIMULATE, actionSource) : 0;
        }

        public long extractStock(AEFluidKey key, long amount) {
            if (amount <= 0 || getMainNode().getGrid() == null) return 0;
            long extracted = getMainNode().getGrid().getStorageService().getInventory()
                    .extract(key, amount, Actionable.MODULATE, actionSource);
            if (extracted <= 0) return 0;

            long left = getAvailableAmount(key);
            if (left <= 0) stockMap.removeLong(key);
            else stockMap.put(key, left);
            for (int index : configIndexList) {
                ExportOnlyAEFluidSlot slot = inventory[index];
                GenericStack config = slot.getConfig();
                if (config != null && key.equals(config.what())) {
                    slot.setStock(left > 0 ? new GenericStack(key, left) : null);
                    break;
                }
            }
            return extracted;
        }
    }

    protected static class ExportOnlyAEStockingFluidSlot extends ExportOnlyAEConfigureFluidSlot {

        public ExportOnlyAEStockingFluidSlot() {
            super();
        }

        public ExportOnlyAEStockingFluidSlot(@Nullable GenericStack config, @Nullable GenericStack stock) {
            super(config, stock);
        }

        @Override
        public @NotNull ExportOnlyAEStockingFluidSlot copy() {
            return new ExportOnlyAEStockingFluidSlot(this.config == null ? null : copy(this.config), this.stock == null ? null : copy(this.stock));
        }
    }
}
