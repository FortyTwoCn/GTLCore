package org.gtlcore.gtlcore.mixin.ae2;

import org.gtlcore.gtlcore.integration.ae2.FastInfinityCell;
import org.gtlcore.gtlcore.integration.ae2.InfinityCell;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.menu.me.common.MEStorageMenu;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** AE2 19 opens chest menus directly; infinity cells retain the original general terminal. */
@Mixin(value = MEChestBlockEntity.class, remap = false)
public abstract class InfinityCellChestMenuMixin {
    @ModifyArg(method = "openGui", at = @At(value = "INVOKE",
            target = "Lappeng/menu/MenuOpener;open(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/world/entity/player/Player;Lappeng/menu/locator/MenuHostLocator;)Z"),
            index = 0, require = 1)
    private MenuType<?> gtlcore$openInfinityTerminal(MenuType<?> original) {
        var cell = ((MEChestBlockEntity) (Object) this).getCell();
        return cell.getItem() instanceof InfinityCell || cell.getItem() instanceof FastInfinityCell ? MEStorageMenu.TYPE : original;
    }
}
