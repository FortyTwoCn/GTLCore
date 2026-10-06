package org.gtlcore.gtlcore.mixin.gtm.api.recipe.gui;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.NativeContentState;
import org.gtlcore.gtlcore.api.recipe.IRecipeChanceDisplay;
import org.gtlcore.gtlcore.api.recipe.IRecipeTooltip;
import org.gtlcore.gtlcore.integration.jei.RecipeChanceTooltip;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.Unique;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;

import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.recipe.gui.CapabilityContentBuilder;
import com.gregtechceu.gtceu.api.recipe.gui.GTRecipeTypeUILayout;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import brachy.modularui.integration.recipeviewer.RecipeViewerSlotWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Extend native recipe slots without replacing their chance, range, tick, or research UI. */
@Mixin(GTRecipeTypeUILayout.class)
public abstract class CapabilityContentBuilderMixin {
    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$longQuantityTooltip(CallbackInfo ci) {
        var info = ((GTRecipeTypeUILayout) (Object) this).capabilityInfo(ItemRecipeCapability.CAP);
        CapabilityContentBuilder original = info.capabilityWidgetBuilder;
        if (original == null) return;
        info.capabilityWidgetBuilder = (widget, content, io, perTick, recipeType, recipe, chanceTier, recipeTier) -> {
            // Native tooltip builders compose. Rebuilding a slot after OC must discard the previous tier's builders.
            if (widget instanceof RecipeViewerSlotWidget<?, ?> slot) ((IRecipeTooltip) (Object) slot.tooltip()).gtlcore$resetContentBuilders();
            Content preview = gtlcore$chancePreview(content, ItemRecipeCapability.CAP, recipeTier, chanceTier, io);
            original.buildWidgetContent(widget, preview, io, perTick, recipeType, recipe, chanceTier, recipeTier);
            if (preview != content && widget instanceof RecipeViewerSlotWidget<?, ?> slot) {
                slot.chance((float) ((IRecipeChanceDisplay) (Object) preview).gtlcore$getDisplayChance() / Math.max(1, preview.maxChance()));
            }
            var ingredient = ItemRecipeCapability.CAP.of(content.content());
            if (LongIngredient.get(ingredient) != null && widget instanceof RecipeViewerSlotWidget<?, ?> slot) {
                RecipeViewerSlotWidget<net.minecraft.world.item.ItemStack, ?> itemSlot =
                        (RecipeViewerSlotWidget<net.minecraft.world.item.ItemStack, ?>) slot;
                var mapping = itemSlot.renderMappingFunction();
                itemSlot.renderMappingFunction(stack -> mapping.apply(stack).copyWithCount(1));
                slot.tooltipBuilder(tooltip -> tooltip.addLine(
                        Component.translatable("gtceu.machine.quantum_chest.items_stored")
                                .withStyle(ChatFormatting.DARK_AQUA)
                                .append(Component.literal(Long.toString(LongIngredient.amount(ingredient))))));
            }
        };
    }

    @Inject(method = "<init>", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$longFluidQuantityTooltip(CallbackInfo ci) {
        var info = ((GTRecipeTypeUILayout) (Object) this).capabilityInfo(FluidRecipeCapability.CAP);
        CapabilityContentBuilder original = info.capabilityWidgetBuilder;
        if (original == null) return;
        info.capabilityWidgetBuilder = (widget, content, io, perTick, recipeType, recipe, chanceTier, recipeTier) -> {
            if (widget instanceof RecipeViewerSlotWidget<?, ?> slot) ((IRecipeTooltip) (Object) slot.tooltip()).gtlcore$resetContentBuilders();
            Content preview = gtlcore$chancePreview(content, FluidRecipeCapability.CAP, recipeTier, chanceTier, io);
            original.buildWidgetContent(widget, preview, io, perTick, recipeType, recipe, chanceTier, recipeTier);
            if (widget instanceof RecipeViewerSlotWidget<?, ?> slot) {
                slot.tooltipBuilder(tooltip -> {
                    var lines = new ArrayList<Component>();
                    RecipeChanceTooltip.add(preview, recipe.getChanceLogicForCapability(FluidRecipeCapability.CAP, io, perTick), lines, io);
                    lines.forEach(tooltip::addLine);
                });
                if (preview != content) slot.chance((float) ((IRecipeChanceDisplay) (Object) preview).gtlcore$getDisplayChance() / Math.max(1, preview.maxChance()));
            }
            var ingredient = FluidRecipeCapability.CAP.of(content.content());
            if (LongFluidIngredient.get(ingredient) != null && widget instanceof RecipeViewerSlotWidget<?, ?> slot) {
                ((IRecipeTooltip) (Object) slot.tooltip()).gtlcore$hideClampedFluidAmount(true);
                slot.tooltipBuilder(tooltip -> tooltip.addLine(Component.literal(
                        Long.toString(LongFluidIngredient.amount(ingredient)) + " mB").withStyle(ChatFormatting.DARK_AQUA)));
            }
        };
    }
    @Unique
    private static Content gtlcore$chancePreview(Content content, RecipeCapability<?> cap, int recipeTier, int selectedTier, com.gregtechceu.gtceu.api.capability.recipe.IO io) {
        if (NativeContentState.of(content).gtlcore$getTierChanceBoost() == 0) return content;
        var copy = content.copy(cap);
        NativeContentState.of(copy).gtlcore$setTooltipIO(io);
        ((IRecipeChanceDisplay) (Object) copy).gtlcore$setChanceDisplayTiers(recipeTier, selectedTier);
        return copy;
    }
}
