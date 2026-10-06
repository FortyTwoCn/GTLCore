package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.Ae2BaseProcessingPattern;
import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.Ae2BaseProcessingPatternHelper;

import com.gregtechceu.gtceu.common.data.GTItems;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Actual AE2 19 pattern component, decoder and codecs; no AE grid is created by these tests. */
class NativeProcessingPatternIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;

    private static GenericStack item(long amount) {
        var item = new ItemStack(Items.IRON_INGOT);
        item.set(DataComponents.CUSTOM_NAME, Component.literal("precise catalyst"));
        return new GenericStack(AEItemKey.of(item), amount);
    }

    private static GenericStack fluid(long amount) {
        var fluid = new FluidStack(Fluids.WATER, 1);
        fluid.set(DataComponents.CUSTOM_NAME, Component.literal("precise fluid"));
        return new GenericStack(AEFluidKey.of(fluid), amount);
    }

    private static ItemStack pattern(List<GenericStack> input, List<GenericStack> output) {
        var stack = PatternDetailsHelper.encodeProcessingPattern(input, output);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("processing"));
        stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("keep this lore"))));
        return stack;
    }

    private static AEProcessingPattern decode(ItemStack stack) {
        var decoded = Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(stack, null);
        assertNotNull(decoded, "AE2's registered decoder must recognize the encoded item");
        return decoded;
    }

    @Test void scalePreservesSparseSlotsComponentsAndExactQuantitiesAboveDoublePrecision() {
        var input = Arrays.asList(item(LARGE), null, fluid(3_000_000_001L), item(6));
        var output = Arrays.asList(new GenericStack(AEItemKey.of(Items.DIAMOND), LARGE), null, fluid(2_000_000_001L));
        var source = pattern(input, output);
        var before = source.getComponentsPatch();
        var scaled = Ae2BaseProcessingPatternHelper.multiplyScale(2, false, decode(source), Long.MAX_VALUE, Long.MAX_VALUE);
        assertNotNull(scaled);
        var decoded = decode(scaled);
        assertEquals(4, decoded.getSparseInputs().size());
        assertNull(decoded.getSparseInputs().get(1));
        assertNull(decoded.getSparseOutputs().get(1));
        assertEquals(LARGE * 2, decoded.getSparseInputs().getFirst().amount());
        assertEquals(6_000_000_002L, decoded.getSparseInputs().get(2).amount());
        assertEquals(input.getFirst().what(), decoded.getSparseInputs().getFirst().what());
        assertEquals(input.get(2).what(), decoded.getSparseInputs().get(2).what());
        assertEquals(source.get(DataComponents.LORE), scaled.get(DataComponents.LORE));
        assertEquals(source.get(DataComponents.CUSTOM_NAME), scaled.get(DataComponents.CUSTOM_NAME));
        var restored = Ae2BaseProcessingPatternHelper.multiplyScale(2, true, decoded, Long.MAX_VALUE, Long.MAX_VALUE);
        assertNotNull(restored);
        assertEquals(input, decode(restored).getSparseInputs());
        assertEquals(output, decode(restored).getSparseOutputs());
        assertEquals(before, source.getComponentsPatch());
    }

    @Test void invalidScaleAndNonDivisibleAmountsLeaveTheSourceUnmodified() {
        var source = pattern(List.of(item(9)), List.of(fluid(7)));
        var before = source.getComponentsPatch();
        for (int scale : new int[] { 0, -1, Integer.MIN_VALUE }) {
            assertNull(Ae2BaseProcessingPatternHelper.multiplyScale(scale, false, decode(source), Long.MAX_VALUE, Long.MAX_VALUE));
            assertNull(Ae2BaseProcessingPatternHelper.multiplyScale(scale, true, decode(source), Long.MAX_VALUE, Long.MAX_VALUE));
        }
        assertNull(Ae2BaseProcessingPatternHelper.multiplyScale(3, true, decode(source), Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(before, source.getComponentsPatch());
    }

    @Test void configuredItemAndBucketLimitsRejectBothInputsAndOutputs() {
        var source = pattern(List.of(item(10)), List.of(fluid(1000)));
        assertNotNull(Ae2BaseProcessingPatternHelper.multiplyScale(2, false, decode(source), 20, 2));
        assertNull(Ae2BaseProcessingPatternHelper.multiplyScale(2, false, decode(source), 19, 2));
        assertNull(Ae2BaseProcessingPatternHelper.multiplyScale(2, false, decode(source), 20, 1));
        assertNull(Ae2BaseProcessingPatternHelper.multiplyScale(1, false, decode(source), 0, Long.MAX_VALUE));
        // A huge bucket limit must saturate to the representable quantity rather than wrap negative.
        assertNotNull(Ae2BaseProcessingPatternHelper.multiplyScale(2, false, decode(source), Long.MAX_VALUE, Long.MAX_VALUE));
    }

    @Test void multiplicationAndAe2CondensationOverflowAreRejectedBeforeReencoding() {
        var source = pattern(List.of(item(Long.MAX_VALUE / 2 + 1)), List.of(fluid(1)));
        assertNull(Ae2BaseProcessingPatternHelper.multiplyScale(2, false, decode(source), Long.MAX_VALUE, Long.MAX_VALUE));
        long half = Long.MAX_VALUE / 2;
        var duplicateInputs = pattern(List.of(item(half), item(half)), List.of(fluid(1)));
        assertNull(Ae2BaseProcessingPatternHelper.multiplyScale(2, false, decode(duplicateInputs), Long.MAX_VALUE, Long.MAX_VALUE));
        var duplicateOutputs = pattern(List.of(item(1)), List.of(fluid(half), fluid(half)));
        assertNull(Ae2BaseProcessingPatternHelper.multiplyScale(2, false, decode(duplicateOutputs), Long.MAX_VALUE, Long.MAX_VALUE));
        assertEquals(half, decode(duplicateOutputs).getSparseOutputs().getFirst().amount());
    }

    @Test void blacklistRecognizesConfiguredCircuitsAndNamedMoldsWithoutMatchingFluidKeys() {
        var circuit = GTItems.PROGRAMMED_CIRCUIT.asStack();
        circuit.set(DataComponents.CUSTOM_NAME, Component.literal("configured circuit"));
        var mold = GTItems.SHAPE_MOLD_PLATE.asStack();
        mold.set(DataComponents.CUSTOM_NAME, Component.literal("named mold"));
        var source = pattern(Arrays.asList(new GenericStack(AEItemKey.of(circuit), 1), null,
                new GenericStack(AEItemKey.of(mold), 1), item(3), fluid(1000)), List.of(item(2)));
        var before = source.getComponentsPatch();
        var modifier = new Ae2BaseProcessingPattern(source, null);
        modifier.setDefaultFilter();
        var filtered = modifier.getPatternItemStack();
        assertEquals(List.of(item(3), fluid(1000)), decode(filtered).getSparseInputs());
        assertEquals(source.get(DataComponents.LORE), filtered.get(DataComponents.LORE));
        assertEquals(before, source.getComponentsPatch());
    }

    @Test void filteringAllRequiredContentAndNonPatternItemsDoesNotCreateInvalidPatterns() {
        var source = pattern(List.of(new GenericStack(AEItemKey.of(GTItems.PROGRAMMED_CIRCUIT.asItem()), 1)), List.of(item(2)));
        var modifier = new Ae2BaseProcessingPattern(source, null);
        modifier.setDefaultFilter();
        assertSame(source, modifier.getPatternItemStack());
        var ordinary = new ItemStack(Items.DIAMOND);
        var invalid = new Ae2BaseProcessingPattern(ordinary, null);
        invalid.setDefaultFilter(); invalid.setScale(2, false);
        assertSame(ordinary, invalid.getPatternItemStack());
        assertNull(Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(ordinary, null));
    }

    @Test void nativeLoreAndScaleMutationsKeepTheEncodedProcessingComponent() {
        var source = pattern(List.of(item(3)), List.of(fluid(1000)));
        var modifier = new Ae2BaseProcessingPattern(source, null);
        var lore = Component.translatable("gtlcore.multiblock.contains_Patttern", 9);
        modifier.setLore(lore);
        assertEquals(new ItemLore(List.of(lore)), modifier.getPatternItemStack().get(DataComponents.LORE));
        modifier.setScale(2, false, Long.MAX_VALUE, Long.MAX_VALUE);
        assertEquals(new ItemLore(List.of(lore)), modifier.getPatternItemStack().get(DataComponents.LORE));
        assertEquals(6, decode(modifier.getPatternItemStack()).getSparseInputs().getFirst().amount());
    }

    @Test void scaledPatternPersistsAndCrossesTheNativeRegistryAwareItemStream() {
        var source = pattern(Arrays.asList(item(LARGE), null, fluid(LARGE)), List.of(item(LARGE)));
        var scaled = Ae2BaseProcessingPatternHelper.multiplyScale(2, false, decode(source), Long.MAX_VALUE, Long.MAX_VALUE);
        assertNotNull(scaled);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        var saved = ItemStack.CODEC.encodeStart(ops, scaled).getOrThrow();
        var loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        assertEquals(scaled.getComponentsPatch(), loaded.getComponentsPatch());
        assertEquals(decode(scaled).getSparseInputs(), decode(loaded).getSparseInputs());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, scaled);
            var received = ItemStack.STREAM_CODEC.decode(buffer);
            assertEquals(scaled.getComponentsPatch(), received.getComponentsPatch());
            assertEquals(LARGE * 2, decode(received).getSparseOutputs().getFirst().amount());
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
