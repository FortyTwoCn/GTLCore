package org.gtlcore.gtlcore.mixin.gtm.registry;

import org.gtlcore.gtlcore.common.data.NativeMachineAssetRegistry;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.builder.MultiblockMachineBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The native multiblock builder creates its definition without calling MachineBuilder.createEntry. */
@Mixin(MultiblockMachineBuilder.class)
public abstract class MultiblockMachineBuilderMixin {
    @Inject(method = "createEntry()Lcom/gregtechceu/gtceu/api/machine/MultiblockMachineDefinition;", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$captureMultiblock(CallbackInfoReturnable<MultiblockMachineDefinition> cir) {
        var builder = (MultiblockMachineBuilder<?>) (Object) this;
        NativeMachineAssetRegistry.capture(builder, cir.getReturnValue(), builder.getProperties());
    }
}
