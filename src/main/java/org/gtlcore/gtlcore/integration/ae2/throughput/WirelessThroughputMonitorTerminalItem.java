package org.gtlcore.gtlcore.integration.ae2.throughput;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Player;
import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;

public final class WirelessThroughputMonitorTerminalItem extends ItemWT {

    public static final String TERMINAL_NAME = "throughput_monitor";
    public static final String HOTKEY_NAME = "wireless_throughput_monitor_terminal";

    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return WTDefinition.of(TERMINAL_NAME).menuType();
    }
}
