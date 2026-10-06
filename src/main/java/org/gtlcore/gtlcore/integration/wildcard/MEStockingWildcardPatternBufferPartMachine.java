package org.gtlcore.gtlcore.integration.wildcard;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.ExportOnlyAEConfigureFluidSlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.ExportOnlyAEConfigureItemSlot;
import org.gtlcore.gtlcore.api.machine.trait.MEStock.IMESlot;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.StockingWildcardRecipeHandlerTrait;
import org.gtlcore.gtlcore.api.gui.AdvancedMEConfigurator;
import org.gtlcore.gtlcore.client.gui.widget.AEDualConfigWidget;
import brachy.modularui.widget.ParentWidget;
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

/** Wildcard expansion with server-owned ME stocking. A failed withdrawal never consumes local ownership. */
public class MEStockingWildcardPatternBufferPartMachine extends MEWildcardPatternBufferPartMachine implements IModifiableSyncOffset {
    protected static final int CONFIG_SIZE = 32;
    @SaveField protected final ExportOnlyAEStockingItemList stockItemHandler;
    @SaveField protected final ExportOnlyAEStockingFluidList stockFluidHandler;
    @SaveField private int syncOffset;
    @SaveField protected int page = 1;

    public MEStockingWildcardPatternBufferPartMachine(BlockEntityCreationInfo info, IO io) {
        super(info, io);
        stockItemHandler = attachTrait(new ExportOnlyAEStockingItemList(this, CONFIG_SIZE));
        stockFluidHandler = attachTrait(new ExportOnlyAEStockingFluidList(this, CONFIG_SIZE));
    }
    @Override protected MEPatternBufferRecipeHandlerTraitBase createRecipeHandler(IO io) {
        return new StockingWildcardRecipeHandlerTrait(this, io);
    }
    @Override protected InternalSlot createWildcardInternalSlot(int index) { return new StockingWildcardInternalSlot(index); }
    @Override public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel) scheduleForNextServerTick(() -> {
            if (isRemoved()) return;
            stockItemHandler.onConfigChanged(); stockFluidHandler.onConfigChanged(); syncStockInput();
        });
    }
    @Override protected void update() {
        super.update();
        int interval = Math.max(1, syncOffset == 0 ? com.gregtechceu.gtceu.config.ConfigHolder.INSTANCE.compat.ae2.updateIntervals : syncOffset);
        if (getOffsetTimer() % interval == 0) syncStockInput();
    }
    @Override public int getMEUpdateInterval() { return syncOffset; }
    @Override public void setMEUpdateInterval(int offset) { syncOffset = Math.max(0, offset); markAsChanged(); }
    public int getPage() { return page; }
    public void setPage(int value) { page = Math.clamp(value, 1, (CONFIG_SIZE + 7) / 8); markAsChanged(); }

    /** Paired item/fluid configuration uses the original 32 shared positions. */
    public void configureStock(int index, @Nullable GenericStack config) {
        if (index < 0 || index >= CONFIG_SIZE || config != null && config.amount() <= 0) return;
        if (config == null || config.what() instanceof AEItemKey) {
            stockFluidHandler.getInventory()[index].setConfig(null);
            stockItemHandler.getInventory()[index].setConfig(config == null ? null : new GenericStack(config.what(), 1));
        } else if (config.what() instanceof AEFluidKey) {
            stockItemHandler.getInventory()[index].setConfig(null);
            stockFluidHandler.getInventory()[index].setConfig(new GenericStack(config.what(), 1000));
        }
        markAsChanged();
    }

    public @Nullable GenericStack getConfiguredStock(int index) {
        if (index < 0 || index >= CONFIG_SIZE) return null;
        var item = stockItemHandler.getInventory()[index].getConfig();
        return item != null ? item : stockFluidHandler.getInventory()[index].getConfig();
    }
    protected void onStockInputConfigChanged(boolean removed) {
        if (!isRemote()) { markAsChanged(); syncStockInput(); if (removed) clearRuntimeRecipeCache(); }
    }
    protected void syncStockInput() {
        var oldItems = new Object2LongOpenHashMap<>(stockItemHandler.stockMap);
        var oldFluids = new Object2LongOpenHashMap<>(stockFluidHandler.stockMap);
        IGrid grid = getMainNode().getGrid();
        if (grid == null) { stockItemHandler.clearStocks(); stockFluidHandler.clearStocks(); }
        else { var storage = grid.getStorageService().getInventory(); stockItemHandler.syncStock(storage); stockFluidHandler.syncStock(storage); }
        if (!oldItems.equals(stockItemHandler.stockMap) || !oldFluids.equals(stockFluidHandler.stockMap)) {
            recipeHandler.notifyInputListeners();
        }
    }
    @Override public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var builder = new AdvancedMEConfigurator(this::setMEUpdateInterval, this::getMEUpdateInterval)
                .attachTo(super.getPanelBuilder(data, sync, settings), sync);
        var inherited = builder.leftConfigurators();
        var stock = sync.syncedPanel("wildcardStockConfiguration", true, (manager, handler) -> {
            var configured = SyncHandlers.intNumber(() -> stockItemHandler.configList.size() + stockFluidHandler.configList.size(), null);
            manager.syncValue("wildcardStockConfigured", configured);
            return PopupPanel.createPopupPanel("wildcardStockConfigurationPanel", 164, 108)
                    .child(Text.lang("gui.gtlcore.stock_input_config").asWidget().pos(8, 22))
                    .child(Text.dynamic(() -> Component.literal(configured.getIntValue() + " / " + CONFIG_SIZE)).asWidget().pos(125, 22))
                    .child(new AEDualConfigWidget(10, 38, stockItemHandler, stockFluidHandler, this::setPage, page, false, 1).syncManager(manager));
        });
        return builder.leftConfigurators(flow -> {
            inherited.accept(flow);
            flow.child(new ButtonWidget<>().name("wildcardStockConfigurationButton").size(18).overlay(Text.str("S"))
                    .tooltip(t -> t.addLine(Component.translatable("gui.gtlcore.stock_input_config")))
                    .onMousePressed((context, button) -> { if (button != 0) return false; stock.openPanel(); return true; }));
        });
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

    protected class StockingWildcardInternalSlot extends InternalSlot {

        public StockingWildcardInternalSlot(int slotIndex) {
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
