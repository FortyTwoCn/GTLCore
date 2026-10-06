package org.gtlcore.gtlcore.common.data.machines;

import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.gregtechceu.gtceu.GTCEu;

import org.gtlcore.gtlcore.common.machine.multiblock.generator.GTLLargeCombustionEngineMachine;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.common.data.GTMaterialItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ItemLike;
import net.neoforged.neoforge.fluids.FluidType;
import java.util.stream.IntStream;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;
import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;

/** Native queued registrations, isolated from the remainder of Core's machine declarations. */
public final class GTLGeneratorMachines {
    private GTLGeneratorMachines() {}

    public static final MachineEntry<MachineDefinition>[] SEMI_FLUID_GENERATOR = GTMachineUtils.registerSimpleGenerator(
            REGISTRATE, "semi_fluid", GTLRecipeTypes.SEMI_FLUID_GENERATOR_FUELS,
            GTMachineUtils.genericGeneratorTankSizeFunction, 0.1f, LV, MV, HV);
    public static final MachineEntry<MachineDefinition>[] ROCKET_ENGINE_GENERATOR = GTMachineUtils.registerSimpleGenerator(
            REGISTRATE, "rocket_engine", GTLRecipeTypes.ROCKET_ENGINE_FUELS,
            GTMachineUtils.genericGeneratorTankSizeFunction, 0.1f, EV, IV, LuV);
    public static final MachineEntry<MachineDefinition>[] NAQUADAH_REACTOR_GENERATOR = GTMachineUtils.registerSimpleGenerator(
            REGISTRATE, "naquadah_reactor", GTLRecipeTypes.NAQUADAH_REACTOR,
            GTMachineUtils.genericGeneratorTankSizeFunction, 0.1f, IV, LuV, ZPM);

    public static final MachineEntry<MultiblockMachineDefinition> LARGE_SEMI_FLUID_GENERATOR = registerSemiFluidEngine();

    // GT 8 freezes recipe types during registration; retain its native combustion-engine structure and behavior.
    private static MachineEntry<MultiblockMachineDefinition> registerSemiFluidEngine() {
        int tier = EV;
        java.util.function.Supplier<net.minecraft.world.level.block.Block> casing = GTBlocks.CASING_TITANIUM_STABLE::get;
        java.util.function.Supplier<net.minecraft.world.level.block.Block> gear = GTBlocks.CASING_STEEL_GEARBOX::get;
        java.util.function.Supplier<net.minecraft.world.level.block.Block> intake = GTBlocks.CASING_ENGINE_INTAKE::get;
        var casingTexture = GTCEu.id("block/casings/solid/machine_casing_stable_titanium");
        var overlayModel = GTCEu.id("block/multiblock/generator/large_combustion_engine");
        return REGISTRATE.multiblock("large_semi_fluid_generator", holder -> new GTLLargeCombustionEngineMachine(holder, tier))
                .rotationState(RotationState.ALL)
                .recipeType(GTLRecipeTypes.SEMI_FLUID_GENERATOR_FUELS)
                .generator(true)
                .recipeModifier(GTLLargeCombustionEngineMachine::recipeModifier, true)
                .appearanceBlock(casing)
                .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, RIGHT)
                        .slice("XXX", "XDX", "XXX")
                        .slice("XCX", "CGC", "XCX")
                        .slice("XCX", "CGC", "XCX")
                        .slice("AAA", "AYA", "AAA")
                        .where('X', blocks(casing.get()))
                        .where('G', blocks(gear.get()))
                        .where('C', blocks(casing.get()).setMinGlobalLimited(3)
                                .and(autoAbilities(definition.getRecipeTypes(), false, false, true, true, true, true))
                                .and(autoAbilities(true, true, false)))
                        .where('D',
                                ability(PartAbility.OUTPUT_ENERGY,
                                        IntStream.of(ULV, LV, MV, HV, EV, IV, LuV, ZPM, UV, UHV)
                                                .filter(t -> t >= tier)
                                                .toArray())
                                        .addTooltips(Component.translatable(
                                                "gtceu.multiblock.pattern.error.limited.1", VN[tier])))
                        .where('A',
                                blocks(intake.get())
                                        .addTooltips(Component.translatable("gtceu.multiblock.pattern.clear_amount_1")))
                        .where('Y', controller(blocks(definition.getBlock())))
                        .build())
                .recoveryItems(
                        () -> new ItemLike[] {
                                GTMaterialItems.MATERIAL_ITEMS.get(TagPrefix.dustTiny, GTMaterials.Ash).get() })
                .workableCasingModel(casingTexture, overlayModel)
                .tooltips(
                        Component.translatable("gtceu.universal.tooltip.base_production_eut", V[tier]),
                        Component.translatable("gtceu.universal.tooltip.uses_per_hour_lubricant",
                                FluidType.BUCKET_VOLUME),
                        tier > EV ?
                                Component.translatable("gtceu.machine.large_combustion_engine.tooltip.boost_extreme",
                                        V[tier] * 4) :
                                Component.translatable("gtceu.machine.large_combustion_engine.tooltip.boost_regular",
                                        V[tier] * 3))
                .register();
    }

    public static final MachineEntry<MultiblockMachineDefinition> ROCKET_LARGE_TURBINE = GTMachineUtils.registerLargeTurbine(
            REGISTRATE, "rocket_large_turbine", EV, GTLRecipeTypes.ROCKET_ENGINE_FUELS,
            GTBlocks.CASING_TITANIUM_TURBINE, GTBlocks.CASING_TITANIUM_GEARBOX,
            GTCEu.id("block/casings/mechanic/machine_casing_turbine_titanium"), GTCEu.id("block/multiblock/generator/large_gas_turbine"));

    public static void init() {}
}
