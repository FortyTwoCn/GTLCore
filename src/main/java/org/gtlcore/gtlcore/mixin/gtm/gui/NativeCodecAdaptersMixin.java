package org.gtlcore.gtlcore.mixin.gtm.gui;

import brachy.modularui.utils.serialization.network.IByteBufAdapter;
import com.gregtechceu.gtceu.common.mui.GTByteBufAdapters;
import com.mojang.serialization.Codec;
import net.minecraft.network.FriendlyByteBuf;
import org.gtlcore.gtlcore.api.gui.NativeGuiCodecAdapters;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GTByteBufAdapters.class)
public abstract class NativeCodecAdaptersMixin {
    @Inject(method = "makeAdapter", at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static <T> void gtlcore$codecJsonValues(Codec<T> codec,
            CallbackInfoReturnable<IByteBufAdapter<FriendlyByteBuf, T>> callback) {
        callback.setReturnValue(NativeGuiCodecAdapters.make(codec));
    }
}
