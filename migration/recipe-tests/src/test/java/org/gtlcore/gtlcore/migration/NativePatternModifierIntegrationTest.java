package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.PatternModifier;
import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.Ae2BaseProcessingPatternHelper;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import appeng.util.inv.AppEngInternalInventory;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativePatternModifierIntegrationTest {
    @Test void settingsAreOwnedByEachPhysicalItemAndSurviveNativeItemCodec() {
        var first = NativeAePatternFixtures.PATTERN_MODIFIER.asStack(); var second = first.copy();
        var tag = new CompoundTag(); tag.putString("other", "retained"); first.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        PatternModifier.setSetting(first, "scale", 7); PatternModifier.setSetting(first, "maxItem", 9_007_199_254_740_993L);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var restored = ItemStack.parse(registries, first.save(registries)).orElseThrow();
        assertEquals(7, PatternModifier.getSettings(restored).scale()); assertEquals(9_007_199_254_740_993L, PatternModifier.getSettings(restored).maxItem());
        assertEquals(1, PatternModifier.getSettings(second).scale()); assertEquals("retained", restored.get(DataComponents.CUSTOM_DATA).copyTag().getString("other"));
    }
    @Test void invalidNumbersAreBoundedBeforeSavingAndUnknownSettingsCannotChangeAnItem() {
        var item = NativeAePatternFixtures.PATTERN_MODIFIER.asStack();
        PatternModifier.setSetting(item, "applications", Long.MAX_VALUE); PatternModifier.setSetting(item, "scale", -99);
        PatternModifier.setSetting(item, "divisor", Long.MAX_VALUE); PatternModifier.setSetting(item, "maxFluid", -1);
        var settings = PatternModifier.getSettings(item);
        assertEquals(16, settings.applications()); assertEquals(1, settings.scale()); assertEquals(Integer.MAX_VALUE, settings.divisor()); assertEquals(1, settings.maxFluid());
        var before = item.copy(); assertThrows(IllegalArgumentException.class, () -> PatternModifier.setSetting(item, "unknown", 1)); assertTrue(ItemStack.matches(before, item));
    }
    @Test void repeatedNativeScalingPreservesExactLargeInputsItemComponentsAndPhysicalStackCounts() {
        var inventory = new AppEngInternalInventory(null, 2); var original = NativePatternBoxIntegrationTest.pattern().copyWithCount(2);
        inventory.setItemDirect(0, original); inventory.setItemDirect(1, new ItemStack(Items.IRON_INGOT, 5));
        assertTrue(PatternModifier.modifyInventory(inventory, null, new PatternModifier.Settings(2, 1, Long.MAX_VALUE, Long.MAX_VALUE, 2)));
        var result = inventory.getStackInSlot(0); var decoded = Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(result, null);
        assertEquals(36_028_797_018_963_972L, decoded.getSparseInputs().getFirst().amount()); assertEquals(4, decoded.getSparseOutputs().getFirst().amount());
        assertEquals(2, result.getCount()); assertEquals(original.get(DataComponents.CUSTOM_NAME), result.get(DataComponents.CUSTOM_NAME)); assertEquals(5, inventory.getStackInSlot(1).getCount());
    }
    @Test void overflowOrQuantityLimitRejectionKeepsOriginalPatternsIntact() {
        var inventory = new AppEngInternalInventory(null, 1); var original = NativePatternBoxIntegrationTest.pattern(); inventory.setItemDirect(0, original);
        assertFalse(PatternModifier.modifyInventory(inventory, null, new PatternModifier.Settings(Integer.MAX_VALUE, 1, Long.MAX_VALUE, Long.MAX_VALUE, 1)));
        assertTrue(ItemStack.matches(original, inventory.getStackInSlot(0)));
        assertFalse(PatternModifier.modifyInventory(inventory, null, new PatternModifier.Settings(2, 1, 100, 100, 1)));
        assertTrue(ItemStack.matches(original, inventory.getStackInSlot(0)));
    }
}
