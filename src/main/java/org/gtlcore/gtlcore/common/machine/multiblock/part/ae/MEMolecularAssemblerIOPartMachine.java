package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.api.machine.trait.AECraft.*;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.AEBufferedOutput;
import org.gtlcore.gtlcore.integration.ae2.MolecularAssemblerPatternHelper;
import org.gtlcore.gtlcore.integration.ae2.gui.MolecularPatternPagination;
import org.gtlcore.gtlcore.integration.ae2.gui.MolecularAssemblerUIFactory;
import org.gtlcore.gtlcore.integration.ae2.graph.GraphDispatchPreflight;
import org.gtlcore.gtlcore.integration.lowdragmc.misc.MutableItemTransferList;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.ticking.*;
import appeng.api.stacks.*;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedPatternItem;
import appeng.helpers.patternprovider.PatternContainer;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.UITexture;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.layout.Grid;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.*;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class MEMolecularAssemblerIOPartMachine extends MEIOPartMachine implements PatternContainer, IMECraftIOPart,
                                               GraphDispatchPreflight, IMolecularAssemblerOutputMachine, IMuiMachine {

    private final InternalInventory internalPatternInventory = new InternalInventory() {

        @Override
        public int size() {
            return mutableItemTransferList.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slotIndex) {
            return mutableItemTransferList.getStackInSlot(slotIndex);
        }

        @Override
        public void setItemDirect(int slotIndex, ItemStack stack) {
            mutableItemTransferList.setStackInSlot(slotIndex, stack);
            onPatternChange(slotIndex);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot >= 0 && slot < mutableItemTransferList.getSlots() && MolecularAssemblerPatternHelper.molecularFilter(stack, getLevel());
        }
    };
    @SyncToClient
    @SaveField
    public boolean isHiddenTerminal = false;

    @Override
    public boolean isVisibleInTerminal() {
        return !isHiddenTerminal;
    }
    // ========================================
    // Status
    // ========================================

    @SyncToClient
    @SaveField
    protected String customName = "";

    private boolean needPatternSync;

    private boolean shouldOpen = false;

    @Getter
    private long patternBindingRevision;

    public boolean hasPatternMenu() { return isFormed() && (isRemote() ? !proxies.isEmpty() : shouldOpen); }

    // ========================================
    // Handlers
    // ========================================

    protected final NotifiableMAHandlerTrait maHandler;

    private final MutableItemTransferList mutableItemTransferList;

    // ========================================
    // Inventory
    // ========================================

    @Getter
    @SaveField
    protected final CustomItemStackHandler sharedToolsInventory;

    private final BiMap<@NotNull IPatternDetails, Integer> patternSlotMap;

    private final Int2ObjectMap<@NotNull ObjectSet<@NotNull Item>> toolsSlotMap;

    @Getter
    private final Object2LongLinkedOpenHashMap<GenericStack> outputItems;  // must 1 count

    @Getter
    private final Object2LongOpenHashMap<AEKey> buffer;

    @SaveField
    private final MolecularAssemblerOutputStorage craftStorage;

    @SyncToClient
    private final Set<Long> proxies = new LongOpenHashSet();
    private final ReferenceSortedSet<IItemHandlerModifiable> proxyStackTransfers = new ReferenceLinkedOpenHashSet<>();

    public MEMolecularAssemblerIOPartMachine(BlockEntityCreationInfo holder) {
        super(holder, IO.BOTH);
        getMainNode().addService(IGridTickable.class, new Ticker()).addService(ICraftingProvider.class, this);

        patternSlotMap = HashBiMap.create();
        toolsSlotMap = new Int2ObjectOpenHashMap<>();
        outputItems = new Object2LongLinkedOpenHashMap<>();
        buffer = new Object2LongOpenHashMap<>();
        craftStorage = new MolecularAssemblerOutputStorage(this);
        maHandler = attachTrait(new MECraftHandler());
        sharedToolsInventory = new CustomItemStackHandler(9);
        sharedToolsInventory.setOnContentsChanged(this::markAsChanged);

        mutableItemTransferList = new MutableItemTransferList();
    }

    @Override
    public boolean pushPattern(IPatternDetails iPatternDetails, KeyCounter[] keyCounters) {
        if (!getMainNode().isActive() || !patternSlotMap.containsKey(iPatternDetails) || !(iPatternDetails instanceof AEProcessingPattern processingPattern)) {
            return false;
        }

        final GenericStack output = processingPattern.getOutputs().getFirst();
        if (!(output.what() instanceof AEItemKey)) return false;

        int slot = patternSlotMap.get(processingPattern);
        if (toolsSlotMap.containsKey(slot)) {
            var requiredTools = new ObjectOpenHashSet<>(toolsSlotMap.get(slot));

            for (int i = 0; i < sharedToolsInventory.getSlots(); i++) {
                final var stack = sharedToolsInventory.getStackInSlot(i);
                if (!stack.isDamageableItem()) requiredTools.remove(stack.getItem());
                if (requiredTools.isEmpty()) break;
            }

            if (!requiredTools.isEmpty()) {
                var refund = new Object2LongOpenHashMap<AEKey>();
                try {
                    for (var counter : keyCounters) for (var entry : counter) {
                        if (entry.getLongValue() <= 0) return false;
                        refund.put(entry.getKey(), Math.addExact(refund.getLong(entry.getKey()), entry.getLongValue()));
                    }
                    for (var entry : refund.object2LongEntrySet()) Math.addExact(buffer.getLong(entry.getKey()), entry.getLongValue());
                } catch (ArithmeticException overflow) { return false; }
                for (var entry : refund.object2LongEntrySet()) buffer.addTo(entry.getKey(), entry.getLongValue());
                markAsChanged(); meTrait.notifySelfIO(); return true;
            }
        }

        var supplied = new Object2LongOpenHashMap<AEKey>();
        try {
            for (var counter : keyCounters) for (var entry : counter) {
                if (entry.getLongValue() <= 0) return false;
                supplied.put(entry.getKey(), Math.addExact(supplied.getLong(entry.getKey()), entry.getLongValue()));
            }
        } catch (ArithmeticException overflow) { return false; }
        long multiplier = 0;
        for (var requirement : processingPattern.getInputs()) {
            var stack = requirement.getPossibleInputs()[0];
            long needed;
            try { needed = Math.multiplyExact(stack.amount(), requirement.getMultiplier()); }
            catch (ArithmeticException overflow) { return false; }
            long provided = supplied.removeLong(stack.what());
            if (needed <= 0 || provided <= 0 || provided % needed != 0) return false;
            long crafts = provided / needed;
            if (multiplier == 0) multiplier = crafts;
            else if (multiplier != crafts) return false;
        }
        long queued = outputItems.getLong(output);
        if (!supplied.isEmpty() || multiplier == 0 || queued < 0 || output.amount() <= 0 ||
                multiplier > Long.MAX_VALUE / output.amount() - queued) return false;
        outputItems.put(output, queued + multiplier);
        maHandler.notifyListeners();
        return true;
    }

    @Override
    public boolean gtlcore$canProduceGraphPattern(IPatternDetails pattern) {
        if (!getMainNode().isActive() || !(pattern instanceof AEProcessingPattern) || !patternSlotMap.containsKey(pattern)) return false;
        var required = toolsSlotMap.get(patternSlotMap.get(pattern));
        if (required == null || required.isEmpty()) return true;
        var missing = new ObjectOpenHashSet<>(required);
        for (int i = 0; i < sharedToolsInventory.getSlots(); i++) {
            var stack = sharedToolsInventory.getStackInSlot(i);
            if (!stack.isDamageableItem()) missing.remove(stack.getItem());
        }
        return missing.isEmpty();
    }

    @Override
    public long gtlcore$graphCapacity(IPatternDetails pattern, Map<AEKey, Long> inputPerRun, long requested) {
        if (!gtlcore$canProduceGraphPattern(pattern)) return 0;
        GenericStack output = pattern.getOutputs().getFirst();
        long queued = outputItems.getLong(output);
        if (queued < 0 || output.amount() <= 0) return 0;
        return Math.max(0, Math.min(requested, Long.MAX_VALUE / output.amount() - queued));
    }

    @Override
    public void init(@NotNull Set<BlockPos> proxies) {
        clear();

        this.proxies.addAll(proxies.stream().mapToLong(BlockPos::asLong).collect(LongArrayList::new, LongArrayList::add, LongArrayList::addAll));
        mutableItemTransferList.addTransfers(getProxies());

        if (!mutableItemTransferList.isEmpty()) {
            for (int i = 0; i < mutableItemTransferList.getSlots(); i++) {
                var pattern = getPatternDetailsAsProcessing(mutableItemTransferList.getStackInSlot(i), i);
                if (pattern != null) patternSlotMap.forcePut(pattern, i);
            }
            shouldOpen = true;
        } else {
            shouldOpen = false;
        }
        syncDataHolder.markClientSyncFieldDirty("proxies");
        needPatternSync = true;
    }

    @Override
    public @NotNull NotifiableMAHandlerTrait getNotifiableMAHandlerTrait() {
        return this.maHandler;
    }

    @Override
    public void notifyOutputBufferChanged() {
        this.meTrait.notifySelfIO();
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String substructureName) {
        return false;
    }

    protected void clear() {
        patternBindingRevision++;
        this.mutableItemTransferList.clear();
        this.patternSlotMap.clear();
        this.toolsSlotMap.clear();
        this.proxies.clear();
        this.proxyStackTransfers.clear();
        syncDataHolder.markClientSyncFieldDirty("proxies");
        shouldOpen = false;
    }

    private @Nullable IPatternDetails getPatternDetailsAsProcessing(ItemStack stack, int slotIndex) {
        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof EncodedPatternItem encodedPatternItem) {
                final var decodePattern = encodedPatternItem.decode(stack, getLevel());
                if (decodePattern instanceof IMolecularAssemblerSupportedPattern molecularAssemblerSupportedPattern) {
                    final var pair = MolecularAssemblerPatternHelper.createProcessingFromCraftPattern(molecularAssemblerSupportedPattern, getLevel());
                    final var remainingStacks = pair.getRight();
                    final var pattern = pair.getLeft();
                    if (!(pattern instanceof AEProcessingPattern)) return null;
                    if (remainingStacks != null && !remainingStacks.isEmpty()) toolsSlotMap.put(slotIndex, remainingStacks);
                    else toolsSlotMap.remove(slotIndex);
                    return pattern;
                } else return null;
            }
        }
        return null;
    }

    protected ReferenceSortedSet<IItemHandlerModifiable> getProxies() {
        if (proxyStackTransfers.size() != proxies.size()) {
            proxyStackTransfers.clear();
            ObjectList<IItemHandlerModifiable> patternInventories = new ObjectArrayList<>();
            for (var pos : proxies) {
                if (MetaMachine.getMachine(Objects.requireNonNull(getLevel()), BlockPos.of(pos)) instanceof MECraftPatternContainerPartMachine proxy) {
                    patternInventories.add(proxy.getPatternInventory());
                }
            }
            patternInventories.sort(Comparator.<IItemHandlerModifiable>comparingInt(IMECraftPatternContainer::sumNonEmpty).reversed()
                    .thenComparingInt(IItemHandlerModifiable::getSlots));
            proxyStackTransfers.addAll(patternInventories);
        }
        return ReferenceSortedSets.unmodifiable(proxyStackTransfers);
    }

    // ========================================
    // LIFECYCLE & NETWORK MANAGEMENT
    // ========================================

    @Nullable
    protected TickableSubscription updateSubs;

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.@NotNull State reason) {
        super.onMainNodeStateChanged(reason);
        this.updateSubscription();
    }

    @Override
    public void onUnload() {
        super.onUnload();
        updateSubs = null;
        clear();
    }

    @Override
    public void removedFromController(@NotNull MultiblockControllerMachine controller) {
        super.removedFromController(controller);
        clear();
        needPatternSync = true;
    }

    @Override
    public void onMachineDestroyed() {
        if (!isRemote()) sharedToolsInventory.dropInventoryInWorld(getLevel(), getBlockPos());
        super.onMachineDestroyed();
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
        if (needPatternSync) {
            ICraftingProvider.requestUpdate(getMainNode());
            this.needPatternSync = false;
        }
    }

    protected void onPatternChange(int index) {
        if (isRemote()) return;

        var newPattern = mutableItemTransferList.getStackInSlot(index);
        var newPatternDetails = getPatternDetailsAsProcessing(newPattern, index);

        if (newPatternDetails != null) patternSlotMap.forcePut(newPatternDetails, index);
        else patternSlotMap.inverse().remove(index);

        needPatternSync = true;
    }

    // ========================================
    // GUI
    // ========================================

    public void setCustomName(String name) {
        customName = Objects.requireNonNull(name);
        markAsChanged();
        syncDataHolder.markClientSyncFieldDirty("customName");
    }

    public void setHiddenTerminal(boolean hidden) {
        isHiddenTerminal = hidden;
        markAsChanged();
        syncDataHolder.markClientSyncFieldDirty("isHiddenTerminal");
    }

    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        var tools = syncManager.syncedPanel("molecularSharedTools", true, (manager, handler) -> {
            var group = new SlotGroup("molecularTools", 3, false);
            return PopupPanel.createPopupPanel("molecularSharedToolsPanel", 92, 90)
                    .child(Text.lang("gui.gtceu.share_inventory.title").asWidget().margin(4))
                    .child(new Grid().top(26).left(16).size(54, 54).minElementMargin(0, 0).minColWidth(18).minRowHeight(18)
                            .gridOfSizeWidth(9, 3, (x, y, index) -> new ItemSlot().slot(SyncHandlers.itemSlot(sharedToolsInventory, index)
                                    .slotGroup(group).accessibility(true, true))));
        });
        var hidden = new BooleanSyncValue(() -> isHiddenTerminal, this::setHiddenTerminal).allowC2S();
        syncManager.syncValue("molecularHidden", hidden);
        var visibleTexture = UITexture.builder().location(ResourceLocation.fromNamespaceAndPath("gtlcore", "textures/guis/button_visible.png"))
                .imageSize(18, 36).build();
        return MachineUIPanelBuilder.panelBuilder(this).leftConfigurators(flow -> flow
                .child(new ButtonWidget<>().size(18).overlay(GTGuiTextures.BUTTON_ITEM_OUTPUT)
                        .onMousePressed((context, button) -> { tools.openPanel(); return true; })
                        .tooltipDynamic(t -> {
                            t.addLine(Component.translatable("gui.gtceu.share_inventory.desc.0"));
                            t.addLine(Component.translatable("gui.gtceu.share_inventory.desc.1"));
                        }))
                .child(new ToggleButton().size(18).value(hidden)
                        .overlay(false, visibleTexture.getSubArea(0, 0, 1, .5f))
                        .overlay(true, visibleTexture.getSubArea(0, .5f, 1, 1))
                        .tooltipDynamic(t -> t.addLine(Component.translatable(hidden.getBoolValue() ?
                                "gui.gtlcore.hidden_in_terminal" : "gui.gtlcore.visible_in_terminal")))));
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        var online = new BooleanSyncValue(this::isOnline);
        var name = new StringSyncValue(() -> customName, this::setCustomName).allowC2S();
        syncManager.syncValue("molecularOnline", online); syncManager.syncValue("molecularName", name);
        mainWidget.child(Flow.col().coverChildren().margin(4)
                .child(Flow.row().coverChildren()
                        .child(Text.dynamic(() -> Component.translatable(online.getBoolValue() ?
                                "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline")).asWidget().width(92).height(16))
                        .child(new TextFieldWidget().size(70, 16).value(name)
                                .tooltipDynamic(t -> t.addLine(Component.translatable("gui.gtlcore.rename.desc")))))
                .child(MolecularPatternPagination.build(new MutableItemTransferList(getProxies()),
                        data instanceof MolecularAssemblerUIFactory.Data molecular ? molecular.slotCount() : mutableItemTransferList.getSlots(),
                        syncManager, this::onPatternChange)));
    }

    @Override
    public boolean shouldOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        return hasPatternMenu();
    }

    @Override
    public InteractionResult tryToOpenUI(Player player, InteractionHand hand, BlockHitResult hit) {
        if (!hasPatternMenu()) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer) MolecularAssemblerUIFactory.INSTANCE.open(serverPlayer, this);
        return InteractionResult.sidedSuccess(player.level().isClientSide);
    }

    // ========================================
    // Persist
    // ========================================

    // ========================================
    // Pattern
    // ========================================

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return new ObjectArrayList<>(patternSlotMap.keySet());
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public @Nullable IGrid getGrid() {
        return getMainNode().getGrid();
    }

    @Override
    public InternalInventory getTerminalPatternInventory() {
        return internalPatternInventory;
    }

    @Override
    public PatternContainerGroup getTerminalGroup() {
        var controllers = getControllers();

        // Handle multiblock controller grouping
        if (!controllers.isEmpty()) {
            var controller = controllers.first();
            MachineDefinition controllerDefinition = controller.getDefinition();

            if (!customName.isEmpty()) {
                return new PatternContainerGroup(
                        AEItemKey.of(controllerDefinition.asStack()),
                        Component.literal(customName),
                        Collections.emptyList());
            } else {
                return new PatternContainerGroup(
                        AEItemKey.of(controllerDefinition.asStack()), Component.translatable(controllerDefinition.getDescriptionId()), Collections.emptyList());
            }
        } else {
            if (!customName.isEmpty()) {
                return new PatternContainerGroup(
                        AEItemKey.of(getFallbackTerminalIcon()),
                        Component.literal(customName),
                        Collections.emptyList());
            } else {
                return new PatternContainerGroup(
                        AEItemKey.of(getFallbackTerminalIcon()),
                        getFallbackTerminalIcon().getDescription(),
                        Collections.emptyList());
            }
        }
    }

    private Item getFallbackTerminalIcon() {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.fromNamespaceAndPath("gtceu", "me_extend_pattern_buffer"))
                .orElseThrow(() -> new IllegalStateException("Required Core ME pattern-buffer icon is not registered"));
    }

    protected class Ticker implements IGridTickable {

        @Override
        public TickingRequest getTickingRequest(IGridNode node) {
            return new TickingRequest(ConfigHolder.INSTANCE.MEPatternOutputMin, ConfigHolder.INSTANCE.MEPatternOutputMax, false);
        }

        @Override
        public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            if (!getMainNode().isActive()) {
                isSleeping = true;
                return TickRateModulation.SLEEP;
            }

            if (buffer.isEmpty()) {
                if (ticksSinceLastCall >= ConfigHolder.INSTANCE.MEPatternOutputMax) {
                    isSleeping = true;
                    return TickRateModulation.SLEEP;
                } else return TickRateModulation.SLOWER;
            } else {
                if (AEBufferedOutput.flush(buffer, getMainNode().getGrid(), actionSource)) {
                    markAsChanged();
                    return TickRateModulation.URGENT;
                }
                return TickRateModulation.SLOWER;
            }
        }
    }
}
