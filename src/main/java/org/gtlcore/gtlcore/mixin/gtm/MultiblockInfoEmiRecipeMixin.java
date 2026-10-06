package org.gtlcore.gtlcore.mixin.gtm;
import org.gtlcore.gtlcore.api.gui.NativePatternPreviewWidget;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.integration.recipeviewer.emi.MultiblockInfoEmiCategory.MultiblockInfoEmiWrapper;
import brachy.modularui.api.widget.IWidget;
import java.util.function.Supplier;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = MultiblockInfoEmiWrapper.class, remap = false)
public abstract class MultiblockInfoEmiRecipeMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lbrachy/modularui/integration/emi/recipe/ModularUIEmiRecipe;<init>(Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Supplier;)V"), index = 1)
    private static Supplier<IWidget> gtlcore$preview(Supplier<IWidget> original, @Local(argsOnly = true) MultiblockMachineDefinition definition) {
        return () -> new NativePatternPreviewWidget(definition);
    }
}
