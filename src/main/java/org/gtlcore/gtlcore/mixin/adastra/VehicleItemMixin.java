package org.gtlcore.gtlcore.mixin.adastra;

import earth.terrarium.adastra.common.items.vehicles.VehicleItem;
import earth.terrarium.adastra.common.items.vehicles.RocketItem;
import earth.terrarium.common_storage_lib.resources.fluid.FluidResource;
import org.gtlcore.gtlcore.common.data.GTLRocketFuels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.At;
import java.util.function.Predicate;

@Mixin(VehicleItem.class)
public abstract class VehicleItemMixin {

    @ModifyArg(method = "getFluids", remap = false,
            at = @At(value = "INVOKE", target = "Learth/terrarium/common_storage_lib/fluid/impl/SimpleFluidStorage;filter(ILjava/util/function/Predicate;)Learth/terrarium/common_storage_lib/fluid/impl/SimpleFluidStorage;"),
            index = 1, require = 1)
    private Predicate<FluidResource> gtlcore$itemRocketFuel(Predicate<FluidResource> original) {
        if ((Object) this instanceof RocketItem rocket) {
            int tier = GTLRocketFuels.tier(rocket.type());
            if (tier != 0) return resource -> resource.isOf(GTLRocketFuels.forTier(tier));
        }
        return original;
    }

    @ModifyConstant(method = "getFluids", remap = false, constant = @Constant(longValue = 3000L), require = 1)
    private long modifyContainer(long constant) {
        return 16000L;
    }
}
