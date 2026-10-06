package org.gtlcore.gtlcore.mixin.meRequester;

import com.almostreliable.merequester.requester.Request;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Request.class)
public abstract class RequestMixin {

    @Shadow(remap = false)
    private long batch;

    @Inject(
            method = "updateBatch",
            at = @At(
                     value = "FIELD",
                     target = "Lcom/almostreliable/merequester/requester/Request;batch:J",
                     opcode = Opcodes.PUTFIELD,
                     shift = At.Shift.AFTER),
            remap = false,
            require = 1)
    private void reassignBatchAfterPut(long batch, CallbackInfo ci) {
        this.batch = Math.max(1L, batch);
    }
}
