package org.gtlcore.gtlcore.mixin.gtm.registry;

import org.gtlcore.gtlcore.common.data.machines.GTLGCYMMachinePolicies;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.builder.MachineBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Apply Core policy before native modification events and deferred registration, without replacing GT initialization. */
@Mixin(MachineBuilder.class)
public abstract class MachineBuilderMixin {
    @Shadow(remap = false)
    protected abstract MachineDefinition.Properties getProperties();

    @Inject(method = "register()Lcom/gregtechceu/gtceu/api/registry/registrate/entry/MachineEntry;", at = @At("HEAD"), remap = false, require = 1)
    private void gtlcore$configureGcym(CallbackInfoReturnable<MachineEntry<MachineDefinition>> cir) {
        org.gtlcore.gtlcore.common.data.NativeMachineAssetRegistry.rememberTab((MachineBuilder<?, ?, ?>) (Object) this);
        GTLGCYMMachinePolicies.modify((MachineBuilder<?, ?, ?>) (Object) this);
        org.gtlcore.gtlcore.common.data.machines.TootipsModify.modify((MachineBuilder<?, ?, ?>) (Object) this, getProperties().tooltipBuilder());
    }

    @Inject(method = "createEntry()Lcom/gregtechceu/gtceu/api/machine/MachineDefinition;", at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$captureModels(CallbackInfoReturnable<MachineDefinition> cir) {
        org.gtlcore.gtlcore.common.data.NativeMachineAssetRegistry.capture((MachineBuilder<?, ?, ?>) (Object) this, cir.getReturnValue(), getProperties());
    }
}
