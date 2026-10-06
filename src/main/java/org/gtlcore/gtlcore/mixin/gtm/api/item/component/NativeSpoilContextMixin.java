package org.gtlcore.gtlcore.mixin.gtm.api.item.component;

import com.gregtechceu.gtceu.api.item.component.SpoilContext;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Match the native disk codec's handling of a context decoded without a server. */
@Mixin(value = SpoilContext.class, remap = false)
public abstract class NativeSpoilContextMixin {
    @WrapOperation(method = "deserializeNBT", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/MinecraftServer;getLevel(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/server/level/ServerLevel;"))
    private static @Nullable ServerLevel gtlcore$resolveServerLevel(
            @Nullable MinecraftServer server, ResourceKey<Level> dimension, Operation<ServerLevel> original) {
        // Physical clients have no MinecraftServer. Spoilage is server-owned, as in SpoilContext.CODEC.
        return server == null ? null : original.call(server, dimension);
    }
}
