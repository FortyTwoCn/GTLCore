package org.gtlcore.gtlcore.mixin.ae2wtlib;

import org.gtlcore.gtlcore.integration.ae2.chamber.WirelessMEChamberManagerTerminalItem;
import org.gtlcore.gtlcore.integration.ae2.emitter.WirelessEmitterManagerTerminalItem;

import net.minecraft.world.item.ItemStack;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import de.mari_023.ae2wtlib.api.terminal.ItemWUT;
import de.mari_023.ae2wtlib.api.terminal.WUTHandler;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = ItemWUT.class, remap = false)
public abstract class ItemWUTMixin {

    @ModifyExpressionValue(
                           method = "getUpgrades",
                           at = @At(
                                    value = "INVOKE",
                                    target = "Lde/mari_023/ae2wtlib/api/terminal/ItemWUT;countInstalledTerminals(Lnet/minecraft/world/item/ItemStack;)I",
                                    remap = false))
    private int gtlcore$countUpgradeSlotTerminals(int terminalCount, ItemStack stack) {
        if (WUTHandler.hasTerminal(stack, WTDefinition.of(WirelessEmitterManagerTerminalItem.TERMINAL_NAME))) {
            terminalCount--;
        }
        if (WUTHandler.hasTerminal(stack, WTDefinition.of(WirelessMEChamberManagerTerminalItem.TERMINAL_NAME))) {
            terminalCount--;
        }
        return Math.max(0, terminalCount);
    }
}
