package org.gtlcore.gtlcore.integration.ae2.storage;

import org.gtlcore.gtlcore.integration.ae2.InfinityCell;
import org.gtlcore.gtlcore.utils.NumberUtils;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;

import java.util.List;

public class InfinityCellHandler implements ICellHandler {

    public static final InfinityCellHandler INSTANCE = new InfinityCellHandler();

    @Override
    public boolean isCell(ItemStack is) {
        return is.getItem() instanceof InfinityCell;
    }

    @Override
    public InfinityCellInventory getCellInventory(ItemStack is, ISaveProvider container) {
        return InfinityCellInventory.createInventory(is, container);
    }

    public void addCellInformationToTooltip(ItemStack stack, List<Component> lines) {
        // Tooltips also run on remote clients, which do not own disk_manager.dat.
        var uuid = CellItemData.getDiskUUID(stack);
        if (uuid != null) {
            lines.add(Component.literal("UUID: ").withStyle(ChatFormatting.GRAY)
                    .append(Component.literal(uuid.toString()).withStyle(ChatFormatting.AQUA)));
            lines.add(Component.literal("Byte: ").withStyle(ChatFormatting.GRAY)
                    .append(NumberUtils.numberText(CellItemData.getStoredCount(stack)).withStyle(ChatFormatting.GREEN)));
        }
    }
}
