package org.gtlcore.gtlcore.mixin.gtm.client;

import org.gtlcore.gtlcore.api.recipe.IRecipeChanceDisplay;
import org.gtlcore.gtlcore.api.recipe.NativeContentState;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import brachy.modularui.screen.viewport.GuiContext;
import brachy.modularui.theme.WidgetTheme;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.gui.ContentOverlay;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Native ModularUI chance overlay; transient preview tiers belong to a detached slot content. */
@Mixin(ContentOverlay.class)
public abstract class ContentOverlayMixin {
    @Shadow @Final private Content content;
    @Inject(method = "draw", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$longItemAmount(GuiContext context, int x, int y, int width, int height, WidgetTheme theme, CallbackInfo ci) {
        if (!(content.content() instanceof SizedIngredient ingredient) || LongIngredient.get(ingredient) == null) return;
        long amount = LongIngredient.amount(ingredient);
        if (amount <= 1) return;
        var font = Minecraft.getInstance().font;
        String text = FormattingUtil.formatNumberReadable(amount, false, FormattingUtil.DECIMAL_FORMAT_1F, "");
        var graphics = context.getGraphics();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.pose().scale(0.5f, 0.5f, 1);
        graphics.drawString(font, text, (x + width - 1) * 2 - font.width(text), (y + height - 5) * 2, 0xFFFFFF, true);
        graphics.pose().popPose();
    }
    @Inject(method = "drawChance", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$boostedChanceOverlay(GuiGraphics graphics, float x, float y, int width, int height, CallbackInfo ci) {
        if (NativeContentState.of(content).gtlcore$getTierChanceBoost() == 0) return;
        int chance = ((IRecipeChanceDisplay) (Object) content).gtlcore$getDisplayChance();
        String percent = FormattingUtil.formatNumber2Places(100.0 * chance / Math.max(1, content.maxChance()));
        Component text = content.chance() == 0 ? Component.translatable("gtceu.gui.content.chance_nc_short") : Component.literal(percent + "%");
        var font = Minecraft.getInstance().font;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.pose().scale(0.5f, 0.5f, 1);
        graphics.drawString(font, text, (int) ((x + width / 3f) * 2 - font.width(text) + 23),
                (int) ((y + height / 3f + 6) * 2 - height), chance == 0 ? 0xFF0000 : 0xFFFF00, true);
        graphics.pose().popPose();
        ci.cancel();
    }
    @Inject(method = "drawFluidAmount", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$longFluidAmount(GuiGraphics graphics, float x, float y, int width, int height, CallbackInfo ci) {
        if (!(content.content() instanceof SizedFluidIngredient ingredient) || LongFluidIngredient.get(ingredient) == null) return;
        var font = Minecraft.getInstance().font;
        long amount = LongFluidIngredient.amount(ingredient);
        String text = FormattingUtil.formatBuckets(amount);
        if (font.width(text) > 32) text = FormattingUtil.formatNumberReadable(amount, true, FormattingUtil.DECIMAL_FORMAT_1F, "B");
        if (font.width(text) > 32) text = FormattingUtil.formatNumberReadable(amount, true, FormattingUtil.DECIMAL_FORMAT_0F, "B");
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        graphics.pose().scale(0.5f, 0.5f, 1);
        graphics.drawString(font, text, (int) ((x + width / 3f) * 2 - font.width(text) + 22),
                (int) ((y + height / 3f + 6) * 2), 0xFFFFFF, true);
        graphics.pose().popPose();
        ci.cancel();
    }
}
