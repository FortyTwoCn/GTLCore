package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.storage.CellItemData;

import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CellComponentTest {
    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void componentUpdatesKeepDiskIdentityAndUnrelatedData() {
        UUID id = UUID.fromString("00112233-4455-6677-8899-aabbccddeeff");
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag oldData = new CompoundTag();
        oldData.putUUID("diskuuid", id);
        oldData.putString("other_mod_key", "keep");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(oldData));
        assertEquals(id, CellItemData.ensureDiskUUID(stack));
        CellItemData.setStoredCount(stack, 0x1.0p100);
        assertEquals(0x1.0p100, CellItemData.getStoredCount(stack));
        assertEquals(id, CellItemData.getDiskUUID(stack));
        assertEquals("keep", stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("other_mod_key"));
        CellItemData.clearDisk(stack);
        assertFalse(CellItemData.hasDiskUUID(stack));
        assertNull(CellItemData.getDiskUUID(stack));
        assertEquals("keep", stack.get(DataComponents.CUSTOM_DATA).copyTag().getString("other_mod_key"));
        UUID replacement = CellItemData.ensureDiskUUID(stack);
        assertNotEquals(id, replacement);
        assertEquals(replacement, CellItemData.ensureDiskUUID(stack));
    }

}
