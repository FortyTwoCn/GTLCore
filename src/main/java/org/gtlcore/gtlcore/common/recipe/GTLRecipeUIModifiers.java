package org.gtlcore.gtlcore.common.recipe;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.recipe.gui.RecipeUIModifier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.integration.recipeviewer.RecipeSlotRole;
import brachy.modularui.integration.recipeviewer.RecipeViewerSlotWidget;
import brachy.modularui.integration.recipeviewer.entry.item.ItemStackList;

import java.util.List;
import java.util.function.Function;
import java.util.function.BiPredicate;

/** Data lines and cycling coil candidates in the native recipe viewer's automatically sized layout. */
public final class GTLRecipeUIModifiers {
    private GTLRecipeUIModifiers() {}

    public static final BiPredicate<ICoilType, Integer> STANDARD_COILS =
            (coil, temperature) -> coil.getCoilTemperature() >= temperature;
    public static final BiPredicate<ICoilType, Integer> PLASMA_COILS =
            (coil, temperature) -> coil.getCoilTemperature() == 273 ? temperature <= 32000 :
                    STANDARD_COILS.test(coil, temperature);

    public static RecipeUIModifier dataInfo(Function<CompoundTag, String> text) {
        return (recipe, widget) -> {
            String line = text.apply(recipe.data);
            if (line != null && !line.isEmpty()) widget.textComponents.child(Text.of(Component.literal(line)).asWidget());
        };
    }

    public static RecipeUIModifier componentInfo(Function<CompoundTag, Component> text) {
        return (recipe, widget) -> {
            Component line = text.apply(recipe.data);
            if (line != null && !line.getString().isEmpty()) widget.textComponents.child(Text.of(line).asWidget());
        };
    }

    public static List<ItemStack> coilCandidates(CompoundTag data, BiPredicate<ICoilType, Integer> allowed) {
        int temperature = data.getInt("ebf_temp");
        return GTCEuAPI.HEATING_COILS.entrySet().stream()
                .filter(coil -> allowed.test(coil.getKey(), temperature))
                .map(coil -> new ItemStack(coil.getValue().get())).toList();
    }

    public static RecipeUIModifier coils(BiPredicate<ICoilType, Integer> allowed) {
        return (recipe, widget) -> widget.textComponents.child(
                RecipeViewerSlotWidget.create(ItemStack.class)
                        .recipeSlotRole(RecipeSlotRole.RENDER_ONLY)
                        .value(ItemStackList.of(coilCandidates(recipe.data, allowed))));
    }
}
