package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.graph.GraphRingView;
import org.gtlcore.gtlcore.integration.ae2.graph.PatternFingerprint;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeAeRegistryEncodingTest {
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static AEItemKey key() {
        var stack = new ItemStack(Items.DIAMOND_PICKAXE);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("native key"));
        stack.set(DataComponents.MAX_DAMAGE, 7654);
        return AEItemKey.of(stack);
    }
    private static HolderLookup.Provider counted(AtomicInteger calls) {
        return (HolderLookup.Provider) Proxy.newProxyInstance(HolderLookup.Provider.class.getClassLoader(),
                new Class<?>[]{HolderLookup.Provider.class}, (proxy, method, args) -> {
                    if (method.getName().equals("createSerializationContext")) calls.incrementAndGet();
                    return method.invoke(REGISTRIES, args);
                });
    }

    @Test void nativeKeyCodecCachesByRegistryIdentityAndReturnsCallerOwnedTags() {
        var key = key(); var firstCalls = new AtomicInteger(); var secondCalls = new AtomicInteger();
        var first = counted(firstCalls); var second = counted(secondCalls);
        var tag = key.toTagGeneric(first);
        assertEquals("ae2:i", tag.getString(AEKey.TYPE_FIELD));
        assertFalse(tag.contains("#c"));
        assertEquals(key, AEKey.fromTagGeneric(first, tag));
        int encoded = firstCalls.get();
        tag.remove(AEKey.TYPE_FIELD); tag.putString("components", "corrupted by caller");
        assertEquals(key, AEKey.fromTagGeneric(REGISTRIES, key.toTagGeneric(first)));
        assertEquals(encoded, firstCalls.get());
        key.toTagGeneric(second); key.toTagGeneric(second);
        assertEquals(1, secondCalls.get());
        key.toTagGeneric(first); assertEquals(encoded + 1, firstCalls.get());
        assertEquals(7654, key.getFuzzySearchMaxValue());
        assertEquals(7654, key.getFuzzySearchMaxValue());
    }

    @Test void genericNativeTagsAndPacketsKeepComponentsAndAllLongAmountBits() {
        var key = key(); var source = new GenericStack(key, Long.MAX_VALUE);
        var tag = GenericStack.writeTag(REGISTRIES, source);
        assertEquals(source, GenericStack.readTag(REGISTRIES, tag));
        tag.putLong(GenericStack.AMOUNT_FIELD, 1);
        assertFalse(key.toTagGeneric(REGISTRIES).contains(GenericStack.AMOUNT_FIELD));
        assertEquals(Long.MAX_VALUE, GenericStack.writeTag(REGISTRIES, source).getLong(GenericStack.AMOUNT_FIELD));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        try {
            GenericStack.writeBuffer(source, buffer);
            assertEquals(source, GenericStack.readBuffer(buffer)); assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }

    @Test void fingerprintCapturesNativeOutputListsAndDetectsSemanticOutputChanges() {
        var definition = key(); var outputs = new ArrayList<>(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 3)));
        IPatternDetails pattern = new IPatternDetails() {
            public AEItemKey getDefinition() { return definition; }
            public IInput[] getInputs() { return new IInput[0]; }
            public List<GenericStack> getOutputs() { return outputs; }
        };
        var captured = PatternFingerprint.capture(pattern);
        var context = new PatternFingerprint.Context(REGISTRIES);
        String original = context.of(pattern);
        assertEquals(original, context.of(captured));
        outputs.set(0, new GenericStack(AEItemKey.of(Items.IRON_INGOT), 4));
        assertNotEquals(original, context.of(pattern)); assertEquals(original, context.of(captured));
        definition.toTagGeneric(REGISTRIES).remove("components");
        assertEquals(original, context.of(captured));
    }

    @Test void nativeGraphPageKeepsMixedKeysComponentsAndBigIntegerCounts() {
        var item = new GenericStack(key(), Long.MAX_VALUE);
        var fluidStack = new net.neoforged.neoforge.fluids.FluidStack(Fluids.WATER, 1000);
        fluidStack.set(DataComponents.CUSTOM_NAME, Component.literal("graph water"));
        var fluid = new GenericStack(AEFluidKey.of(fluidStack), 9_007_199_254_740_993L);
        var row = new GraphRingView.Row(GraphRingView.Kind.RECIPE, "native", item,
                BigInteger.ONE.shiftLeft(200), 1, BigInteger.ZERO, List.of(fluid), List.of(item), -1);
        var page = new GraphRingView.Page(UUID.randomUUID(), item, true, 0, 1, 1, List.of(row));
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        try {
            GraphRingView.write(page, buffer);
            assertEquals(page, GraphRingView.read(buffer)); assertFalse(buffer.isReadable());
        } finally { buffer.release(); }
    }
}
