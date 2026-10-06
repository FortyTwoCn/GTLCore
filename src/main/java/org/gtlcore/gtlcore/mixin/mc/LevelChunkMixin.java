package org.gtlcore.gtlcore.mixin.mc;

import org.gtlcore.gtlcore.common.util.BlockStateWatcher;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
public abstract class LevelChunkMixin {

    @Final
    @Shadow
    Level level;

    @Inject(method = "setBlockState", at = @At("RETURN"))
    private void notifyBlockStateWatchers(BlockPos pos, BlockState state, boolean isMoving,
                                          CallbackInfoReturnable<BlockState> cir) {
        if (cir.getReturnValue() != null && level instanceof ServerLevel serverLevel) {
            // onPlace may replace the requested block again; publish the committed state.
            BlockState currentState = serverLevel.getChunkAt(pos).getBlockState(pos);
            BlockStateWatcher.notifyWatchersInternal(serverLevel, pos, currentState);
        }
    }
}
