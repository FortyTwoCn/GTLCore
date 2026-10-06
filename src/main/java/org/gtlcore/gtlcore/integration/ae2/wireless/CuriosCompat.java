package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import appeng.items.tools.powered.WirelessTerminalItem;
import org.jetbrains.annotations.Nullable;

/** Curios integration is loaded only when its native mod is present. */
public final class CuriosCompat {

    private CuriosCompat() {}

    public static boolean isLoaded() {
        return ModList.get().isLoaded("curios");
    }

    public record TerminalSlot(ItemStack stack, String identifier, int index) {}

    public static @Nullable TerminalSlot findWirelessTerminal(Player player) {
        return isLoaded() ? NativeCurios.findWirelessTerminal(player) : null;
    }

    public static ItemStack locateItem(String identifier, int index, Player player) {
        return isLoaded() ? NativeCurios.locateItem(identifier, index, player) : ItemStack.EMPTY;
    }

    public static void writeStackBack(String identifier, int index, Player player, ItemStack stack) {
        if (isLoaded()) NativeCurios.writeStackBack(identifier, index, player, stack);
    }

    // Keep Curios API references behind the mod-presence check, including on dedicated servers.
    private static final class NativeCurios {

        private static @Nullable TerminalSlot findWirelessTerminal(Player player) {
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .flatMap(inventory -> inventory.findFirstCurio(
                            stack -> !stack.isEmpty() && stack.getItem() instanceof WirelessTerminalItem))
                    .map(result -> new TerminalSlot(result.stack(), result.slotContext().identifier(), result.slotContext().index()))
                    .orElse(null);
        }

        private static ItemStack locateItem(String identifier, int index, Player player) {
            if (index < 0) return ItemStack.EMPTY;
            return top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .flatMap(inventory -> inventory.findCurio(identifier, index))
                    .map(top.theillusivec4.curios.api.SlotResult::stack)
                    .orElse(ItemStack.EMPTY);
        }

        private static void writeStackBack(String identifier, int index, Player player, ItemStack stack) {
            if (index < 0) return;
            top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player)
                    .flatMap(inventory -> inventory.getStacksHandler(identifier))
                    .ifPresent(handler -> {
                        var stacks = handler.getStacks();
                        if (index < stacks.getSlots()) stacks.setStackInSlot(index, stack);
                    });
        }
    }
}
