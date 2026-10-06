package org.gtlcore.gtlcore.common.data;

import org.gtlcore.gtlcore.common.recipe.GTLRecipeUIModifiers;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.data.recipe.GenerateDisassembly;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.api.recipe.gui.ProgressBarTextureSet;
import com.gregtechceu.gtceu.api.registry.registrate.entry.GTRecipeTypeEntry;
import com.gregtechceu.gtceu.api.sound.ExistingSoundEntry;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.ResearchManager;

import com.lowdragmc.lowdraglib.utils.LocalizationUtils;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;


import static com.gregtechceu.gtceu.common.data.GTRecipeTypes.*;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

import brachy.modularui.drawable.progress.CircularProgressDrawable;

public class GTLRecipeTypes {
    // Retained from Core's bundled GT 1.4.4; the native GT 8 baseline removed this recipe family.
    public static final GTRecipeTypeEntry EVAPORATION_RECIPES = REGISTRATE.recipeType("evaporation", GTRecipeTypes.ELECTRIC)
            .setMaxIOSize(0, 1, 1, 6)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.MOTOR)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW_MULTIPLE))
            .register();

    public static final GTRecipeTypeEntry PRIMITIVE_VOID_ORE_RECIPES = ConfigHolder.INSTANCE.enablePrimitiveVoidOre ?
            REGISTRATE.recipeType("primitive_void_ore", MULTIBLOCK)
                    .setMaxIOSize(0, 0, 1, 0)
                    .setSound(GTSoundEntries.MINER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW_MULTIPLE)).register() :
            null;

    public final static GTRecipeTypeEntry SEMI_FLUID_GENERATOR_FUELS = REGISTRATE.recipeType("semi_fluid_generator", GENERATOR)
            .setMaxIOSize(0, 0, 1, 0).setEUIO(IO.OUT)
            .setSound(GTSoundEntries.COMBUSTION)
            .UI(ui -> ui.setFluidSlotOverlay(IO.IN, 0, GTGuiTextures.FURNACE_OVERLAY_2)
                    .setProgressBar(GTGuiTextures.PROGRESS_ARROW_MULTIPLE))
            .register();

    public final static GTRecipeTypeEntry SUPERCRITICAL_STEAM_TURBINE_FUELS = REGISTRATE.recipeType(
            "supercritical_steam_turbine", GENERATOR)
            .setMaxIOSize(0, 0, 1, 1)
            .setEUIO(IO.OUT)
            .setSound(GTSoundEntries.TURBINE)
            .UI(ui -> ui.setFluidSlotOverlay(IO.IN, 0, GTGuiTextures.CENTRIFUGE_OVERLAY)
                    .setProgressBar(GTGuiTextures.PROGRESS_GAS_COLLECTOR))
            .register();

    public final static GTRecipeTypeEntry ROCKET_ENGINE_FUELS = REGISTRATE.recipeType("rocket_engine", GENERATOR)
            .setEUIO(IO.OUT)
            .setMaxIOSize(0, 0, 1, 0)
            .setSound(GTSoundEntries.JET_ENGINE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public final static GTRecipeTypeEntry NAQUADAH_REACTOR = REGISTRATE.recipeType("naquadah_reactor", GENERATOR)
            .setEUIO(IO.OUT)
            .setMaxIOSize(0, 0, 1, 0)
            .setSound(GTSoundEntries.COMBUSTION)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public final static GTRecipeTypeEntry ELECTRIC_IMPLOSION_COMPRESSOR_RECIPES = REGISTRATE.recipeType("electric_implosion_compressor",
            MULTIBLOCK)
            .setMaxIOSize(2, 1, 0, 0).setEUIO(IO.IN)
            .prepareBuilder(recipeBuilder -> recipeBuilder.duration(1).EUt(GTValues.VA[GTValues.UV]))
            .setSound(new ExistingSoundEntry(SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS))
            .UI(ui -> ui.setItemSlotOverlay(IO.IN, 1, GTGuiTextures.IMPLOSION_OVERLAY_1)
                    .setItemSlotOverlay(IO.IN, 0, GTGuiTextures.IMPLOSION_OVERLAY_2)
                    .setItemSlotOverlay(IO.OUT, 0, GTGuiTextures.DUST_OVERLAY))
            .register();

    public static final GTRecipeTypeEntry DISASSEMBLY_RECIPES = REGISTRATE.recipeType("disassembly", GTRecipeTypes.MULTIBLOCK)
            .setMaxIOSize(1, 16, 0, 4)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ASSEMBLER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW_MULTIPLE))
            .register();

    public static final GTRecipeTypeEntry NEUTRON_ACTIVATOR_RECIPES = REGISTRATE.recipeType("neutron_activator", MULTIBLOCK)
            .setMaxIOSize(6, 3, 1, 1)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> LocalizationUtils.format("gtceu.recipe.ev_min", data.getInt("ev_min"))))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> LocalizationUtils.format("gtceu.recipe.ev_max", data.getInt("ev_max"))))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> LocalizationUtils.format("gtceu.recipe.evt", data.getInt("evt")))))
            .register();

    public static final GTRecipeTypeEntry HEAT_EXCHANGER_RECIPES = REGISTRATE.recipeType("heat_exchanger", GTRecipeTypes.MULTIBLOCK)
            .setMaxIOSize(0, 0, 2, 2)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBarSupplier((layout, value, machine) -> new CircularProgressDrawable()
                            .emptyTexture(GTGuiTextures.PROGRESS_MIXER[0])
                            .filledTexture(GTGuiTextures.PROGRESS_MIXER[1])
                            .clockwise().asWidget().value(value)))
            .register();

    public static final GTRecipeTypeEntry ELEMENT_COPYING_RECIPES = REGISTRATE.recipeType("element_copying", GTRecipeTypes.MULTIBLOCK)
            .setMaxIOSize(1, 1, 2, 1)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW_MULTIPLE))
            .register();

    public static final GTRecipeTypeEntry LIGHTNING_PROCESSOR_RECIPES = REGISTRATE.recipeType("lightning_processor", GTRecipeTypes.ELECTRIC)
            .setMaxIOSize(6, 1, 6, 1)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW_MULTIPLE))
            .register();

    public static final GTRecipeTypeEntry DEHYDRATOR_RECIPES = REGISTRATE.recipeType("dehydrator", GTRecipeTypes.ELECTRIC)
            .setMaxIOSize(2, 6, 2, 2)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_EXTRACT))
            .register();

    public static final GTRecipeTypeEntry INTEGRATED_ORE_PROCESSOR = REGISTRATE.recipeType("integrated_ore_processor", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 9, 1, 0)
            .setSound(GTSoundEntries.MACERATOR)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry FISSION_REACTOR_RECIPES = REGISTRATE.recipeType("fission_reactor", MULTIBLOCK)
            .setMaxIOSize(1, 1, 0, 0)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW)
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> LocalizationUtils.format("gtceu.recipe.frheat", FormattingUtil.formatNumbers(data.getInt("FRheat"))))))
            .register();

    public static final GTRecipeTypeEntry SPACE_ELEVATOR_RECIPES = REGISTRATE.recipeType("space_elevator", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 0, 0, 0)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static String getSCTier(int tier) {
        return switch (tier) {
            case 3 -> I18n.get("gtceu.tier.ultimate");
            case 2 -> I18n.get("gtceu.tier.advanced");
            default -> I18n.get("gtceu.tier.base");
        };
    }

    public static final GTRecipeTypeEntry STELLAR_FORGE_RECIPES = REGISTRATE.recipeType("stellar_forge", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(3, 2, 9, 2)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(new ProgressBarTextureSet(GTGuiTextures.PROGRESS_BAR_ARC_FURNACE))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> LocalizationUtils.format("gtceu.recipe.stellar_containment_tier", getSCTier(data.getInt("SCTier"))))))
            .register();

    public static final GTRecipeTypeEntry COMPONENT_ASSEMBLY_LINE_RECIPES = REGISTRATE.recipeType("component_assembly_line", MULTIBLOCK)
            .setMaxIOSize(9, 1, 9, 0)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ASSEMBLER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW)
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> LocalizationUtils.format("gtceu.recipe.ca_tier", GTValues.VN[data.getInt("CATier")]))))
            .register();

    public static final GTRecipeTypeEntry SLAUGHTERHOUSE_RECIPES = REGISTRATE.recipeType("slaughterhouse", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 0, 0, 0)
            .setSound(GTSoundEntries.MACERATOR)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry GREENHOUSE_RECIPES = REGISTRATE.recipeType("greenhouse", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(3, 3, 1, 0)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBarSupplier((layout, value, machine) -> new CircularProgressDrawable()
                            .emptyTexture(GTGuiTextures.PROGRESS_BATH[0])
                            .filledTexture(GTGuiTextures.PROGRESS_BATH[1])
                            .clockwise().asWidget().value(value)))
            .register();

    public static final GTRecipeTypeEntry DIMENSIONALLY_TRANSCENDENT_PLASMA_FORGE_RECIPES = REGISTRATE.recipeType("dimensionally_transcendent_plasma_forge", MULTIBLOCK)
            .setMaxIOSize(2, 2, 2, 2)
            .setEUIO(IO.IN)
            .setSound(GTLSoundEntries.DTPF)
            .UI(ui -> ui.setProgressBar(new ProgressBarTextureSet(GTGuiTextures.PROGRESS_BAR_ARC_FURNACE))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> LocalizationUtils.format("gtceu.recipe.temperature", FormattingUtil.formatNumbers(data.getInt("ebf_temp")))))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                ICoilType requiredCoil = ICoilType.getMinRequiredType(temp);
                return LocalizationUtils.format("gtceu.recipe.coil.tier", (temp > 21600 && temp <= 32000) ? "超级热容" : I18n.get(requiredCoil.getMaterial().getUnlocalizedName()));
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.coils(GTLRecipeUIModifiers.PLASMA_COILS)))
            .register();

    public static final GTRecipeTypeEntry PLASMA_CONDENSER_RECIPES = REGISTRATE.recipeType("plasma_condenser", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 2, 2, 2)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry RARE_EARTH_CENTRIFUGAL_RECIPES = REGISTRATE.recipeType("rare_earth_centrifugal", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 17, 1, 1)
            .setSound(GTSoundEntries.CENTRIFUGE)
            .UI(ui -> ui.setProgressBar(new ProgressBarTextureSet(GTGuiTextures.CENTRIFUGE_OVERLAY)))
            .register();

    public static final GTRecipeTypeEntry MAGIC_MANUFACTURER_RECIPES = REGISTRATE.recipeType("magic_manufacturer", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 0, 0, 1)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBar(new ProgressBarTextureSet(GTGuiTextures.PROGRESS_BAR_MASS_FAB)))
            .register();

    public static final GTRecipeTypeEntry SPS_CRAFTING_RECIPES = REGISTRATE.recipeType("sps_crafting", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(3, 1, 3, 1)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry MATTER_FABRICATOR_RECIPES = REGISTRATE.recipeType("matter_fabricator", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 1, 0, 1)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry VOID_MINER_RECIPES = REGISTRATE.recipeType("void_miner", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 4, 2, 0)
            .setSound(GTSoundEntries.MINER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry LARGE_VOID_MINER_RECIPES = REGISTRATE.recipeType("large_void_miner", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 4, 1, 0)
            .setSound(GTSoundEntries.MINER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry RANDOM_ORE_RECIPES = REGISTRATE.recipeType("random_ore", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(0, 216, 1, 0)
            .setSound(GTSoundEntries.MINER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry VOID_FLUID_DRILLING_RIG_RECIPES = REGISTRATE.recipeType("void_fluid_drilling_rig", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 0, 0, 1)
            .setSound(GTSoundEntries.CHEMICAL)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry WORLD_DATA_SCANNER_RECIPES = REGISTRATE.recipeType("world_data_scanner", ELECTRIC)
            .setEUIO(IO.IN)
            .setMaxIOSize(3, 1, 2, 0)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry ANNIHILATE_GENERATOR_RECIPES = REGISTRATE.recipeType("annihilate_generator", MULTIBLOCK)
            .setEUIO(IO.OUT)
            .setMaxIOSize(1, 1, 0, 0)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry HYPER_REACTOR_RECIPES = REGISTRATE.recipeType("hyper_reactor", MULTIBLOCK)
            .setEUIO(IO.OUT)
            .setMaxIOSize(0, 0, 2, 0)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry ADVANCED_HYPER_REACTOR_RECIPES = REGISTRATE.recipeType("advanced_hyper_reactor", MULTIBLOCK)
            .setEUIO(IO.OUT)
            .setMaxIOSize(0, 0, 1, 0)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry LARGE_NAQUADAH_REACTOR_RECIPES = REGISTRATE.recipeType("large_naquadah_reactor", MULTIBLOCK)
            .setEUIO(IO.OUT)
            .setMaxIOSize(0, 0, 2, 0)
            .setSound(GTSoundEntries.COMBUSTION)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry COSMOS_SIMULATION_RECIPES = REGISTRATE.recipeType("cosmos_simulation", MULTIBLOCK)
            .setMaxIOSize(1, 120, 1, 18)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry SPACE_PROBE_SURFACE_RECEPTION_RECIPES = REGISTRATE.recipeType("space_probe_surface_reception", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 0, 0, 1)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry SPACE_COSMIC_PROBE_RECEIVERS_RECIPES = REGISTRATE.recipeType("space_cosmic_probe_receivers", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 0, 0, 1)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry DECAY_HASTENER_RECIPES = REGISTRATE.recipeType("decay_hastener", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(0, 1, 1, 1)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry LARGE_RECYCLER_RECIPES = REGISTRATE.recipeType("large_recycler", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 1, 0, 0)
            .setSound(GTSoundEntries.MACERATOR)
            .UI(ui -> ui.setProgressBar(new ProgressBarTextureSet(GTGuiTextures.PROGRESS_BAR_RECYCLER)))
            .register();

    public static final GTRecipeTypeEntry MASS_FABRICATOR_RECIPES = REGISTRATE.recipeType("mass_fabricator", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 0, 1, 1)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(new ProgressBarTextureSet(GTGuiTextures.PROGRESS_BAR_REPLICATOR)))
            .register();

    public static final GTRecipeTypeEntry CIRCUIT_ASSEMBLY_LINE_RECIPES = REGISTRATE.recipeType("circuit_assembly_line", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(16, 1, 4, 0)
            .setSound(GTSoundEntries.ASSEMBLER)
            .onRecipeBuild(GenerateDisassembly::generateDisassembly)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry SUPRACHRONAL_ASSEMBLY_LINE_RECIPES = REGISTRATE.recipeType("suprachronal_assembly_line", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(16, 1, 4, 0)
            .setSound(GTSoundEntries.ASSEMBLER)
            .setHasResearchSlot(true)
            .onRecipeBuild((recipeBuilder, provider) -> {
                ResearchManager.createDefaultResearchRecipe(recipeBuilder, provider);
                GenerateDisassembly.generateDisassembly(recipeBuilder, provider);
            })
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry PRECISION_ASSEMBLER_RECIPES = REGISTRATE.recipeType("precision_assembler", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(4, 1, 4, 0)
            .setSound(GTSoundEntries.ASSEMBLER)
            .onRecipeBuild(GenerateDisassembly::generateDisassembly)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry ASSEMBLER_MODULE_RECIPES = REGISTRATE.recipeType("assembler_module", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(16, 1, 4, 0)
            .setSound(GTSoundEntries.ASSEMBLER)
            .onRecipeBuild(GenerateDisassembly::generateDisassembly)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW)
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> LocalizationUtils.format("gtceu.recipe.sepm_tier", FormattingUtil.formatNumbers(data.getInt("SEPMTier"))))))
            .register();

    public static final GTRecipeTypeEntry MINER_MODULE_RECIPES = REGISTRATE.recipeType("miner_module", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 6, 1, 0)
            .setSound(GTSoundEntries.MINER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry DRILLING_MODULE_RECIPES = REGISTRATE.recipeType("drilling_module", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 0, 1, 1)
            .setSound(GTSoundEntries.MINER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry FISHING_GROUND_RECIPES = REGISTRATE.recipeType("fishing_ground", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 24, 0, 0)
            .setSound(GTSoundEntries.MINER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry BLOCK_CONVERSION_RECIPES = REGISTRATE.recipeType("block_conversion", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 0, 0, 0)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry INCUBATOR_RECIPES = REGISTRATE.recipeType("incubator", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(6, 1, 2, 1)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBarSupplier((layout, value, machine) -> new CircularProgressDrawable()
                            .emptyTexture(GTGuiTextures.PROGRESS_BATH[0])
                            .filledTexture(GTGuiTextures.PROGRESS_BATH[1])
                            .clockwise().asWidget().value(value)))
            .register();

    public static final GTRecipeTypeEntry PCB_FACTORY_RECIPES = REGISTRATE.recipeType("pcb_factory", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 1, 2, 0)
            .setSound(GTSoundEntries.CHEMICAL)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry LAVA_FURNACE_RECIPES = REGISTRATE.recipeType("lava_furnace", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 0, 0, 1)
            .setSound(GTSoundEntries.FURNACE)
            .UI(ui -> ui.setProgressBar(new ProgressBarTextureSet(GTGuiTextures.PROGRESS_BAR_ARC_FURNACE)))
            .register();

    public static final GTRecipeTypeEntry LARGE_GAS_COLLECTOR_RECIPES = REGISTRATE.recipeType("large_gas_collector", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 0, 0, 1)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry AGGREGATION_DEVICE_RECIPES = REGISTRATE.recipeType("aggregation_device", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(9, 1, 0, 0)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry SUPER_PARTICLE_COLLIDER_RECIPES = REGISTRATE.recipeType("super_particle_collider", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(0, 0, 2, 1)
            .setSound(GTLSoundEntries.FUSIONLOOP)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry DIMENSIONAL_FOCUS_ENGRAVING_ARRAY_RECIPES = REGISTRATE.recipeType("dimensional_focus_engraving_array", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 1, 1, 0)
            .setSound(GTSoundEntries.ARC)
            .onRecipeBuild(ResearchManager::createDefaultResearchRecipe)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry PRECISION_LASER_ENGRAVER_RECIPES = REGISTRATE.recipeType("precision_laser_engraver", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(9, 1, 1, 0)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry DIMENSIONALLY_TRANSCENDENT_MIXER_RECIPES = REGISTRATE.recipeType("dimensionally_transcendent_mixer", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(9, 1, 6, 1)
            .setSound(GTSoundEntries.MIXER)
            .UI(ui -> ui.setProgressBarSupplier((layout, value, machine) -> new CircularProgressDrawable()
                            .emptyTexture(GTGuiTextures.PROGRESS_BATH[0])
                            .filledTexture(GTGuiTextures.PROGRESS_BATH[1])
                            .clockwise().asWidget().value(value)))
            .register();

    public static final GTRecipeTypeEntry NEUTRON_COMPRESSOR_RECIPES = REGISTRATE.recipeType("neutron_compressor", ELECTRIC)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 1, 0, 0)
            .setSound(GTSoundEntries.COMPRESSOR)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_COMPRESS))
            .register();

    public static final GTRecipeTypeEntry QFT_RECIPES = REGISTRATE.recipeType("qft", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(18, 1, 3, 1)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry DRAGON_EGG_COPIER_RECIPES = REGISTRATE.recipeType("dragon_egg_copier", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 2, 1, 0)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry DOOR_OF_CREATE_RECIPES = REGISTRATE.recipeType("door_of_create", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 0, 0, 0)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry BEDROCK_DRILLING_RIG_RECIPES = REGISTRATE.recipeType("bedrock_drilling_rig", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 1, 0, 0)
            .setSound(GTSoundEntries.MACERATOR)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry CREATE_AGGREGATION_RECIPES = REGISTRATE.recipeType("create_aggregation", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 0, 0, 0)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry GRAVITATION_SHOCKBURST_RECIPES = REGISTRATE.recipeType("gravitation_shockburst", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 1, 0, 0)
            .setSound(GTSoundEntries.MACERATOR)
            .onRecipeBuild(ResearchManager::createDefaultResearchRecipe)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry ULTIMATE_MATERIAL_FORGE_RECIPES = REGISTRATE.recipeType("ultimate_material_forge", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(2, 2, 2, 0)
            .setSound(GTSoundEntries.MACERATOR)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry DYSON_SPHERE_RECIPES = REGISTRATE.recipeType("dyson_sphere", MULTIBLOCK)
            .setEUIO(IO.BOTH)
            .setMaxIOSize(1, 0, 1, 0)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry PETROCHEMICAL_PLANT_RECIPES = REGISTRATE.recipeType("petrochemical_plant", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(0, 0, 2, 12)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry WEATHER_CONTROL_RECIPES = REGISTRATE.recipeType("weather_control", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 0, 0, 0)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry DISTORT_RECIPES = REGISTRATE.recipeType("distort", MULTIBLOCK)
            .setMaxIOSize(9, 9, 9, 9)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW)
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                return LocalizationUtils.format("gtceu.recipe.temperature", FormattingUtil.formatNumbers(temp));
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                ICoilType requiredCoil = ICoilType.getMinRequiredType(temp);

                if (requiredCoil != null && requiredCoil.getMaterial() != null) {
                    return LocalizationUtils.format("gtceu.recipe.coil.tier",
                            I18n.get(requiredCoil.getMaterial().getUnlocalizedName()));
                }
                return "";
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.coils(GTLRecipeUIModifiers.STANDARD_COILS)))
            .register();

    public static final GTRecipeTypeEntry NANO_FORGE_RECIPES = REGISTRATE.recipeType("nano_forge", MULTIBLOCK)
            .setMaxIOSize(6, 1, 3, 0)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW)
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> LocalizationUtils.format("gtceu.recipe.nano_forge_tier", FormattingUtil.formatNumbers(data.getInt("nano_forge_tier"))))))
            .register();

    public static final GTRecipeTypeEntry FUEL_REFINING_RECIPES = REGISTRATE.recipeType("fuel_refining", MULTIBLOCK)
            .setMaxIOSize(3, 0, 6, 1)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW)
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                return LocalizationUtils.format("gtceu.recipe.temperature", FormattingUtil.formatNumbers(temp));
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                ICoilType requiredCoil = ICoilType.getMinRequiredType(temp);

                if (requiredCoil != null && requiredCoil.getMaterial() != null) {
                    return LocalizationUtils.format("gtceu.recipe.coil.tier",
                            I18n.get(requiredCoil.getMaterial().getUnlocalizedName()));
                }
                return "";
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.coils(GTLRecipeUIModifiers.STANDARD_COILS)))
            .register();

    public static final GTRecipeTypeEntry ATOMIC_ENERGY_EXCITATION_RECIPES = REGISTRATE.recipeType("atomic_energy_excitation", MULTIBLOCK)
            .setMaxIOSize(3, 0, 6, 2)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW)
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                return LocalizationUtils.format("gtceu.recipe.temperature", FormattingUtil.formatNumbers(temp));
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                ICoilType requiredCoil = ICoilType.getMinRequiredType(temp);

                if (requiredCoil != null && requiredCoil.getMaterial() != null) {
                    return LocalizationUtils.format("gtceu.recipe.coil.tier",
                            I18n.get(requiredCoil.getMaterial().getUnlocalizedName()));
                }
                return "";
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.coils(GTLRecipeUIModifiers.STANDARD_COILS)))
            .register();

    private static String getGrindball(int tier) {
        if (tier == 2) {
            return I18n.get("material.gtceu.aluminium");
        }
        return I18n.get("material.gtceu.soapstone");
    }

    public static final GTRecipeTypeEntry ISA_MILL_RECIPES = REGISTRATE.recipeType("isa_mill", MULTIBLOCK)
            .setMaxIOSize(2, 1, 1, 0)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.MACERATOR)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW)
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> LocalizationUtils.format("gtceu.recipe.grindball", getGrindball(data.getInt("grindball"))))))
            .register();

    public static final GTRecipeTypeEntry FLOTATING_BENEFICIATION_RECIPES = REGISTRATE.recipeType("flotating_beneficiation", MULTIBLOCK)
            .setMaxIOSize(2, 0, 1, 1)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.CHEMICAL)
            .UI(ui -> ui.setProgressBarSupplier((layout, value, machine) -> new CircularProgressDrawable()
                            .emptyTexture(GTGuiTextures.PROGRESS_BATH[0])
                            .filledTexture(GTGuiTextures.PROGRESS_BATH[1])
                            .clockwise().asWidget().value(value)))
            .register();

    public static final GTRecipeTypeEntry VACUUM_DRYING_RECIPES = REGISTRATE.recipeType("vacuum_drying", MULTIBLOCK)
            .setMaxIOSize(1, 6, 1, 2)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.COOLING)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW)
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                return LocalizationUtils.format("gtceu.recipe.temperature", FormattingUtil.formatNumbers(temp));
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                ICoilType requiredCoil = ICoilType.getMinRequiredType(temp);

                if (requiredCoil != null && requiredCoil.getMaterial() != null) {
                    return LocalizationUtils.format("gtceu.recipe.coil.tier",
                            I18n.get(requiredCoil.getMaterial().getUnlocalizedName()));
                }
                return "";
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.coils(GTLRecipeUIModifiers.STANDARD_COILS)))
            .register();

    public static final GTRecipeTypeEntry DISSOLUTION_TREATMENT = REGISTRATE.recipeType("dissolution_treatment", MULTIBLOCK)
            .setMaxIOSize(2, 2, 2, 1)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_EXTRACT))
            .register();

    public static final GTRecipeTypeEntry DIGESTION_TREATMENT = REGISTRATE.recipeType("digestion_treatment", MULTIBLOCK)
            .setMaxIOSize(1, 1, 1, 1)
            .setEUIO(IO.IN)
            .setSound(GTSoundEntries.ARC)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_EXTRACT)
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                return LocalizationUtils.format("gtceu.recipe.temperature", FormattingUtil.formatNumbers(temp));
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.dataInfo(data -> {
                int temp = data.getInt("ebf_temp");
                ICoilType requiredCoil = ICoilType.getMinRequiredType(temp);

                if (requiredCoil != null && requiredCoil.getMaterial() != null) {
                    return LocalizationUtils.format("gtceu.recipe.coil.tier",
                            I18n.get(requiredCoil.getMaterial().getUnlocalizedName()));
                }
                return "";
            }))
                    .addRecipeUIModifier(GTLRecipeUIModifiers.coils(GTLRecipeUIModifiers.STANDARD_COILS)))
            .register();

    public static final GTRecipeTypeEntry WOOD_DISTILLATION_RECIPES = REGISTRATE.recipeType("wood_distillation", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(1, 1, 1, 16)
            .setSound(GTSoundEntries.BATH)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry DESULFURIZER_RECIPES = REGISTRATE.recipeType("desulfurizer", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(0, 1, 1, 1)
            .setSound(GTSoundEntries.MIXER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry MOLECULAR_ASSEMBLER = REGISTRATE.recipeType("molecular_assembler", MULTIBLOCK)
            .setMaxIOSize(3, 12, 1, 1)
            .setSound(GTSoundEntries.SCIENCE)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_ARROW))
            .register();

    public static final GTRecipeTypeEntry FRAGMENT_WORLD_COLLECTION = REGISTRATE.recipeType("fragment_world_collection", MULTIBLOCK)
            .setEUIO(IO.IN)
            .setMaxIOSize(3, 12, 1, 1)
            .setSound(GTSoundEntries.MINER)
            .UI(ui -> ui.setProgressBar(GTGuiTextures.PROGRESS_MACERATE))
            .register();

    public static void init() {
        // Static entries are queued during construction; modifications of bound GT entries run in common setup.
    }
}
