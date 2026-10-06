package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.PatternBoxBehavior;
import org.gtlcore.gtlcore.config.ConfigHolder;
import com.gregtechceu.gtceu.api.transfer.item.CustomItemStackHandler;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.util.inv.AppEngInternalInventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePatternBoxIntegrationTest {
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    static ItemStack pattern() {
        var result = PatternDetailsHelper.encodeProcessingPattern(
                java.util.List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 9_007_199_254_740_993L)),
                java.util.List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
        result.set(DataComponents.CUSTOM_NAME, Component.literal("saved native box pattern")); return result;
    }
    @Test void maximumBoxInventoryRetainsLastPageComponentsAndCustomDataAfterConfigShrinks() {
        int before = ConfigHolder.INSTANCE.patternBoxPages;
        try {
            ConfigHolder.INSTANCE.patternBoxPages = 10; var box = NativeAePatternFixtures.PATTERN_BOX.asStack();
            var other = new CompoundTag(); other.putString("other", "retained"); box.set(DataComponents.CUSTOM_DATA, CustomData.of(other));
            var inventory = PatternBoxBehavior.getInventory(box, REGISTRIES); inventory.setStackInSlot(359, pattern());
            PatternBoxBehavior.saveInventory(box, inventory, REGISTRIES); ConfigHolder.INSTANCE.patternBoxPages = 1;
            var restored = PatternBoxBehavior.getInventory(ItemStack.parse(REGISTRIES, box.save(REGISTRIES)).orElseThrow(), REGISTRIES);
            assertEquals(360, restored.getSlots()); assertTrue(ItemStack.matches(pattern(), restored.getStackInSlot(359)));
            assertEquals("retained", box.get(DataComponents.CUSTOM_DATA).copyTag().getString("other"));
        } finally { ConfigHolder.INSTANCE.patternBoxPages = before; }
    }
    @Test void transfersPreserveEncodedComponentsAndNeverRemoveRejectedOrNonPatternItems() {
        var box = new CustomItemStackHandler(1); var provider = new AppEngInternalInventory(null, 2);
        provider.setItemDirect(0, pattern()); provider.setItemDirect(1, new ItemStack(Items.IRON_INGOT, 5));
        assertTrue(PatternBoxBehavior.moveProviderToPouch(provider, box)); assertTrue(provider.getStackInSlot(0).isEmpty());
        assertTrue(ItemStack.matches(pattern(), box.getStackInSlot(0))); assertEquals(5, provider.getStackInSlot(1).getCount());
        provider.setFilter(new appeng.util.inv.filter.IAEItemFilter() {
            @Override public boolean allowInsert(appeng.api.inventories.InternalInventory inventory, int slot, ItemStack stack) { return false; }
        });
        assertFalse(PatternBoxBehavior.movePouchToProvider(box, provider)); assertTrue(ItemStack.matches(pattern(), box.getStackInSlot(0)));
        provider.setFilter(null); assertTrue(PatternBoxBehavior.movePouchToProvider(box, provider)); assertTrue(box.getStackInSlot(0).isEmpty());
        assertTrue(ItemStack.matches(pattern(), provider.getStackInSlot(0))); assertFalse(PatternBoxBehavior.movePouchToProvider(box, provider));
    }
    @Test void nativeBoxSlotValidationRejectsOrdinaryItemsAndInvalidSerializedSizes() {
        var box = NativeAePatternFixtures.PATTERN_BOX.asStack(); var inventory = PatternBoxBehavior.getInventory(box, REGISTRIES);
        assertEquals(1, inventory.insertItem(0, new ItemStack(Items.DIAMOND), false).getCount());
        assertTrue(inventory.insertItem(0, pattern(), false).isEmpty());
        var saved = new CompoundTag(); var contents = new CompoundTag(); contents.putInt("Size", 361); saved.put("PatternInv", contents);
        box.set(DataComponents.CUSTOM_DATA, CustomData.of(saved));
        assertThrows(IllegalArgumentException.class, () -> PatternBoxBehavior.getInventory(box, REGISTRIES));
        assertEquals(361, box.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("PatternInv").getInt("Size"));
    }
}
