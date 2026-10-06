package org.gtlcore.gtlcore.mixin.gtm.registry;

import com.gregtechceu.gtceu.common.data.GTDimensionMarkers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Native marker keys must be the actual dimension IDs used by recipe/worldgen lookup. */
@Mixin(GTDimensionMarkers.class)
public abstract class GTDimensionMarkersMixin {
    @ModifyArg(method = "<clinit>", at = @At(value = "INVOKE", target =
            "Lnet/neoforged/neoforge/registries/DeferredRegister;register(Ljava/lang/String;Ljava/util/function/Supplier;)Lnet/neoforged/neoforge/registries/DeferredHolder;"),
            index = 0, remap = false, require = 3)
    private static String gtlcore$useDimensionRegistryPath(String path) {
        return switch (path) {
            case "nether" -> "the_nether";
            case "end" -> "the_end";
            default -> path;
        };
    }
}
