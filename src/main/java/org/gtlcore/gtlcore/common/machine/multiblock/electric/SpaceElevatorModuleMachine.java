package org.gtlcore.gtlcore.common.machine.multiblock.electric;

import org.gtlcore.gtlcore.api.machine.multiblock.IModularMachineModule;
import org.gtlcore.gtlcore.common.data.GTLBlocks;
import org.gtlcore.gtlcore.common.data.GTLRecipeModifiers;
import org.gtlcore.gtlcore.utils.MachineUtil;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.OverclockingLogic;
import com.gregtechceu.gtceu.api.recipe.modifier.ModifierFunction;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import org.gtlcore.gtlcore.api.recipe.CoreRecipeModifiers;
import org.gtlcore.gtlcore.common.data.GTLMultiblockDisplays;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;

import net.minecraft.ChatFormatting;
import net.minecraft.MethodsReturnNonnullByDefault;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class SpaceElevatorModuleMachine extends WorkableElectricMultiblockMachine
                                        implements IModularMachineModule<SpaceElevatorMachine, SpaceElevatorModuleMachine> {

    private static final int CONNECTION_CHECK_INTERVAL_TICKS = 20;

    public SpaceElevatorModuleMachine(BlockEntityCreationInfo holder, boolean sepmTier) {
        super(holder);
        this.sepmTier = sepmTier;
    }

    @SyncToClient
    private int spaceElevatorTier = 0;
    @SyncToClient
    private int moduleTier = 0;

    private final boolean sepmTier;
    @Nullable
    private TickableSubscription connectionSubscription;

    @SaveField
    @Nullable
    private BlockPos hostPosition;

    @Nullable
    private SpaceElevatorMachine host;

    @Override
    public @NotNull Class<SpaceElevatorMachine> getHostType() {
        return SpaceElevatorMachine.class;
    }

    @Override
    public BlockPos getHostPosition() { return hostPosition; }
    @Override
    public void setHostPosition(@Nullable BlockPos pos) { hostPosition = pos == null ? null : pos.immutable(); setChanged(); }
    @Override
    public SpaceElevatorMachine getHost() { return host; }
    @Override
    public void setHost(@Nullable SpaceElevatorMachine host) { this.host = host; }

    // ========================================
    // Elevator connection
    // ========================================

    @Override
    public void onConnected(@NotNull SpaceElevatorMachine host) {
        updateConnectionSubscription();
        getSpaceElevatorTier();
        recipeLogic.updateTickSubscription();
    }

    @Override
    public BlockPos[] getHostScanPositions() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return MachineUtil.EMPTY_POS_ARRAY;
        }
        return getHostPositions(findPowerCore(serverLevel));
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        updateConnectionSubscription();
        reconcileHostConnection("module_structure_formed");
    }

    @Override
    public void invalidateStructure(String name) {
        super.invalidateStructure(name);
        removeFromHost(this.host);
        updateConnectionSubscription();
    }

    @Override
    public void onPartUnload() {
        super.onPartUnload();
        removeFromHost(this.host);
        stopConnectionSubscription();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        updateConnectionSubscription();
    }

    @Override
    public void onUnload() {
        removeFromHost(this.host);
        stopConnectionSubscription();
        super.onUnload();
    }

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
        if (getOffsetTimer() % CONNECTION_CHECK_INTERVAL_TICKS == 0) {
            reconcileHostConnection("periodic_retry");
        }
    }

    private void reconcileHostConnection(String trigger) {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockPos savedHostPosition = getHostPosition();
        SpaceElevatorMachine liveHost = getLiveHost(serverLevel, savedHostPosition);
        if (liveHost != null) {
            boolean repairedRegistration = !liveHost.getModuleSet().contains(this);
            if (getHost() != liveHost || repairedRegistration) {
                connectToHost(liveHost);

            }
            return;
        }

        if (getHost() != null) {
            removeFromHost(getHost());
        }

        BlockPos powerCore = findPowerCore(serverLevel);
        BlockPos[] candidates = getHostPositions(powerCore);

        for (BlockPos candidatePos : candidates) {
            if (!serverLevel.hasChunkAt(candidatePos)) continue;
            MetaMachine machine = MetaMachine.getMachine(serverLevel, candidatePos);
            boolean formed = machine instanceof SpaceElevatorMachine elevator && elevator.isFormed();
            boolean valid = isValidHost(machine);

            if (valid) {
                SpaceElevatorMachine candidateHost = (SpaceElevatorMachine) machine;
                connectToHost(candidateHost);

                return;
            }
        }
    }

    @Nullable
    private SpaceElevatorMachine getLiveHost(ServerLevel level, @Nullable BlockPos position) {
        if (position == null || !level.hasChunkAt(position)) {
            return null;
        }
        MetaMachine machine = MetaMachine.getMachine(level, position);
        return isValidHost(machine) ? (SpaceElevatorMachine) machine : null;
    }

    @Nullable
    private BlockPos findPowerCore(Level level) {
        BlockPos pos = getBlockPos();
        BlockPos[] powerCorePositions = new BlockPos[] {
                pos.offset(8, -2, 3),
                pos.offset(8, -2, -3),
                pos.offset(-8, -2, 3),
                pos.offset(-8, -2, -3),
                pos.offset(3, -2, 8),
                pos.offset(-3, -2, 8),
                pos.offset(3, -2, -8),
                pos.offset(-3, -2, -8)
        };
        for (BlockPos position : powerCorePositions) {
            if (level.hasChunkAt(position) && level.getBlockState(position).is(GTLBlocks.POWER_CORE.get())) {
                return position;
            }
        }
        return null;
    }

    private static BlockPos[] getHostPositions(@Nullable BlockPos powerCore) {
        if (powerCore == null) {
            return MachineUtil.EMPTY_POS_ARRAY;
        }
        return new BlockPos[] {
                powerCore.offset(3, 2, 0),
                powerCore.offset(-3, 2, 0),
                powerCore.offset(0, 2, 3),
                powerCore.offset(0, 2, -3)
        };
    }

    private static String hostCandidateRejection(MetaMachine machine, boolean formed) {
        if (machine == null) {
            return "missing_machine";
        }
        if (!(machine instanceof SpaceElevatorMachine)) {
            return "wrong_machine_type";
        }
        return formed ? "invalid_host" : "host_not_formed";
    }

    private static String machineType(MetaMachine machine) {
        return machine == null ? "none" : machine.getClass().getName();
    }

    // ========================================
    // Recipe Tier
    // ========================================

    private void getSpaceElevatorTier() {
        int oldElevatorTier = spaceElevatorTier;
        int oldModuleTier = moduleTier;
        if (this.host != null) {
            final RecipeLogic logic = host.getRecipeLogic();
            if (logic.isWorking() && logic.getProgress() > 80) {
                spaceElevatorTier = host.getTier() - GTValues.ZPM;
                moduleTier = host.getCasingTier();
            } else if (!logic.isWorking()) {
                spaceElevatorTier = 0;
                moduleTier = 0;
            }
        } else {
            spaceElevatorTier = 0;
            moduleTier = 0;
        }
        if (oldElevatorTier != spaceElevatorTier) getSyncDataHolder().markClientSyncFieldDirty("spaceElevatorTier");
        if (oldModuleTier != moduleTier) getSyncDataHolder().markClientSyncFieldDirty("moduleTier");
    }

    @Nullable
    public static ModifierFunction recipeModifier(MetaMachine machine, @NotNull GTRecipe recipe) {
        if (machine instanceof SpaceElevatorModuleMachine moduleMachine) {
            moduleMachine.getSpaceElevatorTier();
            if (moduleMachine.spaceElevatorTier < 1) {
                return ModifierFunction.NULL;
            }
            if (moduleMachine.sepmTier && recipe.data.getInt("SEPMTier") > moduleMachine.moduleTier) {
                return ModifierFunction.NULL;
            }
            return input -> {
                var reduced = GTLRecipeModifiers.reduction(machine, input, 1, Math.pow(0.8, moduleMachine.spaceElevatorTier - 1));
                if (reduced == null) return null;
                var parallel = CoreRecipeModifiers.accurateParallel(machine, reduced,
                        (int) Math.pow(4, moduleMachine.moduleTier - 1), false).getFirst();
                return parallel == null ? null : OverclockingLogic.NON_PERFECT_OVERCLOCK_SUBTICK
                        .getModifier(machine, parallel, moduleMachine.getOverclockVoltage()).apply(parallel);
            };
        }
        return ModifierFunction.NULL;
    }

    @Override
    public boolean onWorking() {
        boolean value = super.onWorking();
        if (getOffsetTimer() % 20 == 0) {
            getSpaceElevatorTier();
            if (spaceElevatorTier < 1) {
                getRecipeLogic().setProgress(0);
            }
        }
        return value;
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager sync) {
        var widgets = new ArrayList<>(super.getWidgetsForDisplay(sync));
        widgets.addAll(GTLMultiblockDisplays.syncedLines("spaceElevatorModule", (controller, lines) -> addDisplayText(lines)).apply(this, sync));
        return widgets;
    }

    private void addDisplayText(@NotNull List<Component> textList) {
        if (!this.isFormed) return;
        if (getOffsetTimer() % 10 == 0) {
            getSpaceElevatorTier();
        }
        textList.add(Component.translatable("gtceu.multiblock.parallel", Component.literal(FormattingUtil.formatNumbers(Math.pow(4, moduleTier - 1))).withStyle(ChatFormatting.DARK_PURPLE)).withStyle(ChatFormatting.GRAY));
        textList.add(Component.translatable(spaceElevatorTier < 1 ? "tooltip.gtlcore.space_elevator_not_connected" : "tooltip.gtlcore.space_elevator_connected"));
        textList.add(Component.translatable("gtceu.machine.duration_multiplier.tooltip", FormattingUtil.formatPercent(Math.pow(0.8, spaceElevatorTier - 1))));
    }
}
