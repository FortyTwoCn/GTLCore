package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;

import appeng.menu.locator.ItemMenuHostLocator;
import appeng.menu.locator.MenuLocators;
import org.jetbrains.annotations.Nullable;

/** AE2 reads the equipped stack through this locator for validation and power consumption. */
public record CuriosTerminalLocator(String identifier, int index) implements ItemMenuHostLocator {

    public CuriosTerminalLocator {
        if (identifier == null || identifier.isBlank() || index < 0) {
            throw new IllegalArgumentException("Invalid Curios terminal slot");
        }
    }

    public static void register() {
        MenuLocators.register(CuriosTerminalLocator.class, CuriosTerminalLocator::write, CuriosTerminalLocator::read);
    }

    @Override
    public ItemStack locateItem(Player player) {
        return CuriosCompat.locateItem(identifier, index, player);
    }

    @Override
    public @Nullable BlockHitResult hitResult() {
        return null;
    }

    private static void write(CuriosTerminalLocator locator, FriendlyByteBuf buffer) {
        buffer.writeUtf(locator.identifier);
        buffer.writeInt(locator.index);
    }

    private static CuriosTerminalLocator read(FriendlyByteBuf buffer) {
        return new CuriosTerminalLocator(buffer.readUtf(), buffer.readInt());
    }
}
