package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.api.recipe.*;
import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import static org.junit.jupiter.api.Assertions.*;

class NativeWirelessRecipeIntegrationTest {
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static final BigInteger AMOUNT = BigInteger.TEN.pow(60).add(BigInteger.valueOf(9_007_199_254_740_993L)).negate();
    private static WirelessGTRecipe recipe() {
        var source = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_wireless_codec").outputItems(Items.IRON_INGOT).duration(40).build();
        source.ocLevel = 5; source.groupColor = 0x35aee1; source.keepSpoilingProgress = true;
        IGTRecipe.of(source).setRealParallels(9_007_199_254_740_993L); IGTRecipe.of(source).setBatchSize(3);
        return new WirelessGTRecipe(source, AMOUNT);
    }
    private static void assertState(com.gregtechceu.gtceu.api.recipe.GTRecipe copy) {
        assertEquals(AMOUNT, ((IWirelessGTRecipe) copy).getWirelessEuTickInputs());
        assertEquals(5, copy.ocLevel); assertEquals(0x35aee1, copy.groupColor); assertTrue(copy.keepSpoilingProgress);
        assertEquals(9_007_199_254_740_993L, IGTRecipe.of(copy).getRealParallels());
        assertEquals(3, IGTRecipe.of(copy).getBatchSize()); assertEquals(40, copy.duration);
    }

    @Test void nativeCopiesAndBuilderKeepWirelessSubtypeAndDoNotShareMutableRecipeData() {
        var source = recipe(); var before = source.data.copy();
        assertState(source.copy()); assertState(source.copy(ContentModifier.IDENTITY, false));
        var copy = source.copy(); copy.data.putString("detached", "yes"); assertEquals(before, source.data);
        var noTicks = source.copyWithoutTicks(); assertNull(noTicks.getWirelessEuTickInputs());
        assertFalse(noTicks.data.contains(NativeWirelessRecipeState.KEY)); assertEquals(before, source.data);
        var builder = new WirelessGTRecipeBuilder(ResourceLocation.fromNamespaceAndPath("gtladditions", "wireless_native_builder"), GTRecipeTypes.FURNACE_RECIPES.value());
        builder.setWirelessEut(AMOUNT).duration(17);
        assertEquals(AMOUNT, builder.build().getWirelessEuTickInputs()); assertEquals(17, builder.buildRawRecipe().duration);
        assertEquals(AMOUNT, builder.copy().build().getWirelessEuTickInputs());
    }

    @Test void actualNativeJsonNbtAndStreamRestoreWirelessSubtypeTogetherWithCoreExtensionState() {
        var source = recipe(); var before = source.data.copy(); var codec = GTRecipeSerializer.CODEC.codec();
        var json = RegistryOps.create(JsonOps.INSTANCE, REGISTRIES);
        assertState(codec.parse(json, codec.encodeStart(json, source).getOrThrow()).getOrThrow());
        var nbt = RegistryOps.create(NbtOps.INSTANCE, REGISTRIES);
        assertState(codec.parse(nbt, codec.encodeStart(nbt, source).getOrThrow()).getOrThrow());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        try {
            GTRecipeSerializer.STREAM_CODEC.encode(buffer, source); assertState(GTRecipeSerializer.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        assertEquals(before, source.data);
    }

    @Test void ordinaryRecipesRemainNativeAndMalformedWirelessDataIsRejected() {
        var ordinary = GTRecipeTypes.FURNACE_RECIPES.recipeBuilder("native_ordinary_recipe").duration(10).build();
        assertSame(ordinary, NativeWirelessRecipeState.restore(ordinary));
        ordinary.data.putByteArray(NativeWirelessRecipeState.KEY, new byte[0]);
        assertThrows(IllegalArgumentException.class, () -> NativeWirelessRecipeState.restore(ordinary));
    }

    @Test void zeroPerTickBatchRestoresItsRemainderThroughNativeJsonNbtAndStreamCodecs() {
        var source = com.gtladd.gtladditions.utils.RecipeCalculationHelper.INSTANCE.buildWirelessRecipe(
                java.util.List.of(), java.util.List.of(), 20, BigInteger.valueOf(9), GTRecipeTypes.DUMMY_RECIPES.value());
        var codec = GTRecipeSerializer.CODEC.codec();
        var json = RegistryOps.create(JsonOps.INSTANCE, REGISTRIES);
        var nbt = RegistryOps.create(NbtOps.INSTANCE, REGISTRIES);
        var copies = new java.util.ArrayList<com.gregtechceu.gtceu.api.recipe.GTRecipe>();
        copies.add(codec.parse(json, codec.encodeStart(json, source).getOrThrow()).getOrThrow());
        copies.add(codec.parse(nbt, codec.encodeStart(nbt, source).getOrThrow()).getOrThrow());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        try {
            GTRecipeSerializer.STREAM_CODEC.encode(buffer, source);
            copies.add(GTRecipeSerializer.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        for (var copy : copies) {
            assertInstanceOf(IWirelessGTRecipe.class, copy);
            assertTrue(copy.hasTick());
            for (int tick = 0; tick < 19; tick++) assertEquals(BigInteger.ZERO, NativeWirelessRecipeState.tickAmount(copy, tick, 20));
            assertEquals(BigInteger.valueOf(-9), NativeWirelessRecipeState.tickAmount(copy, 19, 20));
            assertFalse(copy.copyWithoutTicks().hasTick());
        }
    }

    @Test void paidPrefixSurvivesNativeCodecsAndRegressionWithoutBillingTwice() {
        var source = com.gtladd.gtladditions.utils.RecipeCalculationHelper.INSTANCE.buildWirelessRecipe(
                java.util.List.of(), java.util.List.of(), 20, BigInteger.valueOf(56), GTRecipeTypes.DUMMY_RECIPES.value());
        NativeWirelessRecipeState.recordPayment(source, BigInteger.valueOf(-38));
        var codec = GTRecipeSerializer.CODEC.codec();
        var json = RegistryOps.create(JsonOps.INSTANCE, REGISTRIES);
        var nbt = RegistryOps.create(NbtOps.INSTANCE, REGISTRIES);
        var copies = new java.util.ArrayList<com.gregtechceu.gtceu.api.recipe.GTRecipe>();
        copies.add(source.copy());
        copies.add(codec.parse(json, codec.encodeStart(json, source).getOrThrow()).getOrThrow());
        copies.add(codec.parse(nbt, codec.encodeStart(nbt, source).getOrThrow()).getOrThrow());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), REGISTRIES);
        try {
            GTRecipeSerializer.STREAM_CODEC.encode(buffer, source);
            copies.add(GTRecipeSerializer.STREAM_CODEC.decode(buffer)); assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        for (var copy : copies) {
            for (int tick = 1; tick < 19; tick++) assertEquals(BigInteger.ZERO, NativeWirelessRecipeState.paymentAmount(copy, tick, 20));
            assertEquals(BigInteger.valueOf(-18), NativeWirelessRecipeState.paymentAmount(copy, 19, 20));
            NativeWirelessRecipeState.recordPayment(copy, BigInteger.valueOf(-18));
            assertEquals(BigInteger.ZERO, NativeWirelessRecipeState.paymentAmount(copy, 19, 20));
            assertFalse(copy.copyWithoutTicks().data.contains(NativeWirelessRecipeState.PAID_KEY));
        }
        assertEquals(BigInteger.valueOf(-38), NativeWirelessRecipeState.read(source, NativeWirelessRecipeState.PAID_KEY));
    }
}
