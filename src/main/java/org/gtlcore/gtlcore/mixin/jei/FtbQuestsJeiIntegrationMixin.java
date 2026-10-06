package org.gtlcore.gtlcore.mixin.jei;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidUtil;

import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IJeiRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "dev.ftb.mods.ftbxmodcompat.ftbquests.jei.FTBQuestsJEIIntegration", remap = false)
public abstract class FtbQuestsJeiIntegrationMixin {

    @Shadow
    public static IJeiRuntime runtime;

    @Inject(method = "showRecipes(Lnet/minecraft/world/item/ItemStack;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private static void gtlcore$showContainedFluidRecipes(ItemStack stack, CallbackInfo ci) {
        if (runtime == null) {
            return;
        }
        FluidUtil.getFluidContained(stack).ifPresent(fluid -> {
            var focus = runtime.getJeiHelpers()
                    .getFocusFactory()
                    .createFocus(RecipeIngredientRole.OUTPUT, NeoForgeTypes.FLUID_STACK, fluid);
            runtime.getRecipesGui().show(focus);
            ci.cancel();
        });
    }
}
