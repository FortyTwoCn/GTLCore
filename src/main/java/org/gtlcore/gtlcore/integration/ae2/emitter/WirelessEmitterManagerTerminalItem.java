package org.gtlcore.gtlcore.integration.ae2.emitter;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.entity.player.Player;
import appeng.menu.locator.ItemMenuHostLocator;
import de.mari_023.ae2wtlib.api.registration.WTDefinition;

import de.mari_023.ae2wtlib.api.terminal.ItemWT;

public final class WirelessEmitterManagerTerminalItem extends ItemWT {

    public static final String TERMINAL_NAME = "emitter_manager";
    public static final String HOTKEY_NAME = "wireless_emitter_manager_terminal";

    @Override
    public MenuType<?> getMenuType(ItemMenuHostLocator locator, Player player) {
        return WTDefinition.of(TERMINAL_NAME).menuType();
    }
}
