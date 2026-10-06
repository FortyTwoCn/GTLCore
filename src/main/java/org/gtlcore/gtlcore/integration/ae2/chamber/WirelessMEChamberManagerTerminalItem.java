package org.gtlcore.gtlcore.integration.ae2.chamber;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Player;
import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;

public final class WirelessMEChamberManagerTerminalItem extends ItemWT {

    public static final String TERMINAL_NAME = "me_chamber_manager";
    public static final String HOTKEY_NAME = "wireless_me_chamber_manager_terminal";

    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return WTDefinition.of(TERMINAL_NAME).menuType();
    }
}
