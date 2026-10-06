package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.helpers.WirelessTerminalMenuHost;
import appeng.items.tools.powered.WirelessTerminalItem;
import appeng.menu.ISubMenu;

import java.util.function.BiConsumer;

/** 原生定位器直接读取饰品槽中的 ItemStack，以持久化耗电和组件变化。 */
public class CuriosWirelessTerminalMenuHost extends WirelessTerminalMenuHost<WirelessTerminalItem> {

    public CuriosWirelessTerminalMenuHost(Player player, ItemStack itemStack,
                                          String curiosIdentifier, int curiosIndex,
                                          BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super((WirelessTerminalItem) itemStack.getItem(), player,
                new CuriosTerminalLocator(curiosIdentifier, curiosIndex), returnToMainMenu);
    }
}
