package org.gtlcore.gtlcore.mixin.ftbu;

import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.utils.Registries;
import org.gtlcore.gtlcore.utils.TextUtil;

import dev.ftb.mods.ftbultimine.FTBUltiminePlayerData;
import dev.ftb.mods.ftbultimine.api.shape.ShapeContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * @author EasterFG on 2024/10/10
 */
@Mixin(FTBUltiminePlayerData.class)
public abstract class YouCantBreakAE2Mixin {

    @ModifyArg(method = "updateBlocks", at = @At(value = "INVOKE",
            target = "Ldev/ftb/mods/ftbultimine/api/shape/ShapeContext;<init>(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;Lnet/minecraft/world/level/block/state/BlockState;Ldev/ftb/mods/ftbultimine/api/shape/ShapeContext$Matcher;I)V"),
            index = 4, remap = false, require = 1)
    public ShapeContext.Matcher modifyBlockMatcher(ShapeContext.Matcher matcher) {
        if (ConfigHolder.INSTANCE.blackBlockList == null || ConfigHolder.INSTANCE.blackBlockList.length < 1) {
            return matcher;
        }
        return (original, state) -> {
            boolean flag = !TextUtil.containsWithWildcard(ConfigHolder.INSTANCE.blackBlockList, Registries.getBlockId(state.getBlock()));
            return flag && state.getBlock() == original.getBlock();
        };
    }
}
