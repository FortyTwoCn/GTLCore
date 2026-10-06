package org.gtlcore.gtlcore.mixin.gtm.api.recipe;

import org.gtlcore.gtlcore.api.recipe.IRecipeChanceDisplay;
import org.gtlcore.gtlcore.api.recipe.NativeContentState;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.mojang.serialization.Codec;
import brachy.modularui.screen.RichTooltip;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.recipe.chance.logic.ChanceLogic;
import org.gtlcore.gtlcore.integration.jei.RecipeChanceTooltip;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Content.class)
public abstract class ContentMixin implements NativeContentState, IRecipeChanceDisplay {
    @Unique private int gtlcore$tierChanceBoost;
    @Unique private IO gtlcore$tooltipIO = IO.OUT;
    @Override public IO gtlcore$getTooltipIO() { return gtlcore$tooltipIO; }
    @Override public void gtlcore$setTooltipIO(IO io) { gtlcore$tooltipIO = io; }
    @Unique private @Nullable String gtlcore$slotName;
    @Unique private @Nullable String gtlcore$uiName;
    @Unique private boolean gtlcore$hasDisplayTiers;
    @Unique private int gtlcore$recipeTier;
    @Unique private int gtlcore$selectedTier;
    @Override public int gtlcore$getTierChanceBoost() { return gtlcore$tierChanceBoost; }
    @Override public void gtlcore$setTierChanceBoost(int boost) { gtlcore$tierChanceBoost = boost; }
    @Override public String gtlcore$getSlotName() { return gtlcore$slotName; }
    @Override public void gtlcore$setSlotName(String name) { gtlcore$slotName = name == null || name.isEmpty() ? null : name; }
    @Override public String gtlcore$getUiName() { return gtlcore$uiName; }
    @Override public void gtlcore$setUiName(String name) { gtlcore$uiName = name == null || name.isEmpty() ? null : name; }
    @Override public void gtlcore$setChanceDisplayTiers(int recipeTier, int selectedTier) {
        gtlcore$hasDisplayTiers = true; gtlcore$recipeTier = recipeTier; gtlcore$selectedTier = selectedTier;
    }
    @Override public int gtlcore$getDisplayChance() { return Math.max(0, gtlcore$getUnclampedDisplayChance()); }
    @Override public int gtlcore$getUnclampedDisplayChance() {
        var content = (Content) (Object) this;
        return gtlcore$hasDisplayTiers ? NativeContentState.boostedChance(content, gtlcore$recipeTier, gtlcore$selectedTier) : content.chance();
    }
    @Override public int gtlcore$getChanceBoostTierCount() {
        if (!gtlcore$hasDisplayTiers || gtlcore$tierChanceBoost == 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE, Math.max(0L, (long) gtlcore$selectedTier - gtlcore$recipeTier - (gtlcore$recipeTier == 0 ? 1L : 0L)));
    }
    @Inject(method = "addChanceTooltips", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void gtlcore$boostedChanceTooltip(RichTooltip tooltip, Content content, ChanceLogic logic, CallbackInfo ci) {
        if (NativeContentState.of(content).gtlcore$getTierChanceBoost() == 0) return;
        var lines = new ArrayList<Component>();
        RecipeChanceTooltip.add(content, logic, lines, NativeContentState.of(content).gtlcore$getTooltipIO());
        lines.forEach(tooltip::addLine);
        ci.cancel();
    }
    @Inject(method = "codec", at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private static <T> void gtlcore$metadataCodec(RecipeCapability<T> capability, CallbackInfoReturnable<Codec<Content>> cir) {
        cir.setReturnValue(NativeContentState.codec(capability));
    }
    @Inject(method = "copy(Lcom/gregtechceu/gtceu/api/capability/recipe/RecipeCapability;)Lcom/gregtechceu/gtceu/api/recipe/content/Content;",
            at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$copyMetadata(RecipeCapability<?> cap, CallbackInfoReturnable<Content> cir) {
        NativeContentState.copy((Content) (Object) this, cir.getReturnValue());
    }
    @Inject(method = {"copy(Lcom/gregtechceu/gtceu/api/capability/recipe/RecipeCapability;Lcom/gregtechceu/gtceu/api/recipe/content/ContentModifier;)Lcom/gregtechceu/gtceu/api/recipe/content/Content;",
            "copyChanced"}, at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$copyModifiedMetadata(RecipeCapability<?> cap, ContentModifier modifier, CallbackInfoReturnable<Content> cir) {
        NativeContentState.copy((Content) (Object) this, cir.getReturnValue());
    }
}
