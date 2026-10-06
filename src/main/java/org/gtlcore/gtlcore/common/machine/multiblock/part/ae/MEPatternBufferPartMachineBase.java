package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.gui.PatternBufferProxyPositionConfigurator;
import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternPartMachine;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;
import org.gtlcore.gtlcore.api.machine.trait.NotifiableCircuitItemStackHandler;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphDispatchPreflight;
import org.gtlcore.gtlcore.integration.ae2.handler.MEBufferPatternHelper;
import org.gtlcore.gtlcore.integration.ae2.handler.SlotCacheManager;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;


import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.common.util.INBTSerializable;
import net.minecraft.core.HolderLookup;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.common.mui.GTMuiMachineUtil;
import com.gregtechceu.gtceu.common.mui.GTMuiWidgets;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import org.gtlcore.gtlcore.integration.ae2.AEOutputBuffer;
import org.gtlcore.gtlcore.integration.ae2.AERefundQueue;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.phys.BlockHitResult;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.*;
import appeng.helpers.patternprovider.PatternContainer;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystFluidStackHandler;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystItemStackHandler;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.IntCollection;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared implementation for pattern buffer variants.
 */
public abstract class MEPatternBufferPartMachineBase extends MEIOPartMachine
                                                     implements ICraftingProvider, PatternContainer, IMEPatternPartMachine, IMuiMachine, IDataStickInteractable,
                                                     GraphDispatchPreflight {

    private static final Comparator<BlockPos> PROXY_POSITION_ORDER = Comparator.comparingInt((BlockPos position) -> position.getX())
            .thenComparingInt(position -> position.getY())
            .thenComparingInt(position -> position.getZ());

    // ========================================
    // Common Fields
    // ========================================

    @SyncToClient
    @SaveField
    @Getter
    protected String customName = "";

    @SaveField
    protected final AEOutputBuffer outputStorage = new AEOutputBuffer(() -> {});

    @SaveField
    protected final AERefundQueue refundQueue = new AERefundQueue(() -> {
        markAsChanged(); getMETrait().notifySelfIO();
    });

    @Getter
    protected final Object2LongMap<AEKey> buffer = outputStorage.storage;

    @Getter
    @SaveField(nbtKey = "shareInventory")
    protected final CatalystItemStackHandler sharedCatalystInventory;

    @Getter
    @SaveField(nbtKey = "shareTank")
    protected final CatalystFluidStackHandler sharedCatalystTank;

    @Getter
    @SaveField(nbtKey = "mePatternCircuitInventory")
    protected final NotifiableItemStackHandler sharedCircuitInventory;

    @Getter
    protected final MEBufferPatternHelper realPatternHelper;

    protected boolean needPatternSync;
    protected boolean destroying;

    @Nullable
    protected TickableSubscription updateSubs;

    @SaveField(nbtKey = "proxies")
    private final ObjectOpenHashSet<BlockPos> proxies;
    private final Set<MEPatternBufferProxyPartMachine> proxyMachines;

    // ========================================
    // Constructor
    // ========================================

    public MEPatternBufferPartMachineBase(BlockEntityCreationInfo holder, IO io) {
        super(holder, io);

        this.sharedCatalystInventory = attachTrait(new CatalystItemStackHandler(9, IO.IN, IO.NONE));
        this.sharedCatalystTank = attachTrait(new CatalystFluidStackHandler(9, 16 * FluidType.BUCKET_VOLUME, IO.IN, IO.NONE));
        this.sharedCircuitInventory = attachTrait(new NotifiableCircuitItemStackHandler());
        this.proxies = new ObjectOpenHashSet<>();
        this.proxyMachines = new ReferenceOpenHashSet<>();

        this.realPatternHelper = new MEBufferPatternHelper((NotifiableCircuitItemStackHandler) sharedCircuitInventory);

        getMainNode().addService(ICraftingProvider.class, this);
    }

    // ========================================
    // LIFECYCLE & NETWORK MANAGEMENT
    // ========================================

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.@NotNull State reason) {
        super.onMainNodeStateChanged(reason);
        this.updateSubscription();
    }

    protected void updateSubscription() {
        if (getMainNode().isOnline()) {
            updateSubs = subscribeServerTick(updateSubs, this::update);
        } else if (updateSubs != null) {
            updateSubs.unsubscribe();
            updateSubs = null;
        }
    }

    protected void update() {
        refundQueue.drainInto(buffer);
        if (needPatternSync) {
            ICraftingProvider.requestUpdate(getMainNode());
            this.needPatternSync = false;
        }
    }

    @Override
    public void onMachineDestroyed() {
        destroying = true;
        releaseProxies();
        super.onMachineDestroyed();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        updateSubs = null;
        proxyMachines.clear();
    }

    public void setCustomName(String name) {
        String bounded = name == null ? "" : name.substring(0, Math.min(128, name.length()));
        if (customName.equals(bounded)) return;
        customName = bounded;
        markAsChanged();
        syncDataHolder.markClientSyncFieldDirty("customName");
    }

    // ========================================
    // PROXY MANAGEMENT
    // ========================================

    public void addProxy(MEPatternBufferProxyPartMachine proxy) {
        if (proxies.add(proxy.getBlockPos())) markAsChanged();
        proxyMachines.add(proxy);
    }

    public void removeProxy(MEPatternBufferProxyPartMachine proxy) {
        if (proxies.remove(proxy.getBlockPos())) markAsChanged();
        proxyMachines.remove(proxy);
    }

    public Set<MEPatternBufferProxyPartMachine> getProxies() {
        var level = getLevel();
        if (level == null) return Collections.unmodifiableSet(proxyMachines);
        proxyMachines.clear();
        for (var it = proxies.iterator(); it.hasNext();) {
            var pos = it.next();
            // Keep unloaded bindings until their chunks become available.
            if (!level.hasChunkAt(pos)) continue;
            if (MetaMachine.getMachine(level, pos) instanceof MEPatternBufferProxyPartMachine proxy &&
                    getBlockPos().equals(proxy.getBufferPos())) proxyMachines.add(proxy);
            else { it.remove(); markAsChanged(); }
        }
        return Collections.unmodifiableSet(proxyMachines);
    }

    public List<BlockPos> getBoundProxyPositions() {
        return proxies.stream().sorted(PROXY_POSITION_ORDER).toList();
    }

    protected List<Long> getProxyPosList() {
        return getBoundProxyPositions().stream().map(BlockPos::asLong).toList();
    }

    protected void releaseProxies() {
        for (MEPatternBufferProxyPartMachine proxy : List.copyOf(getProxies())) {
            proxy.setBuffer(null);
        }
        proxyMachines.clear();
        proxies.clear();
    }

    protected void notifyProxySlotRemoved(int slot) {
        for (MEPatternBufferProxyPartMachine proxy : getProxies()) {
            proxy.removeSlotFromMap.accept(slot);
        }
    }

    // ========================================
    // REFUND SYSTEM
    // ========================================

    protected void refundAll() {
        if (isRemote()) return;
        for (int i = 0; i < getInternalSlotCount(); i++) {
            InternalSlot slot = getInternalSlot(i);
            if (refundSlot(slot.itemInventory, slot.fluidInventory)) slot.clearVirtualSupply();
        }
        AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);
        markAsChanged();
        getMETrait().notifySelfIO();
    }

    /** Transfer the whole slot into persisted refunds, retaining quantities that cannot fit one long ledger. */
    protected boolean refundSlot(Object2LongOpenHashMap<AEItemKey> items, Object2LongOpenHashMap<AEFluidKey> fluids) {
        refundQueue.takeOwnership(items, fluids);
        refundQueue.drainInto(buffer);
        return true;
    }

    @Override
    public void saveToItem(ItemStack stack, HolderLookup.Provider registries) {
        super.saveToItem(stack, registries);
        var owned = new AERefundQueue(() -> {});
        owned.deserializeNBT(registries, refundQueue.serializeNBT(registries));
        owned.appendSnapshot(buffer);
        for (int i = 0; i < getInternalSlotCount(); i++) {
            var slot = getInternalSlot(i);
            owned.appendSnapshot(slot.getItemInventory());
            owned.appendSnapshot(slot.getFluidInventory());
        }
        appendPendingRefundsForDrop(owned, registries);
        var custom = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!owned.isEmpty()) custom.put("gtlcoreMEPatternRefunds", owned.serializeNBT(registries));
        else custom.remove("gtlcoreMEPatternRefunds");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(custom));
    }

    /** Dynamic buffers can be broken before their deferred world-load restoration runs. */
    protected void appendPendingRefundsForDrop(AERefundQueue owned, HolderLookup.Provider registries) {}

    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        var custom = input.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (custom.contains("gtlcoreMEPatternRefunds", Tag.TAG_LIST)) {
            refundQueue.deserializeNBT(getLevel().registryAccess(), custom.getList("gtlcoreMEPatternRefunds", Tag.TAG_COMPOUND));
            refundQueue.drainInto(buffer);
            markAsChanged();
        }
    }

    // ========================================
    // GUI SYSTEM
    // ========================================

    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var items = sync.syncedPanel("patternSharedItems", true, (manager, handler) -> {
            var group = new SlotGroup("patternSharedItems", 3, false);
            return PopupPanel.createPopupPanel("patternSharedItemsPanel", 80, 86)
                    .child(Text.lang("gui.gtceu.share_inventory.title").asWidget().pos(4, 4))
                    .child(new Grid().top(26).leftRel(0.5f).minElementMargin(0, 0).minColWidth(18).minRowHeight(18)
                            .gridOfSizeWidth(9, 3, (x, y, i) -> new ItemSlot().slot(
                                    SyncHandlers.itemSlot(sharedCatalystInventory.storage, i).slotGroup(group))));
        });
        var fluids = sync.syncedPanel("patternSharedFluids", true, (manager, handler) ->
                PopupPanel.createPopupPanel("patternSharedFluidsPanel", 85, 86)
                        .child(Text.lang("gui.gtceu.share_tank.title").asWidget().pos(4, 4))
                        .child(GTMuiMachineUtil.createSlotGroupFromInventory(manager, sharedCatalystTank,
                                "patternSharedFluids", 9, 'F', GTMuiMachineUtil.createSquareMatrix(9, 'F')).top(26).leftRel(0.5f)));
        var circuit = sync.syncedPanel("patternSharedCircuit", true, (manager, handler) -> GTMuiWidgets.createCircuitSlotPanel(
                stack -> sharedCircuitInventory.storage.setStackInSlot(0, stack),
                () -> sharedCircuitInventory.storage.getStackInSlot(0), manager));
        var positions = new PatternBufferProxyPositionConfigurator(this::getBoundProxyPositions);
        var proxiesPanel = sync.syncedPanel("patternBufferProxyPositions", true, (manager, handler) -> positions.createPanel(manager));
        sync.registerServerSyncedAction("patternRefundAll", packet -> refundAll());
        return MachineUIPanelBuilder.panelBuilder(this).leftConfigurators(flow -> flow
                .child(new ButtonWidget<>().size(18).overlay(GTGuiTextures.BUTTON_ITEM_OUTPUT)
                        .onMousePressed((context, button) -> { if (button != 0) return false; items.openPanel(); return true; }))
                .child(new ButtonWidget<>().size(18).overlay(GTGuiTextures.BUTTON_FLUID_OUTPUT)
                        .onMousePressed((context, button) -> { if (button != 0) return false; fluids.openPanel(); return true; }))
                .child(new ButtonWidget<>().size(18).overlay(GTGuiTextures.REFUND_OVERLAY)
                        .onMousePressed((context, button) -> { if (button != 0) return false; sync.callSyncedAction("patternRefundAll"); return true; }))
                .child(new ButtonWidget<>().size(18).overlay(Text.str("#"))
                        .onMousePressed((context, button) -> { if (button != 0) return false; circuit.openPanel(); return true; }))
                .child(new ButtonWidget<>().size(18).overlay(positions.getIcon())
                        .onMousePressed((context, button) -> { if (button != 0) return false; proxiesPanel.openPanel(); return true; })));
    }

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack stack) {
        if (stack.has(GTDataComponents.RESEARCH_ITEM)) return InteractionResult.PASS;
        if (!isRemote()) {
            stack.set(GTDataComponents.DATA_COPY_POS, getBlockPos());
            player.sendSystemMessage(Component.translatable("gtceu.machine.me.import_copy_settings"));
        }
        return InteractionResult.sidedSuccess(isRemote());
    }

    // ========================================
    // AE2 PATTERN CONTAINER
    // ========================================

    @Override
    public @Nullable IGrid getGrid() {
        return getMainNode().getGrid();
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    public Pair<Object2LongOpenHashMap<Item>, Object2LongOpenHashMap<Fluid>> getMergedInternalSlot() {
        Object2LongOpenHashMap<Item> items = new Object2LongOpenHashMap<>();
        Object2LongOpenHashMap<Fluid> fluids = new Object2LongOpenHashMap<>();
        for (InternalSlot slot : getActiveInternalSlots()) {
            if (!slot.isActive()) continue;
            MEPatternBufferJadeMerger.mergeItemKeys(items, slot.getItemInventory());
            MEPatternBufferJadeMerger.mergeFluidKeys(fluids, slot.getFluidInventory());
        }
        for (int slotIndex = 0; slotIndex < getInternalSlotCount(); slotIndex++) {
            InternalSlot slot = getInternalSlot(slotIndex);
            MEPatternBufferJadeMerger.mergeItemKeys(items, slot.getItemCatalystInventory());
            MEPatternBufferJadeMerger.mergeFluidKeys(fluids, slot.getFluidCatalystInventory());
        }
        MEPatternBufferJadeMerger.mergeItemStacks(items, sharedCatalystInventory.getContents());
        MEPatternBufferJadeMerger.mergeFluidStacks(fluids, sharedCatalystTank.getContents());
        return Pair.of(items, fluids);
    }

    @Override
    public @NotNull IMEPatternTrait getMETrait() {
        return (IMEPatternTrait) meTrait;
    }

    public ItemStack getCircuitForRecipe(int slotIndex) {
        return realPatternHelper.getCircuitForRecipe(getInternalSlot(slotIndex).getCacheManager().getCircuitStack());
    }

    // ========================================
    // SLOT ACCESS AND PATTERN ROUTING
    // ========================================

    protected Iterable<InternalSlot> getActiveInternalSlots() {
        int slotCount = getInternalSlotCount();
        List<InternalSlot> slots = new ObjectArrayList<>(slotCount);
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            InternalSlot slot = getInternalSlot(slotIndex);
            if (slot.isActive()) slots.add(slot);
        }
        return slots;
    }

    protected @Nullable InternalSlot getFirstActiveInternalSlot(IntCollection slots) {
        for (int slot : slots) {
            if (slot >= 0 && slot < getInternalSlotCount()) {
                InternalSlot internalSlot = getInternalSlot(slot);
                if (internalSlot.isActive()) return internalSlot;
            }
        }
        return null;
    }

    protected @Nullable InternalSlot getInternalSlotOrNull(int slotIndex) {
        if (slotIndex >= 0 && slotIndex < getInternalSlotCount()) {
            return getInternalSlot(slotIndex);
        }
        return null;
    }

    protected int[] getActiveSlots() {
        int slotCount = getInternalSlotCount();
        int[] slots = new int[slotCount];
        int size = 0;
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            if (getInternalSlot(slotIndex).isActive()) slots[size++] = slotIndex;
        }
        return size == slotCount ? slots : Arrays.copyOf(slots, size);
    }

    protected int[] getActiveAndUnCachedSlots() {
        int slotCount = getInternalSlotCount();
        int[] slots = new int[slotCount];
        int size = 0;
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            if (getInternalSlot(slotIndex).isActive() && !hasRecipeCacheInSlot(slotIndex)) slots[size++] = slotIndex;
        }
        return size == slotCount ? slots : Arrays.copyOf(slots, size);
    }

    @Nullable
    protected abstract Integer getSlotIndexForPattern(IPatternDetails pattern);

    protected abstract int getInternalSlotCount();

    protected abstract InternalSlot getInternalSlot(int slotIndex);

    protected boolean hasRecipeCacheInSlot(int slotIndex) {
        return false;
    }

    protected abstract boolean hasPatternInSlot(int slotIndex);

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!getMainNode().isActive()) {
            return false;
        }

        var slotIndex = getSlotIndexForPattern(patternDetails);
        if (slotIndex != null && slotIndex >= 0 && slotIndex < getInternalSlotCount()) {
            return getInternalSlot(slotIndex).pushPattern(inputHolder);
        }
        return false;
    }

    @Override
    public boolean gtlcore$canProduceGraphPattern(IPatternDetails pattern) {
        Integer slot = getSlotIndexForPattern(pattern);
        return getMainNode().isActive() && slot != null && slot >= 0 && slot < getInternalSlotCount();
    }

    @Override
    public long gtlcore$graphCapacity(IPatternDetails pattern, Map<AEKey, Long> inputPerRun, long requested) {
        if (!gtlcore$canProduceGraphPattern(pattern)) return 0;
        InternalSlot slot = getInternalSlot(getSlotIndexForPattern(pattern));
        if (requested <= 0) return 0;
        for (var input : inputPerRun.entrySet()) {
            if (input.getValue() <= 0) return 0;
            long held;
            if (input.getKey() instanceof AEItemKey item) held = slot.getItemInventory().getLong(item);
            else if (input.getKey() instanceof AEFluidKey fluid) held = slot.getFluidInventory().getLong(fluid);
            else return 0;
            if (held < 0) return 0;
            requested = Math.min(requested, (Long.MAX_VALUE - held) / input.getValue());
        }
        return requested;
    }

    @Override
    public abstract Pair<IMERecipeHandlerTrait<SizedIngredient, ItemStack>, IMERecipeHandlerTrait<SizedFluidIngredient, FluidStack>> getMERecipeHandlerTraits();

    // ========================================
    // INTERNAL SLOT CLASS
    // ========================================

    /**
     * SizedIngredient storage for one resolved pattern slot.
     */
    public class InternalSlot implements INBTSerializable<CompoundTag> {

        @Getter
        @Setter
        protected Runnable onContentsChanged = () -> {};

        @Getter
        private final Object2LongOpenHashMap<AEItemKey> itemInventory = new Object2LongOpenHashMap<>();

        @Getter
        private final Object2LongOpenHashMap<AEFluidKey> fluidInventory = new Object2LongOpenHashMap<>();

        /** Sets rather than counted maps: having no quantity is what lets these satisfy a demand for any amount. */
        private final ObjectSet<AEItemKey> virtualItemSupply = new ObjectOpenHashSet<>();

        private final ObjectSet<AEFluidKey> virtualFluidSupply = new ObjectOpenHashSet<>();

        /**
         * A pattern carrying a wrapped circuit has no circuit left for {@code processPatternWithCircuit} to recover,
         * so unlike a real one this is the only record of it and has to outlive a reload on its own.
         */
        private int virtualCircuitConfig = -1;

        /**
         * Kept apart from the pushed ones: these live as long as the wrapper sits in the slot, not as long as the
         * pattern.
         */
        @Getter
        private final ObjectSet<AEItemKey> configuredVirtualItems = new ObjectOpenHashSet<>();

        @Getter
        private final ObjectSet<AEFluidKey> configuredVirtualFluids = new ObjectOpenHashSet<>();

        private boolean suppliesItem(AEItemKey key) {
            return virtualItemSupply.contains(key) || configuredVirtualItems.contains(key);
        }

        private boolean suppliesFluid(AEFluidKey key) {
            return virtualFluidSupply.contains(key) || configuredVirtualFluids.contains(key);
        }

        @SaveField
        @Getter
        private final SlotCacheManager cacheManager = new SlotCacheManager();

        @Getter
        private final int slotIndex;

        public InternalSlot(int slotIndex) {
            this.slotIndex = slotIndex;
            itemInventory.defaultReturnValue(0);
            fluidInventory.defaultReturnValue(0);
        }

        public boolean isActive() {
            return hasPatternInSlot(slotIndex) && (!itemInventory.isEmpty() || !fluidInventory.isEmpty());
        }

        public boolean isItemActive(boolean simulate) {
            return hasPatternInSlot(slotIndex) && (simulate ?
                    (!itemInventory.isEmpty() || !sharedCatalystInventory.isEmpty() ||
                            !getCircuitForRecipe(slotIndex).isEmpty() || hasItemCatalystInventory() ||
                            hasVirtualItemSupply()) :
                    !itemInventory.isEmpty());
        }

        public boolean isFluidActive(boolean simulate) {
            return hasPatternInSlot(slotIndex) && (simulate ?
                    (!fluidInventory.isEmpty() || !sharedCatalystTank.isEmpty() || hasFluidCatalystInventory() ||
                            hasVirtualFluidSupply()) :
                    !fluidInventory.isEmpty());
        }

        public void add(AEKey what, long amount) {
            if (amount <= 0L) return;
            if (what instanceof AEItemKey itemKey) {
                ItemStack stack = itemKey.getReadOnlyStack();
                if (VirtualIngredientBehavior.isVirtualIngredient(stack)) {
                    if (!VirtualIngredientBehavior.isBound(stack)) return;
                    // Wrapper is spent here: supply is unlimited, so only the payload is worth remembering.
                    AEItemKey payloadItem = VirtualIngredientBehavior.payloadItemKey(stack);
                    if (payloadItem != null) {
                        ItemStack payload = payloadItem.toStack(1);
                        if (IntCircuitBehaviour.isIntegratedCircuit(payload)) {
                            // Circuits are stripped out of the item map and matched against the slot's cached
                            // configuration, so a virtual one has to land in that channel or nothing consults it.
                            virtualCircuitConfig = IntCircuitBehaviour.getCircuitConfiguration(payload);
                            cacheManager.setCircuitCache(virtualCircuitConfig);
                        } else {
                            virtualItemSupply.add(payloadItem);
                        }
                    }
                    AEFluidKey payloadFluid = VirtualIngredientBehavior.payloadFluidKey(stack);
                    if (payloadFluid != null) virtualFluidSupply.add(payloadFluid);
                    return;
                }
                itemInventory.put(itemKey, Math.addExact(itemInventory.getLong(itemKey), amount));
            } else if (what instanceof AEFluidKey fluidKey) {
                fluidInventory.put(fluidKey, Math.addExact(fluidInventory.getLong(fluidKey), amount));
            }
        }

        public boolean pushPattern(KeyCounter[] inputHolder) {
            var inputs = new Object2LongOpenHashMap<AEKey>();
            try {
                for (var holder : inputHolder) {
                    if (holder == null) return false;
                    for (var entry : holder) {
                        if (entry.getLongValue() <= 0 || !(entry.getKey() instanceof AEItemKey || entry.getKey() instanceof AEFluidKey)) return false;
                        inputs.put(entry.getKey(), Math.addExact(inputs.getLong(entry.getKey()), entry.getLongValue()));
                    }
                }
                for (var entry : inputs.object2LongEntrySet()) {
                    var key = entry.getKey();
                    if (key instanceof AEItemKey item && !VirtualIngredientBehavior.isVirtualIngredient(item.getReadOnlyStack())) {
                        Math.addExact(itemInventory.getLong(item), entry.getLongValue());
                    } else if (key instanceof AEFluidKey fluid) Math.addExact(fluidInventory.getLong(fluid), entry.getLongValue());
                }
            } catch (ArithmeticException overflow) { return false; }
            for (var entry : inputs.object2LongEntrySet()) add(entry.getKey(), entry.getLongValue());
            onContentsChanged.run();
            markAsChanged();
            return true;
        }

        public Object2LongMap<ItemStack> getItemStackInputMap() {
            var itemInputMap = new Object2LongOpenHashMap<ItemStack>();
            for (Object2LongMap.Entry<AEItemKey> entry : Object2LongMaps.fastIterable(itemInventory)) {
                AEItemKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                itemInputMap.addTo(key.toStack(1), amount);
            }
            return itemInputMap;
        }

        public Object2LongMap<FluidStack> getFluidStackInputMap() {
            var fluidInputMap = new Object2LongOpenHashMap<FluidStack>();
            for (Object2LongMap.Entry<AEFluidKey> entry : Object2LongMaps.fastIterable(fluidInventory)) {
                AEFluidKey key = entry.getKey();
                long amount = entry.getLongValue();
                if (amount <= 0) continue;
                fluidInputMap.addTo(key.toStack(1), amount);
            }
            return fluidInputMap;
        }

        public ObjectList<ItemStack> getLimitItemStackInput() {
            var limitInput = new ObjectArrayList<ItemStack>(itemInventory.size());
            for (var it = Object2LongMaps.fastIterator(itemInventory); it.hasNext();) {
                var entry = it.next();
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    it.remove();
                    continue;
                }
                limitInput.add(entry.getKey().toStack((int) Math.min(amount, Integer.MAX_VALUE)));
            }
            appendLimitItemCatalystInputs(limitInput);
            appendVirtualItemInputs(limitInput);
            return limitInput;
        }

        public ObjectList<FluidStack> getLimitFluidStackInput() {
            var limitInput = new ObjectArrayList<FluidStack>(fluidInventory.size());
            for (var it = Object2LongMaps.fastIterator(fluidInventory); it.hasNext();) {
                var entry = it.next();
                long amount = entry.getLongValue();
                if (amount <= 0) {
                    it.remove();
                    continue;
                }
                limitInput.add(entry.getKey().toStack((int) Math.min(amount, Integer.MAX_VALUE)));
            }
            appendLimitFluidCatalystInputs(limitInput);
            appendVirtualFluidInputs(limitInput);
            return limitInput;
        }

        public boolean testCatalystItemInternal(GTRecipe recipe) {
            return true;
        }

        public boolean testCatalystFluidInternal(GTRecipe recipe) {
            return true;
        }

        public boolean handleItemInternal(Object2LongMap<SizedIngredient> left, int leftCircuit, boolean simulate) {
            if (left.isEmpty() && leftCircuit < 0) return true;

            if (simulate && leftCircuit >= 0 && !(leftCircuit == cacheManager.getCircuitCache())) {
                return false;
            }

            var available = new Object2LongOpenHashMap<>(itemInventory);
            for (var entry : Object2LongMaps.fastIterable(left)) {
                long need = entry.getLongValue();
                if (need <= 0) continue;
                var match = simulate ? cacheManager.getBestItemMatchSimulate(entry.getKey(), available, getItemCatalystInventory(), need) :
                        cacheManager.getBestItemMatch(entry.getKey(), available, need);
                if (match == null) return false;
                // Reusable catalysts are checked without consuming them, including in the simulation.
                if (simulate && getItemCatalystInventory().getLong(match) >= need) continue;
                long rest = available.getLong(match) - need;
                if (rest == 0) available.removeLong(match); else available.put(match, rest);
            }
            if (!simulate) {
                itemInventory.clear(); itemInventory.putAll(available); left.clear(); markAsChanged();
            }
            return true;
        }

        public boolean handleFluidInternal(Object2LongMap<SizedFluidIngredient> left, boolean simulate) {
            if (left.isEmpty()) return true;
            var available = new Object2LongOpenHashMap<>(fluidInventory);
            for (var entry : Object2LongMaps.fastIterable(left)) {
                long need = entry.getLongValue();
                if (need <= 0) continue;
                var match = simulate ? cacheManager.getBestFluidMatchSimulate(entry.getKey(), available, getFluidCatalystInventory(), need) :
                        cacheManager.getBestFluidMatch(entry.getKey(), available, need);
                if (match == null) return false;
                if (simulate && getFluidCatalystInventory().getLong(match) >= need) continue;
                long rest = available.getLong(match) - need;
                if (rest == 0) available.removeLong(match); else available.put(match, rest);
            }
            if (!simulate) {
                fluidInventory.clear(); fluidInventory.putAll(available); left.clear(); markAsChanged();
            }
            return true;
        }

        /**
         * Drops the pattern-vouched virtual supply. Safe at any time: the next dispatch pushes the wrapper again and
         * re-establishes it. A real circuit is untouched -- its cache is owned by pattern processing, not by
         * {@code virtualCircuitConfig}.
         */
        public void clearVirtualSupply() {
            virtualItemSupply.clear();
            virtualFluidSupply.clear();
            if (virtualCircuitConfig >= 0) {
                virtualCircuitConfig = -1;
                cacheManager.clearCircuitCache();
            }
        }

        public Object2LongMap<AEItemKey> getItemCatalystInventory() {
            return Object2LongMaps.emptyMap();
        }

        public Object2LongMap<AEFluidKey> getFluidCatalystInventory() {
            return Object2LongMaps.emptyMap();
        }

        protected boolean hasItemCatalystInventory() {
            return false;
        }

        protected boolean hasFluidCatalystInventory() {
            return false;
        }

        protected void appendLimitItemCatalystInputs(ObjectList<ItemStack> limitInput) {}

        protected void appendLimitFluidCatalystInputs(ObjectList<FluidStack> limitInput) {}

        // ==================== Virtual ingredient supply ====================
        //
        // Keys this slot may satisfy without holding the material. Restricted to inputs the recipe declares
        // non-consumed (chance == 0), which RecipeRunner already drops from the real pass, so nothing that would have
        // been paid for is skipped. Defaults below are inert; buffers that do not opt in behave as before.

        /** Keeps the slot eligible during matching even when its real inventory is empty. */
        protected boolean hasVirtualItemSupply() {
            return !virtualItemSupply.isEmpty() || !configuredVirtualItems.isEmpty();
        }

        protected boolean hasVirtualFluidSupply() {
            return !virtualFluidSupply.isEmpty() || !configuredVirtualFluids.isEmpty();
        }

        /**
         * Publishes virtual keys to GT recipe lookup, without which the branch tree never reaches a recipe needing
         * them. Amounts are unbounded because this list only feeds lookup; parallel calculation reads
         * {@link #getItemStackInputMap()}, which never sees these.
         */
        protected void appendVirtualItemInputs(ObjectList<ItemStack> limitInput) {
            for (AEItemKey key : virtualItemSupply) {
                limitInput.add(key.toStack(Integer.MAX_VALUE));
            }
            for (AEItemKey key : configuredVirtualItems) {
                if (!virtualItemSupply.contains(key)) limitInput.add(key.toStack(Integer.MAX_VALUE));
            }
        }

        protected void appendVirtualFluidInputs(ObjectList<FluidStack> limitInput) {
            for (AEFluidKey key : virtualFluidSupply) {
                limitInput.add(key.toStack(Integer.MAX_VALUE));
            }
            for (AEFluidKey key : configuredVirtualFluids) {
                if (!virtualFluidSupply.contains(key)) {
                    limitInput.add(key.toStack(Integer.MAX_VALUE));
                }
            }
        }

        /**
         * Rejects recipes that actually consume a virtually supplied input. One slipping through would match and then
         * fail every dispatch, caching a recipe that can never run.
         *
         * @return false to veto this recipe for this slot
         */
        public boolean testVirtualItemInternal(GTRecipe recipe) {
            if (!hasVirtualItemSupply()) return true;
            for (var content : recipe.getInputContents(ItemRecipeCapability.CAP)) {
                if (content.chance() <= 0) continue;
                for (ItemStack item : LongIngredient.matchingIngredient((SizedIngredient) content.content()).getItems()) {
                    if (suppliesItem(AEItemKey.of(item))) return false;
                }
            }
            return true;
        }

        public boolean testVirtualFluidInternal(GTRecipe recipe) {
            if (!hasVirtualFluidSupply()) return true;
            for (var content : recipe.getInputContents(FluidRecipeCapability.CAP)) {
                if (content.chance() <= 0) continue;
                for (FluidStack stack : LongFluidIngredient.matchingIngredient((SizedFluidIngredient) content.content()).getStacks()) {
                    if (suppliesFluid(AEFluidKey.of(stack))) return false;
                }
            }
            return true;
        }

        /**
         * Drops the demands this slot covers virtually, whatever amount they ask for. Returns a filtered copy because
         * callers pass the same map to several slots in turn, or the argument itself when nothing is covered.
         */
        public Object2LongMap<SizedIngredient> stripVirtuallySuppliedItems(Object2LongMap<SizedIngredient> left) {
            if (left.isEmpty() || !hasVirtualItemSupply()) return left;
            Object2LongMap<SizedIngredient> remaining = null;
            for (var entry : Object2LongMaps.fastIterable(left)) {
                if (!coversItem(entry.getKey())) continue;
                if (remaining == null) remaining = new Object2LongOpenHashMap<>(left);
                remaining.removeLong(entry.getKey());
            }
            return remaining == null ? left : remaining;
        }

        public Object2LongMap<SizedFluidIngredient> stripVirtuallySuppliedFluids(Object2LongMap<SizedFluidIngredient> left) {
            if (left.isEmpty() || !hasVirtualFluidSupply()) return left;
            Object2LongMap<SizedFluidIngredient> remaining = null;
            for (var entry : Object2LongMaps.fastIterable(left)) {
                if (!coversFluid(entry.getKey())) continue;
                if (remaining == null) remaining = new Object2LongOpenHashMap<>(left);
                remaining.removeLong(entry.getKey());
            }
            return remaining == null ? left : remaining;
        }

        private boolean coversItem(SizedIngredient ingredient) {
            for (ItemStack item : LongIngredient.matchingIngredient(ingredient).getItems()) {
                if (suppliesItem(AEItemKey.of(item))) return true;
            }
            return false;
        }

        private boolean coversFluid(SizedFluidIngredient ingredient) {
            for (FluidStack stack : LongFluidIngredient.matchingIngredient(ingredient).getStacks()) {
                if (suppliesFluid(AEFluidKey.of(stack))) return true;
            }
            return false;
        }

        @Override
        public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();

            ListTag itemsTag = AEUtils.createListTag(key -> key.toTag(registries), itemInventory);
            if (!itemsTag.isEmpty()) tag.put("inventory", itemsTag);

            ListTag fluidsTag = AEUtils.createListTag(key -> key.toTag(registries), fluidInventory);
            if (!fluidsTag.isEmpty()) tag.put("fluidInventory", fluidsTag);

            // Kept across a reload so a slot stays runnable until its next dispatch re-establishes the supply.
            if (!virtualItemSupply.isEmpty()) {
                ListTag list = new ListTag();
                for (AEItemKey key : virtualItemSupply) list.add(key.toTag(registries));
                tag.put("virtualItemSupply", list);
            }
            if (!virtualFluidSupply.isEmpty()) {
                ListTag list = new ListTag();
                for (AEFluidKey key : virtualFluidSupply) list.add(key.toTag(registries));
                tag.put("virtualFluidSupply", list);
            }
            if (virtualCircuitConfig >= 0) tag.putInt("virtualCircuit", virtualCircuitConfig);

            tag.put("cacheManager", cacheManager.serializeNBT(registries));
            return tag;
        }

        @Override
        public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            itemInventory.clear();
            fluidInventory.clear();

            ListTag items = tag.getList("inventory", Tag.TAG_COMPOUND);
            AEUtils.loadInventory(items, key -> AEItemKey.fromTag(registries, key), itemInventory);

            ListTag fluids = tag.getList("fluidInventory", Tag.TAG_COMPOUND);
            AEUtils.loadInventory(fluids, key -> AEFluidKey.fromTag(registries, key), fluidInventory);

            virtualItemSupply.clear();
            ListTag virtualItems = tag.getList("virtualItemSupply", Tag.TAG_COMPOUND);
            for (int i = 0; i < virtualItems.size(); i++) {
                AEItemKey key = AEItemKey.fromTag(registries, virtualItems.getCompound(i));
                if (key != null) virtualItemSupply.add(key);
            }

            virtualFluidSupply.clear();
            ListTag virtualFluids = tag.getList("virtualFluidSupply", Tag.TAG_COMPOUND);
            for (int i = 0; i < virtualFluids.size(); i++) {
                AEFluidKey key = AEFluidKey.fromTag(registries, virtualFluids.getCompound(i));
                if (key != null) virtualFluidSupply.add(key);
            }

            cacheManager.deserializeNBT(registries, tag.getCompound("cacheManager"));
            configuredVirtualItems.clear();
            configuredVirtualFluids.clear();
            virtualCircuitConfig = tag.contains("virtualCircuit", Tag.TAG_INT) ? tag.getInt("virtualCircuit") : -1;
            // A pattern holding a real circuit re-registers it by itself and outranks this.
            if (virtualCircuitConfig >= 0 && virtualCircuitConfig <= IntCircuitBehaviour.CIRCUIT_MAX) cacheManager.setCircuitCache(virtualCircuitConfig);
            else virtualCircuitConfig = -1;
        }
    }
}
