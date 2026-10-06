package org.gtlcore.gtlcore.mixin.gtm.client;

import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.gui.GTRecipeViewerWidget;
import brachy.modularui.widgets.ButtonWidget;
import brachy.modularui.widgets.layout.Flow;
import net.neoforged.fml.loading.FMLLoader;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native viewer passes its selected tier twice; the boost formula needs the original recipe tier. */
@Mixin(GTRecipeViewerWidget.class)
public abstract class GTRecipeViewerWidgetMixin {
    @Shadow @Final private GTRecipe baseRecipe;
    @Shadow @Final public Flow textComponents;

    @Inject(method = "attachOverclockButton", at = @At("TAIL"), remap = false, require = 1)
    private void gtlcore$leaveSpaceForDebugButton(CallbackInfo ci) {
        if (!FMLLoader.isProduction() && textComponents.getChildren().getLast() instanceof ButtonWidget<?> button) {
            button.right(20);
        }
    }
    @ModifyArg(method = "loadCapContent", at = @At(value = "INVOKE", target =
            "Lcom/gregtechceu/gtceu/api/recipe/gui/CapabilityContentBuilder;buildWidgetContent(Lbrachy/modularui/api/widget/IWidget;Lcom/gregtechceu/gtceu/api/recipe/content/Content;Lcom/gregtechceu/gtceu/api/capability/recipe/IO;ZLcom/gregtechceu/gtceu/api/recipe/GTRecipeType;Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;II)V"),
            index = 7, remap = false, require = 1)
    private int gtlcore$originalRecipeTier(int selectedTier) { return IGTRecipe.of(baseRecipe).getEuTier(); }
}
