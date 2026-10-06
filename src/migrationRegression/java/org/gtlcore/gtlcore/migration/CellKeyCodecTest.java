package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.storage.CellItemData;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CellKeyCodecTest {
    @BeforeAll
    static void bootstrapVanilla() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nativeItemKeyKeepsCellIdentityAndLargeCountComponents() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        ItemStack stack = new ItemStack(Items.PAPER);
        var uuid = CellItemData.ensureDiskUUID(stack);
        CellItemData.setStoredCount(stack, 0x1.0p100);
        var key = AEItemKey.of(stack);
        var tag = key.toTag(registries);
        assertTrue(tag.contains("components"));
        var restored = AEItemKey.fromTag(registries, tag);
        assertNotNull(restored);
        assertEquals(key, restored);
        assertEquals(uuid, CellItemData.getDiskUUID(restored.toStack()));
        assertEquals(0x1.0p100, CellItemData.getStoredCount(restored.toStack()));
    }

    @Test
    void nativeFluidKeyRoundTripsWithRegistryContext() {
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var key = AEFluidKey.of(Fluids.WATER);
        assertEquals(key, AEFluidKey.fromTag(registries, key.toTag(registries)));
    }
}
