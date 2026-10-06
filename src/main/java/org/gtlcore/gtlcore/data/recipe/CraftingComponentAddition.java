package org.gtlcore.gtlcore.data.recipe;

import org.gtlcore.gtlcore.common.data.GTLItems;
import org.gtlcore.gtlcore.common.data.GTLMaterials;

import com.gregtechceu.gtceu.api.data.chemical.material.stack.MaterialEntry;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.data.recipe.CraftingComponent;
import com.gregtechceu.gtceu.data.recipe.GTCraftingComponents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.data.recipe.GTCraftingComponents.*;

public final class CraftingComponentAddition {

    public static CraftingComponent BUFFER;

    private static ItemStack requiredItem(String id) {
        var key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            throw new IllegalStateException("Missing required GTL content-pack item " + id +
                    "; install the matching KubeJS startup scripts and resources before loading recipes");
        }
        return BuiltInRegistries.ITEM.get(key).getDefaultInstance();
    }

    private static CraftingComponent append(CraftingComponent component, Map<Integer, Object> additions) {
        additions.forEach(component::add);
        return component;
    }

    public static void init() {
        append(GTCraftingComponents.PUMP, Stream.of(new Object[][] {
                { 14, GTLItems.ELECTRIC_PUMP_MAX.asStack() },
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(GTCraftingComponents.CONVEYOR, Stream.of(new Object[][] {
                { 14, GTLItems.CONVEYOR_MODULE_MAX.asStack() },
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(GTCraftingComponents.MOTOR, Stream.of(new Object[][] {
                { 14, GTLItems.ELECTRIC_MOTOR_MAX.asStack() },
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(GTCraftingComponents.PISTON, Stream.of(new Object[][] {
                { 14, GTLItems.ELECTRIC_PISTON_MAX.asStack() },
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(GTCraftingComponents.EMITTER, Stream.of(new Object[][] {
                { 14, GTLItems.EMITTER_MAX.asStack() },
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(GTCraftingComponents.SENSOR, Stream.of(new Object[][] {
                { 14, GTLItems.SENSOR_MAX.asStack() },
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(GTCraftingComponents.FIELD_GENERATOR, Stream.of(new Object[][] {
                { 14, GTLItems.FIELD_GENERATOR_MAX.asStack() },
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(GTCraftingComponents.ROBOT_ARM, Stream.of(new Object[][] {
                { 14, GTLItems.ROBOT_ARM_MAX.asStack() },
        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(WIRE_ELECTRIC, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.wireGtSingle, GTMaterials.Mendelevium) },
                { 11, new MaterialEntry(TagPrefix.wireGtSingle, GTMaterials.Mendelevium) },
                { 12, new MaterialEntry(TagPrefix.wireGtSingle, GTMaterials.Mendelevium) },
                { 13, new MaterialEntry(TagPrefix.wireGtSingle, GTLMaterials.Uruium) },
                { 14, new MaterialEntry(TagPrefix.wireGtSingle, GTLMaterials.Uruium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(WIRE_QUAD, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.wireGtQuadruple, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.wireGtQuadruple, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.wireGtQuadruple, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.wireGtQuadruple, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.wireGtQuadruple, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(WIRE_OCT, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.wireGtOctal, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.wireGtOctal, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.wireGtOctal, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.wireGtOctal, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.wireGtOctal, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(WIRE_HEX, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.wireGtHex, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.wireGtHex, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.wireGtHex, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.wireGtHex, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.wireGtHex, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(CABLE, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.cableGtSingle, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.cableGtSingle, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.cableGtSingle, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.cableGtSingle, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.cableGtSingle, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(CABLE_DOUBLE, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.cableGtDouble, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(CABLE_QUAD, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.cableGtDouble, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(WIRE_ELECTRIC, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.cableGtQuadruple, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.cableGtQuadruple, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.cableGtQuadruple, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.cableGtQuadruple, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.cableGtQuadruple, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(CABLE_OCT, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.cableGtOctal, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.cableGtOctal, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.cableGtOctal, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.cableGtOctal, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.cableGtOctal, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(CABLE_HEX, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.cableGtHex, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.cableGtHex, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.cableGtHex, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.cableGtHex, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.cableGtHex, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(CABLE_TIER_UP, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.cableGtSingle, GTLMaterials.Mithril) },
                { 10, new MaterialEntry(TagPrefix.cableGtSingle, GTMaterials.Neutronium) },
                { 11, new MaterialEntry(TagPrefix.cableGtSingle, GTLMaterials.Taranium) },
                { 12, new MaterialEntry(TagPrefix.cableGtSingle, GTLMaterials.Crystalmatrix) },
                { 13, new MaterialEntry(TagPrefix.cableGtSingle, GTLMaterials.CosmicNeutronium) },
                { 14, new MaterialEntry(TagPrefix.wireGtSingle, GTLMaterials.SpaceTime) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(CABLE_TIER_UP_DOUBLE, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.Mithril) },
                { 10, new MaterialEntry(TagPrefix.cableGtDouble, GTMaterials.Neutronium) },
                { 11, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.Taranium) },
                { 12, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.Crystalmatrix) },
                { 13, new MaterialEntry(TagPrefix.cableGtDouble, GTLMaterials.CosmicNeutronium) },
                { 14, new MaterialEntry(TagPrefix.wireGtDouble, GTLMaterials.SpaceTime) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(CABLE_TIER_UP_QUAD, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.cableGtQuadruple, GTLMaterials.Mithril) },
                { 10, new MaterialEntry(TagPrefix.cableGtQuadruple, GTMaterials.Neutronium) },
                { 11, new MaterialEntry(TagPrefix.cableGtQuadruple, GTLMaterials.Taranium) },
                { 12, new MaterialEntry(TagPrefix.cableGtQuadruple, GTLMaterials.Crystalmatrix) },
                { 13, new MaterialEntry(TagPrefix.cableGtQuadruple, GTLMaterials.CosmicNeutronium) },
                { 14, new MaterialEntry(TagPrefix.wireGtQuadruple, GTLMaterials.SpaceTime) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(CABLE_TIER_UP_OCT, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.cableGtOctal, GTLMaterials.Mithril) },
                { 10, new MaterialEntry(TagPrefix.cableGtOctal, GTMaterials.Neutronium) },
                { 11, new MaterialEntry(TagPrefix.cableGtOctal, GTLMaterials.Taranium) },
                { 12, new MaterialEntry(TagPrefix.cableGtOctal, GTLMaterials.Crystalmatrix) },
                { 13, new MaterialEntry(TagPrefix.cableGtOctal, GTLMaterials.CosmicNeutronium) },
                { 14, new MaterialEntry(TagPrefix.wireGtOctal, GTLMaterials.SpaceTime) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(CABLE_TIER_UP_HEX, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.cableGtHex, GTLMaterials.Mithril) },
                { 10, new MaterialEntry(TagPrefix.cableGtHex, GTMaterials.Neutronium) },
                { 11, new MaterialEntry(TagPrefix.cableGtHex, GTLMaterials.Taranium) },
                { 12, new MaterialEntry(TagPrefix.cableGtHex, GTLMaterials.Crystalmatrix) },
                { 13, new MaterialEntry(TagPrefix.cableGtHex, GTLMaterials.CosmicNeutronium) },
                { 14, new MaterialEntry(TagPrefix.wireGtHex, GTLMaterials.SpaceTime) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(PIPE_NORMAL, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.pipeNormalFluid, GTMaterials.Neutronium) },
                { 10, new MaterialEntry(TagPrefix.pipeNormalFluid, GTMaterials.Neutronium) },
                { 11, new MaterialEntry(TagPrefix.pipeNormalFluid, GTLMaterials.Enderium) },
                { 12, new MaterialEntry(TagPrefix.pipeNormalFluid, GTLMaterials.Enderium) },
                { 13, new MaterialEntry(TagPrefix.pipeNormalFluid,
                        GTLMaterials.HeavyQuarkDegenerateMatter) },
                { 14, new MaterialEntry(TagPrefix.pipeNormalFluid,
                        GTLMaterials.HeavyQuarkDegenerateMatter) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(PIPE_LARGE, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.pipeLargeFluid, GTMaterials.Neutronium) },
                { 10, new MaterialEntry(TagPrefix.pipeLargeFluid, GTMaterials.Neutronium) },
                { 11, new MaterialEntry(TagPrefix.pipeLargeFluid, GTLMaterials.Enderium) },
                { 12, new MaterialEntry(TagPrefix.pipeLargeFluid, GTLMaterials.Enderium) },
                { 13, new MaterialEntry(TagPrefix.pipeLargeFluid,
                        GTLMaterials.HeavyQuarkDegenerateMatter) },
                { 14, new MaterialEntry(TagPrefix.pipeLargeFluid,
                        GTLMaterials.HeavyQuarkDegenerateMatter) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(PIPE_NONUPLE, Stream.of(new Object[][] {
                { 0, new MaterialEntry(TagPrefix.pipeNonupleFluid, GTMaterials.Bronze) },
                { 1, new MaterialEntry(TagPrefix.pipeNonupleFluid, GTMaterials.Bronze) },
                { 2, new MaterialEntry(TagPrefix.pipeNonupleFluid, GTMaterials.Steel) },
                { 3, new MaterialEntry(TagPrefix.pipeNonupleFluid, GTMaterials.StainlessSteel) },
                { 9, new MaterialEntry(TagPrefix.pipeNonupleFluid, GTMaterials.Neutronium) },
                { 10, new MaterialEntry(TagPrefix.pipeNonupleFluid, GTMaterials.Neutronium) },
                { 11, new MaterialEntry(TagPrefix.pipeNonupleFluid, GTLMaterials.Enderium) },
                { 12, new MaterialEntry(TagPrefix.pipeNonupleFluid, GTLMaterials.Enderium) },
                { 13, new MaterialEntry(TagPrefix.pipeNonupleFluid,
                        GTLMaterials.HeavyQuarkDegenerateMatter) },
                { 14, new MaterialEntry(TagPrefix.pipeNonupleFluid,
                        GTLMaterials.HeavyQuarkDegenerateMatter) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(GLASS, Stream.of(new Object[][] {
                { UHV, GTBlocks.FUSION_GLASS.asStack() },
                { UEV, GTBlocks.FUSION_GLASS.asStack() },
                { UIV, requiredItem("kubejs:force_field_glass") },
                { UXV, requiredItem("kubejs:force_field_glass") },
                { OpV, requiredItem("kubejs:force_field_glass") },
                { MAX, requiredItem("kubejs:force_field_glass") },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(PLATE, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.plate, GTLMaterials.Quantanium) },
                { 11, new MaterialEntry(TagPrefix.plate, GTLMaterials.Adamantium) },
                { 12, new MaterialEntry(TagPrefix.plate, GTLMaterials.Vibranium) },
                { 13, new MaterialEntry(TagPrefix.plate, GTLMaterials.Draconium) },
                { 14, new MaterialEntry(TagPrefix.plate, GTLMaterials.Chaos) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(HULL_PLATE, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.plate, GTLMaterials.Polyetheretherketone) },
                { 10, new MaterialEntry(TagPrefix.plate, GTLMaterials.Polyetheretherketone) },
                { 11, new MaterialEntry(TagPrefix.plate, GTLMaterials.Zylon) },
                { 12, new MaterialEntry(TagPrefix.plate, GTLMaterials.Zylon) },
                { 13, new MaterialEntry(TagPrefix.plate,
                        GTLMaterials.FullerenePolymerMatrixPulp) },
                { 14, new MaterialEntry(TagPrefix.plate, GTLMaterials.Radox) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(ROTOR, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.rotor, GTMaterials.Neutronium) },
                { 10, new MaterialEntry(TagPrefix.rotor, GTLMaterials.Quantanium) },
                { 11, new MaterialEntry(TagPrefix.rotor, GTLMaterials.Adamantium) },
                { 12, new MaterialEntry(TagPrefix.rotor, GTLMaterials.Vibranium) },
                { 13, new MaterialEntry(TagPrefix.rotor, GTLMaterials.Draconium) },
                { 14, new MaterialEntry(TagPrefix.rotor, GTLMaterials.TranscendentMetal) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(COIL_HEATING, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.wireGtDouble, GTLMaterials.AbyssalAlloy) },
                { 10, new MaterialEntry(TagPrefix.wireGtDouble, GTLMaterials.TitanSteel) },
                { 11, new MaterialEntry(TagPrefix.wireGtDouble, GTLMaterials.Adamantine) },
                { 12, new MaterialEntry(TagPrefix.wireGtDouble,
                        GTLMaterials.NaquadriaticTaranium) },
                { 13, new MaterialEntry(TagPrefix.wireGtDouble, GTLMaterials.Starmetal) },
                { 14, new MaterialEntry(TagPrefix.wireGtDouble, GTLMaterials.Hypogen) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(COIL_HEATING_DOUBLE, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.wireGtQuadruple, GTLMaterials.AbyssalAlloy) },
                { 10, new MaterialEntry(TagPrefix.wireGtQuadruple, GTLMaterials.TitanSteel) },
                { 11, new MaterialEntry(TagPrefix.wireGtQuadruple, GTLMaterials.Adamantine) },
                { 12, new MaterialEntry(TagPrefix.wireGtQuadruple,
                        GTLMaterials.NaquadriaticTaranium) },
                { 13, new MaterialEntry(TagPrefix.wireGtQuadruple, GTLMaterials.Starmetal) },
                { 14, new MaterialEntry(TagPrefix.wireGtQuadruple, GTLMaterials.Hypogen) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(COIL_ELECTRIC, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.wireGtOctal, GTLMaterials.Mithril) },
                { 10, new MaterialEntry(TagPrefix.wireGtOctal, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.wireGtHex, GTLMaterials.Mithril) },
                { 12, new MaterialEntry(TagPrefix.wireGtHex, GTLMaterials.Mithril) },
                { 13, new MaterialEntry(TagPrefix.wireGtOctal, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.wireGtHex, GTLMaterials.Crystalmatrix) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(ROD_DISTILLATION, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.spring, GTMaterials.Europium) },
                { 10, new MaterialEntry(TagPrefix.spring, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.spring, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.spring, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.spring, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.spring, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(ROD_ELECTROMAGNETIC, Stream.of(new Object[][] {
                { 5, new MaterialEntry(TagPrefix.rod, GTMaterials.VanadiumGallium) },
                { 6, new MaterialEntry(TagPrefix.rod, GTMaterials.VanadiumGallium) },
                { 7, new MaterialEntry(TagPrefix.rod, GTMaterials.NiobiumTitanium) },
                { 8, new MaterialEntry(TagPrefix.rod, GTMaterials.NiobiumTitanium) },
                { 9, requiredItem("kubejs:netherite_rod") },
                { 10, requiredItem("kubejs:netherite_rod") },
                { 11, new MaterialEntry(TagPrefix.rod, GTLMaterials.Mithril) },
                { 12, new MaterialEntry(TagPrefix.rod, GTLMaterials.Mithril) },
                { 13, new MaterialEntry(TagPrefix.rod, GTLMaterials.Echoite) },
                { 14, new MaterialEntry(TagPrefix.rod, GTLMaterials.Echoite) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(PIPE_REACTOR, Stream.of(new Object[][] {
                { 9, new MaterialEntry(TagPrefix.pipeNormalFluid, GTMaterials.Polybenzimidazole) },
                { 10, new MaterialEntry(TagPrefix.pipeLargeFluid, GTMaterials.Polybenzimidazole) },
                { 11, new MaterialEntry(TagPrefix.pipeHugeFluid, GTMaterials.Polybenzimidazole) },
                { 12, new MaterialEntry(TagPrefix.pipeNormalFluid,
                        GTLMaterials.FullerenePolymerMatrixPulp) },
                { 13, new MaterialEntry(TagPrefix.pipeLargeFluid,
                        GTLMaterials.FullerenePolymerMatrixPulp) },
                { 14, new MaterialEntry(TagPrefix.pipeHugeFluid,
                        GTLMaterials.FullerenePolymerMatrixPulp) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(POWER_COMPONENT, Stream.of(new Object[][] {
                { 10, requiredItem("kubejs:nm_chip") },
                { 11, requiredItem("kubejs:pm_chip") },
                { 12, requiredItem("kubejs:pm_chip") },
                { 13, requiredItem("kubejs:fm_chip") },
                { 14, requiredItem("kubejs:fm_chip") },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(VOLTAGE_COIL, Stream.of(new Object[][] {
                { 9, requiredItem("kubejs:uhv_voltage_coil") },
                { 10, requiredItem("kubejs:uev_voltage_coil") },
                { 11, requiredItem("kubejs:uiv_voltage_coil") },
                { 12, requiredItem("kubejs:uxv_voltage_coil") },
                { 13, requiredItem("kubejs:opv_voltage_coil") },
                { 14, requiredItem("kubejs:max_voltage_coil") },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        append(SPRING, Stream.of(new Object[][] {
                { 10, new MaterialEntry(TagPrefix.spring, GTLMaterials.Mithril) },
                { 11, new MaterialEntry(TagPrefix.spring, GTMaterials.Neutronium) },
                { 12, new MaterialEntry(TagPrefix.spring, GTLMaterials.Taranium) },
                { 13, new MaterialEntry(TagPrefix.spring, GTLMaterials.Crystalmatrix) },
                { 14, new MaterialEntry(TagPrefix.spring, GTLMaterials.CosmicNeutronium) },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));

        BUFFER = append(CraftingComponent.of("gtlcore_buffer", ItemStack.EMPTY), Stream.of(new Object[][] {

                { 1, GTMachines.BUFFER[1].asStack() },
                { 2, GTMachines.BUFFER[2].asStack() },
                { 3, GTMachines.BUFFER[3].asStack() },
                { 4, GTMachines.BUFFER[4].asStack() },
                { 5, GTMachines.BUFFER[5].asStack() },
                { 6, GTMachines.BUFFER[6].asStack() },
                { 7, GTMachines.BUFFER[7].asStack() },
                { 8, GTMachines.BUFFER[8].asStack() },
                { 9, GTMachines.BUFFER[9].asStack() },
                { 10, GTMachines.BUFFER[10].asStack() },
                { 11, GTMachines.BUFFER[11].asStack() },
                { 12, GTMachines.BUFFER[12].asStack() },
                { 13, GTMachines.BUFFER[13].asStack() },
                { 14, GTMachines.BUFFER[14].asStack() },

        }).collect(Collectors.toMap(data -> (Integer) data[0], data -> data[1])));
    }
}
