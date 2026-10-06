package org.gtlcore.gtlcore.mixin.gtmt;

import com.gregtechceu.gtceu.common.data.machines.GTMachineUtils;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.registry.registrate.builder.MachineBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import org.gtlcore.gtlcore.common.data.GTLCompassLinks;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.utils.FormattingUtil;

import net.minecraft.network.chat.Component;

import com.hepdd.gtmthings.data.CustomMachines;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.BiFunction;

import static com.gregtechceu.gtceu.api.GTValues.VNF;
import static com.hepdd.gtmthings.common.block.machine.multiblock.part.HugeBusPartMachine.INV_MULTIPLE;

@Mixin(CustomMachines.class)
public class CustomMachinesMixin {

    @Redirect(method = "registerTieredMachines",
              at = @At(value = "INVOKE",
                       target = "Ljava/util/function/BiFunction;apply(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"),
              remap = false)
    private static <R, T, U> R registerTieredMachines(BiFunction instance, T t, U u, @Local(name = "name") String name) {
        switch (name) {
            case "huge_item_import_bus" -> {
                BiFunction<Integer, MachineBuilder<MachineDefinition, ?, ?>, MachineEntry<MachineDefinition>> b = (tier, builder) -> builder.langValue(VNF[tier] + " Input Bus")
                        .rotationState(RotationState.ALL)
                        .abilities(
                                tier == 0 ? new PartAbility[] { PartAbility.IMPORT_ITEMS, PartAbility.STEAM_IMPORT_ITEMS } :
                                        new PartAbility[] { PartAbility.IMPORT_ITEMS })
                        .colorOverlayTieredHullModel("overlay_pipe_in_emissive", null, "overlay_item_hatch")
                        .tooltips(Component.translatable("gtmthings.machine.huge_item_bus.import.tooltip"),
                                Component.translatable("gtceu.universal.tooltip.item_storage_capacity",
                                        (1 + tier) * INV_MULTIPLE - 1))
                        .onRegister(GTLCompassLinks.node("parts", "item_bus"))
                        .register();
                return (R) b.apply((Integer) t, (MachineBuilder) u);
            }
            case "huge_item_export_bus" -> {
                BiFunction<Integer, MachineBuilder<MachineDefinition, ?, ?>, MachineEntry<MachineDefinition>> b = (tier, builder) -> builder.langValue(VNF[tier] + " Output Bus")
                        .rotationState(RotationState.ALL)
                        .abilities(
                                tier == 0 ? new PartAbility[] { PartAbility.EXPORT_ITEMS, PartAbility.STEAM_EXPORT_ITEMS } :
                                        new PartAbility[] { PartAbility.EXPORT_ITEMS })
                        .colorOverlayTieredHullModel("overlay_pipe_out_emissive", null, "overlay_item_hatch")
                        .tooltips(Component.translatable("gtmthings.machine.huge_item_bus.export.tooltip"),
                                Component.translatable("gtceu.universal.tooltip.item_storage_capacity",
                                        (1 + tier) * INV_MULTIPLE - 1))
                        .onRegister(GTLCompassLinks.node("parts", "item_bus"))
                        .register();
                return (R) b.apply((Integer) t, (MachineBuilder) u);
            }
            case "huge_dual_hatch" -> {
                BiFunction<Integer, MachineBuilder<MachineDefinition, ?, ?>, MachineEntry<MachineDefinition>> b = (tier, builder) -> builder.langValue(VNF[tier] + " Huge Input Dual Hatch")
                        .rotationState(RotationState.ALL)
                        .abilities(GTMachineUtils.DUAL_INPUT_HATCH_ABILITIES)
                        .colorOverlayTieredHullModel("overlay_pipe_in_emissive", null, "overlay_huge_dual_hatch")
                        .tooltips(Component.translatable("gtceu.machine.dual_hatch.import.tooltip"),
                                Component.translatable("gtceu.universal.tooltip.item_storage_capacity",
                                        (1 + tier) * INV_MULTIPLE - 1),
                                Component.translatable("gtceu.universal.tooltip.fluid_storage_capacity_mult", new Object[] { tier, FormattingUtil.formatNumbers(Integer.MAX_VALUE) }))
                        .onRegister(GTLCompassLinks.node("parts", "huge_dual_hatch"))
                        .register();
                return (R) b.apply((Integer) t, (MachineBuilder) u);
            }
        }
        return (R) instance.apply(t, u);
    }
}
