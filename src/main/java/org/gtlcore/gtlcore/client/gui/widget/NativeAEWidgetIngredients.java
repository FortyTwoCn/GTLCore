package org.gtlcore.gtlcore.client.gui.widget;

import org.gtlcore.gtlcore.integration.ae2.MEFluidUnits;
import org.gtlcore.gtlcore.utils.NumberUtils;
import appeng.api.stacks.*;
import brachy.modularui.ModularUI;
import brachy.modularui.integration.emi.EmiStackConverter;
import brachy.modularui.integration.jei.ModularUIJeiPlugin;
import brachy.modularui.integration.recipeviewer.entry.EntryList;
import brachy.modularui.integration.recipeviewer.entry.fluid.FluidStackList;
import brachy.modularui.integration.recipeviewer.entry.item.ItemStackList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import java.util.function.UnaryOperator;

/** Supplies component-sensitive native ingredients without changing the input widget's ghost-item contract. */
@OnlyIn(Dist.CLIENT)
public final class NativeAEWidgetIngredients {
    private NativeAEWidgetIngredients() {}
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static EntryList<Object> entries(GenericStack value) {
        EntryList<?> entries = value != null && value.what() instanceof AEFluidKey fluid ? FluidStackList.of(fluid.toStack(1000)) :
                value != null && value.what() instanceof AEItemKey item ? ItemStackList.of(item.toStack()) : new ItemStackList();
        return (EntryList) entries;
    }
    public static Object clickable(GenericStack value, Rect2i area) {
        var entries = entries(value);
        if (ModularUI.Mods.EMI.isLoaded()) return EmiStackConverter.convertToEmiEntry(entries, 1, UnaryOperator.identity());
        if (entries.isEmpty() || !ModularUI.Mods.JEI.isLoaded() || ModularUI.Mods.REI.isLoaded()) return null;
        var runtime = ModularUIJeiPlugin.getRuntime();
        if (runtime == null) return null;
        if (value.what() instanceof AEItemKey item) return runtime.getIngredientManager()
                .createClickableIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, item.toStack(), area, false).orElse(null);
        if (value.what() instanceof AEFluidKey fluid) return runtime.getIngredientManager()
                .createClickableIngredient(mezz.jei.api.neoforge.NeoForgeTypes.FLUID_STACK, fluid.toStack(1000), area, false).orElse(null);
        return null;
    }
    public static String amountText(GenericStack stack) {
        return stack.what() instanceof AEFluidKey ? MEFluidUnits.formatDisplayAmount(stack.amount()) : NumberUtils.formatLong(stack.amount());
    }
    public static void drawAmount(GuiGraphics graphics, GenericStack stack, int x, int y) {
        var text = amountText(stack);
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        graphics.pose().scale(0.5f, 0.5f, 1f);
        graphics.drawString(font, text, (x + 16) * 2 - font.width(text), (y + 16) * 2 - font.lineHeight, 0xFFFFFF, true);
        graphics.pose().popPose();
    }
}
