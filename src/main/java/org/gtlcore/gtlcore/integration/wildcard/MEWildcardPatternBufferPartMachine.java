package org.gtlcore.gtlcore.integration.wildcard;

import org.gtlcore.gtlcore.api.machine.trait.IMERecipeHandlerTrait;
import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEPatternTrait;
import org.gtlcore.gtlcore.client.gui.widget.PatternCycleWidget;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.WildcardRecipeHandlerTrait;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferRecipeHandlerTraitBase;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.util.INBTSerializable;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;


import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.*;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Pattern buffer variant backed by one wildcard pattern.
 * <p>
 * The wildcard item is persisted. Expanded pattern slots are rebuilt at runtime, and slot-bound state is restored by
 * matching complete saved component inputs and outputs because wildcard expansion order is not stable across reloads.
 */
public class MEWildcardPatternBufferPartMachine extends MEPatternBufferPartMachineBase {

    // ========================================
    // Wildcard Pattern State
    // ========================================

    @Getter
    @SaveField
    @SyncToClient
    private final CustomItemStackHandler wildcardPatternSlot;

    private final List<IPatternDetails> expandedPatterns = new ObjectArrayList<>();

    private final Object2IntMap<IPatternDetails> patternToSlotMap = new Object2IntOpenHashMap<>();

    private final List<InternalSlot> internalSlots = new ObjectArrayList<>();

    private final IntSet activeSlotIndices = new IntOpenHashSet();

    private final MEWildcardPatternBufferPersistenceHelper persistenceHelper = new MEWildcardPatternBufferPersistenceHelper();
    private CompoundTag pendingDynamicState = new CompoundTag();

    @SaveField
    private final DynamicState wildcardDynamicState = new DynamicState();

    // ========================================
    // Runtime Recipe Cache
    // ========================================

    protected final Int2ReferenceMap<GTRecipe> recipeCacheMap = new Int2ReferenceOpenHashMap<>();
    protected IntConsumer removeSlotFromMap = i -> {};

    // ========================================
    // Traits
    // ========================================

    protected final MEPatternBufferRecipeHandlerTraitBase recipeHandler;

    @SyncToClient
    @SaveField
    public boolean isHiddenTerminal = false;

    @Override
    public boolean isVisibleInTerminal() {
        return !isHiddenTerminal;
    }

    // AE2 terminal wrapper for the persisted wildcard pattern slot.
    private final InternalInventory internalPatternInventory = new InternalInventory() {

        @Override
        public int size() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return wildcardPatternSlot.getStackInSlot(0);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            wildcardPatternSlot.setStackInSlot(0, stack);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return NativeWildcardPatternDecoder.isWildcardPattern(stack);
        }
    };

    public MEWildcardPatternBufferPartMachine(BlockEntityCreationInfo holder, IO io) {
        super(holder, io);

        this.wildcardPatternSlot = new CustomItemStackHandler(1) {
            @Override public void onContentsChanged(int slot) {
                super.onContentsChanged(slot);
                if (!isRemote() && getLevel() != null) onWildcardPatternChange();
                markAsChanged();
                syncDataHolder.markClientSyncFieldDirty("wildcardPatternSlot");
            }
        };
        this.wildcardPatternSlot.setFilter(NativeWildcardPatternDecoder::isWildcardPattern);

        this.patternToSlotMap.defaultReturnValue(-1);

        this.recipeHandler = attachTrait(createRecipeHandler(io));

        getMainNode().addService(IGridTickable.class, new Ticker());
    }

    protected MEPatternBufferRecipeHandlerTraitBase createRecipeHandler(IO io) {
        return new WildcardRecipeHandlerTrait(this, io);
    }

    protected InternalSlot createWildcardInternalSlot(int index) { return new InternalSlot(index); }

    // ========================================
    // LIFECYCLE & NETWORK MANAGEMENT
    // ========================================

    @Override
    public void onLoad() {
        super.onLoad();
        if (getLevel() instanceof ServerLevel serverLevel) {
            scheduleForNextServerTick(() -> {
                if (isRemoved()) return;
                refreshPatterns(true);
                needPatternSync = true;
            });
        }
    }

    @Override
    protected void update() {
        super.update();
        if (!buffer.isEmpty()) {
            AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);
        }
    }

    protected void onWildcardPatternChange() {
        if (isRemote() || destroying) return;

        for (InternalSlot slot : internalSlots) {
            refundSlot(slot.getItemInventory(), slot.getFluidInventory());
        }
        AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource);

        // A pattern can be changed before the delayed world-load restore runs. Refund that saved ownership too.
        persistenceHelper.clearPatternIndex();
        persistenceHelper.restore(recipeCacheMap, (slot, data) -> { throw new IllegalStateException("Unexpected wildcard restore slot"); },
                this::refundPersistedSlotData);
        pendingDynamicState = new CompoundTag();
        clearPatternData();

        refreshPatterns(false);
        needPatternSync = true;
        markAsChanged();
    }

    private void clearPatternData() {
        clearRuntimeRecipeCache();
        internalSlots.clear();
        expandedPatterns.clear();
        patternToSlotMap.clear();
        activeSlotIndices.clear();
        persistenceHelper.clearPatternIndex();
        persistenceHelper.clearPendingRestoreData();
    }

    protected void clearRuntimeRecipeCache() {
        for (int slot : activeSlotIndices) {
            removeSlotFromMap.accept(slot);
            notifyProxySlotRemoved(slot);
        }
        recipeCacheMap.clear();
        markAsChanged();
    }

    private void refreshPatterns(boolean restorePersistedData) {
        clearRuntimeRecipeCache();
        internalSlots.clear(); expandedPatterns.clear(); patternToSlotMap.clear(); activeSlotIndices.clear();
        persistenceHelper.clearPatternIndex();
        ItemStack wildcardStack = wildcardPatternSlot.getStackInSlot(0);
        if (!wildcardStack.isEmpty() && getLevel() != null) {
            List<IPatternDetails> patterns = NativeWildcardPatternDecoder.expand(wildcardStack, getLevel());

            for (var original : patterns) {
                int i = internalSlots.size();
                InternalSlot slot = createWildcardInternalSlot(i);
                IPatternDetails pattern = realPatternHelper.processPatternWithCircuit(original.getDefinition().toStack(),
                        slot.getCacheManager()::setCircuitCache, getLevel(), true);
                if (pattern == null) continue;
                expandedPatterns.add(pattern);
                patternToSlotMap.put(pattern, i);
                activeSlotIndices.add(i);
                persistenceHelper.indexPattern(original, i);

                slot.setOnContentsChanged(() -> {
                    recipeHandler.notifyInputListeners();
                    markAsChanged();
                });
                internalSlots.add(slot);
            }

        }
        if (restorePersistedData) {
            persistenceHelper.restore(recipeCacheMap,
                    (slot, data) -> internalSlots.get(slot).deserializeNBT(getLevel().registryAccess(), data),
                    this::refundPersistedSlotData);
            pendingDynamicState = new CompoundTag();
            markAsChanged();
        }
    }

    private void refundPersistedSlotData(List<CompoundTag> slotDataList) {
        for (CompoundTag slotData : slotDataList) {
            var items = new Object2LongOpenHashMap<AEItemKey>();
            var fluids = new Object2LongOpenHashMap<AEFluidKey>();
            AEUtils.loadInventory(slotData.getList("inventory", Tag.TAG_COMPOUND), data -> AEItemKey.fromTag(getLevel().registryAccess(), data), items);
            AEUtils.loadInventory(slotData.getList("fluidInventory", Tag.TAG_COMPOUND), data -> AEFluidKey.fromTag(getLevel().registryAccess(), data), fluids);
            refundSlot(items, fluids);
        }
    }

    @Override
    protected void appendPendingRefundsForDrop(org.gtlcore.gtlcore.integration.ae2.AERefundQueue owned, HolderLookup.Provider registries) {
        for (var slot : persistenceHelper.pendingInventories()) {
            var items = new Object2LongOpenHashMap<AEItemKey>();
            var fluids = new Object2LongOpenHashMap<AEFluidKey>();
            AEUtils.loadInventory(slot.getList("inventory", Tag.TAG_COMPOUND), data -> AEItemKey.fromTag(registries, data), items);
            AEUtils.loadInventory(slot.getList("fluidInventory", Tag.TAG_COMPOUND), data -> AEFluidKey.fromTag(registries, data), fluids);
            owned.appendSnapshot(items); owned.appendSnapshot(fluids);
        }
    }

    @Override
    public void onMachineDestroyed() {
        destroying = true;
        wildcardPatternSlot.dropInventoryInWorld(getLevel(), getBlockPos());
        super.onMachineDestroyed();
    }

    // ========================================
    // Slot Access and Pattern Routing
    // ========================================

    @Override
    @Nullable
    protected Integer getSlotIndexForPattern(IPatternDetails pattern) {
        int slot = patternToSlotMap.getInt(pattern);
        return slot >= 0 ? slot : null;
    }

    @Override
    public int getInternalSlotCount() {
        return internalSlots.size();
    }

    @Override
    protected boolean hasRecipeCacheInSlot(int slotIndex) {
        return recipeCacheMap.containsKey(slotIndex);
    }

    @Override
    protected boolean hasPatternInSlot(int slotIndex) {
        return activeSlotIndices.contains(slotIndex);
    }

    @Override
    public InternalSlot getInternalSlot(int index) {
        return internalSlots.get(index);
    }

    // ========================================
    // Persistence
    // ========================================

    private final class DynamicState implements INBTSerializable<CompoundTag> {
        @Override public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            if (!pendingDynamicState.isEmpty()) return pendingDynamicState.copy();
            var tag = new CompoundTag();
            persistenceHelper.save(tag, registries, internalSlots.size(),
                    slot -> internalSlots.get(slot).isActive(),
                    slot -> internalSlots.get(slot).serializeNBT(registries),
                    recipeCacheMap::containsKey, recipeCacheMap);
            return tag;
        }
        @Override public void deserializeNBT(HolderLookup.Provider registries, CompoundTag tag) {
            pendingDynamicState = tag.copy();
            persistenceHelper.load(tag, registries);
        }
    }

    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var builder = super.getPanelBuilder(data, sync, settings).addTitleBar(false);
        var inherited = builder.leftConfigurators();
        var hidden = SyncHandlers.bool(() -> isHiddenTerminal, value -> {
            isHiddenTerminal = value; markAsChanged(); syncDataHolder.markClientSyncFieldDirty("isHiddenTerminal");
        }).allowC2S();
        sync.syncValue("wildcardHiddenTerminal", hidden);
        var rename = sync.syncedPanel("wildcardRename", true, (manager, handler) -> {
            var name = SyncHandlers.string(() -> customName, this::setCustomName).allowC2S();
            manager.syncValue("wildcardCustomName", name);
            return PopupPanel.createPopupPanel("wildcardRenamePanel", 150, 52)
                    .child(Text.lang("gtceu.gui.pattern_buffer.set_custom_name").asWidget().pos(4, 4))
                    .child(new TextFieldWidget().value(name).size(140, 18).pos(4, 26));
        });
        return builder.leftConfigurators(flow -> {
            inherited.accept(flow);
            flow.child(new ToggleButton().name("wildcardTerminalVisibility").size(18).value(hidden).overlay(Text.str("V"))
                    .tooltipDynamic(t -> t.addLine(Component.translatable(hidden.getBoolValue() ?
                            "gui.gtlcore.hidden_in_terminal" : "gui.gtlcore.visible_in_terminal"))))
                    .child(new ButtonWidget<>().name("wildcardRenameButton").size(18).overlay(Text.str("✎"))
                            .onMousePressed((context, button) -> { if (button != 0) return false; rename.openPanel(); return true; }));
        });
    }

    @Override
    public void buildMainUI(ParentWidget<?> parent, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var online = SyncHandlers.bool(this::isOnline, null);
        var patterns = SyncHandlers.intNumber(expandedPatterns::size, null);
        var cached = SyncHandlers.intNumber(recipeCacheMap::size, null);
        sync.syncValue("wildcardOnline", online); sync.syncValue("wildcardPatterns", patterns); sync.syncValue("wildcardCached", cached);
        parent.child(new ParentWidget<>().name("wildcardMain").size(158, mainPanelHeight())
                .child(Text.of(getDefinition().asStack().getHoverName()).asWidget().name("wildcardTitle").pos(8, 0).size(144, 20))
                .child(Text.dynamic(() -> Component.translatable(online.getBoolValue() ?
                        "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline")).asWidget().pos(8, 24))
                .child(new ItemSlot().name("wildcardPatternSlot").slot(wildcardPatternSlot, 0).pos(132, 23)
                        .background(GTGuiTextures.SLOT).overlay(GTGuiTextures.PATTERN_OVERLAY))
                .child(Text.dynamic(() -> Component.translatable("gtceu.machine.me_wildcard_pattern_buffer.patterns",
                        patterns.getIntValue())).asWidget().pos(8, 44))
                .child(new PatternCycleWidget(8, 56, 142, 36, this::getAvailablePatterns).sync(sync, "wildcardExpandedPreview", this::getOffsetTimer))
                .child(Text.dynamic(() -> Component.translatable("gtceu.machine.me_wildcard_pattern_buffer.cached",
                        cached.getIntValue())).asWidget().pos(8, 94))
                .child(new PatternCycleWidget(8, 106, 142, 36, this::getCachedPreviewPatterns).sync(sync, "wildcardCachedPreview", this::getOffsetTimer)));
    }

    protected int mainPanelHeight() { return 142; }

    private List<IPatternDetails> getCachedPreviewPatterns() {
        List<IPatternDetails> patterns = new ObjectArrayList<>();
        for (var entry : Int2ReferenceMaps.fastIterable(recipeCacheMap)) {
            int slot = entry.getIntKey();
            if (slot >= 0 && slot < expandedPatterns.size()) {
                patterns.add(expandedPatterns.get(slot));
            }
        }
        return patterns;
    }

    // ========================================
    // AE2 CRAFTING
    // ========================================

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return Collections.unmodifiableList(expandedPatterns);
    }

    // ========================================
    // PATTERN CONTAINER IMPLEMENTATION
    // ========================================

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return internalPatternInventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        var icon = getDefinition().asStack();
        return new PatternContainerGroup(AEItemKey.of(icon),
                customName.isEmpty() ? icon.getHoverName() : Component.literal(customName), List.of());
    }

    // ========================================
    // IMEPatternPartMachine
    // ========================================

    @Override
    protected @NotNull WildcardMEPatternTrait createMETrait() {
        return new WildcardMEPatternTrait();
    }

    @Override
    public Pair<IMERecipeHandlerTrait<net.neoforged.neoforge.common.crafting.SizedIngredient, ItemStack>, IMERecipeHandlerTrait<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient, net.neoforged.neoforge.fluids.FluidStack>> getMERecipeHandlerTraits() {
        return Pair.of(recipeHandler.getMeItemHandler(), recipeHandler.getMeFluidHandler());
    }

    // ========================================
    // TICKER
    // ========================================

    protected class Ticker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(5, 60, false);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!getMainNode().isActive()) {
                isSleeping = true;
                return TickRateModulation.SLEEP;
            }

            if (buffer.isEmpty()) {
                if (ticksSinceLastCall >= 60) {
                    isSleeping = true;
                    return TickRateModulation.SLEEP;
                } else return TickRateModulation.SLOWER;
            } else {
                return AEUtils.reFunds(buffer, getMainNode().getGrid(), actionSource) ?
                        TickRateModulation.URGENT : TickRateModulation.SLOWER;
            }
        }
    }

    // ========================================
    // ME PATTERN TRAIT
    // ========================================

    protected class WildcardMEPatternTrait extends MEIOTrait implements IMEPatternTrait {

        public WildcardMEPatternTrait() {
            super();
        }

        @Override
        public MEWildcardPatternBufferPartMachine getMachine() {
            return (MEWildcardPatternBufferPartMachine) super.getMachine();
        }

        @Override
        public @NotNull ObjectSet<@NotNull GTRecipe> getCachedGTRecipe() {
            ObjectSet<GTRecipe> recipes = new ObjectOpenHashSet<>();
            for (var it = Int2ReferenceMaps.fastIterator(recipeCacheMap); it.hasNext();) {
                var entry = it.next();
                int slot = entry.getIntKey();
                if (slot < internalSlots.size() && internalSlots.get(slot).isActive()) {
                    recipes.add(entry.getValue());
                }
            }
            return recipes;
        }

        @Override
        public void setSlotCacheRecipe(int index, GTRecipe recipe) {
            if (recipe != null && recipe.recipeType != GTRecipeTypes.DUMMY_RECIPES.get() && index >= 0 && index < internalSlots.size()) {
                recipeCacheMap.put(index, recipe);
                markAsChanged();
            }
        }

        @Override
        public @NotNull Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> getSlot2RecipesCache() {
            Int2ReferenceMap<ObjectSet<@NotNull GTRecipe>> slot2Recipes = new Int2ReferenceOpenHashMap<>();
            for (var entry : Int2ReferenceMaps.fastIterable(recipeCacheMap)) {
                ObjectSet<GTRecipe> recipes = new ObjectArraySet<>();
                recipes.add(entry.getValue());
                slot2Recipes.put(entry.getIntKey(), recipes);
            }
            return slot2Recipes;
        }

        @Override
        public void setOnPatternChange(IntConsumer removeMapOnSlot) {
            removeSlotFromMap = removeMapOnSlot;
        }

        @Override
        public boolean hasCacheInSlot(int slot) {
            return recipeCacheMap.containsKey(slot);
        }
    }
}
