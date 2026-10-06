package org.gtlcore.gtlcore.mixin.gtm.registry;

import org.gtlcore.gtlcore.common.data.GTLRegistryTooltips;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.GTLLargeCombustionEngineMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.GeneratorArrayMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.generator.MegaTurbineMachine;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MachineInstanceFactory;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;
import com.gregtechceu.gtceu.api.registry.registrate.builder.MachineBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTMaterialItems;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.common.machine.multiblock.part.RotorHolderPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DualHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.storage.BufferMachine;
import com.gregtechceu.gtceu.common.mui.GTSingleblockMachinePanels;
import com.gregtechceu.gtceu.api.machine.SimpleGeneratorMachine;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.fluids.FluidType;
import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Locale;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import java.util.stream.IntStream;

import static com.gregtechceu.gtceu.api.GTValues.*;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;
import static com.gregtechceu.gtceu.common.registry.GTRegistration.REGISTRATE;
import static com.gregtechceu.gtceu.api.machine.property.GTMachineModelProperties.IS_FORMED;
import static com.gregtechceu.gtceu.common.data.models.GTMachineModels.*;
import static com.gregtechceu.gtceu.utils.FormattingUtil.toEnglishName;

/** Core policy on the native factories. Entries are resolved by NeoForge's registry events. */
@Mixin(GTMachineUtils.class)
public abstract class GTMachineUtilsMixin {
    @Unique
    @SuppressWarnings("unchecked")
    private static <M extends MetaMachine> MachineEntry<MachineDefinition>[] gtlcore$register(
            GTRegistrate registrate, String name, MachineInstanceFactory.Tiered<M> factory,
            BiFunction<Integer, MachineBuilder<MachineDefinition, M, ?>, MachineEntry<MachineDefinition>> builder,
            int... tiers) {
        MachineEntry<MachineDefinition>[] entries = new MachineEntry[GTValues.TIER_COUNT];
        for (int tier : tiers) {
            entries[tier] = builder.apply(tier, registrate
                    .machine(VN[tier].toLowerCase(Locale.ROOT) + "_" + name,
                            info -> factory.buildMachine(info, tier)).tier(tier));
        }
        return entries;
    }

    @Inject(method = "registerTieredMachines", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private static <M extends MetaMachine> void gtlcore$tierRanges(
            GTRegistrate registrate, String name, MachineInstanceFactory.Tiered<M> factory,
            BiFunction<Integer, MachineBuilder<MachineDefinition, M, ?>, MachineEntry<MachineDefinition>> builder,
            int[] tiers, CallbackInfoReturnable<MachineEntry<MachineDefinition>[]> cir) {
        if (registrate != REGISTRATE) return;
        int[] replacement = switch (name) {
            case "input_bus", "output_bus" -> GTMachineUtils.ALL_TIERS;
            case "energy_input_hatch_4a", "energy_output_hatch_4a", "energy_input_hatch_16a",
                    "energy_output_hatch_16a", "dual_input_hatch", "dual_output_hatch", "buffer" ->
                    GTValues.tiersBetween(LV, GTCEuAPI.isHighTier() ? MAX : UHV);
            case "rotor_holder" -> new int[] { HV, EV, IV, LuV, ZPM, UV, UHV, UEV };
            case "parallel_hatch" -> GTValues.tiersBetween(IV, MAX);
            default -> name.matches("[0-9]+a_laser_(target|source)_hatch") ?
                    GTValues.tiersBetween(IV, GTCEuAPI.isHighTier() ? MAX : UHV) : null;
        };
        // Use GT's builder and factory so model properties, menus and part abilities survive the port.
        if (replacement != null) cir.setReturnValue(gtlcore$register(registrate, name, factory, (tier, nativeBuilder) -> {
            if (name.equals("input_bus") || name.equals("output_bus")) {
                boolean input = name.equals("input_bus");
                var ability = input ? PartAbility.IMPORT_ITEMS : PartAbility.EXPORT_ITEMS;
                var steamAbility = input ? PartAbility.STEAM_IMPORT_ITEMS : PartAbility.STEAM_EXPORT_ITEMS;
                return nativeBuilder.langValue(VNF[tier] + (input ? " Input Bus" : " Output Bus"))
                        .rotationState(RotationState.ALL).abilities(tier == ULV ? new PartAbility[] { ability, steamAbility } : new PartAbility[] { ability })
                        .modelProperty(IS_FORMED, false)
                        .colorOverlayTieredHullModel(input ? OVERLAY_ITEM_HATCH_INPUT : OVERLAY_ITEM_HATCH_OUTPUT,
                                "overlay_pipe", input ? "overlay_pipe_in_emissive" : "overlay_pipe_out_emissive")
                        .tooltips(Component.translatable(input ? "gtceu.machine.item_bus.import.tooltip" : "gtceu.machine.item_bus.export.tooltip"),
                                Component.translatable("gtceu.universal.tooltip.item_storage_capacity", (tier + 1) * (tier + 1))).register();
            }
            if (name.equals("buffer")) {
                return nativeBuilder.langValue("%s Buffer %s".formatted(VLVH[tier], VLVT[tier]))
                        .rotationState(RotationState.NONE)
                        .model(createSingleOverlayTieredHullMachineModel(GTCEu.id("block/overlay/machine/overlay_buffer"),
                                GTCEu.id("block/overlay/machine/overlay_buffer_emissive")))
                        .tooltips(Component.translatable("gtceu.machine.buffer.tooltip"),
                                Component.translatable("gtceu.universal.tooltip.item_storage_capacity", BufferMachine.getInventorySize(tier)),
                                Component.translatable("gtceu.universal.tooltip.fluid_storage_capacity_mult", BufferMachine.getTankSize(tier),
                                        FluidHatchPartMachine.getTankCapacity(DualHatchPartMachine.INITIAL_TANK_CAPACITY, tier)))
                        .tooltipBuilder(GTLRegistryTooltips.MODIFY).register();
            }
            if (name.equals("parallel_hatch")) {
                return nativeBuilder.langValue(tier + " Parallel Control Hatch").rotationState(RotationState.ALL)
                        .abilities(PartAbility.PARALLEL_HATCH).modelProperty(IS_FORMED, false)
                        .workableTieredHullModel(GTCEu.id("block/machines/parallel_hatch_mk" + (tier - 4)))
                        .tooltips(Component.translatable("gtceu.machine.parallel_hatch_mk" + (tier + 2) + ".tooltip"))
                        .tooltipBuilder(GTLRegistryTooltips.MODIFY).register();
            }
            return builder.apply(tier, nativeBuilder);
        }, replacement));
    }

    @Inject(method = "registerSimpleGenerator", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private static void gtlcore$generator(GTRegistrate registrate, String name, Supplier<GTRecipeType> recipeType,
            Int2IntFunction tankScalingFunction, float hazardStrength, int[] tiers,
            CallbackInfoReturnable<MachineEntry<MachineDefinition>[]> cir) {
        if (registrate != REGISTRATE) return;
        cir.setReturnValue(gtlcore$register(registrate, name,
                (info, tier) -> new SimpleGeneratorMachine(info, tier, hazardStrength * tier, tankScalingFunction),
                (tier, builder) -> builder
                        .langValue("%s %s Generator %s".formatted(VLVH[tier], toEnglishName(name), VLVT[tier]))
                        .rotationState(RotationState.ALL).recipeType(recipeType)
                        .ui(GTSingleblockMachinePanels.GENERAL_MACHINE)
                        .recipeModifier(SimpleGeneratorMachine::recipeModifier, true)
                        .addOutputLimit(ItemRecipeCapability.CAP, 0).addOutputLimit(FluidRecipeCapability.CAP, 0)
                        .simpleGeneratorModel(GTCEu.id("block/generators/" + name))
                        .tooltips(() -> Component.translatable("gtceu.machine.efficiency.tooltip",
                                GeneratorArrayMachine.getEfficiency(recipeType.get(), tier)).append("%"))
                        .tooltips(Component.translatable("gtceu.universal.tooltip.ampere_out",
                                GeneratorArrayMachine.getAmperage(tier)))
                        .tooltips(GTMachineUtils.workableTiered(tier, V[tier],
                                Math.multiplyExact(V[tier] * 64, GeneratorArrayMachine.getAmperage(tier)), recipeType,
                                tankScalingFunction.applyAsInt(tier), false))
                        .tooltipBuilder(GTLRegistryTooltips.MODIFY).register(), tiers));
    }

    @Inject(method = "registerLargeCombustionEngine", at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private static void gtlcore$combustion(GTRegistrate registrate, String name, int tier,
            Supplier<? extends Block> casing, Supplier<? extends Block> gear, Supplier<? extends Block> intake,
            ResourceLocation texture, ResourceLocation overlay,
            CallbackInfoReturnable<MachineEntry<MultiblockMachineDefinition>> cir) {
        if (registrate != REGISTRATE) return;
        cir.setReturnValue(registrate.multiblock(name, info -> new GTLLargeCombustionEngineMachine(info, tier))
                .rotationState(RotationState.ALL).recipeType(name.equals("large_semi_fluid_generator") ?
                        GTLRecipeTypes.SEMI_FLUID_GENERATOR_FUELS : GTRecipeTypes.COMBUSTION_GENERATOR_FUELS).generator(true)
                .recipeModifier(GTLLargeCombustionEngineMachine::recipeModifier, true).appearanceBlock(casing)
                .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, RIGHT)
                        .slice("XXX", "XDX", "XXX").slice("XCX", "CGC", "XCX")
                        .slice("XCX", "CGC", "XCX").slice("AAA", "AYA", "AAA")
                        .where('X', blocks(casing.get())).where('G', blocks(gear.get()))
                        .where('C', blocks(casing.get()).setMinGlobalLimited(3)
                                .and(autoAbilities(definition.getRecipeTypes(), false, false, true, true, true, true))
                                .and(autoAbilities(true, true, false)))
                        .where('D', ability(PartAbility.OUTPUT_ENERGY,
                                IntStream.rangeClosed(ULV, UHV).filter(t -> t >= tier).toArray())
                                .addTooltips(Component.translatable("gtceu.multiblock.pattern.error.limited.1", VN[tier])))
                        .where('A', blocks(intake.get()).addTooltips(Component.translatable("gtceu.multiblock.pattern.clear_amount_1")))
                        .where('Y', controller(definition)).build())
                .recoveryItems(() -> new ItemLike[] { GTMaterialItems.MATERIAL_ITEMS.get(TagPrefix.dustTiny, GTMaterials.Ash).get() })
                .workableCasingModel(texture, overlay)
                .tooltips(Component.translatable("gtceu.universal.tooltip.base_production_eut", V[tier] * 4),
                        Component.translatable("gtceu.universal.tooltip.uses_per_hour_lubricant", FluidType.BUCKET_VOLUME),
                        Component.translatable(tier > EV ? "gtceu.machine.large_combustion_engine.tooltip.boost_extreme" :
                                "gtceu.machine.large_combustion_engine.tooltip.boost_regular", V[tier] * (tier > EV ? 16 : 12)))
                .tooltipBuilder(GTLRegistryTooltips.MODIFY).register());
    }

    // Hook the overload with needsMuffler; the shorter overload delegates here exactly once.
    @Inject(method = "registerLargeTurbine(Lcom/gregtechceu/gtceu/api/registry/registrate/GTRegistrate;Ljava/lang/String;ILjava/util/function/Supplier;Ljava/util/function/Supplier;Ljava/util/function/Supplier;Lnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Z)Lcom/gregtechceu/gtceu/api/registry/registrate/entry/MachineEntry;",
            at = @At("HEAD"), remap = false, cancellable = true, require = 1)
    private static void gtlcore$turbine(GTRegistrate registrate, String name, int tier, Supplier<GTRecipeType> recipeType,
            Supplier<? extends Block> casing, Supplier<? extends Block> gear, ResourceLocation texture,
            ResourceLocation overlay, boolean needsMuffler,
            CallbackInfoReturnable<MachineEntry<MultiblockMachineDefinition>> cir) {
        if (registrate != REGISTRATE) return;
        int amperage = switch (name) { case "rocket_large_turbine" -> 12; case "supercritical_steam_turbine" -> 16; default -> 8; };
        cir.setReturnValue(registrate.multiblock(name, info -> new MegaTurbineMachine(info, tier, amperage))
                .rotationState(RotationState.ALL).recipeType(recipeType).generator(true)
                .recipeModifier(MegaTurbineMachine::recipeModifier, true).appearanceBlock(casing)
                .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, RIGHT)
                        .slice("CCCC", "CHHC", "CCCC").slice("CHHC", "RGGR", "CHHC").slice("CCCC", "CSHC", "CCCC")
                        .where('S', controller(definition)).where('G', blocks(gear.get())).where('C', blocks(casing.get()))
                        .where('R', gtlcore$rotor(tier).setExactLimit(1).and(abilities(PartAbility.OUTPUT_ENERGY).setExactLimit(1)))
                        .where('H', blocks(casing.get())
                                .and(autoAbilities(definition.getRecipeTypes(), false, false, true, true, true, true))
                                .and(autoAbilities(true, needsMuffler, false))).build())
                .recoveryItems(() -> new ItemLike[] { GTMaterialItems.MATERIAL_ITEMS.get(TagPrefix.dustTiny, GTMaterials.Ash).get() })
                .workableCasingModel(texture, overlay)
                .tooltips(Component.translatable("gtceu.universal.tooltip.base_production_eut", V[tier] * amperage),
                        Component.translatable("gtceu.multiblock.turbine.efficiency_tooltip", VNF[tier]))
                .tooltipBuilder(GTLRegistryTooltips.MODIFY).register());
    }

    @Unique
    private static MultiPredicate gtlcore$rotor(int tier) {
        return builder("GTLNativeRotor")
                .predicate(context -> MetaMachine.getMachine(context.level(), context.pos()) instanceof RotorHolderPartMachine rotor &&
                        rotor.getTier() >= tier &&
                        context.level().getBlockState(context.pos().relative(rotor.getFrontFacing())).isAir())
                .candidatesSupplier(() -> PartAbility.ROTOR_HOLDER.getBlocks(GTValues.tiersBetween(tier, UEV)).stream()
                        .map(BlockInfo::fromBlock).toList())
                .toMultiPredicate().addTooltips(Component.translatable("gtceu.multiblock.pattern.clear_amount_3"))
                .addTooltips(Component.translatable("gtceu.multiblock.pattern.error.limited.1", VN[tier]));
    }
}
