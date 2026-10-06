package org.gtlcore.gtlcore.integration.ae2.emitter;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

import java.util.function.BiConsumer;

public final class WirelessEmitterManagerMenuHost extends WTMenuHost {

    public WirelessEmitterManagerMenuHost(ItemWT item, Player player, ItemMenuHostLocator locator,
                                          BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(WTDefinition.of(WirelessEmitterManagerTerminalItem.TERMINAL_NAME).item());
    }
}
