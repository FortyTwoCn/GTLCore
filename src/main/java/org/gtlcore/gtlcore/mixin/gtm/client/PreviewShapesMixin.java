package org.gtlcore.gtlcore.mixin.gtm.client;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewShapes;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MultiblockMachineDefinition.class, remap = false)
public abstract class PreviewShapesMixin {
    @Inject(method = "<init>", at = @At("RETURN"))
    private void gtlcore$definitionCreated(CallbackInfo ci) {
        NativePreviewShapes.invalidate((MultiblockMachineDefinition) (Object) this);
    }
}
