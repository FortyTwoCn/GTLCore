package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineHost;
import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.data.GTLBlocks;
import org.gtlcore.gtlcore.utils.MachineUtil;
import org.gtlcore.gtlcore.utils.datastructure.ModuleRenderInfo;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;


import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import earth.terrarium.adastra.common.menus.base.PlanetsMenuProvider;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.ButtonWidget;

import static org.gtlcore.gtlcore.common.data.machines.AdvancedMultiBlockMachineA.ASSEMBLER_MODULE;
import static org.gtlcore.gtlcore.common.data.machines.AdvancedMultiBlockMachineA.RESOURCE_COLLECTION;

public class SpaceElevatorMachine extends TierCasingMachine
                                  implements IModularMachineHost<SpaceElevatorMachine> {

    private final Set<IModularMachineModule<SpaceElevatorMachine, ?>> modules = new ReferenceOpenHashSet<>();
    private int mam = 0;
    private TickableSubscription connectionSubscription;

    public SpaceElevatorMachine(BlockEntityCreationInfo holder) {
        super(holder, "SEPMTier");
        getRecipeLogic().setKeepSubscribing(true);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateConnectionSubscription();
    }

    @Override
    public void onUnload() {
        safeClearModules();
        stopConnectionSubscription();
        super.onUnload();
    }

    @Override
    public @NotNull Set<IModularMachineModule<SpaceElevatorMachine, ?>> getModuleSet() {
        return modules;
    }

    @Override
    public void invalidateStructure(String name) {
        super.invalidateStructure(name);
        safeClearModules();
        stopConnectionSubscription();
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        safeClearModules();
        scanAndConnectSpaceElevatorModules("host_structure_formed");
        updateConnectionSubscription();
    }

    /**
     * 主机侧周期性兜底重连：仅当已连接模块数少于槽位数时重扫。
     * 修复「主机已成型后拆除模块再重新放置、或模块事件时序错过」时不再连接的问题
     * （模块成型事件只会在模块自身生命周期触发一次，主机不再重扫则永久掉线）。
     */
    private void updateConnectionSubscription() {
        if (getLevel() instanceof ServerLevel && isFormed()) {
            if (connectionSubscription == null) {
                connectionSubscription = subscribeServerTick(this::connectionTick);
            }
        } else {
            stopConnectionSubscription();
        }
    }

    private void stopConnectionSubscription() {
        if (connectionSubscription != null) {
            connectionSubscription.unsubscribe();
            connectionSubscription = null;
        }
    }

    private void connectionTick() {
        if (!isFormed()) {
            stopConnectionSubscription();
            return;
        }
        if (getOffsetTimer() % 20L == 0L && modules.size() < getModuleScanPositions().length) {
            scanAndConnectSpaceElevatorModules("host_periodic_retry");
        }
    }

    @Override
    public BlockPos[] getModuleScanPositions() {
        final Level level = getLevel();
        final BlockPos powerCore = getPowerCore(getBlockPos(), level);
        if (powerCore != null) {
            return new BlockPos[] {
                    powerCore.offset(8, 2, 3),
                    powerCore.offset(8, 2, -3),
                    powerCore.offset(-8, 2, 3),
                    powerCore.offset(-8, 2, -3),
                    powerCore.offset(3, 2, 8),
                    powerCore.offset(-3, 2, 8),
                    powerCore.offset(3, 2, -8),
                    powerCore.offset(-3, 2, -8)
            };
        }
        return MachineUtil.EMPTY_POS_ARRAY;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public @NotNull List<ModuleRenderInfo> getModulesForRendering() {
        return List.of(
                new ModuleRenderInfo(
                        new BlockPos(-3, 0, -5),
                        Direction.NORTH,
                        Direction.UP,
                        Direction.NORTH,
                        Direction.UP,
                        RESOURCE_COLLECTION.get()),
                new ModuleRenderInfo(
                        new BlockPos(-8, 0, 0),
                        Direction.NORTH,
                        Direction.UP,
                        Direction.WEST,
                        Direction.UP,
                        ASSEMBLER_MODULE.get()),
                new ModuleRenderInfo(
                        new BlockPos(-8, 0, 6),
                        Direction.NORTH,
                        Direction.UP,
                        Direction.WEST,
                        Direction.UP,
                        RESOURCE_COLLECTION.get()),
                new ModuleRenderInfo(
                        new BlockPos(-3, 0, 11),
                        Direction.NORTH,
                        Direction.UP,
                        Direction.SOUTH,
                        Direction.UP,
                        ASSEMBLER_MODULE.get()),
                new ModuleRenderInfo(
                        new BlockPos(3, 0, 11),
                        Direction.NORTH,
                        Direction.UP,
                        Direction.SOUTH,
                        Direction.UP,
                        RESOURCE_COLLECTION.get()),
                new ModuleRenderInfo(
                        new BlockPos(8, 0, 6),
                        Direction.NORTH,
                        Direction.UP,
                        Direction.EAST,
                        Direction.UP,
                        ASSEMBLER_MODULE.get()),
                new ModuleRenderInfo(
                        new BlockPos(8, 0, 0),
                        Direction.NORTH,
                        Direction.UP,
                        Direction.EAST,
                        Direction.UP,
                        RESOURCE_COLLECTION.get()),
                new ModuleRenderInfo(
                        new BlockPos(3, 0, -5),
                        Direction.NORTH,
                        Direction.UP,
                        Direction.NORTH,
                        Direction.UP,
                        ASSEMBLER_MODULE.get()));
    }

    private BlockPos getPowerCore(BlockPos pos, Level level) {
        if (level == null) {
            return null;
        }
        BlockPos[] coordinates = new BlockPos[] { pos.offset(3, -2, 0),
                pos.offset(-3, -2, 0),
                pos.offset(0, -2, 3),
                pos.offset(0, -2, -3) };
        for (BlockPos blockPos : coordinates) {
            if (level.hasChunkAt(blockPos) && level.getBlockState(blockPos).is(GTLBlocks.POWER_CORE.get())) {
                return blockPos;
            }
        }
        return null;
    }

    private void scanAndConnectSpaceElevatorModules(String trigger) {
        Level level = getLevel();
        if (level == null) {
            return;
        }
        BlockPos powerCore = getPowerCore(getBlockPos(), level);
        BlockPos[] positions = getModuleScanPositions();

        for (BlockPos pos : positions) {
            if (!level.hasChunkAt(pos)) continue;
            MetaMachine machine = MetaMachine.getMachine(level, pos);
            boolean formed = machine instanceof IModularMachineModule<?, ?> module && module.isFormed();
            boolean valid = isValidModule(machine);

            if (valid) {
                @SuppressWarnings("unchecked")
                IModularMachineModule<SpaceElevatorMachine, ?> module = (IModularMachineModule<SpaceElevatorMachine, ?>) machine;
                module.connectToHost(this);

            }
        }
    }

    private static String candidateRejection(MetaMachine machine, boolean formed) {
        if (machine == null) {
            return "missing_machine";
        }
        if (!(machine instanceof IModularMachineModule<?, ?>)) {
            return "wrong_machine_type";
        }
        return formed ? "incompatible_host_type" : "module_not_formed";
    }

    private static String machineType(MetaMachine machine) {
        return machine == null ? "none" : machine.getClass().getName();
    }

    private int getMAM() {
        if (getOffsetTimer() % 20 == 0) {
            mam = getFormedModuleCount();
        }
        return mam;
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        if (getOffsetTimer() % 20 == 0) {
            if (getRecipeLogic().getProgress() > 240) {
                RecipeResult.of(this, RecipeResult.SUCCESS);
                getRecipeLogic().setProgress(120);
            }
        }
        return value;
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = new ArrayList<>(super.getWidgetsForDisplay(sync));
        widgets.addAll(GTLMultiblockDisplays.syncedLines("spaceElevator", (controller, lines) -> {
            if (isFormed()) lines.add(Component.translatable("gtceu.machine.module", getMAM()));
        }).apply(this, sync));
        var working = sync.getOrCreateSyncHandler("spaceElevatorWorking", BooleanSyncValue.class,
                () -> new BooleanSyncValue(() -> isFormed() && getRecipeLogic().isWorking()));
        var depart = sync.getOrCreateSyncHandler("spaceElevatorDepart", InteractionSyncHandler.class,
                () -> new InteractionSyncHandler().setOnMousePressed(mouse -> {
                    if (mouse.isLeftMouseButton() && !sync.isClient() && sync.getPlayer() instanceof ServerPlayer player) {
                        openPlanetsMenu(player);
                    }
                }));
        widgets.add(new ButtonWidget<>().name("spaceElevatorDepart").size(150, 18)
                .overlay(Text.of(Component.translatable("gtceu.machine.space_elevator.set_out")))
                .syncHandler(depart).setEnabledIf(widget -> working.getBoolValue()));
        return widgets;
    }

    private void openPlanetsMenu(ServerPlayer player) {
        if (getLevel() instanceof ServerLevel && !isRemoved() && isFormed() && getRecipeLogic().isWorking()) {
            player.addTag("spaceelevatorst");
            // ResourcefulLib's provider sends the native planet/station content with the menu.
            new PlanetsMenuProvider().openMenu(player);
        }
    }
}
