package org.gtlcore.gtlcore.common.data.machines;

import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.pattern.GTLPredicates;
import org.gtlcore.gtlcore.common.data.*;
import org.gtlcore.gtlcore.common.data.machines.structure.GeneratorMachineStructure;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.ChemicalEnergyDevourerMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.DysonSphereMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine;
import org.gtlcore.gtlcore.utils.Registries;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import org.gtlcore.gtlcore.common.data.GTLAnimationModels;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKeys;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;

import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

@SuppressWarnings("unused")
public class GeneratorMachine {

    public static void init() {}

    public static final MachineEntry<MultiblockMachineDefinition> LARGE_SEMI_FLUID_GENERATOR = GTLGeneratorMachines.LARGE_SEMI_FLUID_GENERATOR;

    public final static MachineEntry<MultiblockMachineDefinition> CHEMICAL_ENERGY_DEVOURER = REGISTRATE
            .multiblock("chemical_energy_devourer", ChemicalEnergyDevourerMachine::new)
            .rotationState(RotationState.ALL)
            .recipeTypes(GTRecipeTypes.COMBUSTION_GENERATOR_FUELS, GTLRecipeTypes.SEMI_FLUID_GENERATOR_FUELS,
                    GTRecipeTypes.GAS_TURBINE_FUELS, GTLRecipeTypes.ROCKET_ENGINE_FUELS)
            .generator(true)
            .tooltips(Component.translatable(
                    "gtceu.universal.tooltip.base_production_eut", 2 * GTValues.V[GTValues.ZPM]),
                    Component.translatable(
                            "gtceu.universal.tooltip.uses_per_hour_lubricant", 2000),
                    Component.translatable(
                            "gtlcore.machine.chemical_energy_devourer.tooltip.0", 2 * GTValues.V[GTValues.UV]),
                    Component.translatable(
                            "gtlcore.machine.chemical_energy_devourer.tooltip.1", 2 * GTValues.V[GTValues.UHV]))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier(ChemicalEnergyDevourerMachine::recipeModifier, true)
            .appearanceBlock(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST)
            .pattern(definition -> GeneratorMachineStructure.CHEMICAL_ENERGY_DEVOURER.get()
                    .where('S', controller(blocks(definition.getBlock())))
                    .where('A', blocks(GTBlocks.CASING_EXTREME_ENGINE_INTAKE.get()))
                    .where('B', blocks(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get()))
                    .where('F', blocks(GTBlocks.CASING_TUNGSTENSTEEL_ROBUST.get())
                            .and(abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .and(abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(4)))
                    .where('C', blocks(GTBlocks.CASING_LAMINATED_GLASS.get()))
                    .where('G', blocks(GCYMBlocks.ELECTROLYTIC_CELL.get()))
                    .where('D', blocks(GTBlocks.FIREBOX_TITANIUM.get()))
                    .where('E', blocks(GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX.get()))
                    .where('H', blocks(GTBlocks.CASING_TITANIUM_GEARBOX.get()))
                    .where('P', abilities(PartAbility.OUTPUT_ENERGY))
                    .where('I', abilities(PartAbility.MUFFLER))
                    .build())
            .recoveryItems(
                    () -> new ItemLike[] { GTMaterialItems.MATERIAL_ITEMS.get(TagPrefix.dustTiny, GTMaterials.Ash).get() })
            .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_robust_tungstensteel"),
                    GTCEu.id("block/multiblock/generator/extreme_combustion_engine"))
            .register();

    public static MachineEntry<MultiblockMachineDefinition> registerMegaTurbine(String name, int tier, int value, Supplier<GTRecipeType> recipeType,
                                                                  Supplier<Block> casing, Supplier<Block> gear, ResourceLocation baseCasing,
                                                                  ResourceLocation overlayModel) {
        return REGISTRATE.multiblock(name, holder -> new MegaTurbineMachine(holder, tier, value))
                .rotationState(RotationState.ALL)
                .recipeType(recipeType)
                .generator(true)
                .tooltips(Component.translatable("tooltip.gtlcore.can_use_transformer_hatch"))
                .tooltips(Component.translatable("gtceu.universal.tooltip.base_production_eut", FormattingUtil.formatNumbers(GTValues.V[tier] * value)))
                .tooltips(Component.translatable("gtceu.multiblock.turbine.efficiency_tooltip", GTValues.VNF[tier]))
                .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
                .tooltipBuilder(GTLMachines.GTL_ADD)
                .recipeModifier(MegaTurbineMachine::recipeModifier)
                .appearanceBlock(casing)
                .pattern(definition -> GeneratorMachineStructure.CHEMICAL_ENERGY_DEVOURER_2.get()
                        .where('S', Predicates.controller(Predicates.blocks(definition.getBlock())))
                        .where('A', Predicates.blocks(casing.get())
                                .and(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                                .and(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(8))
                                .and(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(2)))
                        .where('B', GTLPredicates.RotorBlock())
                        .where('D', Predicates.blocks(gear.get()))
                        .where('C', Predicates.blocks(casing.get()).and(Predicates.blocks(GTLMachines.ROTOR_HATCH.getBlock()).setMaxGlobalLimited(1)))
                        .where('E', Predicates.abilities(PartAbility.OUTPUT_ENERGY).and(Predicates.abilities(PartAbility.SUBSTATION_OUTPUT_ENERGY)))
                        .where('M', Predicates.abilities(PartAbility.MUFFLER))
                        .build())
                .workableCasingModel(baseCasing, overlayModel)
                .register();
    }

    public static final MachineEntry<MultiblockMachineDefinition> ROCKET_LARGE_TURBINE = GTLGeneratorMachines.ROCKET_LARGE_TURBINE;

    public final static MachineEntry<MultiblockMachineDefinition> SUPERCRITICAL_STEAM_TURBINE = GTMachineUtils.registerLargeTurbine(REGISTRATE, "supercritical_steam_turbine", GTValues.IV,
            GTLRecipeTypes.SUPERCRITICAL_STEAM_TURBINE_FUELS,
            GTLBlocks.CASING_SUPERCRITICAL_TURBINE, GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX,
            GTLCore.id("block/supercritical_turbine_casing"),
            GTCEu.id("block/multiblock/generator/large_plasma_turbine"));

    public final static MachineEntry<MultiblockMachineDefinition> STEAM_MEGA_TURBINE = registerMegaTurbine("steam_mega_turbine", GTValues.EV, 32, GTRecipeTypes.STEAM_TURBINE_FUELS, GTBlocks.CASING_STEEL_TURBINE, GTBlocks.CASING_STEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_steel"), GTCEu.id("block/multiblock/generator/large_steam_turbine"));
    public final static MachineEntry<MultiblockMachineDefinition> GAS_MEGA_TURBINE = registerMegaTurbine("gas_mega_turbine", GTValues.IV, 32, GTRecipeTypes.GAS_TURBINE_FUELS, GTBlocks.CASING_STAINLESS_TURBINE, GTBlocks.CASING_STAINLESS_STEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_stainless_steel"), GTCEu.id("block/multiblock/generator/large_gas_turbine"));
    public final static MachineEntry<MultiblockMachineDefinition> ROCKET_MEGA_TURBINE = registerMegaTurbine("rocket_mega_turbine", GTValues.IV, 48, GTLRecipeTypes.ROCKET_ENGINE_FUELS, GTBlocks.CASING_TITANIUM_TURBINE, GTBlocks.CASING_STAINLESS_STEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_titanium"), GTCEu.id("block/multiblock/generator/large_gas_turbine"));
    public final static MachineEntry<MultiblockMachineDefinition> PLASMA_MEGA_TURBINE = registerMegaTurbine("plasma_mega_turbine", GTValues.LuV, 64, GTRecipeTypes.PLASMA_GENERATOR_FUELS, GTBlocks.CASING_TUNGSTENSTEEL_TURBINE, GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_tungstensteel"), GTCEu.id("block/multiblock/generator/large_plasma_turbine"));
    public final static MachineEntry<MultiblockMachineDefinition> SUPERCRITICAL_MEGA_STEAM_TURBINE = registerMegaTurbine("supercritical_mega_steam_turbine", GTValues.LuV, 128, GTLRecipeTypes.SUPERCRITICAL_STEAM_TURBINE_FUELS, GTLBlocks.CASING_SUPERCRITICAL_TURBINE, GTBlocks.CASING_TUNGSTENSTEEL_GEARBOX,
            GTLCore.id("block/supercritical_turbine_casing"), GTCEu.id("block/multiblock/generator/large_plasma_turbine"));

    public final static MachineEntry<MultiblockMachineDefinition> DYSON_SPHERE = REGISTRATE.multiblock("dyson_sphere", DysonSphereMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.DYSON_SPHERE_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.8"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.2"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.3"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.4"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.5"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.7"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.9"))
            .tooltips(Component.translatable("gtceu.machine.dyson_sphere.tooltip.6"))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.dyson_sphere")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier(DysonSphereMachine::recipeModifier)
            .appearanceBlock(() -> Registries.getBlock("kubejs:dyson_receiver_casing"))
            .pattern((definition) -> GeneratorMachineStructure.DYSON_SPHERE.get()
                    .where('O', Predicates.controller(Predicates.blocks(definition.getBlock())))
                    .where('I', Predicates.blocks(Registries.getBlock("kubejs:dyson_receiver_casing"))
                            .and(Predicates.abilities(PartAbility.IMPORT_ITEMS).setMaxGlobalLimited(1))
                            .and(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1))
                            .and(Predicates.abilities(PartAbility.INPUT_ENERGY).setMaxGlobalLimited(2))
                            .and(Predicates.abilities(PartAbility.COMPUTATION_DATA_RECEPTION).setMaxGlobalLimited(2))
                            .and(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setMaxGlobalLimited(1))
                            .and(Predicates.abilities(PartAbility.OUTPUT_LASER).setMaxGlobalLimited(1))
                            .and(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1)))
                    .where('A', Predicates.blocks(Registries.getBlock("kubejs:high_strength_concrete")))
                    .where('B', Predicates.blocks(Registries.getBlock("kubejs:dyson_control_casing")))
                    .where('C', Predicates.blocks(GCYMBlocks.HEAT_VENT.get()))
                    .where('D', Predicates.blocks(GTBlocks.CASING_PALLADIUM_SUBSTATION.get()))
                    .where('E', Predicates.blocks(Registries.getBlock("kubejs:dyson_control_toroid")))
                    .where('F', Predicates.blocks(Registries.getBlock("kubejs:spacetime_assembly_line_unit")))
                    .where('G', Predicates.blocks(Registries.getBlock("kubejs:hollow_casing")))
                    .where('H', Predicates.blocks(GTLBlocks.HYPER_MECHANICAL_CASING.get()))
                    .where('J', Predicates.blocks(Registries.getBlock("kubejs:dyson_deployment_casing")))
                    .where('K', Predicates.blocks(Registries.getBlock("kubejs:adamantine_coil_block")))
                    .where('L', Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTLMaterials.Vibranium)))
                    .where('M', Predicates.blocks(GTLBlocks.HYPER_CORE.get()))
                    .where('N', Predicates.blocks(GTLBlocks.ECHO_CASING.get()))
                    .where('P', Predicates.blocks(Registries.getBlock("kubejs:dyson_deployment_magnet")))
                    .where('Q', Predicates.blocks(GTLBlocks.POWER_MODULE_5.get()))
                    .where('R', Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTLMaterials.Adamantium)))
                    .where('S', Predicates.blocks(Registries.getBlock("kubejs:containment_field_generator")))
                    .where('T', Predicates.blocks(Registries.getBlock("kubejs:dyson_deployment_core")))
                    .where(' ', Predicates.any())
                    .build())
            .workableCasingModel(ResourceLocation.parse("kubejs:block/dyson_receiver_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MachineEntry<MultiblockMachineDefinition> LARGE_NAQUADAH_REACTOR = REGISTRATE.multiblock("large_naquadah_reactor", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.LARGE_NAQUADAH_REACTOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.large_naquadah_reactor")))
            .tooltips(Component.translatable("tooltip.gtlcore.structure.source", "GTNH"))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .generator(true)
            .recipeModifiers(GTLRecipeModifiers.GENERATOR_OC)
            .appearanceBlock(GTLBlocks.HYPER_MECHANICAL_CASING)
            .pattern((definition) -> GeneratorMachineStructure.LARGE_NAQUADAH_REACTOR.get()
                    .where('a', Predicates.controller(Predicates.blocks(definition.getBlock())))
                    .where('b', Predicates.blocks(GTLBlocks.HYPER_MECHANICAL_CASING.get())
                            .and(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(2))
                            .and(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .and(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setMaxGlobalLimited(1))
                            .and(Predicates.abilities(PartAbility.OUTPUT_LASER).setMaxGlobalLimited(1)))
                    .where('c', Predicates.blocks(GTBlocks.CASING_POLYTETRAFLUOROETHYLENE_PIPE.get()))
                    .where('d', Predicates.blocks(GCYMBlocks.HEAT_VENT.get()))
                    .where('e', Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Naquadria)))
                    .where('f', Predicates.blocks(Registries.getBlock("kubejs:neutronium_gearbox")))
                    .where(' ', Predicates.any())
                    .build())
            .workableCasingModel(GTLCore.id("block/casings/hyper_mechanical_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MachineEntry<MultiblockMachineDefinition> ADVANCED_HYPER_REACTOR = REGISTRATE.multiblock("advanced_hyper_reactor", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .allowExtendedFacing(false)
            .recipeType(GTLRecipeTypes.ADVANCED_HYPER_REACTOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.advanced_hyper_reactor.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.advanced_hyper_reactor.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.advanced_hyper_reactor")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .generator(true)
            .recipeModifiers(GTLPlasmaGeneratorModifiers.advanced(
                    () -> GTLMaterials.Starmetal.getFluid(FluidStorageKeys.PLASMA, 1),
                    () -> GTLMaterials.DenseNeutron.getFluid(FluidStorageKeys.PLASMA, 1)))
            .appearanceBlock(GTLBlocks.ENHANCE_HYPER_MECHANICAL_CASING)
            .pattern((definition) -> GeneratorMachineStructure.ADVANCED_HYPER_REACTOR.get()
                    .where('a', Predicates.controller(Predicates.blocks(definition.getBlock())))
                    .where('b', Predicates.blocks(GTLBlocks.ENHANCE_HYPER_MECHANICAL_CASING.get())
                            .and(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1))
                            .and(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .and(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setMaxGlobalLimited(1))
                            .and(Predicates.abilities(PartAbility.OUTPUT_LASER).setMaxGlobalLimited(1)))
                    .where('c', Predicates.blocks(GTLBlocks.HYPER_CORE.get()))
                    .where('e', Predicates.blocks(ChemicalHelper.getBlock(TagPrefix.frameGt, GTMaterials.Naquadria)))
                    .where(' ', Predicates.any())
                    .where('-', Predicates.air())
                    .build())
            .workableCasingModel(GTLCore.id("block/casings/enhance_hyper_mechanical_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public final static MachineEntry<MultiblockMachineDefinition> HYPER_REACTOR = REGISTRATE.multiblock("hyper_reactor", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTLRecipeTypes.HYPER_REACTOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.hyper_reactor.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.hyper_reactor.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.hyper_reactor.tooltip.2"))
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.multiblock.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.hyper_reactor")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .generator(true)
            .recipeModifiers(GTLPlasmaGeneratorModifiers.tiered(java.util.Map.of(
                    GTValues.V[GTValues.UEV], () -> GTLMaterials.Orichalcum.getFluid(FluidStorageKeys.PLASMA, 1),
                    GTValues.V[GTValues.UIV], () -> GTLMaterials.Enderium.getFluid(FluidStorageKeys.PLASMA, 1),
                    GTValues.V[GTValues.UXV], () -> GTLMaterials.Infuscolium.getFluid(FluidStorageKeys.PLASMA, 1),
                    GTValues.V[GTValues.OpV], () -> GTLMaterials.MetastableHassium.getFluid(FluidStorageKeys.PLASMA, 1))))
            .appearanceBlock(GTLBlocks.ENHANCE_HYPER_MECHANICAL_CASING)
            .pattern((definition) -> GeneratorMachineStructure.HYPER_REACTOR.get()
                    .where('a', Predicates.controller(Predicates.blocks(definition.getBlock())))
                    .where('b', Predicates.blocks(GTLBlocks.ENHANCE_HYPER_MECHANICAL_CASING.get())
                            .and(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(2))
                            .and(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .and(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setMaxGlobalLimited(1))
                            .and(Predicates.abilities(PartAbility.OUTPUT_LASER).setMaxGlobalLimited(1)))
                    .where('c', Predicates.blocks(GTLBlocks.HYPER_CORE.get()))
                    .where('d', Predicates.blocks(GTBlocks.FUSION_GLASS.get()))
                    .where(' ', Predicates.air())
                    .build())
            .workableCasingModel(GTLCore.id("block/casings/enhance_hyper_mechanical_casing"), GTCEu.id("block/multiblock/fusion_reactor"))
            .register();

    public static final MachineEntry<MultiblockMachineDefinition> GENERATOR_ARRAY = REGISTRATE.multiblock("generator_array", GeneratorArrayMachine::new)
            .rotationState(RotationState.ALL)
            .appearanceBlock(GTBlocks.CASING_STEEL_SOLID)
            .recipeType(GTRecipeTypes.DUMMY_RECIPES)
            .generator(true)
            .modelProperty(com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.RECIPE_LOGIC_STATUS,
                    com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic.Status.IDLE)
            .tooltips(Component.translatable("gtceu.machine.generator_array.tooltip.0"))
            .tooltips(Component.translatable("gtceu.machine.generator_array.tooltip.1"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_6.tooltip",
                    Component.translatable("gtceu.steam_turbine"),
                    Component.translatable("gtceu.combustion_generator"),
                    Component.translatable("gtceu.gas_turbine"),
                    Component.translatable("gtceu.semi_fluid_generator"),
                    Component.translatable("gtceu.rocket_engine"),
                    Component.translatable("gtceu.naquadah_reactor")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .recipeModifier(GeneratorArrayMachine::recipeModifier, true)
            .pattern(definition -> GeneratorMachineStructure.HYPER_REACTOR_2.get()
                    .where('S', Predicates.controller(blocks(definition.getBlock())))
                    .where('X', blocks(GTBlocks.CASING_STEEL_SOLID.get())
                            .and(Predicates.abilities(PartAbility.IMPORT_FLUIDS).setMaxGlobalLimited(1))
                            .and(Predicates.abilities(PartAbility.OUTPUT_ENERGY).setExactLimit(1))
                            .and(Predicates.abilities(PartAbility.MAINTENANCE).setExactLimit(1))
                            .and(Predicates.abilities(PartAbility.EXPORT_FLUIDS).setMaxGlobalLimited(1)))
                    .where('C', blocks(GTBlocks.CASING_TEMPERED_GLASS.get()))
                    .where('#', Predicates.air())
                    .build())
            .workableCasingModel(GTCEu.id("block/casings/solid/machine_casing_solid_steel"), GTCEu.id("block/multiblock/processing_array"))
            .register();

    public final static MachineEntry<MultiblockMachineDefinition> ANNIHILATE_GENERATOR = REGISTRATE.multiblock("annihilate_generator", WorkableElectricMultiblockMachine::new)
            .rotationState(RotationState.ALL)
            .recipeType(GTLRecipeTypes.ANNIHILATE_GENERATOR_RECIPES)
            .tooltips(Component.translatable("gtceu.machine.perfect_oc"))
            .tooltips(Component.translatable("gtceu.multiblock.only.laser.tooltip"))
            .tooltips(Component.translatable("gtceu.machine.available_recipe_map_1.tooltip",
                    Component.translatable("gtceu.annihilate_generator")))
            .tooltipBuilder(GTLMachines.GTL_ADD)
            .generator(true)
            .recipeModifiers(GTLRecipeModifiers.GENERATOR_OC)
            .appearanceBlock(GTBlocks.HIGH_POWER_CASING)
            .pattern(definition -> GeneratorMachineStructure.ANNIHILATE_GENERATOR.get()
                    .where('~', Predicates.controller(Predicates.blocks(definition.getBlock())))
                    .where('A', Predicates.blocks(GTLBlocks.GRAVITON_FIELD_CONSTRAINT_CASING.get()))
                    .where('B', Predicates.blocks(Registries.getBlock("kubejs:annihilate_core")))
                    .where('C', Predicates.blocks(GTLBlocks.HYPER_MECHANICAL_CASING.get()))
                    .where('D', Predicates.blocks(Registries.getBlock("kubejs:hollow_casing")))
                    .where('E', Predicates.blocks(GTLBlocks.NAQUADAH_ALLOY_CASING.get()))
                    .where('F', Predicates.blocks(GTBlocks.FUSION_GLASS.get()))
                    .where('G', Predicates.blocks(Registries.getBlock("kubejs:dyson_control_toroid")))
                    .where('H', Predicates.blocks(GTLBlocks.RHENIUM_REINFORCED_ENERGY_GLASS.get()))
                    .where('P', Predicates.blocks(Registries.getBlock("kubejs:dyson_control_casing")))
                    .where('S', Predicates.blocks(GTBlocks.HIGH_POWER_CASING.get())
                            .and(Predicates.abilities(PartAbility.OUTPUT_LASER))
                            .and(Predicates.abilities(PartAbility.IMPORT_ITEMS))
                            .and(Predicates.abilities(PartAbility.EXPORT_ITEMS)))
                    .where('T', Predicates.blocks(GTLBlocks.DEGENERATE_RHENIUM_CONSTRAINED_CASING.get()))
                    .where('R', Predicates.blocks(Registries.getBlock("kubejs:dyson_receiver_casing")))
                    .where(' ', Predicates.any())
                    .build())
            .modelProperty(GTMachineModelProperties.RECIPE_LOGIC_STATUS, RecipeLogic.Status.IDLE)
            .model(GTLAnimationModels.annihilateGenerator())
            .hasBER(true)
            .register();
}
