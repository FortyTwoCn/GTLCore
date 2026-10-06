package org.gtlcore.gtlcore.api.recipe.ingredient;

import net.minecraft.SharedConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LongIngredientTest {
    private static final long ABOVE_DOUBLE_PRECISION = 9_007_199_254_740_993L;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nativeSizedCodecsKeepExactLongQuantityAndRegistryId() {
        var value = LongIngredient.create(Ingredient.of(Items.DIAMOND), ABOVE_DOUBLE_PRECISION);
        assertEquals(Integer.MAX_VALUE, value.count());
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .createSerializationContext(JsonOps.INSTANCE);
        for (var codec : java.util.List.of(SizedIngredient.NESTED_CODEC, SizedIngredient.FLAT_CODEC)) {
            var json = codec.encodeStart(ops, value).getOrThrow();
            assertTrue(json.toString().contains("gtceu:long"));
            var restored = codec.parse(ops, json).getOrThrow();
            assertEquals(ABOVE_DOUBLE_PRECISION, LongIngredient.amount(restored));
            assertTrue(restored.ingredient().test(new ItemStack(Items.DIAMOND)));
            assertFalse(restored.ingredient().test(new ItemStack(Items.COAL)));
        }
    }

    @Test
    void nativeStreamCodecKeepsFullLongAndConsumesAllBytes() {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),
                RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY));
        try {
            var original = LongIngredient.create(Ingredient.of(Items.IRON_INGOT), Long.MAX_VALUE);
            SizedIngredient.STREAM_CODEC.encode(buffer, original);
            var restored = SizedIngredient.STREAM_CODEC.decode(buffer);
            assertEquals(Long.MAX_VALUE, LongIngredient.amount(restored));
            assertEquals(0, buffer.readableBytes());
            assertTrue(restored.ingredient().test(new ItemStack(Items.IRON_INGOT)));
        } finally {
            buffer.release();
        }
    }

    @Test
    void precisionCopyAndRemainderNeverMutateTheRecipe() {
        var original = LongIngredient.create(Ingredient.of(Items.PAPER), ABOVE_DOUBLE_PRECISION);
        var added = LongIngredient.modify(original, 1, 1);
        assertEquals(ABOVE_DOUBLE_PRECISION + 1, LongIngredient.amount(added));
        var doubled = LongIngredient.modify(original, 2, 0);
        assertEquals(ABOVE_DOUBLE_PRECISION * 2, LongIngredient.amount(doubled));
        var remainder = LongIngredient.withAmount(original, 17);
        assertEquals(17, LongIngredient.amount(remainder));
        assertEquals(ABOVE_DOUBLE_PRECISION, LongIngredient.amount(original));
        assertEquals(ABOVE_DOUBLE_PRECISION, LongIngredient.amount(LongIngredient.copy(original)));
    }

    @Test
    void modifierSaturatesOverflowAndRejectsNonFiniteValues() {
        var original = LongIngredient.create(Ingredient.of(Items.PAPER), Long.MAX_VALUE);
        assertEquals(Long.MAX_VALUE, LongIngredient.amount(LongIngredient.modify(original, 2, 0)));
        assertEquals(1, LongIngredient.amount(LongIngredient.modify(original, 0, -10)));
        assertThrows(IllegalArgumentException.class, () -> LongIngredient.modify(original, Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> LongIngredient.modify(original, 1, Double.POSITIVE_INFINITY));
    }

    @Test
    void componentMatchingSurvivesNativeSerialization() {
        var tag = new CompoundTag();
        tag.putString("disk_uuid", "00000000-0000-0000-0000-000000000001");
        var stack = new ItemStack(Items.PAPER);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        var original = LongIngredient.create(stack, 3_000_000_000L);
        var ops = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY)
                .createSerializationContext(JsonOps.INSTANCE);
        var restored = SizedIngredient.NESTED_CODEC.parse(ops,
                SizedIngredient.NESTED_CODEC.encodeStart(ops, original).getOrThrow()).getOrThrow();
        assertEquals(3_000_000_000L, LongIngredient.amount(restored));
        assertTrue(restored.ingredient().test(stack.copy()));
        assertFalse(restored.ingredient().test(new ItemStack(Items.PAPER)));
        var changed = stack.copy();
        changed.set(DataComponents.CUSTOM_NAME, net.minecraft.network.chat.Component.literal("changed"));
        assertFalse(restored.ingredient().test(changed));
    }

    @Test
    void invalidRecipeQuantitiesFailBeforeEnteringHandlers() {
        assertThrows(IllegalArgumentException.class, () -> LongIngredient.create(Ingredient.of(Items.PAPER), 0));
        assertThrows(IllegalArgumentException.class, () -> LongIngredient.create(Ingredient.of(Items.PAPER), -1));
        var json = JsonParser.parseString("{\"ingredient\":{\"item\":\"minecraft:paper\"},\"actualAmount\":0}");
        assertTrue(LongIngredient.CODEC.codec().parse(JsonOps.INSTANCE, json).error().isPresent());
        json.getAsJsonObject().addProperty("actualAmount", -1);
        assertTrue(LongIngredient.CODEC.codec().parse(JsonOps.INSTANCE, json).error().isPresent());
    }
}
