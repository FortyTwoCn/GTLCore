package org.gtlcore.gtlcore.mixin.gtm.api.machine.trait;

import com.gregtechceu.gtceu.common.machine.trait.CleanroomProviderTrait;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomReceiverTrait;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.jetbrains.annotations.Nullable;

@Mixin(value = CleanroomReceiverTrait.class, remap = false)
public interface CleanroomReceiverTraitAccessor {
    @Accessor("cleanroomProvider") @Nullable CleanroomProviderTrait gtlcore$getCleanroomProvider();
}
