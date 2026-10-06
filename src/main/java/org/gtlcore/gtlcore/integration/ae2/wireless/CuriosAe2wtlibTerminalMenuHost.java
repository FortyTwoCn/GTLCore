package org.gtlcore.gtlcore.integration.ae2.wireless;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import appeng.menu.ISubMenu;
import de.mari_023.ae2wtlib.api.terminal.ItemWT;
import de.mari_023.ae2wtlib.api.terminal.WTMenuHost;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

/** Quantum-bridge and power components are read from the current Curios slot. */
public class CuriosAe2wtlibTerminalMenuHost extends WTMenuHost {

    public CuriosAe2wtlibTerminalMenuHost(Player player, @Nullable Integer inventorySlot, ItemStack stack,
                                          String curiosIdentifier, int curiosIndex,
                                          BiConsumer<Player, ISubMenu> returnToMainMenu) {
        super((ItemWT) stack.getItem(), player,
                new CuriosTerminalLocator(curiosIdentifier, curiosIndex), returnToMainMenu);
    }
}
