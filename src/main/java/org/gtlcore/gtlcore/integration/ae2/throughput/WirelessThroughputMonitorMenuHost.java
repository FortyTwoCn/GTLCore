package org.gtlcore.gtlcore.integration.ae2.throughput;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.api.implementations.blockentities.IViewCellStorage;
import appeng.menu.ISubMenu;
import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;

import java.util.function.BiConsumer;

public final class WirelessThroughputMonitorMenuHost extends WTMenuHost implements IViewCellStorage {

    public WirelessThroughputMonitorMenuHost(ItemWT item, Player player, ItemMenuHostLocator locator,
                                             BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super(item, player, locator, returnToMainMenu);
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(WTDefinition.of(WirelessThroughputMonitorTerminalItem.TERMINAL_NAME).item());
    }
}
