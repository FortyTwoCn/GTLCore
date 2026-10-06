package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import net.minecraft.world.level.block.Blocks;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

public final class NativeTerminalFixtures {
    public static final MachineEntry<MultiblockMachineDefinition> RECIPE_MODES = NativeMachineFixtures.REGISTRATE
            .multiblock("native_recipe_modes", com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.ALL)
            .recipeTypes(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES,
                    com.gregtechceu.gtceu.common.data.GTRecipeTypes.MIXER_RECIPES,
                    com.gregtechceu.gtceu.common.data.GTRecipeTypes.ASSEMBLER_RECIPES,
                    com.gregtechceu.gtceu.common.data.GTRecipeTypes.CUTTER_RECIPES,
                    com.gregtechceu.gtceu.common.data.GTRecipeTypes.ELECTROLYZER_RECIPES,
                    com.gregtechceu.gtceu.common.data.GTRecipeTypes.MACERATOR_RECIPES,
                    com.gregtechceu.gtceu.common.data.GTRecipeTypes.FURNACE_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("S")
                    .where('S', controller(definition)).build()).register();
    public static final MachineEntry<MultiblockMachineDefinition> LIFECYCLE = NativeMachineFixtures.REGISTRATE
            .multiblock("native_structure_lifecycle", NoEnergyMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SOA")
                    .where('S', controller(definition))
                    .where('O', machines(com.gregtechceu.gtceu.common.data.GTMachines.FLUID_EXPORT_HATCH[1].get()))
                    .where('A', blocks(com.gregtechceu.gtceu.common.data.GTBlocks.COIL_CUPRONICKEL.get())).build()).register();
    public static final MachineEntry<MultiblockMachineDefinition> DETECTION = NativeMachineFixtures.REGISTRATE
            .multiblock("native_detection_controller", NoEnergyMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(true)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SIG").slice("DDD")
                    .where('S', controller(definition)).where('I', blocks(Blocks.IRON_BLOCK))
                    .where('G', blocks(Blocks.GOLD_BLOCK)).where('D', blocks(Blocks.DIAMOND_BLOCK)).build())
            .pattern("auxiliary", definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("S", "R")
                    .where('S', controller(definition)).where('R', blocks(Blocks.REDSTONE_BLOCK)).build()).register();
    public static final MachineEntry<MultiblockMachineDefinition> REPEATING = NativeMachineFixtures.REGISTRATE
            .multiblock("native_terminal_repeating_controller", NoEnergyMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(true)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SI")
                    .sliceRepeatable(1, 3, "II").where('S', controller(definition))
                    .where('I', blocks(Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK)).build()).register();
    public static final MachineEntry<MultiblockMachineDefinition> FLUID = NativeMachineFixtures.REGISTRATE
            .multiblock("native_terminal_fluid_controller", NoEnergyMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SW")
                    .where('S', controller(definition)).where('W', blocks(Blocks.WATER)).build()).register();
    public static void init() {}
    public static final MachineEntry<com.gregtechceu.gtceu.api.machine.MachineDefinition> QUANTUM_CHEST = NativeMachineFixtures.REGISTRATE
            .machine("native_configuration_quantum_chest", info -> new com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine(info, com.gregtechceu.gtceu.api.GTValues.MAX, Long.MAX_VALUE))
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<com.gregtechceu.gtceu.api.machine.MachineDefinition> QUANTUM_TANK = NativeMachineFixtures.REGISTRATE
            .machine("native_configuration_quantum_tank", info -> new com.gregtechceu.gtceu.common.machine.storage.QuantumTankMachine(info, com.gregtechceu.gtceu.api.GTValues.MAX, Long.MAX_VALUE))
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<com.gregtechceu.gtceu.api.machine.MachineDefinition> SIMPLE = NativeMachineFixtures.REGISTRATE
            .machine("native_configuration_simple", info -> new com.gregtechceu.gtceu.api.machine.SimpleTieredMachine(info, com.gregtechceu.gtceu.api.GTValues.HV))
            .rotationState(RotationState.ALL).recipeType(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES).register();
    public static final MachineEntry<MultiblockMachineDefinition> EXISTING_HATCH = NativeMachineFixtures.REGISTRATE
            .multiblock("native_terminal_existing_hatch_controller", NoEnergyMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("SOO")
                    .where('S', controller(definition)).where('O', blocks(Blocks.IRON_BLOCK)
                            .or(machines(NativeAePatternFixtures.BUFFER.get()).setExactLimit(1))).build()).register();
    public static final MachineEntry<MultiblockMachineDefinition> FAILED_PLACEMENT = NativeMachineFixtures.REGISTRATE
            .multiblock("native_terminal_failed_placement_controller", NoEnergyMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("ST")
                    .where('S', controller(definition)).where('T', blocks(Blocks.TORCH)).build()).register();
}
