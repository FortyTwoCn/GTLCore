package org.gtlcore.gtlcore.mixin.modularui;

import org.gtlcore.gtlcore.integration.ae2.gui.PatternBufferItemSlot;
import brachy.modularui.widgets.slot.ItemSlot;
import net.minecraft.world.item.ItemStack;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Use the Core pattern output hook for drawing while native slots continue to transact encoded patterns. */
@Mixin(value = ItemSlot.class, remap = false)
public abstract class PatternOutputRenderMixin {
    @ModifyExpressionValue(method = "drawSlot", at = @At(value = "INVOKE",
            target = "Lbrachy/modularui/widgets/slot/ModularSlot;getItem()Lnet/minecraft/world/item/ItemStack;", ordinal = 0), require = 1)
    private ItemStack gtlcore$displayPatternOutput(ItemStack stack) {
        return (Object) this instanceof PatternBufferItemSlot pattern ? pattern.displayStackForRender(stack) : stack;
    }
}
