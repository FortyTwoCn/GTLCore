package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.GTLPredicates;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableFluidTank;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.AdvancedAssemblyLineMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.TierCasingMachine;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;

import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;

import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Separate test IDs. Production classes and their traits are unchanged. */
public final class NativeMachineFixtures {
    static final GTRegistrate REGISTRATE = GTRegistrate.create("gtlcore_recipe_test", false);
    public static final MachineEntry<MultiblockMachineDefinition> SIMULATION = REGISTRATE
            .multiblock("native_simulation", org.gtlcore.gtlcore.common.machine.multiblock.SimulationMachine::new)
            .rotationState(RotationState.ALL).recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("S")
                    .where('S', controller(definition)).build()).register();
    public static final MachineEntry<MultiblockMachineDefinition> PLASMA_GENERATOR = plasmaGenerator("native_plasma_generator",
            org.gtlcore.gtlcore.common.data.GTLPlasmaGeneratorModifiers.tiered(java.util.Map.of(32L,
                    () -> new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1))));
    public static final MachineEntry<MultiblockMachineDefinition> ADVANCED_PLASMA_GENERATOR = plasmaGenerator("native_advanced_plasma_generator",
            org.gtlcore.gtlcore.common.data.GTLPlasmaGeneratorModifiers.advanced(
                    () -> new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1),
                    () -> new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA, 1)));

    private static MachineEntry<MultiblockMachineDefinition> plasmaGenerator(String name,
            com.gregtechceu.gtceu.api.recipe.modifier.RecipeModifier modifier) {
        return REGISTRATE.multiblock(name, info -> {
            var machine = new com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine(info);
            machine.getTraitHolder().attachPersistentTrait("plasmaInput", new NotifiableFluidTank(3, 1000, IO.IN, IO.IN));
            machine.getTraitHolder().attachPersistentTrait("plasmaOutput", new NotifiableFluidTank(1, 5000, IO.OUT, IO.OUT));
            return machine;
        }).rotationState(RotationState.ALL).generator(true)
                .recipeType(GTRecipeTypes.PLASMA_GENERATOR_FUELS)
                .recipeModifier(modifier).appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
                .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("ISI")
                        .where('S', controller(definition)).where('I', abilities(PartAbility.OUTPUT_ENERGY)).build()).register();
    }
    public static final java.util.Set<Object> NEUTRON_BLOCKED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    public static final MachineEntry<com.gregtechceu.gtceu.api.machine.MachineDefinition> NEUTRON_ACCELERATOR = REGISTRATE
            .machine("native_neutron_accelerator", info -> new org.gtlcore.gtlcore.common.machine.multiblock.part.NeutronAcceleratorPartMachine(info, com.gregtechceu.gtceu.api.GTValues.HV))
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MultiblockMachineDefinition> NEUTRON = REGISTRATE
            .multiblock("native_neutron_activator", info -> {
                var machine = new org.gtlcore.gtlcore.common.machine.multiblock.noenergy.NeutronActivatorMachine(info);
                machine.attachTrait(new NotifiableItemStackHandler(1, IO.IN, IO.IN));
                machine.attachTrait(new NotifiableItemStackHandler(1, IO.OUT, IO.OUT));
                return machine;
            })
            .rotationState(RotationState.ALL)
            .recipeType(org.gtlcore.gtlcore.common.data.GTLRecipeTypes.NEUTRON_ACTIVATOR_RECIPES)
            .recipeModifier(org.gtlcore.gtlcore.common.machine.multiblock.noenergy.NeutronActivatorMachine::recipeModifier)
            .onWorking(machine -> !NEUTRON_BLOCKED.contains(machine.self()))
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("PPP", "PSP", "PPP")
                    .where('S', controller(definition))
                    .where('P', GTLPredicates.countBlock("SpeedPipe", Blocks.IRON_BLOCK)).build())
            .register();
    public static final MachineEntry<com.gregtechceu.gtceu.api.machine.MachineDefinition> LOW_PRESSURE = REGISTRATE
            .machine("native_low_pressure", info -> new com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine(info, false))
            .rotationState(RotationState.ALL).recipeType(GTRecipeTypes.FURNACE_RECIPES)
            .recipeModifier(com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine::recipeModifier).register();
    public static final MachineEntry<com.gregtechceu.gtceu.api.machine.MachineDefinition> HIGH_PRESSURE = REGISTRATE
            .machine("native_high_pressure", info -> new com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine(info, true))
            .rotationState(RotationState.ALL).recipeType(GTRecipeTypes.FURNACE_RECIPES)
            .recipeModifier(com.gregtechceu.gtceu.api.machine.steam.SimpleSteamMachine::recipeModifier).register();
    public static final MachineEntry<MultiblockMachineDefinition> TIER = REGISTRATE
            .multiblock("native_tier", info -> new TierCasingMachine(info, "CATier"))
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.VACUUM_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .allowFlip(false)
            .pattern(definition -> {
                var tiers = new Int2ObjectLinkedOpenHashMap<Supplier<?>>();
                tiers.put(3, () -> Blocks.IRON_BLOCK);
                tiers.put(4, () -> Blocks.GOLD_BLOCK);
                return MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("CCC", "CSC", "CCC")
                        .where('S', controller(definition))
                        .where('C', GTLPredicates.tierCasings(tiers, "CATier"))
                        .build();
            }).register();

    public static final MachineEntry<MultiblockMachineDefinition> ASSEMBLY = REGISTRATE
            .multiblock("native_assembly", AdvancedAssemblyLineMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.ASSEMBLY_LINE_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT)
                    .slice("S U")
                    .sliceRepeatable(1, 4, "UUU")
                    .slice("UFU")
                    .where('S', controller(definition))
                    .where('U', GTLPredicates.countBlock("Unit", Blocks.IRON_BLOCK))
                    .where('F', blocks(Blocks.GOLD_BLOCK)).build())
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> STORAGE = REGISTRATE
            .multiblock("native_storage", info -> new StorageMachine(info, 1))
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.VACUUM_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT)
                    .slice("S").where('S', controller(definition)).build())
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> ROUTING = REGISTRATE
            .multiblock("native_routing", info -> {
                var machine = new NoEnergyMultiblockMachine(info);
                machine.attachTrait(new ProgrammableCircuitSlotTrait()).setEnabled(true);
                return machine;
            })
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.VACUUM_RECIPES)
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT)
                    .slice("ISI").where('S', controller(definition)).where('I', abilities(PartAbility.IMPORT_ITEMS)).build())
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> BATCH = REGISTRATE
            .multiblock("native_batch", info -> {
                var machine = new StorageMachine(info, 1);
                machine.attachTrait(new NotifiableItemStackHandler(3, IO.IN, IO.IN));
                machine.attachTrait(new NotifiableFluidTank(1, 10_000, IO.OUT, IO.OUT));
                return machine;
            })
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.CHEMICAL_RECIPES)
            .noRecipeModifier()
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT)
                    .slice("S").where('S', controller(definition)).build())
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> ENERGY_BATCH = REGISTRATE
            .multiblock("native_energy_batch", info -> {
                var machine = new StorageMachine(info, 1);
                machine.attachTrait(new NotifiableItemStackHandler(3, IO.IN, IO.IN));
                machine.attachTrait(new NotifiableFluidTank(1, 10_000, IO.OUT, IO.OUT));
                return machine;
            })
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.CHEMICAL_RECIPES)
            .noRecipeModifier()
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT)
                    .slice("ISI").where('S', controller(definition)).where('I', abilities(PartAbility.INPUT_ENERGY)).build())
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> AUXILIARY_IO = REGISTRATE
            .multiblock("native_auxiliary_io", info -> {
                var machine = new StorageMachine(info, 1);
                machine.attachTrait(new NotifiableItemStackHandler(2, IO.IN, IO.IN));
                machine.attachTrait(new NotifiableItemStackHandler(1, IO.OUT, IO.OUT));
                machine.attachTrait(new NotifiableFluidTank(1, 1000, IO.IN, IO.IN));
                machine.attachTrait(new NotifiableFluidTank(1, 1000, IO.OUT, IO.OUT));
                machine.attachTrait(new ProgrammableCircuitSlotTrait()).setEnabled(true);
                return machine;
            }).rotationState(RotationState.ALL).recipeType(GTRecipeTypes.CHEMICAL_RECIPES)
            .noRecipeModifier().appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT)
                    .slice("ISI").where('S', controller(definition)).where('I', abilities(PartAbility.INPUT_ENERGY)).build()).register();

    public static final MachineEntry<MultiblockMachineDefinition> CROSS = REGISTRATE
            .multiblock("native_cross_recipe", NativeCrossMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTRecipeTypes.CHEMICAL_RECIPES)
            .noRecipeModifier()
            .appearanceBlock(() -> Blocks.IRON_BLOCK)
            .allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT)
                    .slice("ISI").where('S', controller(definition)).where('I', abilities(PartAbility.INPUT_ENERGY)).build())
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> ADD_MUTABLE = REGISTRATE
            .multiblock("native_add_mutable", NativeMutableMachine::new)
            .rotationState(RotationState.ALL).recipeType(GTRecipeTypes.CHEMICAL_RECIPES)
            .noRecipeModifier().appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT)
                    .slice("ISI").where('S', controller(definition)).where('I', abilities(PartAbility.INPUT_ENERGY)).build())
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> HEAT_EXCHANGER = REGISTRATE
            .multiblock("native_heat_exchanger", info -> {
                var machine = new org.gtlcore.gtlcore.common.machine.multiblock.noenergy.HeatExchangerMachine(info);
                machine.attachTrait(new NotifiableFluidTank(2, 20_000, IO.IN, IO.IN));
                machine.attachTrait(new NotifiableFluidTank(2, 20_000, IO.OUT, IO.OUT));
                return machine;
            }).rotationState(RotationState.ALL).recipeType(org.gtlcore.gtlcore.common.data.GTLRecipeTypes.HEAT_EXCHANGER_RECIPES)
            .recipeModifier(org.gtlcore.gtlcore.common.machine.multiblock.noenergy.HeatExchangerMachine::recipeModifier)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("S").where('S', controller(definition)).build()).register();

    public static final MachineEntry<MultiblockMachineDefinition> COIL_MENU = REGISTRATE
            .multiblock("native_coil_menu", com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(GTRecipeTypes.CHEMICAL_RECIPES).noRecipeModifier()
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("CSC")
                    .where('S', controller(definition)).where('C', heatingCoils()).build()).register();

    public static final MachineEntry<MultiblockMachineDefinition> COIL_CROSS = REGISTRATE
            .multiblock("native_coil_cross", org.gtlcore.gtlcore.common.machine.multiblock.electric.CoilWorkableElectricMultipleRecipesMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(GTRecipeTypes.CHEMICAL_RECIPES).noRecipeModifier()
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("CSC").where('S', controller(definition)).where('C', heatingCoils()).build()).register();

    public static final MachineEntry<com.gregtechceu.gtceu.api.machine.MachineDefinition> ENERGY_RECEIVER = REGISTRATE
            .machine("native_energy_receiver", info -> {
                var machine = new com.gregtechceu.gtceu.api.machine.MetaMachine(info);
                machine.attachTrait(com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableEnergyContainer.receiverContainer(65_536, 32, 16));
                return machine;
            }).rotationState(RotationState.ALL).register();

    public static final MachineEntry<MultiblockMachineDefinition> GENERATOR_ARRAY = REGISTRATE
            .multiblock("native_generator_array", info -> {
                var machine = new org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine(info);
                machine.attachTrait(new NotifiableFluidTank(1, 20_000, IO.IN, IO.IN));
                return machine;
            }).rotationState(RotationState.ALL).recipeType(GTRecipeTypes.DUMMY_RECIPES).generator(true)
            .modelProperty(com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.RECIPE_LOGIC_STATUS,
                    com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic.Status.IDLE)
            .recipeModifier(org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine::recipeModifier)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("ISI").where('S', controller(definition))
                    .where('I', abilities(PartAbility.OUTPUT_ENERGY).or(blocks(Blocks.IRON_BLOCK))).build()).register();

    public static final MachineEntry<MultiblockMachineDefinition> COMBUSTION_ENGINE = REGISTRATE
            .multiblock("native_combustion_engine", info -> {
                var machine = new org.gtlcore.gtlcore.common.machine.multiblock.generator.GTLLargeCombustionEngineMachine(info, com.gregtechceu.gtceu.api.GTValues.EV);
                machine.attachTrait(new NotifiableFluidTank(4, 20_000, IO.IN, IO.IN)); return machine;
            }).rotationState(RotationState.ALL).recipeType(GTRecipeTypes.COMBUSTION_GENERATOR_FUELS).generator(true)
            .recipeModifier(org.gtlcore.gtlcore.common.machine.multiblock.generator.GTLLargeCombustionEngineMachine::recipeModifier)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("ISI").where('S', controller(definition))
                    .where('I', abilities(PartAbility.OUTPUT_ENERGY)).build()).register();

    public static final MachineEntry<com.gregtechceu.gtceu.api.machine.MachineDefinition> ROTOR_HATCH = REGISTRATE
            .machine("native_rotor_hatch", org.gtlcore.gtlcore.common.machine.multiblock.part.RotorHatchPartMachine::new)
            .rotationState(RotationState.ALL).register();
    public static final MachineEntry<MultiblockMachineDefinition> MEGA_TURBINE = REGISTRATE
            .multiblock("native_mega_turbine", info -> {
                var machine = new org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine(info, com.gregtechceu.gtceu.api.GTValues.IV, 8);
                machine.attachTrait(new NotifiableFluidTank(1, 20_000, IO.IN, IO.IN)); return machine;
            }).rotationState(RotationState.ALL).recipeType(GTRecipeTypes.GAS_TURBINE_FUELS).generator(true)
            .recipeModifier(org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine::recipeModifier)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, RIGHT).slice("RRSHD").where('S', controller(definition))
                    .where('R', abilities(PartAbility.ROTOR_HOLDER)).where('H', blocks(ROTOR_HATCH.getBlock())).where('D', abilities(PartAbility.OUTPUT_ENERGY)).build()).register();
    public static final MachineEntry<MultiblockMachineDefinition> CHEMICAL_ENGINE = REGISTRATE
            .multiblock("native_chemical_engine", info -> {
                var machine = new org.gtlcore.gtlcore.common.machine.multiblock.generator.ChemicalEnergyDevourerMachine(info);
                machine.attachTrait(new NotifiableFluidTank(4, 20_000, IO.IN, IO.IN)); return machine;
            }).rotationState(RotationState.ALL).recipeType(GTRecipeTypes.COMBUSTION_GENERATOR_FUELS).generator(true)
            .recipeModifier(org.gtlcore.gtlcore.common.machine.multiblock.generator.ChemicalEnergyDevourerMachine::recipeModifier)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("ISI").where('S', controller(definition))
                    .where('I', abilities(PartAbility.OUTPUT_ENERGY)).build()).register();


    public static final MachineEntry<com.gregtechceu.gtceu.api.machine.MachineDefinition> STEAM_HATCH = REGISTRATE
            .machine("native_large_steam_hatch", info -> new org.gtlcore.gtlcore.common.machine.multiblock.part.LargeSteamHatchPartMachine(info, IO.IN, 8192, false))
            .rotationState(RotationState.ALL).abilities(PartAbility.STEAM).register();
    public static final MachineEntry<MultiblockMachineDefinition> STEAM = REGISTRATE
            .multiblock("native_core_steam", info -> {
                var machine = new org.gtlcore.gtlcore.common.machine.multiblock.steam.LargeSteamParallelMultiblockMachine(info, 8);
                machine.attachTrait(new NotifiableItemStackHandler(2, IO.IN, IO.IN));
                machine.attachTrait(new NotifiableFluidTank(1, 10_000, IO.OUT, IO.OUT));
                return machine;
            }).rotationState(RotationState.ALL).recipeType(GTRecipeTypes.CHEMICAL_RECIPES)
            .recipeModifiers(org.gtlcore.gtlcore.common.data.GTLRecipeModifiers.STEAM_OC)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT)
                    .slice("ISI").where('S', controller(definition)).where('I', abilities(PartAbility.STEAM).or(blocks(Blocks.IRON_BLOCK))).build()).register();

    public static final class NativeCrossMachine extends org.gtlcore.gtlcore.common.machine.multiblock.electric.WorkableElectricMultipleRecipesMachine {
        public NativeCrossMachine(com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo info) {
            super(info, new CrossFixtureLogic());
            attachTrait(new NotifiableItemStackHandler(3, IO.IN, IO.IN));
            attachTrait(new NotifiableFluidTank(1, 10_000, IO.OUT, IO.OUT));
        }
        @Override public int getMaxParallel() { return 4; }
    }
    public static final class CrossFixtureLogic extends org.gtlcore.gtlcore.common.machine.trait.MultipleRecipesLogic {
        public java.util.List<com.gregtechceu.gtceu.api.recipe.GTRecipe> sources = java.util.List.of();
        @Override protected java.util.Iterator<com.gregtechceu.gtceu.api.recipe.GTRecipe> lookupRecipeIterator() { return sources.iterator(); }
    }

    public static final class NativeMutableMachine extends org.gtlcore.gtlcore.common.machine.multiblock.electric.WorkableElectricMultipleRecipesMachine
            implements com.gtladd.gtladditions.api.machine.IWirelessElectricMultiblockMachine, com.gtladd.gtladditions.api.machine.IThreadModifierMachine {
        private com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler wireless;
        private com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart threadPart;
        public NativeMutableMachine(com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo info) {
            super(info, new MutableFixtureLogic());
            attachTrait(new NotifiableItemStackHandler(3, IO.IN, IO.IN));
            attachTrait(new NotifiableFluidTank(1, 10_000, IO.OUT, IO.OUT));
        }
        @Override public int getMaxParallel() { return 4; }
        @Override public int getAdditionalThread() { return threadPart == null ? 2 : threadPart.getThreadCount(); }
        @Override public com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart getThreadPartMachine() { return threadPart; }
        @Override public void setThreadPartMachine(com.gtladd.gtladditions.api.machine.feature.IThreadModifierPart part) { threadPart = part; }
        @Override public com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler getWirelessNetworkEnergyHandler() { return wireless; }
        @Override public void setWirelessNetworkEnergyHandler(com.gtladd.gtladditions.api.machine.trait.IWirelessNetworkEnergyHandler handler) { wireless = handler; }
    }
    public static final class MutableFixtureLogic extends com.gtladd.gtladditions.api.machine.logic.MutableRecipesLogic<NativeMutableMachine> {
        public java.util.List<com.gregtechceu.gtceu.api.recipe.GTRecipe> sources = java.util.List.of();
        @Override protected java.util.Set<com.gregtechceu.gtceu.api.recipe.GTRecipe> lookupRecipeSet() { return new java.util.LinkedHashSet<>(sources); }
        @Override public java.util.Iterator<com.gregtechceu.gtceu.api.recipe.GTRecipe> searchRecipe() { return sources.iterator(); }
    }

    public static void register(IEventBus modBus) { REGISTRATE.registerEventListeners(modBus); }
}
