package org.gtlcore.gtlcore.common.recipe;

import org.gtlcore.gtlcore.common.data.GTLCallbackMaterials;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.data.recipe.GenerateDisassembly;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.minecraft.network.chat.Component;

import brachy.modularui.api.drawable.Text;
import java.text.NumberFormat;

/** Constructor-time installation covers world recipe reloads and native data generation alike. */
public final class RecipeModify {
    private RecipeModify() {}
    public static void init() {}

    public static void modifyOnRegistration(GTRecipeType type) {
        switch (type.id.toString()) {
            case "gtceu:sifter" -> maxIO(type, 1, 6, 1, 1);
            // Core's raw-ore decompression route retains its non-consumable configuration circuit.
            case "gtceu:forge_hammer" -> type.maxInputs.put(ItemRecipeCapability.CAP, 2);
            case "gtceu:assembler" -> type.getRecipeBuilder().onSave(GenerateDisassembly::generateDisassembly);
            case "gtceu:cutter" -> type.getRecipeBuilder().onSave(GTLRecipeCallbacks.cutting(
                    () -> GTLCallbackMaterials.GradePurifiedWater8.getFluid(),
                    () -> GTLCallbackMaterials.GradePurifiedWater16.getFluid()));
            case "gtceu:circuit_assembler" -> type.getRecipeBuilder().onSave(GTLRecipeCallbacks.circuitAssembly(
                    () -> GTLCallbackMaterials.MutatedLivingSolder.getFluid(),
                    () -> GTLCallbackMaterials.SuperMutatedLivingSolder.getFluid()));
            case "gtceu:plasma_generator" -> type.getRecipeBuilder().onSave(GTLRecipeCallbacks.heatExchange(
                    () -> GTLRecipeTypes.HEAT_EXCHANGER_RECIPES.value(),
                    () -> GTLCallbackMaterials.SupercriticalSteam.getFluid()));
            case "gtceu:laser_engraver" -> {
                maxIO(type, 2, 1, 2, 1);
                var focus = GTLRecipeCallbacks.focusedEngraving(
                        () -> GTLRecipeTypes.DIMENSIONAL_FOCUS_ENGRAVING_ARRAY_RECIPES.value(),
                        () -> GTLCallbackMaterials.Photoresist.getFluid(),
                        () -> GTLCallbackMaterials.EuvPhotoresist.getFluid());
                var previous = type.getRecipeBuilder().onSave;
                type.getRecipeBuilder().onSave(previous == null ? focus : previous.andThen(focus));
            }
            case "gtceu:fusion_reactor" -> {
                var modifiers = type.getUiLayout().getRecipeUIModifiers();
                // The pinned GT baseline installs its EU-to-start label as the sole default modifier.
                modifiers.clear();
                modifiers.add((recipe, widget) -> {
                    long millions = recipe.data.getLong("eu_to_start") / 1000000;
                    widget.textComponents.child(Text.of(Component.translatable("gtceu.recipe.eu_to_starts",
                            NumberFormat.getCompactNumberInstance().format(millions),
                            GTLRecipeCallbacks.fusionTier(millions))).asWidget());
                });
            }
        }
    }

    private static void maxIO(GTRecipeType type, int itemInputs, int itemOutputs, int fluidInputs, int fluidOutputs) {
        type.maxInputs.put(ItemRecipeCapability.CAP, itemInputs);
        type.maxOutputs.put(ItemRecipeCapability.CAP, itemOutputs);
        type.maxInputs.put(FluidRecipeCapability.CAP, fluidInputs);
        type.maxOutputs.put(FluidRecipeCapability.CAP, fluidOutputs);
    }
}
