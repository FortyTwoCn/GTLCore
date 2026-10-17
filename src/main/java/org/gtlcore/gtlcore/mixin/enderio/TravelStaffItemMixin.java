package org.gtlcore.gtlcore.mixin.enderio;

import org.gtlcore.gtlcore.config.ConfigHolder;

import com.enderio.enderio.content.travel.TravelStaffItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Preserve the GTL cooldown for blink, anchor travel and remote interaction. */
@Mixin(TravelStaffItem.class)
public abstract class TravelStaffItemMixin {

    @ModifyArg(method = "performAction", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemCooldowns;addCooldown(Lnet/minecraft/world/item/Item;I)V"),
            index = 1, require = 3, expect = 3, allow = 3)
    private int gtlcore$travelStaffCooldown(int original) {
        return ConfigHolder.INSTANCE.travelStaffCD;
    }
}
