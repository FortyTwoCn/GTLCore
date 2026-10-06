package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MECraftingCPUInterfacePartMachine;
import org.gtlcore.gtlcore.integration.ae2.crafting.transfinite.TransfiniteCraftingCPU;
import org.gtlcore.gtlcore.utils.NumberUtils;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.common.mui.widgets.PopupPanel;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import net.minecraft.core.HolderLookup;
import net.neoforged.neoforge.common.util.INBTSerializable;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.service.CraftingService;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

public class TransfiniteComputationArrayMachine extends MultiblockControllerMachine
                                                implements IMuiMachine, org.gtlcore.gtlcore.api.machine.IStartupCriticalMachine {

    public static final long MIN_PARALLELISM = 1L;
    public static final long MAX_PARALLELISM = Long.MAX_VALUE;
    public static final long DEFAULT_PARALLELISM = 1L;
    public static final long UNBOUNDED_JOB_STORAGE = Long.MAX_VALUE;

    private static final CpuSelectionMode DEFAULT_SELECTION_MODE = CpuSelectionMode.ANY;
    private static final CpuSelectionMode[] CPU_SELECTION_MODES = CpuSelectionMode.values();
    private static final String NBT_CPUS = "TransfiniteCraftingCpus";
    private static final String NBT_CPU_ID = "Id";
    private static final String NBT_CPU_BYTES = "Bytes";
    private static final String NBT_CPU_STATE = "State";
    private static final int EAGER_CHECK_TICKS = 100;
    private static final long PATTERN_CHECK_INTERVAL = 4;

    @SaveField @SyncToClient
    private CpuSelectionMode selectionMode = DEFAULT_SELECTION_MODE;
    @SaveField(nbtKey = NBT_CPUS)
    private final PersistedCpuState persistedCpus = new PersistedCpuState();

    private final Map<UUID, TransfiniteCraftingCPU> activeCpus = new LinkedHashMap<>();
    private final Map<AEKey, ObjectOpenHashSet<TransfiniteCraftingCPU>> waitingIndex = new Object2ObjectOpenHashMap<>();
    @Nullable
    private ListTag pendingCpus;
    @Nullable
    private MECraftingCPUInterfacePartMachine networkInterface;
    private TransfiniteCraftingCPU capacityCpu;
    private int eagerCheckTicks;
    @Nullable private TickableSubscription structureRetry;

    public TransfiniteComputationArrayMachine(BlockEntityCreationInfo holder) {
        super(holder);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        restoreCpus();
        eagerCheckTicks = EAGER_CHECK_TICKS;
        if (!isRemote()) structureRetry = subscribeServerTick(structureRetry, this::retryStructure);
    }

    @Override
    public void onUnload() {
        MECraftingCPUInterfacePartMachine previous = networkInterface;
        super.onUnload();
        networkInterface = null;
        if (previous != null) previous.notifyCraftingCpuChange();
        if (structureRetry != null) { structureRetry.unsubscribe(); structureRetry = null; }
    }

    private void retryStructure() {
        if (isRemoved()) return;
        if (isFormed() && networkInterface != null) {
            structureRetry.unsubscribe(); structureRetry = null;
            return;
        }
        if (eagerCheckTicks > 0 || getOffsetTimer() % PATTERN_CHECK_INTERVAL == 0) {
            if (eagerCheckTicks > 0) eagerCheckTicks--;
            checkAndFormStructure();
        }
    }

    public long getParallelism() {
        return this.networkInterface == null ? DEFAULT_PARALLELISM : this.networkInterface.getParallelism();
    }

    public void restoreNetworkInterface(@NotNull MECraftingCPUInterfacePartMachine networkInterface) {
        if (isFormed() && getParts().contains(networkInterface)) this.networkInterface = networkInterface;
    }

    public boolean isOperational() {
        return isFormed() && this.networkInterface != null && this.networkInterface.getMainNode().isActive();
    }

    public long getAvailableStorage() {
        return UNBOUNDED_JOB_STORAGE;
    }

    public IActionSource getActionSource() {
        return this.networkInterface == null ? IActionSource.empty() : this.networkInterface.getActionSource();
    }

    @Nullable
    public IGrid getGrid() {
        return this.networkInterface == null ? null : this.networkInterface.getMainNode().getGrid();
    }

    public Component getCpuName() {
        Component defaultName = getBlockState().getBlock().getName();
        return this.networkInterface == null ? defaultName :
                this.networkInterface.resolveCpuName(defaultName);
    }

    public CpuSelectionMode getSelectionMode() {
        return this.selectionMode;
    }

    public boolean canBeAutoSelectedFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> true;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    public boolean isPreferredFor(IActionSource source) {
        return switch (getSelectionMode()) {
            case ANY -> false;
            case PLAYER_ONLY -> source.player().isPresent();
            case MACHINE_ONLY -> source.player().isEmpty();
        };
    }

    public ICraftingSubmitResult submitJob(IGrid grid, ICraftingPlan plan, IActionSource source,
                                           @Nullable ICraftingRequester requester) {
        if (!isOperational()) {
            return CraftingSubmitResult.CPU_OFFLINE;
        }
        if (plan.bytes() > getAvailableStorage()) {
            return CraftingSubmitResult.CPU_TOO_SMALL;
        }

        UUID id = UUID.randomUUID();
        var cpu = new TransfiniteCraftingCPU(this, id, plan.bytes());
        ICraftingSubmitResult result = cpu.getCraftingLogic().trySubmitJob(grid, plan, source, requester);
        if (result.successful()) {
            this.activeCpus.put(id, cpu);
            markAsChanged();
            notifyCraftingCpuChange();
        }
        return result;
    }

    public void forEachActiveCpu(Consumer<TransfiniteCraftingCPU> consumer) {
        this.activeCpus.values().forEach(consumer);
    }

    public long tickCraftingCpus(IEnergyService energyService, CraftingService craftingService) {
        boolean removedCpu = false;
        long latestChange = 0;
        var iterator = this.activeCpus.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            TransfiniteCraftingCPU cpu = entry.getValue();
            cpu.getCraftingLogic().tickCraftingLogic(energyService, craftingService);
            latestChange = Math.max(latestChange, cpu.getCraftingLogic().getLastModifiedOnTick());
            if (cpu.getCraftingLogic().canBeRemoved()) {
                removeFromWaitingIndex(cpu);
                iterator.remove();
                removedCpu = true;
            }
        }
        if (removedCpu) {
            markAsChanged();
            notifyCraftingCpuChange();
        }
        return latestChange;
    }

    public void collectWaitingFor(Set<AEKey> output) {
        output.addAll(this.waitingIndex.keySet());
    }

    public void collectRequestingKeys(Set<AEKey> output) {
        output.addAll(this.waitingIndex.keySet());
        for (TransfiniteCraftingCPU cpu : this.activeCpus.values()) {
            var finalOutput = cpu.getCraftingLogic().getFinalJobOutput();
            if (finalOutput != null) {
                output.add(finalOutput.what());
            }
        }
    }

    public long insertIntoCpus(AEKey key, long amount, Actionable mode) {
        if (amount <= 0) {
            return 0;
        }
        var candidates = this.waitingIndex.get(key);
        if (candidates == null || candidates.isEmpty()) {
            return 0;
        }

        if (mode == Actionable.SIMULATE) {
            long accepted = 0;
            for (TransfiniteCraftingCPU cpu : candidates) {
                accepted += cpu.getCraftingLogic().insert(key, amount - accepted, mode);
                if (accepted >= amount) {
                    break;
                }
            }
            return accepted;
        }

        long inserted = 0;
        while (inserted < amount) {
            candidates = this.waitingIndex.get(key);
            if (candidates == null || candidates.isEmpty()) {
                break;
            }
            TransfiniteCraftingCPU cpu = candidates.iterator().next();
            long accepted = cpu.getCraftingLogic().insert(key, amount - inserted, mode);
            if (accepted <= 0) {
                if (cpu.getCraftingLogic().getWaitingFor(key) <= 0) {
                    updateWaitingIndex(cpu, key, false);
                    continue;
                }
                break;
            }
            inserted += accepted;
        }
        return inserted;
    }

    public long getRequestedAmount(AEKey key) {
        var candidates = this.waitingIndex.get(key);
        if (candidates == null) {
            return 0;
        }
        long requested = 0;
        for (TransfiniteCraftingCPU cpu : candidates) {
            requested = NumberUtils.saturatedAdd(requested, cpu.getCraftingLogic().getWaitingFor(key));
        }
        return requested;
    }

    public boolean isRequesting(AEKey key) {
        if (this.waitingIndex.containsKey(key)) {
            return true;
        }
        for (TransfiniteCraftingCPU cpu : this.activeCpus.values()) {
            if (cpu.getCraftingLogic().isRequesting(key)) {
                return true;
            }
        }
        return false;
    }

    public boolean isRequestingAny() {
        if (!this.waitingIndex.isEmpty()) {
            return true;
        }
        for (TransfiniteCraftingCPU cpu : this.activeCpus.values()) {
            if (cpu.getCraftingLogic().isRequestingAny()) {
                return true;
            }
        }
        return false;
    }

    public boolean containsCpu(TransfiniteCraftingCPU cpu) {
        UUID id = cpu.getId();
        return id != null && this.activeCpus.get(id) == cpu;
    }

    public void updateWaitingIndex(TransfiniteCraftingCPU cpu, AEKey key, boolean waiting) {
        if (waiting) {
            this.waitingIndex.computeIfAbsent(key, ignored -> new ObjectOpenHashSet<>()).add(cpu);
            return;
        }
        var candidates = this.waitingIndex.get(key);
        if (candidates != null) {
            candidates.remove(cpu);
            if (candidates.isEmpty()) {
                this.waitingIndex.remove(key);
            }
        }
    }

    public TransfiniteCraftingCPU getCapacityCpu() {
        if (this.capacityCpu == null) {
            this.capacityCpu = TransfiniteCraftingCPU.capacityView(this);
        }
        return this.capacityCpu;
    }

    public int getActiveJobCount() {
        int count = 0;
        for (TransfiniteCraftingCPU cpu : this.activeCpus.values()) {
            if (cpu.isBusy()) {
                count++;
            }
        }
        return count;
    }

    private void removeFromWaitingIndex(TransfiniteCraftingCPU cpu) {
        var iterator = this.waitingIndex.values().iterator();
        while (iterator.hasNext()) {
            var candidates = iterator.next();
            candidates.remove(cpu);
            if (candidates.isEmpty()) {
                iterator.remove();
            }
        }
    }

    public void markCpuDirty() {
        markAsChanged();
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        if (!DEFAULT_STRUCTURE.equals(name) || !isFormed()) return;
        networkInterface = getParts().stream().filter(MECraftingCPUInterfacePartMachine.class::isInstance)
                .map(MECraftingCPUInterfacePartMachine.class::cast).findFirst().orElse(null);
        notifyCraftingCpuChange();
    }

    @Override
    public void invalidateStructure(String name) {
        MECraftingCPUInterfacePartMachine previous = networkInterface;
        super.invalidateStructure(name);
        if (!DEFAULT_STRUCTURE.equals(name)) return;
        networkInterface = null;
        if (previous != null) previous.notifyCraftingCpuChange();
        if (!isRemote() && !isRemoved()) structureRetry = subscribeServerTick(structureRetry, this::retryStructure);
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        if (networkInterface != null && !getParts().contains(networkInterface)) invalidateStructure(DEFAULT_STRUCTURE);
    }

    private void notifyCraftingCpuChange() {
        if (networkInterface != null) networkInterface.notifyCraftingCpuChange();
    }

    /** Native save callbacks retain jobs even if their patterns cannot yet be resolved during attachment. */
    private final class PersistedCpuState implements INBTSerializable<CompoundTag> {
        @Override public CompoundTag serializeNBT(HolderLookup.Provider registries) {
            var result = new CompoundTag();
            if (pendingCpus != null) { result.put("cpus", pendingCpus.copy()); return result; }
            var list = new ListTag();
            for (var entry : activeCpus.entrySet()) {
                var cpu = new CompoundTag(); cpu.putUUID(NBT_CPU_ID, entry.getKey());
                cpu.putLong(NBT_CPU_BYTES, entry.getValue().getAvailableStorage());
                var state = new CompoundTag(); entry.getValue().getCraftingLogic().writeToNbt(state, registries);
                cpu.put(NBT_CPU_STATE, state); list.add(cpu);
            }
            result.put("cpus", list); return result;
        }
        @Override public void deserializeNBT(HolderLookup.Provider registries, CompoundTag data) {
            activeCpus.clear(); waitingIndex.clear();
            pendingCpus = data.getList("cpus", Tag.TAG_COMPOUND).copy();
            restoreCpus();
        }
    }

    private void restoreCpus() {
        if (this.pendingCpus == null || getLevel() == null) return;
        ListTag cpuList = this.pendingCpus;
        this.activeCpus.clear();
        this.waitingIndex.clear();
        for (int i = 0; i < cpuList.size(); i++) {
            CompoundTag cpuTag = cpuList.getCompound(i);
            if (!cpuTag.hasUUID(NBT_CPU_ID)) {
                continue;
            }
            UUID id = cpuTag.getUUID(NBT_CPU_ID);
            long bytes = Math.max(0L, cpuTag.getLong(NBT_CPU_BYTES));
            var cpu = new TransfiniteCraftingCPU(this, id, bytes);
            cpu.getCraftingLogic().readFromNbt(cpuTag.getCompound(NBT_CPU_STATE), getLevel().registryAccess());
            this.activeCpus.put(id, cpu);
        }
        this.pendingCpus = null;
    }

    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var builder = IMuiMachine.super.getPanelBuilder(data, sync, settings);
        var interfacePanel = sync.syncedPanel("transfiniteNetworkInterface", true, (manager, handler) ->
                PopupPanel.createPopupPanel("transfiniteNetworkInterfacePanel", 178, 120)
                        .child(MECraftingCPUInterfacePartMachine.settings(manager, () -> networkInterface).pos(8, 8)));
        return builder.leftConfigurators(flow -> flow.child(new ButtonWidget<>().name("transfiniteInterfaceButton")
                .size(18).overlay(Text.str("AE"))
                .tooltip(t -> t.addLine(Component.translatable("gui.gtlcore.me_crafting_cpu_interface.cpu_name.rename")))
                .onMousePressed((context, button) -> { if (button != 0) return false; interfacePanel.openPanel(); return true; })));
    }

    @Override
    public void buildMainUI(ParentWidget<?> contents, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        var mode = new IntSyncValue(() -> selectionMode.ordinal()); sync.syncValue("transfiniteSelectionMode", mode);
        var cycle = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (mouse.isLeftMouseButton()) cycleSelectionMode();
        });
        sync.syncValue("transfiniteCycleMode", cycle);
        var display = Flow.col().name("transfiniteStatus").width(182).coverChildrenHeight().childPadding(4)
                .children(GTLMultiblockDisplays.syncedLines("transfinite", 174, (controller, lines) -> addDisplayText(lines)).apply(this, sync))
                .child(new ButtonWidget<>().name("transfiniteSelectionModeButton").size(174, 18)
                        .overlay(Text.dynamic(() -> Component.translatable("gui.gtlcore.transfinite_computation_array.selection_mode",
                                Component.translatable(selectionModeTranslationKey(CPU_SELECTION_MODES[Math.clamp(mode.getIntValue(), 0, CPU_SELECTION_MODES.length - 1)])))))
                        .syncHandler(cycle).tooltip(t -> {
                            t.addLine(Component.translatable("gui.gtlcore.transfinite_computation_array.selection_mode.tooltip"));
                            t.addLine(Component.translatable("gui.gtlcore.transfinite_computation_array.selection_mode.any.tooltip"));
                            t.addLine(Component.translatable("gui.gtlcore.transfinite_computation_array.selection_mode.player_only.tooltip"));
                            t.addLine(Component.translatable("gui.gtlcore.transfinite_computation_array.selection_mode.machine_only.tooltip"));
                        }));
        contents.child(display.padding(4).background(GuiTextures.DISPLAY));
    }

    public void addDisplayText(List<Component> textList) {
        boolean formed = isFormed();
        boolean online = isOperational();
        textList.add(Component.translatable(formed ?
                "gui.gtlcore.transfinite_computation_array.formed" :
                "gui.gtlcore.transfinite_computation_array.unformed")
                .withStyle(formed ? ChatFormatting.GREEN : ChatFormatting.RED));
        textList.add(Component.translatable(online ?
                "gui.gtlcore.transfinite_computation_array.online" :
                "gui.gtlcore.transfinite_computation_array.offline")
                .withStyle(online ? ChatFormatting.GREEN : ChatFormatting.RED));
        textList.add(Component.translatable("gui.gtlcore.transfinite_computation_array.jobs",
                NumberUtils.numberText(getActiveJobCount()).withStyle(ChatFormatting.AQUA))
                .withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable("gui.gtlcore.transfinite_computation_array.storage")
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    private void cycleSelectionMode() {
        int nextMode = (this.selectionMode.ordinal() + 1) % CPU_SELECTION_MODES.length;
        setSelectionMode(CPU_SELECTION_MODES[nextMode]);
    }

    private void setSelectionMode(CpuSelectionMode selectionMode) {
        if (this.selectionMode == selectionMode) {
            return;
        }
        this.selectionMode = selectionMode;
        syncDataHolder.markClientSyncFieldDirty("selectionMode");
        markAsChanged();
        notifyCraftingCpuChange();
    }

    private static String selectionModeTranslationKey(CpuSelectionMode mode) {
        return switch (mode) {
            case ANY -> "gui.gtlcore.transfinite_computation_array.selection_mode.any";
            case PLAYER_ONLY -> "gui.gtlcore.transfinite_computation_array.selection_mode.player_only";
            case MACHINE_ONLY -> "gui.gtlcore.transfinite_computation_array.selection_mode.machine_only";
        };
    }
}
