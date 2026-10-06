package org.gtlcore.gtlcore.mixin.gtm.api.capability;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** LDLib 2 exposes NeoForge handlers directly; keep the shutdown guard on GT's native lookup. */
@Mixin(value = GTCapabilityHelper.class, remap = false)
public abstract class NativeCapabilityLookupMixin {
    private static boolean gtlcore$stopped(Level level) {
        return level instanceof ServerLevel serverLevel && (!serverLevel.getServer().isRunning() || serverLevel.getServer().isStopped());
    }
    @Inject(method = "getItemHandler", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtlcore$skipStoppedItemLookup(Level level, BlockPos pos, Direction side, CallbackInfoReturnable<IItemHandler> cir) {
        if (gtlcore$stopped(level)) cir.setReturnValue(null);
    }
    @Inject(method = "getFluidHandler", at = @At("HEAD"), cancellable = true, require = 1)
    private static void gtlcore$skipStoppedFluidLookup(Level level, BlockPos pos, Direction side, CallbackInfoReturnable<IFluidHandler> cir) {
        if (gtlcore$stopped(level)) cir.setReturnValue(null);
    }
}
