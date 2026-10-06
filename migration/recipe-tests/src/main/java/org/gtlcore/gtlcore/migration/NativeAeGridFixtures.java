package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IMEIOTrait;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEIOPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedOutputPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEExtendedAsyncOutputPartMachine;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.networking.security.IActionSource;

/** Exercises the production ME base on an actual AE2 network; no storage service is substituted. */
public final class NativeAeGridFixtures {
    public static final MachineEntry<MachineDefinition> PART = NativeMachineFixtures.REGISTRATE
            .machine("native_ae_grid_part", Part::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> DUAL_STOCK = NativeMachineFixtures.REGISTRATE
            .machine("native_ae_dual_stock", org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualHatchStockPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> DUAL_INPUT = NativeMachineFixtures.REGISTRATE
            .machine("native_ae_dual_input", org.gtlcore.gtlcore.common.machine.multiblock.part.MEDualInputHatchPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> INPUT_FLUID = NativeMachineFixtures.REGISTRATE
            .machine("native_gt_input_fluid", com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> STOCK_FLUID = NativeMachineFixtures.REGISTRATE
            .machine("native_gt_stock_fluid", com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> TAG_STOCK_ITEM = NativeMachineFixtures.REGISTRATE
            .machine("native_tag_stock_item", org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockBusPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> TAG_STOCK_FLUID = NativeMachineFixtures.REGISTRATE
            .machine("native_tag_stock_fluid", org.gtlcore.gtlcore.common.machine.multiblock.part.TagFilterMEStockHatchPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> STOCK_ITEM = NativeMachineFixtures.REGISTRATE
            .machine("native_gt_stock_item", com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> FILTERED_OUTPUT = NativeMachineFixtures.REGISTRATE
            .machine("native_ae_filtered_output", MEExtendedOutputPartMachine::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> ASYNC_OUTPUT = NativeMachineFixtures.REGISTRATE
            .machine("native_ae_async_output", MEExtendedAsyncOutputPartMachine::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MultiblockMachineDefinition> OUTPUT_CONTROLLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_ae_output_controller", info -> {
                var machine = new NoEnergyMultiblockMachine(info);
                machine.attachTrait(new NotifiableItemStackHandler(1, IO.IN, IO.IN));
                return machine;
            }).rotationState(RotationState.ALL)
            .recipeType(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> net.minecraft.world.level.block.Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(
                    com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.FRONT,
                    com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.UP,
                    com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.LEFT).slice("SO")
                    .where('S', com.gregtechceu.gtceu.api.multiblock.Predicates.controller(definition))
                    .where('O', com.gregtechceu.gtceu.api.multiblock.Predicates.machines(FILTERED_OUTPUT.get(), ASYNC_OUTPUT.get()))
                    .build()).register();
    public static void init() {}

    public static final class Part extends MEIOPartMachine implements IGridTickable {
        public int ticks;
        public Part(BlockEntityCreationInfo info) {
            super(info, IO.BOTH);
            getMainNode().addService(IGridTickable.class, this);
        }
        @Override protected IMEIOTrait createMETrait() { return new CheckpointTrait(); }
        public CheckpointTrait checkpointTrait() { return (CheckpointTrait) getMETrait(); }
        public IActionSource source() { return actionSource; }
        public void sleep() {
            getMainNode().ifPresent((grid, node) -> grid.getTickManager().sleepDevice(node));
            isSleeping = true;
        }
        @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(1, 1, true); }
        @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
            ticks++;
            isSleeping = true;
            return TickRateModulation.SLEEP;
        }
        public final class CheckpointTrait extends MEIOTrait {
            @SaveField public int checkpoint;
        }
    }
}
