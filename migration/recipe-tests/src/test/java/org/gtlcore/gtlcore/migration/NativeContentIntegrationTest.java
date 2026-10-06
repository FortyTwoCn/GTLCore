package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.NativeContentState;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.sync_system.TypeDeclaration;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.ValueTransformer.TransformerContext;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.gtceu.GTRecipeTransformer;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Actual native codecs and synchronization transformers, with production Content extensions. */
class NativeContentIntegrationTest {
    private static RegistryAccess registries() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    private static Content content() {
        return NativeContentState.create(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 9_007_199_254_740_993L),
                5000, 10000, -2000, "recipe_cycle_container", "plasma_return");
    }
    private static void assertMetadata(Content content) {
        var state = NativeContentState.of(content);
        assertEquals(5000, content.chance()); assertEquals(10000, content.maxChance());
        assertEquals(-2000, state.gtlcore$getTierChanceBoost());
        assertEquals("recipe_cycle_container", state.gtlcore$getSlotName());
        assertEquals("plasma_return", state.gtlcore$getUiName());
    }
    private static GTRecipe recipe() {
        var builder = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_content_state")
                .inputItems(Items.IRON_INGOT).outputItems(Items.DIAMOND)
                .perTick(true).inputFluids(LongFluidIngredient.create(Fluids.WATER, 3_000_000_003L))
                .outputFluids(LongFluidIngredient.create(Fluids.LAVA, 3_000_000_004L)).perTick(false)
                .duration(100).EUt(32);
        builder.input.get(ItemRecipeCapability.CAP).set(0, content());
        builder.output.get(ItemRecipeCapability.CAP).set(0, content());
        for (var map : java.util.List.of(builder.tickInput, builder.tickOutput)) {
            var fluid = map.get(FluidRecipeCapability.CAP).getFirst();
            map.get(FluidRecipeCapability.CAP).set(0, NativeContentState.create(fluid.content(), 5000, 10000, -2000,
                    "recipe_cycle_container", "plasma_return"));
        }
        return builder.build();
    }
    private static void assertRecipe(GTRecipe recipe) {
        assertMetadata(recipe.inputs.get(ItemRecipeCapability.CAP).getFirst());
        assertMetadata(recipe.outputs.get(ItemRecipeCapability.CAP).getFirst());
        assertMetadata(recipe.tickInputs.get(FluidRecipeCapability.CAP).getFirst());
        assertMetadata(recipe.tickOutputs.get(FluidRecipeCapability.CAP).getFirst());
        assertEquals(9_007_199_254_740_993L, LongIngredient.amount(ItemRecipeCapability.CAP.of(recipe.outputs.get(ItemRecipeCapability.CAP).getFirst().content())));
        assertEquals(3_000_000_003L, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(recipe.tickInputs.get(FluidRecipeCapability.CAP).getFirst().content())));
        assertEquals(3_000_000_004L, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(recipe.tickOutputs.get(FluidRecipeCapability.CAP).getFirst().content())));
    }
    @Test void contentJsonAndNbtKeepNegativeBoostsSlotNamesUiNamesAndLongIngredients() {
        var content = content(); var codec = Content.codec(ItemRecipeCapability.CAP);
        var jsonOps = RegistryOps.create(JsonOps.INSTANCE, registries());
        var json = codec.encodeStart(jsonOps, content).getOrThrow();
        assertEquals(-2000, json.getAsJsonObject().get("tierChanceBoost").getAsInt());
        assertMetadata(codec.parse(jsonOps, json).getOrThrow());
        var nbtOps = RegistryOps.create(NbtOps.INSTANCE, registries());
        assertMetadata(codec.parse(nbtOps, codec.encodeStart(nbtOps, content).getOrThrow()).getOrThrow());
    }
    @Test void everyNativeContentCopyRetainsMetadataAndKeepsChancedQuantityRules() {
        var content = content();
        assertMetadata(content.copy(ItemRecipeCapability.CAP));
        var copy = content.copy(ItemRecipeCapability.CAP, ContentModifier.multiplier(3)); assertMetadata(copy);
        assertEquals(9_007_199_254_740_993L, LongIngredient.amount(ItemRecipeCapability.CAP.of(copy.content())));
        copy = content.copyChanced(ItemRecipeCapability.CAP, ContentModifier.multiplier(3)); assertMetadata(copy);
        assertEquals(27_021_597_764_222_979L, LongIngredient.amount(ItemRecipeCapability.CAP.of(copy.content())));
        NativeContentState.of(copy).gtlcore$setTierChanceBoost(1000); assertMetadata(content);
    }
    @Test void nativeRecipeJsonAndNbtKeepMetadataForEachCapabilityAndTickDirection() {
        var recipe = recipe(); var codec = GTRecipeSerializer.CODEC.codec();
        var jsonOps = RegistryOps.create(JsonOps.INSTANCE, registries());
        assertRecipe(codec.parse(jsonOps, codec.encodeStart(jsonOps, recipe).getOrThrow()).getOrThrow());
        var nbtOps = RegistryOps.create(NbtOps.INSTANCE, registries());
        assertRecipe(codec.parse(nbtOps, codec.encodeStart(nbtOps, recipe).getOrThrow()).getOrThrow());
    }
    @Test void nativeRecipePacketKeepsMetadataWithoutAddingBytesToTheContentProtocol() {
        var recipe = recipe(); var before = recipe.data.copy();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries());
        try {
            GTRecipeSerializer.STREAM_CODEC.encode(buffer, recipe);
            assertRecipe(GTRecipeSerializer.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        assertEquals(before, recipe.data, "Encoding must not modify the running recipe");
    }
    @Test void nativeRecipeFieldTransformerKeepsTheMetadataAcrossMachinePersistence() {
        var recipe = recipe();
        var context = new TransformerContext<GTRecipe>(this, new TypeDeclaration(GTRecipe.class), recipe,
                "lastRecipe", false, false, registries());
        var transformer = new GTRecipeTransformer();
        assertRecipe(transformer.deserializeNBT(transformer.serializeNBT(recipe, context), context));
    }
    @Test void removingTickContentsDoesNotShareMutableContentMetadataWithTheCachedRecipe() {
        var source = recipe();
        var copy = source.copyWithoutTicks();
        assertTrue(copy.tickInputs.isEmpty()); assertTrue(copy.tickOutputs.isEmpty());
        for (var capability : java.util.List.of(ItemRecipeCapability.CAP)) {
            var input = copy.inputs.get(capability).getFirst();
            var output = copy.outputs.get(capability).getFirst();
            assertNotSame(source.inputs.get(capability).getFirst(), input);
            assertNotSame(source.outputs.get(capability).getFirst(), output);
            assertMetadata(input); assertMetadata(output);
            NativeContentState.of(input).gtlcore$setTierChanceBoost(1000);
            NativeContentState.of(output).gtlcore$setSlotName("changed");
            assertMetadata(source.inputs.get(capability).getFirst());
            assertMetadata(source.outputs.get(capability).getFirst());
        }
    }
    @Test void recipesWithoutExtraMetadataUseTheNativeDefaults() {
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_plain_content")
                .inputItems(Items.IRON_INGOT).outputItems(Items.DIAMOND).EUt(32).build();
        var state = NativeContentState.of(recipe.outputs.get(ItemRecipeCapability.CAP).getFirst());
        assertEquals(0, state.gtlcore$getTierChanceBoost()); assertNull(state.gtlcore$getSlotName()); assertNull(state.gtlcore$getUiName());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries());
        try {
            GTRecipeSerializer.STREAM_CODEC.encode(buffer, recipe);
            var copy = GTRecipeSerializer.STREAM_CODEC.decode(buffer);
            assertFalse(copy.data.contains(NativeContentState.KEY));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
    }
}
