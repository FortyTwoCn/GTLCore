package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.pattern.NativePatternModifierAmounts;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeExtendedPatternAmountsTest {
    @Test void replacementUsesExactRatiosAboveDoublePrecisionAndBoundsOnlyTheFinalResult() {
        long large = 9_007_199_254_740_993L;
        assertEquals(large, NativePatternModifierAmounts.replacement(large, 3, 3));
        assertEquals(6_004_799_503_160_662L, NativePatternModifierAmounts.replacement(large, 2, 3));
        assertEquals(Long.MAX_VALUE, NativePatternModifierAmounts.replacement(Long.MAX_VALUE, Long.MAX_VALUE, 1));
        assertEquals(1, NativePatternModifierAmounts.replacement(1, 1, Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> NativePatternModifierAmounts.replacement(1, 1, 0));
    }
    @Test void scalingAndCompactionRejectOverflowAndRetainTheFirstComponentKeyOrder() {
        var iron = AEItemKey.of(Items.IRON_INGOT); var gold = AEItemKey.of(Items.GOLD_INGOT);
        assertFalse(NativePatternModifierAmounts.canScale(new GenericStack[]{new GenericStack(iron, Long.MAX_VALUE)}, 2, false));
        assertFalse(NativePatternModifierAmounts.canScale(new GenericStack[]{new GenericStack(iron, 7)}, 3, true));
        assertArrayEquals(new GenericStack[]{new GenericStack(iron, 9), new GenericStack(gold, 3)},
                NativePatternModifierAmounts.compact(new GenericStack[]{new GenericStack(iron, 4), null, new GenericStack(gold, 3), new GenericStack(iron, 5)}));
        assertThrows(IllegalArgumentException.class, () -> NativePatternModifierAmounts.compact(new GenericStack[]{new GenericStack(iron, Long.MAX_VALUE), new GenericStack(iron, 1)}));
    }
    @Test void metadataCopyRetainsPhysicalCountAndNamesWithoutRestoringTheOldEncodedRecipe() {
        var iron = AEItemKey.of(Items.IRON_INGOT); var gold = AEItemKey.of(Items.GOLD_INGOT);
        var original = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(iron, 6)), List.of(new GenericStack(gold, 2))).copyWithCount(3);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("encoded by native player"));
        var tag = new CompoundTag(); tag.putString("encoder", "native author"); original.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        var rewritten = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(iron, 18)), List.of(new GenericStack(gold, 2)));
        NativePatternModifierAmounts.copyMetadata(original, rewritten);
        var decoded = new AEProcessingPattern(AEItemKey.of(rewritten));
        assertEquals(18, decoded.getSparseInputs().getFirst().amount()); assertEquals(6, new AEProcessingPattern(AEItemKey.of(original)).getSparseInputs().getFirst().amount());
        assertEquals(3, rewritten.getCount()); assertEquals(original.get(DataComponents.CUSTOM_NAME), rewritten.get(DataComponents.CUSTOM_NAME));
        assertEquals(tag, rewritten.get(DataComponents.CUSTOM_DATA).copyTag());
    }
}
