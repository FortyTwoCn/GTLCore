package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.api.machine.PerformanceMonitorMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.GTLPartAbility;
import org.gtlcore.gtlcore.common.data.machines.*;
import org.gtlcore.gtlcore.common.machine.VirtualIngredientSupplyMachine;
import org.gtlcore.gtlcore.common.machine.generator.LightningRodMachine;
import org.gtlcore.gtlcore.common.machine.generator.MagicEnergyMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.CoilWorkableElectricMultipleRecipesMultiblockMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.*;
import org.gtlcore.gtlcore.integration.wildcard.WildcardPatternCompat;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.common.mui.GTSingleblockMachinePanels;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.PanelSyncManager;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import org.gtlcore.gtlcore.common.data.machines.structure.DirtForgeStructure;
import com.gregtechceu.gtceu.client.util.TooltipHelper;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.common.registry.GTRegistration;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEInputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEOutputHatchPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingBusPartMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.MEStockingHatchPartMachine;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import com.lowdragmc.lowdraglib.utils.FluidHelper;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import com.hepdd.gtmthings.GTMThings;
import com.hepdd.gtmthings.common.registry.GTMTRegistration;
import com.hepdd.gtmthings.data.CreativeModeTabs;
import com.hepdd.gtmthings.data.WirelessMachines;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.GTValues.LV;
import static com.gregtechceu.gtceu.api.GTValues.LuV;
import static com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.ALL_TIERS;
import static com.gregtechceu.gtceu.common.data.GTMachines.*;
import static com.gregtechceu.gtceu.common.data.machines.GTMachineUtils.*;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

@SuppressWarnings("unused")
public class GTLMachines {

    public static final BiConsumer<ItemStack, List<Component>> GTL_MODIFY = GTLRegistryTooltips.MODIFY;

    public static final BiConsumer<ItemStack, List<Component>> GTL_ADD = GTLRegistryTooltips.ADD;

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> CHEMICAL_PLANT_DISPLAY = GTLMultiblockDisplays.CHEMICAL_PLANT;

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> MAX_TEMPERATURE = GTLMultiblockDisplays.MAX_TEMPERATURE;

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> TEMPERATURE = GTLMultiblockDisplays.TEMPERATURE;

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> COIL_PARALLEL = GTLMultiblockDisplays.COIL_PARALLEL;

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> MULTIPLERECIPES_COIL_PARALLEL = GTLMultiblockDisplays.MULTIPLERECIPES_COIL_PARALLEL;

    public static final BiFunction<MultiblockControllerMachine, PanelSyncManager, List<IWidget>> PROCESSING_PLANT_PARALLEL = GTLMultiblockDisplays.PROCESSING_PLANT_PARALLEL;

    public static void init() {
        GeneratorMachine.init();
        MultiBlockMachineA.init();
        AdvancedMultiBlockMachineA.init();
        AdvancedMultiBlockMachineB.init();
        AdditionalMultiBlockMachine.init();
        GTAEMachines.init();
    }

    static {
        REGISTRATE.creativeModeTab(() -> GTCreativeModeTabs.MACHINE);
    }

    public static final Supplier<MultiblockPatternBuilder> DTPF = DirtForgeStructure.DTPF;

    //////////////////////////////////////
    // *** Simple Machine ***//
    //////////////////////////////////////
    public static final MachineEntry<MachineDefinition>[] SEMI_FLUID_GENERATOR = GTLGeneratorMachines.SEMI_FLUID_GENERATOR;
    public static final MachineEntry<MachineDefinition>[] ROCKET_ENGINE_GENERATOR = GTLGeneratorMachines.ROCKET_ENGINE_GENERATOR;
    public static final MachineEntry<MachineDefinition>[] NAQUADAH_REACTOR_GENERATOR = GTLGeneratorMachines.NAQUADAH_REACTOR_GENERATOR;

    public static final MachineEntry<MachineDefinition>[] LIGHTNING_PROCESSOR = new GTMachineUtils.SimpleMachineBuilder(REGISTRATE, "lightning_processor", GTLRecipeTypes.LIGHTNING_PROCESSOR_RECIPES).register();

    public static final MachineEntry<MachineDefinition>[] DEHYDRATOR = new GTMachineUtils.SimpleMachineBuilder(REGISTRATE, "dehydrator", GTLRecipeTypes.DEHYDRATOR_RECIPES).register();

    public static final MachineEntry<MachineDefinition>[] WORLD_DATA_SCANNER = new GTMachineUtils.SimpleMachineBuilder(REGISTRATE, "world_data_scanner", GTLRecipeTypes.WORLD_DATA_SCANNER_RECIPES)
            .tankScalingFunction(tier -> 64000).register();

    public static final MachineEntry<MachineDefinition>[] NEUTRON_COMPRESSOR = new GTMachineUtils.SimpleMachineBuilder(REGISTRATE, "neutron_compressor", GTLRecipeTypes.NEUTRON_COMPRESSOR_RECIPES)
            .tiers(GTValues.MAX).register();

    public static final MachineEntry<MachineDefinition>[] FRAGMENT_WORLD_COLLECTION_MACHINE = GTMachineUtils.registerTieredMachines(REGISTRATE, "fragment_world_collection_machine",
            (holder, tier) -> new SimpleTieredMachine(holder, tier, largeTankSizeFunction), (tier, builder) -> builder
                    .ui(GTSingleblockMachinePanels.GENERAL_MACHINE)
                    .rotationState(RotationState.NON_Y_AXIS)
                    .recipeType(GTLRecipeTypes.FRAGMENT_WORLD_COLLECTION)
                    .workableTieredHullModel(GTCEu.id("block/machines/fragment_world_collection_machine"))
                    .tooltips(Component.translatable("gtlcore.machine.fragment_world_collection.tooltip"))
                    .onRegister(GTLCompassLinks.node("machines", "fragment_world_collection"))
                    .register(),
            ULV);

    public static final MachineEntry<MachineDefinition>[] LIGHTNING_ROD = GTMachineUtils.registerTieredMachines(
            REGISTRATE, "lightning_rod",
            LightningRodMachine::new,
            (tier, builder) -> builder
                    .langValue("%s Lightning Rod %s".formatted(GTValues.VLVH[tier], GTValues.VLVT[tier]))
                    .rotationState(RotationState.NON_Y_AXIS)
                    .simpleGeneratorModel(GTCEu.id("block/generators/lightning_rod"))
                    .tooltips(Component.translatable("gtceu.machine.lightning_rod.tooltip.0"))
                    .tooltips(Component.translatable("gtceu.machine.lightning_rod.tooltip.1"))
                    .tooltips(Component.translatable("gtceu.machine.lightning_rod.tooltip.2"))
                    .tooltips(Component.translatable("gtceu.universal.tooltip.ampere_out", 512))
                    .tooltips(Component.translatable("gtceu.universal.tooltip.voltage_out",
                            FormattingUtil.formatNumbers(GTValues.V[tier - 1]), GTValues.VNF[tier - 1]))
                    .tooltips(Component.translatable("gtceu.universal.tooltip.energy_storage_capacity",
                            FormattingUtil.formatNumbers((long) (48828 * Math.pow(4, tier)))))
                    .tooltipBuilder(GTL_ADD)
                    .register(),
            GTValues.EV, GTValues.IV, GTValues.LuV);

    public static final MachineEntry<MachineDefinition>[] PRIMITIVE_MAGIC_ENERGY = GTMachineUtils.registerTieredMachines(
            REGISTRATE, "primitive_magic_energy",
            MagicEnergyMachine::new,
            (tier, builder) -> builder
                    .langValue("%s Primitive Magic Energy %s".formatted(GTValues.VLVH[tier], GTValues.VLVT[tier]))
                    .rotationState(RotationState.NON_Y_AXIS)
                    .simpleGeneratorModel(GTCEu.id("block/generators/primitive_magic_energy"))
                    .tooltips(Component.translatable("gtceu.machine.primitive_magic_energy.tooltip.0"))
                    .tooltips(Component.translatable("gtceu.universal.tooltip.ampere_out", 16))
                    .tooltips(Component.translatable("gtceu.universal.tooltip.voltage_out",
                            FormattingUtil.formatNumbers(GTValues.V[tier]), GTValues.VNF[tier]))
                    .tooltips(Component.translatable("gtceu.universal.tooltip.energy_storage_capacity",
                            FormattingUtil.formatNumbers(GTValues.V[tier] * 512L)))
                    .tooltipBuilder(GTL_ADD)
                    .register(),
            GTValues.ULV, GTValues.LV);

    private static MachineEntry<MachineDefinition>[] registerHugeFluidHatches(String name, String displayname, String model,
                                                                String tooltip, IO io, PartAbility... abilities) {
        return GTMachineUtils.registerTieredMachines(REGISTRATE, name,
                (holder, tier) -> new HugeFluidHatchPartMachine(holder, tier, io),
                (tier, builder) -> {
                    builder.langValue(GTValues.VNF[tier] + ' ' + displayname)
                            .rotationState(RotationState.ALL)
                            .colorOverlayTieredHullModel(model)
                            .abilities(abilities)
                            .onRegister(GTLCompassLinks.node("parts", "fluid_hatch"))
                            .tooltips(Component.translatable("gtceu.machine." + tooltip + ".tooltip"));
                    builder.tooltips(Component.translatable("gtceu.universal.tooltip.fluid_storage_capacity_mult",
                            tier, FormattingUtil.formatNumbers(Integer.MAX_VALUE)))
                            .tooltipBuilder(GTL_ADD);
                    return builder.register();
                },
                GTValues.tiersBetween(GTValues.LV, GTValues.OpV));
    }

    //////////////////////////////////////
    // ********** Part **********//
    //////////////////////////////////////
    public static final MachineEntry<MachineDefinition> LARGE_STEAM_HATCH = REGISTRATE
            .machine("large_steam_input_hatch", holder -> new LargeSteamHatchPartMachine(holder, IO.IN, 8192, false))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.STEAM)
            .overlaySteamHullModel("steam_hatch")
            .tooltips(Component.translatable("gtceu.machine.large_steam_input_hatch.tooltip.0"),
                    Component.translatable("gtceu.universal.tooltip.fluid_storage_capacity",
                            16384 * FluidHelper.getBucket()),
                    Component.translatable("gtceu.machine.steam.steam_hatch.tooltip"))
            .tooltipBuilder(GTL_ADD)
            .onRegister(GTLCompassLinks.node("steam", "steam_hatch"))
            .register();

    public static final MachineEntry<MachineDefinition> STERILE_CLEANING_MAINTENANCE_HATCH = REGISTRATE
            .machine("sterile_cleaning_maintenance_hatch",
                    holder -> new GTLCleaningMaintenanceHatchPartMachine(holder, ICleaningRoom.STERILE_DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.0"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.1"))
            .tooltipBuilder((stack, tooltips) -> {
                for (CleanroomType type : ICleaningRoom
                        .getCleanroomTypes(ICleaningRoom.STERILE_DUMMY_CLEANROOM)) {
                    tooltips.add(Component.literal("  ")
                            .append(Component.translatable(type.getTranslationKey())
                                    .withStyle(ChatFormatting.GREEN)));
                }
                GTL_ADD.accept(stack, tooltips);
            })
            .tier(7).overlayTieredHullModel(GTLCore.id("block/machine/part/native_sterile_cleaning_maintenance"))
            .register();

    public static final MachineEntry<MachineDefinition> LAW_CLEANING_MAINTENANCE_HATCH = REGISTRATE
            .machine("law_cleaning_maintenance_hatch",
                    holder -> new GTLCleaningMaintenanceHatchPartMachine(holder, ICleaningRoom.LAW_DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.0"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.1"))
            .tooltipBuilder((stack, tooltips) -> {
                for (CleanroomType type : ICleaningRoom
                        .getCleanroomTypes(ICleaningRoom.LAW_DUMMY_CLEANROOM)) {
                    tooltips.add(Component.literal("  ")
                            .append(Component.translatable(type.getTranslationKey())
                                    .withStyle(ChatFormatting.GREEN)));
                }
                GTL_ADD.accept(stack, tooltips);
            })
            .tier(10).overlayTieredHullModel(GTLCore.id("block/machine/part/native_law_cleaning_maintenance"))
            .register();

    public static final MachineEntry<MachineDefinition> AUTO_CONFIGURATION_MAINTENANCE_HATCH = REGISTRATE
            .machine("auto_configuration_maintenance_hatch", AutoConfigurationMaintenanceHatchPartMachine::new)
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"))
            .tooltipBuilder(GTL_ADD)
            .tier(5).overlayTieredHullModel(GTCEu.id("block/machine/part/auto_maintenance_hatch"))
            .register();

    public static final MachineEntry<MachineDefinition> CLEANING_CONFIGURATION_MAINTENANCE_HATCH = REGISTRATE
            .machine("cleaning_configuration_maintenance_hatch",
                    holder -> new CleaningConfigurationMaintenanceHatchPartMachine(holder, ICleaningRoom.DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.0"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.1"))
            .tooltipBuilder((stack, tooltips) -> {
                for (CleanroomType type : ICleaningRoom
                        .getCleanroomTypes(ICleaningRoom.DUMMY_CLEANROOM)) {
                    tooltips.add(Component.literal("  ")
                            .append(Component.translatable(type.getTranslationKey())
                                    .withStyle(ChatFormatting.GREEN)));
                }
                GTL_ADD.accept(stack, tooltips);
            })
            .tier(5).overlayTieredHullModel(GTCEu.id("block/machine/part/cleaning_maintenance_hatch"))
            .register();

    public static final MachineEntry<MachineDefinition> STERILE_CONFIGURATION_CLEANING_MAINTENANCE_HATCH = REGISTRATE
            .machine("sterile_configuration_cleaning_maintenance_hatch",
                    holder -> new CleaningConfigurationMaintenanceHatchPartMachine(holder, ICleaningRoom.STERILE_DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.0"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.1"))
            .tooltipBuilder((stack, tooltips) -> {
                for (CleanroomType type : ICleaningRoom
                        .getCleanroomTypes(ICleaningRoom.STERILE_DUMMY_CLEANROOM)) {
                    tooltips.add(Component.literal("  ")
                            .append(Component.translatable(type.getTranslationKey())
                                    .withStyle(ChatFormatting.GREEN)));
                }
                GTL_ADD.accept(stack, tooltips);
            })
            .tier(9).overlayTieredHullModel(GTLCore.id("block/machine/part/native_sterile_cleaning_maintenance"))
            .register();

    public static final MachineEntry<MachineDefinition> LAW_CONFIGURATION_CLEANING_MAINTENANCE_HATCH = REGISTRATE
            .machine("law_configuration_cleaning_maintenance_hatch",
                    holder -> new CleaningConfigurationMaintenanceHatchPartMachine(holder, ICleaningRoom.LAW_DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.0"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.1"))
            .tooltipBuilder((stack, tooltips) -> {
                for (CleanroomType type : ICleaningRoom
                        .getCleanroomTypes(ICleaningRoom.LAW_DUMMY_CLEANROOM)) {
                    tooltips.add(Component.literal("  ")
                            .append(Component.translatable(type.getTranslationKey())
                                    .withStyle(ChatFormatting.GREEN)));
                }
                GTL_ADD.accept(stack, tooltips);
            })
            .tier(12).overlayTieredHullModel(GTLCore.id("block/machine/part/native_law_cleaning_maintenance"))
            .register();

    public static final MachineEntry<MachineDefinition> GRAVITY_HATCH = REGISTRATE
            .machine("gravity_hatch", holder -> new GravityCleaningConfigurationMaintenancePartMachine(holder, false))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"))
            .tooltipBuilder(GTL_ADD)
            .tier(8).overlayTieredHullModel(GTCEu.id("block/machine/part/auto_maintenance_hatch"))
            .register();

    public static final MachineEntry<MachineDefinition> GRAVITY_CONFIGURATION_HATCH = REGISTRATE
            .machine("gravity_configuration_hatch", GravityCleaningConfigurationMaintenancePartMachine::new)
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"))
            .tooltipBuilder(GTL_ADD)
            .tier(9).overlayTieredHullModel(GTCEu.id("block/machine/part/auto_maintenance_hatch"))
            .register();

    public static final MachineEntry<MachineDefinition> CLEANING_GRAVITY_CONFIGURATION_MAINTENANCE_HATCH = REGISTRATE
            .machine("cleaning_gravity_configuration_maintenance_hatch", holder -> new GravityCleaningConfigurationMaintenancePartMachine(holder, ICleaningRoom.DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.0"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.1"))
            .tooltipBuilder((stack, tooltips) -> {
                for (CleanroomType type : ICleaningRoom
                        .getCleanroomTypes(ICleaningRoom.DUMMY_CLEANROOM)) {
                    tooltips.add(Component.literal("  ")
                            .append(Component.translatable(type.getTranslationKey())
                                    .withStyle(ChatFormatting.GREEN)));
                }
                GTL_ADD.accept(stack, tooltips);
            })
            .tier(10).overlayTieredHullModel(GTCEu.id("block/machine/part/cleaning_maintenance_hatch"))
            .register();

    public static final MachineEntry<MachineDefinition> STERILE_CLEANING_GRAVITY_CONFIGURATION_MAINTENANCE_HATCH = REGISTRATE
            .machine("sterile_cleaning_gravity_configuration_maintenance_hatch", holder -> new GravityCleaningConfigurationMaintenancePartMachine(holder, ICleaningRoom.STERILE_DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.0"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.1"))
            .tooltipBuilder((stack, tooltips) -> {
                for (CleanroomType type : ICleaningRoom
                        .getCleanroomTypes(ICleaningRoom.STERILE_DUMMY_CLEANROOM)) {
                    tooltips.add(Component.literal("  ")
                            .append(Component.translatable(type.getTranslationKey())
                                    .withStyle(ChatFormatting.GREEN)));
                }
                GTL_ADD.accept(stack, tooltips);
            })
            .tier(11).overlayTieredHullModel(GTCEu.id("block/machine/part/cleaning_maintenance_hatch"))
            .register();

    public static final MachineEntry<MachineDefinition> LAW_CLEANING_GRAVITY_CONFIGURATION_MAINTENANCE_HATCH = REGISTRATE
            .machine("law_cleaning_gravity_configuration_maintenance_hatch", holder -> new GravityCleaningConfigurationMaintenancePartMachine(holder, ICleaningRoom.LAW_DUMMY_CLEANROOM))
            .rotationState(RotationState.ALL)
            .abilities(PartAbility.MAINTENANCE)
            .tooltips(Component.translatable("gtceu.universal.disabled"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.0"),
                    Component.translatable("gtceu.machine.maintenance_hatch_cleanroom_auto.tooltip.1"))
            .tooltipBuilder((stack, tooltips) -> {
                for (CleanroomType type : ICleaningRoom
                        .getCleanroomTypes(ICleaningRoom.LAW_DUMMY_CLEANROOM)) {
                    tooltips.add(Component.literal("  ")
                            .append(Component.translatable(type.getTranslationKey())
                                    .withStyle(ChatFormatting.GREEN)));
                }
                GTL_ADD.accept(stack, tooltips);
            })
            .tier(12).overlayTieredHullModel(GTCEu.id("block/machine/part/cleaning_maintenance_hatch"))
            .register();

    public static final MachineEntry<MachineDefinition> WIRELESS_DATA_HATCH_TRANSMITTER = REGISTRATE
            .machine("wireless_data_transmitter_hatch", (holder) -> new WirelessOpticalDataHatchMachine(holder, true))
            .rotationState(RotationState.ALL)
            .onRegister(GTLCompassLinks.self("parts"))
            .abilities(PartAbility.OPTICAL_DATA_TRANSMISSION)
            .overlayTieredHullModel("optical_data_hatch")
            .tooltips(Component.translatable("gtceu.machine.wireless_data_transmitter_hatch.tooltip.1"),
                    Component.translatable("gtceu.machine.wireless_data_transmitter_hatch.tooltip.2"))
            .tier(LuV)
            .register();

    public static final MachineEntry<MachineDefinition> WIRELESS_DATA_HATCH_RECEIVER = REGISTRATE
            .machine("wireless_data_receiver_hatch", (holder) -> new WirelessOpticalDataHatchMachine(holder, false))
            .rotationState(RotationState.ALL)
            .onRegister(GTLCompassLinks.self("parts"))
            .abilities(PartAbility.OPTICAL_DATA_RECEPTION)
            .overlayTieredHullModel("optical_data_hatch")
            .tooltips(Component.translatable("gtceu.machine.wireless_data_receiver_hatch.tooltip.1"),
                    Component.translatable("gtceu.machine.wireless_data_receiver_hatch.tooltip.2"))
            .tier(LuV)
            .register();

    public static final MachineEntry<MachineDefinition> PERFORMANCE_MONITOR = REGISTRATE
            .machine("performance_monitor", PerformanceMonitorMachine::new)
            .rotationState(RotationState.NON_Y_AXIS)
            .onRegister(GTLCompassLinks.self("parts"))
            .workableTieredHullModel(GTMThings.id("block/machines/wireless_energy_monitor"))
            .tier(LV)
            .tooltips(Component.translatable("block.gtlcore.performance_monitor.tooltip"))
            .tooltipBuilder(GTL_ADD)
            .register();

    public static final MachineEntry<MachineDefinition> VIRTUAL_INGREDIENT_SUPPLY_MACHINE = REGISTRATE
            .machine("virtual_ingredient_supply_machine", VirtualIngredientSupplyMachine::new)
            .rotationState(RotationState.ALL)
            .tier(LuV)
            .tier(LuV).colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch_proxy"))
            // Deliberately not under the block's own descriptionId: GTCEu's TooltipsHandler auto-appends every
            // "<descriptionId>.tooltip.N" it finds for anything named block.gtceu.*, so keeping them there would
            // print the whole block twice.
            .tooltips(Component.translatable("block.gtlcore.virtual_ingredient_supply_machine.tooltip.0"),
                    Component.translatable("block.gtlcore.virtual_ingredient_supply_machine.tooltip.1"),
                    Component.translatable("block.gtlcore.virtual_ingredient_supply_machine.tooltip.2"))
            .tooltipBuilder(GTL_ADD)
            .register();

    public final static MachineEntry<MachineDefinition>[] HUGE_FLUID_IMPORT_HATCH = registerHugeFluidHatches("huge_input_hatch", "Huge Input Hatch", "overlay_fluid_hatch_input", "fluid_hatch.import", IO.IN, PartAbility.IMPORT_FLUIDS);

    public final static MachineEntry<MachineDefinition>[] HUGE_FLUID_EXPORT_HATCH = registerHugeFluidHatches("huge_output_hatch", "Huge Output Hatch", "overlay_fluid_hatch_output", "fluid_hatch.export", IO.OUT, PartAbility.EXPORT_FLUIDS);

    public static final MachineEntry<MachineDefinition>[] LASER_INPUT_HATCH_16384 = GTMachineUtils.registerLaserHatch(REGISTRATE, IO.IN, 16384,
            PartAbility.INPUT_LASER);
    public static final MachineEntry<MachineDefinition>[] LASER_OUTPUT_HATCH_16384 = GTMachineUtils.registerLaserHatch(REGISTRATE, IO.OUT, 16384,
            PartAbility.OUTPUT_LASER);
    public static final MachineEntry<MachineDefinition>[] LASER_INPUT_HATCH_65536 = GTMachineUtils.registerLaserHatch(REGISTRATE, IO.IN, 65536,
            PartAbility.INPUT_LASER);
    public static final MachineEntry<MachineDefinition>[] LASER_OUTPUT_HATCH_65536 = GTMachineUtils.registerLaserHatch(REGISTRATE, IO.OUT, 65536,
            PartAbility.OUTPUT_LASER);
    public static final MachineEntry<MachineDefinition>[] LASER_INPUT_HATCH_262144 = GTMachineUtils.registerLaserHatch(REGISTRATE, IO.IN, 262144,
            PartAbility.INPUT_LASER);
    public static final MachineEntry<MachineDefinition>[] LASER_OUTPUT_HATCH_262144 = GTMachineUtils.registerLaserHatch(REGISTRATE, IO.OUT, 262144,
            PartAbility.OUTPUT_LASER);
    public static final MachineEntry<MachineDefinition>[] LASER_INPUT_HATCH_1048576 = GTMachineUtils.registerLaserHatch(REGISTRATE, IO.IN, 1048576,
            PartAbility.INPUT_LASER);
    public static final MachineEntry<MachineDefinition>[] LASER_OUTPUT_HATCH_1048576 = GTMachineUtils.registerLaserHatch(REGISTRATE, IO.OUT, 1048576,
            PartAbility.OUTPUT_LASER);
    public static final MachineEntry<MachineDefinition>[] LASER_INPUT_HATCH_4194304 = GTMachineUtils.registerLaserHatch(REGISTRATE, IO.IN, 4194304,
            PartAbility.INPUT_LASER);
    public static final MachineEntry<MachineDefinition>[] LASER_OUTPUT_HATCH_4194304 = GTMachineUtils.registerLaserHatch(REGISTRATE, IO.OUT, 4194304,
            PartAbility.OUTPUT_LASER);

    static {
        GTMTRegistration.GTMTHINGS_REGISTRATE.creativeModeTab(() -> CreativeModeTabs.WIRELESS_TAB);
    }

    public static final MachineEntry<MachineDefinition>[] WIRELESS_ENERGY_INPUT_HATCH_64A = WirelessMachines.registerWirelessEnergyHatch(IO.IN, 64, PartAbility.INPUT_ENERGY, GTValues.tiersBetween(GTValues.EV, GTValues.MAX));
    public static final MachineEntry<MachineDefinition>[] WIRELESS_ENERGY_OUTPUT_HATCH_64A = WirelessMachines.registerWirelessEnergyHatch(IO.OUT, 64, PartAbility.OUTPUT_ENERGY, GTValues.tiersBetween(GTValues.EV, GTValues.MAX));

    public static final MachineEntry<MachineDefinition>[] WIRELESS_ENERGY_INPUT_HATCH_262144A = WirelessMachines.registerWirelessLaserHatch(IO.IN, 262144, PartAbility.INPUT_LASER, WirelessMachines.WIRELL_ENERGY_HIGH_TIERS);
    public static final MachineEntry<MachineDefinition>[] WIRELESS_ENERGY_INPUT_HATCH_1048576A = WirelessMachines.registerWirelessLaserHatch(IO.IN, 1048576, PartAbility.INPUT_LASER, WirelessMachines.WIRELL_ENERGY_HIGH_TIERS);
    public static final MachineEntry<MachineDefinition>[] WIRELESS_ENERGY_INPUT_HATCH_4194304A = WirelessMachines.registerWirelessLaserHatch(IO.IN, 4194304, PartAbility.INPUT_LASER, WirelessMachines.WIRELL_ENERGY_HIGH_TIERS);
    public static final MachineEntry<MachineDefinition>[] WIRELESS_ENERGY_OUTPUT_HATCH_262144A = WirelessMachines.registerWirelessLaserHatch(IO.OUT, 262144, PartAbility.OUTPUT_LASER, WirelessMachines.WIRELL_ENERGY_HIGH_TIERS);
    public static final MachineEntry<MachineDefinition>[] WIRELESS_ENERGY_OUTPUT_HATCH_1048576A = WirelessMachines.registerWirelessLaserHatch(IO.OUT, 1048576, PartAbility.OUTPUT_LASER, WirelessMachines.WIRELL_ENERGY_HIGH_TIERS);
    public static final MachineEntry<MachineDefinition>[] WIRELESS_ENERGY_OUTPUT_HATCH_4194304A = WirelessMachines.registerWirelessLaserHatch(IO.OUT, 4194304, PartAbility.OUTPUT_LASER, WirelessMachines.WIRELL_ENERGY_HIGH_TIERS);

    public static final MachineEntry<MachineDefinition>[] NEUTRON_ACCELERATOR = GTMachineUtils.registerTieredMachines(REGISTRATE, "neutron_accelerator",
            NeutronAcceleratorPartMachine::new,
            (tier, builder) -> builder
                    .langValue(GTValues.VNF[tier] + "Neutron Accelerator")
                    .rotationState(RotationState.ALL)
                    .abilities(GTLPartAbility.NEUTRON_ACCELERATOR)
                    .tooltips(Component.translatable("gtceu.universal.tooltip.max_voltage_in", GTValues.V[tier], GTValues.VNF[tier]),
                            Component.translatable("gtceu.machine.neutron_accelerator.tooltip.0", GTValues.V[tier] * 8 / 10),
                            Component.translatable("gtceu.machine.neutron_accelerator.tooltip.1"),
                            Component.translatable("gtceu.universal.tooltip.energy_storage_capacity", 2 * GTValues.V[tier]))
                    .tooltipBuilder(GTL_ADD)
                    .overlayTieredHullModel("neutron_accelerator")
                    .onRegister(GTLCompassLinks.node("parts", "neutron_accelerator"))
                    .register(),
            GTMachineUtils.ALL_TIERS);

    public final static MachineEntry<MachineDefinition> NEUTRON_SENSOR = REGISTRATE
            .machine("neutron_sensor", NeutronSensorPartMachine::new)
            .langValue("Neutron Sensor")
            .tier(GTValues.IV)
            .rotationState(RotationState.ALL)
            .tooltips(Component.translatable("gtceu.machine.neutron_sensor.tooltip.0"))
            .tooltipBuilder(GTL_ADD)
            .overlayTieredHullModel("neutron_sensor")
            .register();

    public final static MachineEntry<MachineDefinition> HEAT_SENSOR = REGISTRATE
            .machine("heat_sensor", TemperatureSensorPartMachine::new)
            .langValue("Temperature Sensor")
            .tier(GTValues.UXV)
            .rotationState(RotationState.ALL)
            .tooltips(Component.translatable("gtceu.machine.heat_sensor.tooltip.0"))
            .tooltipBuilder(GTL_ADD)
            .overlayTieredHullModel("neutron_sensor")
            .register();

    public static final MachineEntry<MachineDefinition> ROTOR_HATCH = REGISTRATE
            .machine("rotor_hatch", RotorHatchPartMachine::new)
            .langValue("Rotor Hatch")
            .tier(GTValues.EV)
            .rotationState(RotationState.ALL)
            .overlayTieredHullModel("rotor_hatch")
            .register();

    public static final MachineEntry<MachineDefinition> BLOCK_BUS = REGISTRATE
            .machine("block_bus", BlockBusPartMachine::new)
            .tier(GTValues.LuV)
            .rotationState(RotationState.ALL)
            .tier(GTValues.LuV).colorOverlayTieredHullModel(GTCEu.id("block/overlay/machine/overlay_item_hatch"), GTCEu.id("block/overlay/machine/overlay_pipe_in"), null)
            .register();

    public static final MachineEntry<MachineDefinition> TAG_FILTER_ME_STOCK_BUS_PART_MACHINE = REGISTRATE
            .machine("tag_filter_me_stock_bus_part_machine", TagFilterMEStockBusPartMachine::new)
            .tier(LuV)
            .abilities(PartAbility.IMPORT_ITEMS)
            .rotationState(RotationState.ALL)
            .tier(LuV).colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_bus"))
            .tooltips(
                    Component.translatable("gtceu.machine.item_bus.import.tooltip"),
                    Component.translatable("gtceu.machine.me.item_import.tooltip"),
                    Component.translatable("gtceu.machine.me.copy_paste.tooltip"),
                    Component.translatable("gtceu.universal.enabled"))
            .onRegister(GTLCompassLinks.node("parts", "item_bus"))
            .register();

    public static final MachineEntry<MachineDefinition> TAG_FILTER_ME_STOCK_HATCH_PART_MACHINE = REGISTRATE
            .machine("tag_filter_me_stock_hatch_part_machine", TagFilterMEStockHatchPartMachine::new)
            .tier(LuV)
            .abilities(PartAbility.IMPORT_FLUIDS)
            .rotationState(RotationState.ALL)
            .tier(LuV).colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_hatch"))
            .tooltips(
                    Component.translatable("gtceu.machine.fluid_hatch.import.tooltip"),
                    Component.translatable("gtceu.machine.me.fluid_import.tooltip"),
                    Component.translatable("gtceu.machine.me.copy_paste.tooltip"),
                    Component.translatable("gtceu.universal.enabled"))
            .onRegister(GTLCompassLinks.node("parts", "fluid_hatch"))
            .register();

    public static final MachineEntry<MachineDefinition> ME_INPUT_ASSEMBLY = REGISTRATE
            .machine("me_input_assembly", MEDualInputHatchPartMachine::new)
            .tier(EV)
            .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS)
            .rotationState(RotationState.ALL)
            .tier(EV).colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
            .tooltips(
                    Component.translatable("gtceu.machine.dual_hatch.import.tooltip"),
                    Component.translatable("gtlcore.machine.me.dual_import.tooltip"),
                    Component.translatable("gtceu.machine.me.copy_paste.tooltip"),
                    Component.translatable("gtceu.universal.enabled"))
            .onRegister(GTLCompassLinks.node("parts", "dual_hatch"))
            .register();

    public static final MachineEntry<MachineDefinition> ME_DUAL_HATCH_STOCK_PART_MACHINE = REGISTRATE
            .machine("me_dual_hatch_stock_part_machine", MEDualHatchStockPartMachine::new)
            .tier(LuV)
            .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS)
            .rotationState(RotationState.ALL)
            .tier(LuV).colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
            .tooltips(
                    Component.translatable("gtceu.machine.dual_hatch.import.tooltip"),
                    Component.translatable("gtlcore.machine.me.dual_import.tooltip"),
                    Component.translatable("gtceu.machine.me.copy_paste.tooltip"),
                    Component.translatable("gtceu.universal.enabled"))
            .onRegister(GTLCompassLinks.node("parts", "dual_hatch"))
            .register();

    public static class GTAEMachines {

        public static final MachineEntry<MachineDefinition> ITEM_IMPORT_BUS_ME = GTRegistration.REGISTRATE
                .machine("me_input_bus", MEInputBusPartMachine::new)
                .langValue("ME Input Bus")
                .tier(4)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_bus"))
                .tooltips(Component.translatable("gtceu.machine.item_bus.import.tooltip"),
                        Component.translatable("gtceu.machine.me.item_import.tooltip"),
                        Component.translatable("gtceu.machine.me.copy_paste.tooltip"),
                        Component.translatable("gtceu.universal.enabled"))
                .onRegister(GTLCompassLinks.node("parts", "item_bus"))
                .register();

        public static final MachineEntry<MachineDefinition> STOCKING_IMPORT_BUS_ME = GTRegistration.REGISTRATE
                .machine("me_stocking_input_bus", MEStockingBusPartMachine::new)
                .langValue("ME Stocking Input Bus")
                .tier(6)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_bus"))
                .tooltips(Component.translatable("gtceu.machine.item_bus.import.tooltip"),
                        Component.translatable("gtceu.machine.me.stocking_item.tooltip.0"),
                        Component.translatable("gtceu.machine.me_import_item_hatch.configs.tooltip"),
                        Component.translatable("gtceu.machine.me.copy_paste.tooltip"),
                        Component.translatable("gtceu.machine.me.stocking_item.tooltip.1"),
                        Component.translatable("gtceu.universal.enabled"))
                .onRegister(GTLCompassLinks.node("parts", "item_bus"))
                .register();

        public static final MachineEntry<MachineDefinition> ITEM_EXPORT_BUS_ME = GTRegistration.REGISTRATE
                .machine("me_output_bus", MEOutputBusPartMachine::new)
                .langValue("ME Output Bus")
                .tier(4)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.EXPORT_ITEMS)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_output_bus"))
                .tooltips(Component.translatable("gtceu.machine.item_bus.export.tooltip"),
                        Component.translatable("gtceu.machine.me.item_export.tooltip"),
                        Component.translatable("gtceu.machine.me.export.tooltip"),
                        Component.translatable("gtceu.universal.enabled"))
                .onRegister(GTLCompassLinks.node("parts", "item_bus"))
                .register();

        public static final MachineEntry<MachineDefinition> FLUID_IMPORT_HATCH_ME = GTRegistration.REGISTRATE
                .machine("me_input_hatch", MEInputHatchPartMachine::new)
                .langValue("ME Input Hatch")
                .tier(4)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_FLUIDS)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_hatch"))
                .tooltips(Component.translatable("gtceu.machine.fluid_hatch.import.tooltip"),
                        Component.translatable("gtceu.machine.me.fluid_import.tooltip"),
                        Component.translatable("gtceu.machine.me.copy_paste.tooltip"),
                        Component.translatable("gtceu.universal.enabled"))
                .onRegister(GTLCompassLinks.node("parts", "fluid_hatch"))
                .register();

        public static final MachineEntry<MachineDefinition> STOCKING_IMPORT_HATCH_ME = GTRegistration.REGISTRATE
                .machine("me_stocking_input_hatch", MEStockingHatchPartMachine::new)
                .langValue("ME Stocking Input Hatch")
                .tier(6)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_FLUIDS)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_input_hatch"))
                .tooltips(Component.translatable("gtceu.machine.fluid_hatch.import.tooltip"),
                        Component.translatable("gtceu.machine.me.stocking_fluid.tooltip.0"),
                        Component.translatable("gtceu.machine.me_import_fluid_hatch.configs.tooltip"),
                        Component.translatable("gtceu.machine.me.copy_paste.tooltip"),
                        Component.translatable("gtceu.machine.me.stocking_fluid.tooltip.1"),
                        Component.translatable("gtceu.universal.enabled"))
                .onRegister(GTLCompassLinks.node("parts", "fluid_hatch"))
                .register();

        public static final MachineEntry<MachineDefinition> FLUID_EXPORT_HATCH_ME = GTRegistration.REGISTRATE
                .machine("me_output_hatch", MEOutputHatchPartMachine::new)
                .langValue("ME Output Hatch")
                .tier(4)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.EXPORT_FLUIDS)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_output_hatch"))
                .tooltips(Component.translatable("gtceu.machine.fluid_hatch.export.tooltip"),
                        Component.translatable("gtceu.machine.me.fluid_export.tooltip"),
                        Component.translatable("gtceu.machine.me.export.tooltip"),
                        Component.translatable("gtceu.universal.enabled"))
                .onRegister(GTLCompassLinks.node("parts", "fluid_hatch"))
                .register();

        public static final MachineEntry<MachineDefinition> ME_CRAFTING_CPU_INTERFACE = REGISTRATE
                .machine("me_crafting_cpu_interface", MECraftingCPUInterfacePartMachine::new)
                .tier(UV)
                .rotationState(RotationState.ALL)
                .abilities(GTLPartAbility.ME_CRAFTING_CPU_INTERFACE)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
                .langValue("ME Crafting CPU Interface")
                .tooltips(
                        Component.translatable("gtlcore.machine.me_crafting_cpu_interface.tooltip.0"),
                        Component.translatable("gtceu.universal.disabled"))
                .tooltipBuilder(GTL_ADD)
                .register();

        public static final MachineEntry<MachineDefinition> ME_MINI_PATTERN_BUFFER = REGISTRATE
                .machine("me_mini_pattern_buffer", (h) -> new MEPatternBufferPartMachine(h, 9, IO.IN))
                .tier(5)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS)
                .overlayTieredHullModel("me_mini_pattern_buffer")
                .langValue("ME Mini Pattern Buffer")
                .tooltips(Component.translatable("block.gtceu.pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.1"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.2"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.3"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.4"),
                        Component.translatable("gtceu.machine.me_mini_pattern_buffer.desc.0"),
                        Component.translatable("gtlcore.machine.pattern_quick_upload.tooltip"),
                        Component.translatable("tooltip.gtlcore.wireless_bookmark.quick_connect"),
                        Component.translatable("block.gtceu.pattern_buffer.desc.2"),
                        Component.translatable("gtceu.universal.enabled"))
                .tooltipBuilder(GTL_ADD)
                .register();

        public static final MachineEntry<MachineDefinition> ME_EXTEND_PATTERN_BUFFER = REGISTRATE
                .machine("me_extend_pattern_buffer", (h) -> new MEPatternBufferPartMachine(h, 36, IO.BOTH))
                .tier(8)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_ITEMS, PartAbility.EXPORT_FLUIDS)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
                .langValue("ME Extend Pattern Buffer")
                .tooltips(Component.translatable("block.gtceu.pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.1"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.2"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.3"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.4"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.5"),
                        Component.translatable("gtlcore.machine.pattern_quick_upload.tooltip"),
                        Component.translatable("tooltip.gtlcore.wireless_bookmark.quick_connect"),
                        Component.translatable("block.gtceu.pattern_buffer.desc.2"),
                        Component.translatable("gtceu.universal.enabled"))
                .tooltipBuilder(GTL_ADD)
                .register();

        public static final MachineEntry<MachineDefinition> ME_STOCKING_PATTERN_BUFFER = REGISTRATE
                .machine("me_stocking_pattern_buffer", (h) -> new MEStockingPatternBufferPartMachine(h, 36, IO.BOTH))
                .tier(9)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_ITEMS, PartAbility.EXPORT_FLUIDS)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
                .langValue("ME Stocking Pattern Buffer")
                .tooltips(Component.translatable("block.gtceu.pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.1"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.2"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.3"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.4"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.5"),
                        Component.translatable("gtlcore.machine.me_stocking_pattern_buffer.desc.0"),
                        Component.translatable("gtlcore.machine.me_stocking_pattern_buffer.desc.1"),
                        Component.translatable("gtlcore.machine.pattern_quick_upload.tooltip"),
                        Component.translatable("tooltip.gtlcore.wireless_bookmark.quick_connect"),
                        Component.translatable("block.gtceu.pattern_buffer.desc.2"),
                        Component.translatable("gtceu.universal.enabled"))
                .tooltipBuilder(GTL_ADD)
                .register();

        public static final MachineEntry<MachineDefinition> ME_FINAL_PATTERN_BUFFER = REGISTRATE
                .machine("me_final_pattern_buffer", (h) -> new MEPatternBufferPartMachine(h, 72, IO.BOTH))
                .tier(10)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_ITEMS, PartAbility.EXPORT_FLUIDS)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
                .langValue("ME Final Pattern Buffer")
                .tooltips(Component.translatable("tooltip.gtlcore.bigger_stronger").withStyle(ChatFormatting.GOLD),
                        Component.translatable("block.gtceu.pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.0"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.1"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.2"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.3"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.4"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.5"),
                        Component.translatable("gtlcore.machine.pattern_quick_upload.tooltip"),
                        Component.translatable("tooltip.gtlcore.wireless_bookmark.quick_connect"),
                        Component.translatable("block.gtceu.pattern_buffer.desc.2"),
                        Component.translatable("gtceu.universal.enabled"))
                .tooltipBuilder(GTL_ADD)
                .register();

        public static final MachineEntry<MachineDefinition> ME_PATTERN_BUFFER_PROXY = REGISTRATE
                .machine("me_pattern_buffer_proxy", MEPatternBufferProxyPartMachine::new)
                .tier(8)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.IMPORT_ITEMS, PartAbility.IMPORT_FLUIDS, PartAbility.EXPORT_FLUIDS, PartAbility.EXPORT_ITEMS)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch_proxy"))
                .langValue("ME Pattern Buffer Proxy")
                .tooltips(Component.translatable("block.gtceu.pattern_buffer_proxy.desc.0"),
                        Component.translatable("block.gtceu.pattern_buffer_proxy.desc.1"),
                        Component.translatable("block.gtceu.pattern_buffer_proxy.desc.2"),
                        Component.translatable("gtceu.machine.me_pattern_buffer_proxy.desc.0"),
                        Component.translatable("gtceu.universal.enabled"))
                .tooltipBuilder(GTL_MODIFY)
                .register();

        public static final MachineEntry<MachineDefinition> ME_EXTENDED_EXPORT_BUFFER = REGISTRATE
                .machine("me_extended_export_buffer", MEExtendedOutputPartMachine::new)
                .tier(9)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.EXPORT_FLUIDS, PartAbility.EXPORT_ITEMS)
                .overlayTieredHullModel("me_extended_export_buffer")
                .langValue("ME Extended Export Buffer")
                .tooltips(Component.translatable("gtmthings.machine.me_export_buffer.tooltip"),
                        Component.translatable("gtceu.machine.me_extended_export_buffer.tooltip.0"),
                        Component.translatable("gtceu.machine.me.copy_paste.tooltip"))
                .tooltipBuilder(GTL_ADD)
                .register();

        public static final MachineEntry<MachineDefinition> ME_EXTENDED_ASYNC_EXPORT_BUFFER = REGISTRATE
                .machine("me_extended_async_export_buffer", MEExtendedAsyncOutputPartMachine::new)
                .tier(9)
                .rotationState(RotationState.ALL)
                .abilities(PartAbility.EXPORT_FLUIDS, PartAbility.EXPORT_ITEMS)
                .overlayTieredHullModel("me_extended_export_buffer")
                .langValue("ME Extended Async Export Buffer")
                .tooltips(Component.translatable("gtmthings.machine.me_export_buffer.tooltip"),
                        Component.translatable("gtceu.machine.me_extended_async_export_buffer.tooltip.0"),
                        Component.translatable("gtceu.machine.me_extended_async_export_buffer.tooltip.1"),
                        Component.translatable("gtceu.machine.me.copy_paste.tooltip"))
                .tooltipBuilder(GTL_ADD)
                .register();
        public static final MachineEntry<MachineDefinition> ME_CRAFT_SPEED_CORE = REGISTRATE
                .machine("me_craft_speed_core", MECraftSpeedCorePartMachine::new)
                .rotationState(RotationState.ALL)
                .abilities(GTLPartAbility.MOLECULAR_ASSEMBLER_MATRIX)
                .workableCasingModel(GTCEu.id("block/casings/speed_core_casing"), GTCEu.id("block/casings/speed_core_casing"))
                .langValue("ME CRAFT Speed Core")
                .tooltips(Component.translatable("gtceu.universal.disabled"),
                        Component.translatable("gtceu.machine.me_craft_speed_core.tooltip.0"))
                .tooltipBuilder(GTL_ADD)
                .register();

        public static final MachineEntry<MachineDefinition> ME_CRAFT_PARALLEL_CORE = REGISTRATE
                .machine("me_craft_parallel_core", MECraftParallelCorePartMachine::new)
                .rotationState(RotationState.ALL)
                .abilities(GTLPartAbility.MOLECULAR_ASSEMBLER_MATRIX)
                .workableCasingModel(GTCEu.id("block/casings/crafter_core_casing"), GTCEu.id("block/casings/crafter_core_casing"))
                .langValue("ME CRAFT Parallel Core")
                .tooltips(Component.translatable("gtceu.universal.disabled"),
                        Component.translatable("gtceu.machine.me_craft_parallel_core.tooltip.0"))
                .tooltipBuilder(GTL_ADD)
                .register();

        public static final MachineEntry<MachineDefinition> ME_CRAFT_PATTERN_CONTAINER = REGISTRATE.machine("me_craft_pattern_container", MECraftPatternContainerPartMachine::new)
                .rotationState(RotationState.ALL)
                .abilities(GTLPartAbility.MOLECULAR_ASSEMBLER_MATRIX)
                .workableCasingModel(GTCEu.id("block/casings/pattern_core_casing"), GTCEu.id("block/casings/pattern_core_casing"))
                .langValue("ME Craft Pattern Container")
                .tooltips(Component.translatable("gtceu.universal.disabled"), Component.translatable("gtceu.machine.me_craft_pattern_core.tooltip",
                        Component.literal(FormattingUtil.formatNumbers(12 * 9)).withStyle(ChatFormatting.GOLD)))
                .tooltipBuilder(GTL_ADD)
                .register();

        public static final MachineEntry<MachineDefinition> ME_MOLECULAR_ASSEMBLER_IO = REGISTRATE
                .machine("me_molecular_assembler_io", MEMolecularAssemblerIOPartMachine::new)
                .tier(8)
                .rotationState(RotationState.ALL)
                .colorOverlayTieredHullModel(GTCEu.id("block/overlay/appeng/me_buffer_hatch"))
                .langValue("ME Molecular Assembler IO")
                .tooltips(Component.translatable("gtceu.universal.disabled"),
                        Component.translatable("gtceu.machine.me_molecular_assembler_io.tooltip.0"),
                        Component.translatable("gtceu.machine.me_pattern_buffer.desc.3"),
                        Component.translatable("gtlcore.machine.pattern_quick_upload.tooltip"),
                        Component.translatable("gtceu.machine.me_molecular_assembler_io.tooltip.1"))
                .tooltipBuilder(GTL_ADD)
                .register();

        public static void init() {
            // Register optional wildcard pattern integration
            WildcardPatternCompat.registerMachines();
        }
    }
}
