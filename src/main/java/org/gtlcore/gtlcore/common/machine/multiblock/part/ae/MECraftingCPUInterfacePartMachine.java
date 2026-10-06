package org.gtlcore.gtlcore.common.machine.multiblock.part.ae;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.TransfiniteComputationArrayMachine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import net.minecraft.core.HolderLookup;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.security.IActionSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MECraftingCPUInterfacePartMachine extends MEIOPartMachine implements IMuiMachine {

    private static final int MAX_CPU_NAME_LENGTH = 64;
    private static final int UI_WIDTH = 176;
    private static final int UI_HEIGHT = 80;
    private static final int CONTENT_X = 8;
    private static final int NETWORK_STATUS_Y = 3;
    private static final int CPU_NAME_LABEL_Y = 16;
    private static final int CPU_NAME_FIELD_Y = 27;
    private static final int CPU_NAME_FIELD_HEIGHT = 14;
    private static final int CPU_NAME_FIELD_WIDTH = UI_WIDTH - CONTENT_X * 2;
    private static final int PARALLELISM_LABEL_Y = 47;
    private static final int PARALLELISM_FIELD_Y = 58;
    private static final int PARALLELISM_FIELD_HEIGHT = 14;
    private static final int PARALLELISM_FIELD_WIDTH = UI_WIDTH - CONTENT_X * 2;
    private static final int MAX_PARALLELISM_TEXT_LENGTH = Long.toString(TransfiniteComputationArrayMachine.MAX_PARALLELISM).length();
    private static final String NBT_PARALLELISM = "Parallelism";
    private static final String NBT_LINKED_CONTROLLER = "LinkedController";
    private static final long NO_LINKED_CONTROLLER = Long.MIN_VALUE;

    @SaveField
    @SyncToClient
    private String customName = "";

    @SaveField
    @SyncToClient
    private long parallelism = TransfiniteComputationArrayMachine.DEFAULT_PARALLELISM;

    @SaveField
    @SyncToClient
    private long linkedControllerPos = NO_LINKED_CONTROLLER;

    @Nullable
    private TickableSubscription updateSubs;
    private boolean needsCraftingCpuSync = true;

    public MECraftingCPUInterfacePartMachine(BlockEntityCreationInfo holder) {
        super(holder, IO.NONE);
    }

    @Override
    public boolean canShared(MultiblockControllerMachine controller, String structureName) {
        return false;
    }

    public List<TransfiniteComputationArrayMachine> getTransfiniteControllers() {
        List<TransfiniteComputationArrayMachine> controllers = getControllers().stream()
                .filter(TransfiniteComputationArrayMachine.class::isInstance)
                .map(TransfiniteComputationArrayMachine.class::cast)
                .toList();
        if (!controllers.isEmpty()) {
            return controllers;
        }

        var level = getLevel();
        if (level == null) {
            return List.of();
        }
        BlockPos controllerPos = this.linkedControllerPos == NO_LINKED_CONTROLLER ?
                findAdjacentController(level) : BlockPos.of(this.linkedControllerPos);
        if (controllerPos == null) {
            return List.of();
        }
        if (!level.hasChunkAt(controllerPos)) return List.of();
        MetaMachine machine = MetaMachine.getMachine(level, controllerPos);
        if (machine instanceof TransfiniteComputationArrayMachine controller && controller.isFormed() && controller.getParts().contains(this)) {
            if (this.linkedControllerPos != controllerPos.asLong()) {
                this.linkedControllerPos = controllerPos.asLong();
                markAsChanged();
            }
            controller.restoreNetworkInterface(this);
            return List.of(controller);
        }
        return List.of();
    }

    @Nullable
    private BlockPos findAdjacentController(Level level) {
        for (Direction direction : Direction.values()) {
            BlockPos candidate = getBlockPos().relative(direction);
            if (!level.hasChunkAt(candidate)) continue;
            MetaMachine machine = MetaMachine.getMachine(level, candidate);
            if (machine instanceof TransfiniteComputationArrayMachine controller && controller.isFormed() && controller.getParts().contains(this)) {
                return candidate;
            }
        }
        return null;
    }

    @Override
    public void addedToController(@NotNull MultiblockControllerMachine controller, String structureName) {
        super.addedToController(controller, structureName);
        if (controller instanceof TransfiniteComputationArrayMachine transfiniteController) {
            long controllerPos = transfiniteController.getBlockPos().asLong();
            if (this.linkedControllerPos != controllerPos) {
                this.linkedControllerPos = controllerPos;
                markAsChanged();
            }
            notifyCraftingCpuChange();
        }
    }

    @Override
    public void removedFromController(@NotNull MultiblockControllerMachine controller) {
        super.removedFromController(controller);
        if (controller instanceof TransfiniteComputationArrayMachine transfiniteController &&
                this.linkedControllerPos == transfiniteController.getBlockPos().asLong()) {
            this.linkedControllerPos = NO_LINKED_CONTROLLER;
            markAsChanged();
            notifyCraftingCpuChange();
        }
    }

    public IActionSource getActionSource() {
        return this.actionSource;
    }

    public Component resolveCpuName(Component defaultName) {
        return this.customName.isEmpty() ? defaultName : Component.literal(this.customName);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        customName = normalizeCpuName(customName);
        parallelism = Math.max(TransfiniteComputationArrayMachine.MIN_PARALLELISM, parallelism);
    }

    @Override
    public void buildMainUI(ParentWidget<?> contents, PosGuiData data, PanelSyncManager sync, UISettings settings) {
        contents.child(settings(sync, () -> this));
    }

    /** The controller popup uses the same settings with a live server-owned interface binding. */
    public static Flow settings(PanelSyncManager sync, java.util.function.Supplier<MECraftingCPUInterfacePartMachine> binding) {
        var online = SyncHandlers.bool(() -> binding.get() != null && binding.get().isOnline(), null);
        var name = SyncHandlers.string(() -> binding.get() == null ? "" : binding.get().customName, value -> {
            var part = binding.get(); if (part != null && !part.isRemoved()) part.setCustomName(value);
        }).allowC2S();
        var parallel = SyncHandlers.string(() -> binding.get() == null ? "1" : binding.get().getParallelismText(), value -> {
            var part = binding.get(); if (part != null && !part.isRemoved()) part.setParallelismFromText(value);
        }).allowC2S();
        sync.syncValue("craftingCpuOnline", online); sync.syncValue("craftingCpuName", name); sync.syncValue("craftingCpuParallelism", parallel);
        return Flow.col().name("craftingCpuInterfaceSettings").width(160).coverChildrenHeight().childPadding(4)
                .child(Text.dynamic(() -> Component.translatable(online.getBoolValue() ? "gtceu.gui.me_network.online" : "gtceu.gui.me_network.offline")).asWidget())
                .child(Text.dynamic(() -> Component.translatable("gui.gtlcore.me_crafting_cpu_interface.cpu_name", Component.literal(name.getStringValue()))).asWidget())
                .child(new TextFieldWidget().name("craftingCpuNameField").width(160).height(18).value(name).setMaxLength(MAX_CPU_NAME_LENGTH * 2)
                        .tooltip(t -> t.addLine(Component.translatable("gui.gtlcore.me_crafting_cpu_interface.cpu_name.tooltip"))))
                .child(Text.lang("gui.gtlcore.transfinite_computation_array.parallelism").asWidget())
                .child(new TextFieldWidget().name("craftingCpuParallelismField").width(160).height(18).value(parallel)
                        .setMaxLength(MAX_PARALLELISM_TEXT_LENGTH).setNumbersLong(() -> 1L, () -> Long.MAX_VALUE));
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.@NotNull State reason) {
        super.onMainNodeStateChanged(reason);
        updateSubscription();
        notifyCraftingCpuChange();
    }

    public void notifyCraftingCpuChange() {
        if (isRemote()) {
            return;
        }
        this.needsCraftingCpuSync = true;
        postCraftingCpuChange();
    }

    private void postCraftingCpuChange() {
        getMainNode().ifPresent((grid, node) -> grid.postEvent(new GridCraftingCpuChange(node)));
    }

    @Override
    public void onUnload() {
        super.onUnload();
        if (this.updateSubs != null) {
            this.updateSubs.unsubscribe();
            this.updateSubs = null;
        }
    }

    private void updateSubscription() {
        if (getMainNode().isOnline()) {
            this.updateSubs = subscribeServerTick(this.updateSubs, this::update);
        } else if (this.updateSubs != null) {
            this.updateSubs.unsubscribe();
            this.updateSubs = null;
        }
    }

    private void update() {
        if (!this.needsCraftingCpuSync || getTransfiniteControllers().isEmpty()) {
            return;
        }
        this.needsCraftingCpuSync = false;
        postCraftingCpuChange();
    }

    private Component getDisplayedCpuName() {
        TransfiniteComputationArrayMachine controller = getTransfiniteController();
        Component defaultName = controller == null ?
                Component.translatable("block.gtceu.transfinite_computation_array") :
                controller.getBlockState().getBlock().getName();
        return resolveCpuName(defaultName);
    }

    @Nullable
    private TransfiniteComputationArrayMachine getTransfiniteController() {
        return getTransfiniteControllers().stream().findFirst().orElse(null);
    }

    private String getParallelismText() {
        return Long.toString(this.parallelism);
    }

    private void setParallelismFromText(String text) {
        try {
            setParallelism(Long.parseLong(text));
        } catch (NumberFormatException ignored) {
            setParallelism(TransfiniteComputationArrayMachine.DEFAULT_PARALLELISM);
        }
    }

    public long getParallelism() {
        return this.parallelism;
    }

    public void setParallelism(long parallelism) {
        long clamped = Math.max(TransfiniteComputationArrayMachine.MIN_PARALLELISM, parallelism);
        if (this.parallelism == clamped) {
            return;
        }
        this.parallelism = clamped;
        syncDataHolder.markClientSyncFieldDirty("parallelism");
        markAsChanged();
        notifyCraftingCpuChange();
    }

    private void setCustomName(String name) {
        String normalizedName = normalizeCpuName(name);
        if (this.customName.equals(normalizedName)) {
            return;
        }
        this.customName = normalizedName;
        syncDataHolder.markClientSyncFieldDirty("customName");
        markAsChanged();
        notifyCraftingCpuChange();
    }

    private static String normalizeCpuName(@Nullable String name) {
        if (name == null) {
            return "";
        }
        String normalizedName = name.strip();
        if (normalizedName.codePointCount(0, normalizedName.length()) > MAX_CPU_NAME_LENGTH) {
            normalizedName = normalizedName.substring(
                    0, normalizedName.offsetByCodePoints(0, MAX_CPU_NAME_LENGTH));
        }
        return normalizedName;
    }

}
