package org.gtlcore.gtlcore.mixin.adastra;

import org.gtlcore.gtlcore.common.data.GTLRocketFuels;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

import earth.terrarium.adastra.common.entities.vehicles.Rocket;
import earth.terrarium.common_storage_lib.resources.fluid.FluidResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import com.llamalad7.mixinextras.sugar.Local;

import java.util.function.Predicate;

@Mixin(Rocket.class)
public abstract class RocketMixin extends Entity {

    public RocketMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @ModifyArg(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Learth/terrarium/adastra/common/entities/vehicles/Rocket$RocketProperties;)V",
               at = @At(value = "INVOKE",
                        target = "Learth/terrarium/common_storage_lib/fluid/impl/SimpleFluidStorage;filter(ILjava/util/function/Predicate;)Learth/terrarium/common_storage_lib/fluid/impl/SimpleFluidStorage;"),
               index = 1,
               remap = false, require = 1)
    private Predicate<FluidResource> gtlcore$rocketFuelFilter(Predicate<FluidResource> original,
            @Local(argsOnly = true) Rocket.RocketProperties rocketProperties) {
        return fluid -> fluid.isOf(GTLRocketFuels.forTier(rocketProperties.tier()));
    }

    @ModifyConstant(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;Learth/terrarium/adastra/common/entities/vehicles/Rocket$RocketProperties;)V", remap = false, constant = @Constant(longValue = 3000L))
    private long modifyContainer(long constant) {
        return 16000L;
    }

    @ModifyArg(method = "consumeFuel(Z)Z",
               at = @At(value = "INVOKE",
                        target = "Learth/terrarium/common_storage_lib/resources/fluid/util/FluidAmounts;toPlatformAmount(J)J"),
               remap = false, require = 1)
    public long consumeFuel(long millibuckets) {
        return 16000L;
    }
}
