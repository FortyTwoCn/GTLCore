package org.gtlcore.gtlcore.mixin.gtm.machine;

import com.gregtechceu.gtceu.api.sync_system.SyncDataHolder;
import com.gregtechceu.gtceu.common.machine.trait.hpca.HPCAComponentTrait;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The native field is isDamaged; the upstream setter currently dirties the absent damaged field. */
@Mixin(HPCAComponentTrait.class)
public abstract class HPCAComponentTraitMixin {
    @Redirect(method = "setDamaged", at = @At(value = "INVOKE",
            target = "Lcom/gregtechceu/gtceu/api/sync_system/SyncDataHolder;markClientSyncFieldDirty(Ljava/lang/String;)V"), remap = false, require = 1)
    private void gtlcore$syncDamage(SyncDataHolder holder, String field) {
        holder.markClientSyncFieldDirty("isDamaged");
        // MachineTrait.markAsChanged only schedules the owning entity; explicitly include its trait packet.
        ((HPCAComponentTrait) (Object) this).getMachine().getSyncDataHolder().markClientSyncFieldDirty("traitHolder");
    }
}
