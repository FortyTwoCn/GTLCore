package org.gtlcore.gtlcore.data.recipe;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.data.recipe.CustomTags;
import com.gregtechceu.gtceu.data.recipe.VanillaRecipeHelper;

import net.minecraft.data.recipes.RecipeOutput;

import java.util.function.Consumer;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.*;
import static com.gregtechceu.gtceu.common.data.GTBlocks.CASING_TEMPERED_GLASS;
import static com.gregtechceu.gtceu.common.data.GTItems.*;
import static com.gregtechceu.gtceu.common.data.machines.GTMultiMachines.DISTILLATION_TOWER;
import static com.gregtechceu.gtceu.common.data.machines.GTMultiMachines.ELECTRIC_BLAST_FURNACE;
import static com.gregtechceu.gtceu.common.data.machines.GTMultiMachines.VACUUM_FREEZER;
import static com.gregtechceu.gtceu.common.data.GTMachines.*;
import static com.gregtechceu.gtceu.common.data.GTMaterials.*;
import static com.gregtechceu.gtceu.data.recipe.CustomTags.*;
import static org.gtlcore.gtlcore.common.data.machines.GCyMMachines.*;

public class GCyMRecipes {

    public static void init(RecipeOutput provider) {
        registerPartsRecipes(provider);
        registerMultiblockControllerRecipes(provider);
    }

    private static void registerMultiblockControllerRecipes(RecipeOutput provider) {
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_macerator", LARGE_MACERATION_TOWER.asStack(), "PCP",
                "BXB", "MKM", 'C', CustomTags.IV_CIRCUITS, 'P', new MaterialEntry(plate, TungstenCarbide), 'B',
                ELECTRIC_PISTON_IV.asStack(), 'M', ELECTRIC_MOTOR_IV.asStack(), 'X', MACERATOR[IV].asStack(), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_arc_smelter", LARGE_ARC_SMELTER.asStack(), "KDK",
                "CXC", "PPP", 'C', CustomTags.IV_CIRCUITS, 'P', new MaterialEntry(plate, TantalumCarbide), 'X',
                GTMachines.ARC_FURNACE[IV].asStack(), 'D', new MaterialEntry(dust, Graphite), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_ore_washer", LARGE_CHEMICAL_BATH.asStack(), "PGP",
                "CXC", "MKM", 'C', CustomTags.IV_CIRCUITS, 'G', CASING_TEMPERED_GLASS.asStack(), 'P',
                ELECTRIC_PUMP_IV.asStack(), 'M', CONVEYOR_MODULE_IV.asStack(), 'X', ORE_WASHER[IV].asStack(), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_sifter", LARGE_SIFTING_FUNNEL.asStack(), "PCP",
                "EXE", "PKP", 'C', CustomTags.IV_CIRCUITS, 'P', new MaterialEntry(plate, HSLASteel), 'E',
                ELECTRIC_PISTON_IV.asStack(), 'X', SIFTER[IV].asStack(), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_engraver", LARGE_ENGRAVING_LASER.asStack(), "ICI",
                "EXE", "PKP", 'C', CustomTags.IV_CIRCUITS, 'P', new MaterialEntry(plateDouble, TantalumCarbide), 'I',
                EMITTER_IV.asStack(), 'E', ELECTRIC_PISTON_IV.asStack(), 'X', LASER_ENGRAVER[IV].asStack(), 'K',
                new MaterialEntry(TagPrefix.cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_packer", LARGE_PACKER.asStack(), "RCR", "PXP", "KPK",
                'C', CustomTags.EV_CIRCUITS, 'P', new MaterialEntry(plate, HSLASteel), 'R', ROBOT_ARM_HV.asStack(),
                'K', CONVEYOR_MODULE_HV.asStack(), 'X', PACKER[HV].asStack());
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_mixer", LARGE_MIXER.asStack(), "FCF", "RXR", "MKM",
                'C', CustomTags.IV_CIRCUITS, 'F', ChemicalHelper.get(pipeNormalFluid, Polybenzimidazole), 'R',
                ChemicalHelper.get(rotor, Osmiridium), 'M', ELECTRIC_MOTOR_IV.asStack(), 'X', MIXER[IV].asStack(), 'K',
                new MaterialEntry(TagPrefix.cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_centrifuge", LARGE_CENTRIFUGE.asStack(), "SFS",
                "CXC", "MKM", 'C', CustomTags.IV_CIRCUITS, 'F', ChemicalHelper.get(pipeHugeFluid, StainlessSteel), 'S',
                ChemicalHelper.get(spring, MolybdenumDisilicide), 'M', ELECTRIC_MOTOR_IV.asStack(), 'X',
                CENTRIFUGE[IV].asStack(), 'K', new MaterialEntry(TagPrefix.cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_assembler", LARGE_ASSEMBLER.asStack(), "RKR", "CXC",
                "MKM", 'C', CustomTags.IV_CIRCUITS, 'R', ROBOT_ARM_IV.asStack(), 'M', CONVEYOR_MODULE_IV.asStack(), 'X',
                ASSEMBLER[IV].asStack(), 'K', new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_circuit_assembler",
                LARGE_CIRCUIT_ASSEMBLER.asStack(), "RKR", "CXC", "MKM", 'C', CustomTags.IV_CIRCUITS, 'R',
                ROBOT_ARM_IV.asStack(), 'M', CONVEYOR_MODULE_IV.asStack(), 'X', CIRCUIT_ASSEMBLER[IV].asStack(), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_electrolyzer", LARGE_ELECTROLYZER.asStack(), "PCP",
                "WXW", "PKP", 'C', CustomTags.IV_CIRCUITS, 'P', new MaterialEntry(plate, BlackSteel), 'W',
                new MaterialEntry(cableGtSingle, Platinum), 'X', ELECTROLYZER[IV].asStack(), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_electromagnet", LARGE_ELECTROMAGNET.asStack(), "PWP",
                "CXC", "PKP", 'C', CustomTags.IV_CIRCUITS, 'P', new MaterialEntry(plate, BlueSteel), 'W',
                new MaterialEntry(wireGtQuadruple, Osmium), 'X', ELECTROMAGNETIC_SEPARATOR[IV].asStack(), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "blast_alloy_smelter", BLAST_ALLOY_SMELTER.asStack(), "TCT",
                "WXW", "TCT", 'C', CustomTags.EV_CIRCUITS, 'T', new MaterialEntry(plate, TantalumCarbide), 'W',
                new MaterialEntry(cableGtSingle, Aluminium), 'X', ALLOY_SMELTER[EV].asStack());
        VanillaRecipeHelper.addShapedRecipe(provider, true, "mega_blast_furnace", MEGA_BLAST_FURNACE.asStack(), "PCP",
                "FSF", "DWD", 'C', ZPM_CIRCUITS, 'S', ELECTRIC_BLAST_FURNACE.asStack(), 'F',
                FIELD_GENERATOR_ZPM.asStack(), 'P', new MaterialEntry(spring, Naquadah), 'D',
                new MaterialEntry(plateDense, NaquadahAlloy), 'W',
                new MaterialEntry(wireGtQuadruple, EnrichedNaquadahTriniumEuropiumDuranide));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "mega_vacuum_freezer", MEGA_VACUUM_FREEZER.asStack(), "PCP",
                "FSF", "DWD", 'C', ZPM_CIRCUITS, 'S', VACUUM_FREEZER.asStack(), 'F', FIELD_GENERATOR_ZPM.asStack(), 'P',
                // The former conductor has no fluid-pipe property; use the pinned GT 1.21 recipe's cryogenic pipe.
                new MaterialEntry(pipeNormalFluid, NiobiumTitanium), 'D',
                new MaterialEntry(plateDense, RhodiumPlatedPalladium), 'W',
                new MaterialEntry(wireGtQuadruple, RutheniumTriniumAmericiumNeutronate));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_autoclave", LARGE_AUTOCLAVE.asStack(), "PCP", "PAP",
                "BKB", 'C', CustomTags.IV_CIRCUITS, 'A', AUTOCLAVE[IV].asStack(), 'P',
                new MaterialEntry(plateDouble, HSLASteel), 'B', ELECTRIC_PUMP_IV.asStack(), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_material_press", LARGE_MATERIAL_PRESS.asStack(),
                "PKP", "BZG", "FKH", 'Z', CustomTags.IV_CIRCUITS, 'B', BENDER[IV].asStack(), 'P',
                ELECTRIC_PISTON_IV.asStack(), 'G', COMPRESSOR[IV].asStack(), 'F', FORMING_PRESS[IV].asStack(), 'H',
                FORGE_HAMMER[IV].asStack(), 'K', new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_brewer", LARGE_BREWER.asStack(), "SZS", "FBH", "EKE",
                'Z', CustomTags.IV_CIRCUITS, 'S', new MaterialEntry(spring, MolybdenumDisilicide), 'F',
                FERMENTER[IV].asStack(), 'E', ELECTRIC_PUMP_IV.asStack(), 'B', BREWERY[IV].asStack(), 'H',
                FLUID_HEATER[IV].asStack(), 'K', new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_cutter", LARGE_CUTTER.asStack(), "SMS", "CZL", "EKE",
                'Z', CustomTags.IV_CIRCUITS, 'L', LATHE[IV].asStack(), 'E', ELECTRIC_MOTOR_IV.asStack(), 'C',
                CUTTER[IV].asStack(), 'M', CONVEYOR_MODULE_IV.asStack(), 'S',
                new MaterialEntry(toolHeadBuzzSaw, TungstenCarbide), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_distillery", LARGE_DISTILLERY.asStack(), "PZP",
                "EDE", "PZP", 'Z', CustomTags.IV_CIRCUITS, 'D', DISTILLATION_TOWER.asStack(), 'E',
                ELECTRIC_PUMP_IV.asStack(), 'P', ChemicalHelper.get(pipeLargeFluid, Iridium));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_extractor", LARGE_EXTRACTOR.asStack(), "PTP", "EZC",
                "BKB", 'Z', CustomTags.IV_CIRCUITS, 'B', ELECTRIC_PISTON_IV.asStack(), 'P', ELECTRIC_PUMP_IV.asStack(),
                'E', EXTRACTOR[IV].asStack(), 'C', CANNER[IV].asStack(), 'T', CASING_TEMPERED_GLASS.asStack(), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_extruder", LARGE_EXTRUDER.asStack(), "PZP", "SES",
                "PKP", 'Z', CustomTags.IV_CIRCUITS, 'E', EXTRUDER[IV].asStack(), 'P', ELECTRIC_PISTON_IV.asStack(), 'S',
                new MaterialEntry(spring, MolybdenumDisilicide), 'K', new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_solidifier", LARGE_SOLIDIFIER.asStack(), "PZP",
                "ESE", "PKP", 'Z', CustomTags.IV_CIRCUITS, 'S', FLUID_SOLIDIFIER[IV].asStack(), 'E',
                ELECTRIC_PUMP_IV.asStack(), 'P', ChemicalHelper.get(pipeNormalFluid, Polyethylene), 'K',
                new MaterialEntry(cableGtSingle, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, true, "large_wiremill", LARGE_WIREMILL.asStack(), "PZP", "SWS",
                "MKM", 'Z', CustomTags.IV_CIRCUITS, 'W', WIREMILL[IV].asStack(), 'P',
                new MaterialEntry(plate, HSLASteel), 'S', new MaterialEntry(spring, HSLASteel), 'M',
                ELECTRIC_MOTOR_IV.asStack(), 'K', new MaterialEntry(cableGtSingle, Platinum));
    }

    private static void registerPartsRecipes(RecipeOutput provider) {
        VanillaRecipeHelper.addShapedRecipe(provider, "parallel_hatch_mk1", PARALLEL_HATCH[IV].asStack(1), "SZE", "ZHZ",
                "CZC", 'S', SENSOR_IV.asStack(), 'E', EMITTER_IV.asStack(), 'Z', LuV_CIRCUITS, 'H', HULL[IV].asStack(),
                'C', new MaterialEntry(cableGtDouble, Platinum));
        VanillaRecipeHelper.addShapedRecipe(provider, "parallel_hatch_mk2", PARALLEL_HATCH[LuV].asStack(1), "SZE",
                "ZHZ", "CZC", 'S', SENSOR_LuV.asStack(), 'E', EMITTER_LuV.asStack(), 'Z', ZPM_CIRCUITS, 'H',
                HULL[LuV].asStack(), 'C', new MaterialEntry(cableGtDouble, NiobiumTitanium));
        VanillaRecipeHelper.addShapedRecipe(provider, "parallel_hatch_mk3", PARALLEL_HATCH[ZPM].asStack(1), "SZE",
                "ZHZ", "CZC", 'S', SENSOR_ZPM.asStack(), 'E', EMITTER_ZPM.asStack(), 'Z', UV_CIRCUITS, 'H',
                HULL[ZPM].asStack(), 'C', new MaterialEntry(cableGtDouble, VanadiumGallium));
        VanillaRecipeHelper.addShapedRecipe(provider, "parallel_hatch_mk4", PARALLEL_HATCH[UV].asStack(1), "SZE", "ZHZ",
                "CZC", 'S', SENSOR_UV.asStack(), 'E', EMITTER_UV.asStack(), 'Z', UHV_CIRCUITS, 'H', HULL[UV].asStack(),
                'C', new MaterialEntry(cableGtDouble, YttriumBariumCuprate));
    }
}
