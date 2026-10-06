package org.gtlcore.gtlcore.integration.ae2.storage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.UUID;

/** Stores cell identity and display counts in native vanilla custom_data components. */
public final class CellItemData {
    private CellItemData() {}

    public static boolean hasDiskUUID(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().hasUUID("diskuuid");
    }

    public static UUID getDiskUUID(ItemStack stack) {
        var tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.hasUUID("diskuuid") ? tag.getUUID("diskuuid") : null;
    }

    public static UUID ensureDiskUUID(ItemStack stack) {
        UUID existing = getDiskUUID(stack);
        if (existing != null) return existing;
        UUID created = UUID.randomUUID();
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putUUID("diskuuid", created));
        return created;
    }

    public static double getStoredCount(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getDouble("count");
    }

    public static void setStoredCount(ItemStack stack, double count) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putDouble("count", count));
    }

    public static void clearDisk(ItemStack stack) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.remove("diskuuid");
            tag.remove("count");
        });
    }
}
