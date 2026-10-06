package org.gtlcore.gtlcore.mixin.ldlib;

import org.gtlcore.gtlcore.api.machine.trait.IDirectItemStackTransfer;

import com.lowdragmc.lowdraglib.misc.ItemStackTransfer;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * @author EasterFG on 2025/4/8
 */
@Mixin(ItemStackTransfer.class)
public abstract class ItemStackTransferMixin extends ItemStackHandler implements IDirectItemStackTransfer {
    protected ItemStackTransferMixin(int size) { super(size); }

    @Override
    public void gtlcore$setStackWithoutNotify(int slot, ItemStack stack) {
        stacks.set(slot, stack);
    }

    @Inject(method = "setStackInSlot", at = @At("HEAD"), remap = false, cancellable = true)
    public void setStackInSlotHook(int slot, ItemStack stack, boolean notifyChanges, CallbackInfo ci) {
        if (stacks.isEmpty()) {
            ci.cancel();
        }
    }
}
