package org.gtlcore.gtlcore.mixin.adastra;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.teamresourceful.resourcefullib.common.registry.RegistryEntry;
import earth.terrarium.adastra.common.items.vehicles.RocketItem;
import earth.terrarium.adastra.common.utils.FluidUtils;
import earth.terrarium.common_storage_lib.resources.fluid.FluidResource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.material.Fluid;
import org.gtlcore.gtlcore.common.data.GTLRocketFuels;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Native creative-tab prefilled rockets must use the same tier fuel as placed rockets. */
@Mixin(FluidUtils.class)
public abstract class FluidUtilsMixin {
    @ModifyExpressionValue(method = "fluidFilledItem", remap = false, require = 1,
            at = @At(value = "INVOKE", target = "Learth/terrarium/common_storage_lib/resources/fluid/FluidResource;of(Lnet/minecraft/world/level/material/Fluid;)Learth/terrarium/common_storage_lib/resources/fluid/FluidResource;"))
    private static FluidResource gtlcore$prefilledRocketFuel(FluidResource original, RegistryEntry<Item> item,
                                                            RegistryEntry<? extends Fluid> fluid) {
        if (item.get() instanceof RocketItem rocket) {
            int tier = GTLRocketFuels.tier(rocket.type());
            if (tier != 0) return FluidResource.of(GTLRocketFuels.forTier(tier));
        }
        return original;
    }
}
