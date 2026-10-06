package org.gtlcore.gtlcore.mixin.gtm.ftb;

import dev.ftb.mods.ftbchunks.net.TeleportFromMapPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * @author EasterFG on 2024/9/28
 */
@Mixin(TeleportFromMapPacket.class)
public abstract class TeleportFromMapPacketMixin {

    @ModifyArg(method = "lambda$handle$0", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/server/level/ServerPlayer;hasPermissions(I)Z"), index = 0, remap = false, require = 1)
    private static int permissionModify(int constant) {
        return 0;
    }
}
