package org.gtlcore.gtlcore.mixin.gtm;
import org.gtlcore.gtlcore.api.gui.NativePatternPreviewWidget;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.integration.recipeviewer.jei.MultiblockInfoJeiCategory;
import brachy.modularui.api.widget.IWidget;
import java.util.function.Function;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = MultiblockInfoJeiCategory.class, remap = false)
public abstract class MultiblockInfoWrapperMixin {
    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lbrachy/modularui/integration/jei/recipe/ModularUIJeiCategory;<init>(Ljava/util/function/Function;Ljava/util/function/Function;)V"), index = 0)
    private static Function<MultiblockMachineDefinition, IWidget> gtlcore$preview(Function<MultiblockMachineDefinition, IWidget> original) {
        return NativePatternPreviewWidget::new;
    }
}
