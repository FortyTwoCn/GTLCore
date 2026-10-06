package org.gtlcore.gtlcore.mixin.ldlib;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** LDLib now inherits these methods; retain the empty-transfer guard on the actual declaring class. */
@Mixin(ItemStackHandler.class)
public abstract class EmptyItemStackTransferMixin {
    @Shadow(remap = false) protected NonNullList<ItemStack> stacks;

    @Inject(method = "insertItem", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$emptyInsert(int slot, ItemStack stack, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if ((Object) this instanceof ItemStackTransfer && stacks.isEmpty()) cir.setReturnValue(stack);
    }

    @Inject(method = "extractItem", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$emptyExtract(int slot, int amount, boolean simulate, CallbackInfoReturnable<ItemStack> cir) {
        if ((Object) this instanceof ItemStackTransfer && stacks.isEmpty()) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "getStackInSlot", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$emptyRead(int slot, CallbackInfoReturnable<ItemStack> cir) {
        if ((Object) this instanceof ItemStackTransfer && stacks.isEmpty()) cir.setReturnValue(ItemStack.EMPTY);
    }

    @Inject(method = "setStackInSlot", at = @At("HEAD"), remap = false, cancellable = true)
    private void gtlcore$emptySet(int slot, ItemStack stack, CallbackInfo ci) {
        if ((Object) this instanceof ItemStackTransfer && stacks.isEmpty()) ci.cancel();
    }
}
