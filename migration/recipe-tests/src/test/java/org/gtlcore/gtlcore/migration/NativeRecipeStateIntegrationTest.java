package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.IGTRecipe;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.TierCasingMachine;
import org.gtlcore.gtlcore.config.ConfigHolder;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.content.ContentModifier;
import com.gregtechceu.gtceu.api.recipe.ingredient.EnergyStack;
import com.gregtechceu.gtceu.api.sync_system.TypeDeclaration;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.ValueTransformer.TransformerContext;
import com.gregtechceu.gtceu.api.sync_system.data_transformers.gtceu.GTRecipeTransformer;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.data.recipe.builder.GTRecipeBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Production mixins, the native serializer/transformer and actual native machine recipe traits. */
class NativeRecipeStateIntegrationTest {
    private static GTRecipeBuilder builder() {
        return GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_state")
                .inputItems(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 3_000_000_001L))
                .inputFluids(LongFluidIngredient.create(Fluids.WATER, 3_000_000_002L))
                .outputItems(Items.DIAMOND).duration(100).EUt(32, 4);
    }

    private static GTRecipe runningRecipe() {
        var recipe = builder().build();
        var extension = IGTRecipe.of(recipe);
        extension.setRealParallels(9_007_199_254_740_993L);
        extension.setBatchSize(37);
        extension.setBatchProcessed(true);
        extension.setSubTickParallelized(true);
        recipe.ocLevel = 5;
        return recipe;
    }

    private static RegistryAccess registries() {
        return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    }

    private static void assertState(GTRecipe recipe) {
        var extension = IGTRecipe.of(recipe);
        assertEquals(9_007_199_254_740_993L, extension.getRealParallels());
        assertEquals(Integer.MAX_VALUE, recipe.parallels);
        assertEquals(37, extension.getBatchSize());
        assertTrue(extension.isBatchProcessed());
        assertTrue(extension.isSubTickParallelized());
        assertEquals(5, recipe.ocLevel);
        assertEquals(1, extension.getEuTier());
        assertEquals(new EnergyStack(32, 4), recipe.getInputEUt());
        assertEquals(3_000_000_001L, LongIngredient.amount(ItemRecipeCapability.CAP.of(recipe.inputs.get(ItemRecipeCapability.CAP).getFirst().content())));
        assertEquals(3_000_000_002L, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(recipe.inputs.get(FluidRecipeCapability.CAP).getFirst().content())));
    }

    @Test
    void jsonAndNbtKeepExactParallelsBatchFlagsOverclockAndLongContents() {
        var recipe = runningRecipe();
        var before = recipe.data.copy();
        var codec = GTRecipeSerializer.CODEC.codec();
        var jsonOps = RegistryOps.create(JsonOps.INSTANCE, registries());
        assertState(codec.parse(jsonOps, codec.encodeStart(jsonOps, recipe).getOrThrow()).getOrThrow());
        var nbtOps = RegistryOps.create(NbtOps.INSTANCE, registries());
        assertState(codec.parse(nbtOps, codec.encodeStart(nbtOps, recipe).getOrThrow()).getOrThrow());
        assertEquals(before, recipe.data, "Encoding must not modify the running recipe");
    }

    @Test
    void nativeRegistryAwareRecipeStreamKeepsStateAndConsumesExactlyItsPacket() {
        var recipe = runningRecipe();
        var before = recipe.data.copy();
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries());
        try {
            GTRecipeSerializer.STREAM_CODEC.encode(buffer, recipe);
            var copy = GTRecipeSerializer.STREAM_CODEC.decode(buffer);
            assertEquals(recipe.id, copy.id);
            assertState(copy);
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        assertEquals(before, recipe.data);
    }

    @Test
    void nativeMachineRecipeFieldTransformerSavesAndReloadsItsRuntimeState() {
        var recipe = runningRecipe();
        var context = new TransformerContext<GTRecipe>(this, new TypeDeclaration(GTRecipe.class), recipe,
                "lastRecipe", false, false, registries());
        var transformer = new GTRecipeTransformer();
        var copy = transformer.deserializeNBT(transformer.serializeNBT(recipe, context), context);
        assertNotNull(copy);
        assertEquals(recipe.id, copy.id);
        assertState(copy);
    }

    @Test
    void nativeCopiesRetainStateWithoutAllowingChildChangesToModifyTheParent() {
        var source = runningRecipe();
        var copy = source.copy(ContentModifier.multiplier(2), false);
        assertState(source);
        assertEquals(9_007_199_254_740_993L, IGTRecipe.of(copy).getRealParallels());
        IGTRecipe.of(copy).setRealParallels(123);
        IGTRecipe.of(copy).setBatchSize(4);
        IGTRecipe.of(copy).setBatchProcessed(false);
        IGTRecipe.of(copy).setSubTickParallelized(false);
        assertState(source);
        assertEquals(123, IGTRecipe.of(copy).getRealParallels());
        assertEquals(4, IGTRecipe.of(copy).getBatchSize());
        assertFalse(IGTRecipe.of(copy).isBatchProcessed());
        assertFalse(IGTRecipe.of(copy).isSubTickParallelized());
    }

    @Test
    void defaultStateTracksNativeCountsAndSaturatesTheirProduct() {
        var recipe = builder().build();
        assertEquals(1, IGTRecipe.of(recipe).getRealParallels());
        recipe.parallels = 3; recipe.subtickParallels = 5; recipe.batchParallels = 7;
        assertEquals(105, IGTRecipe.of(recipe).getRealParallels());
        assertEquals(7, IGTRecipe.of(recipe).getBatchSize());
        recipe.parallels = recipe.subtickParallels = recipe.batchParallels = Integer.MAX_VALUE;
        assertEquals(Long.MAX_VALUE, IGTRecipe.of(recipe).getRealParallels());
        assertThrows(IllegalArgumentException.class, () -> IGTRecipe.of(recipe).setRealParallels(0));
        assertThrows(IllegalArgumentException.class, () -> IGTRecipe.of(recipe).setBatchSize(0));
    }

    @Test
    void removingTicksClearsTheOverrideWithoutChangingTheSource() {
        var recipe = builder().build();
        IGTRecipe.of(recipe).setHasTick(true);
        var copy = recipe.copyWithoutTicks();
        assertFalse(copy.hasTick());
        assertTrue(recipe.hasTick());
        // Native hasTick must continue to observe contents added after construction.
        copy.tickInputs.put(EURecipeCapability.CAP, List.of(new Content(new EnergyStack(32), 10000, 10000)));
        assertTrue(copy.hasTick());
    }

    @Test
    void durationMultiplierIsAppliedOnceWithoutChangingTheBuilderOrItsCopies() {
        double previous = ConfigHolder.INSTANCE.durationMultiplier;
        try {
            ConfigHolder.INSTANCE.durationMultiplier = 0.25;
            var builder = builder();
            assertEquals(25, builder.build().duration);
            assertEquals(25, builder.build().duration);
            assertEquals(25, builder.copy("native_state_copy").build().duration);
            assertEquals(100, builder.duration);
            assertEquals(25, builder.build().copy().duration);
            ConfigHolder.INSTANCE.durationMultiplier = 0;
            assertEquals(1, builder.build().duration);
            ConfigHolder.INSTANCE.durationMultiplier = Double.MAX_VALUE;
            assertEquals(Integer.MAX_VALUE, builder.build().duration);
        } finally { ConfigHolder.INSTANCE.durationMultiplier = previous; }
    }

    @Test
    void generatorsAndExemptRecipeCategoriesKeepTheirOriginalDuration() {
        double previous = ConfigHolder.INSTANCE.durationMultiplier;
        try {
            ConfigHolder.INSTANCE.durationMultiplier = 0.25;
            assertEquals(100, GTRecipeTypes.PLASMA_GENERATOR_FUELS.recipeBuilder("negative_generator")
                    .EUt(-32).duration(100).build().duration);
            assertEquals(100, GTRecipeTypes.PLASMA_GENERATOR_FUELS.recipeBuilder("native_generator")
                    .perTick(true).outputEU(32).duration(100).build().duration);
            for (var type : List.of(GTRecipeTypes.STEAM_BOILER_RECIPES, GTRecipeTypes.LARGE_BOILER_RECIPES,
                    GTLRecipeTypes.BLOCK_CONVERSION_RECIPES, GTLRecipeTypes.DYSON_SPHERE_RECIPES,
                    GTLRecipeTypes.WEATHER_CONTROL_RECIPES, GTLRecipeTypes.SPACE_ELEVATOR_RECIPES)) {
                assertEquals(100, type.recipeBuilder("exempt").duration(100).EUt(32).build().duration, type.value().id.toString());
            }
        } finally { ConfigHolder.INSTANCE.durationMultiplier = previous; }
    }

    @Test
    void tierUsesNativeVoltageAndIsAlsoAvailableForDirectEnergyInputs() {
        assertEquals(1, IGTRecipe.of(builder().build()).getEuTier());
        var directBuilder = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("direct_eu");
        directBuilder.tickInput.remove(EURecipeCapability.CAP);
        var direct = directBuilder.perTick(true).inputEU(512, 16).build();
        assertEquals(new EnergyStack(512, 16), direct.getInputEUt());
        assertEquals(3, IGTRecipe.of(direct).getEuTier());
        assertEquals(1, IGTRecipe.of(GTRecipeTypes.PLASMA_GENERATOR_FUELS.recipeBuilder("generator_tier").EUt(-32).build()).getEuTier());
    }

    @Test
    void nativeTickMatcherRejectsHighTierRecipesAndPublishesTheVoltageFailureReason() {
        var definition = GTMultiMachines.VACUUM_FREEZER;
        var machine = new TierCasingMachine(new BlockEntityCreationInfo(definition.getBlockEntityType(), BlockPos.ZERO,
                definition.getBlock().defaultBlockState()), "CATier") {
            @Override public long getMaxVoltage() { return 32; }
        };
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("high_tier_tick").EUt(512).build();
        var result = RecipeHelper.matchTickRecipe(machine, recipe);
        assertFalse(result.isSuccess());
        assertEquals(EURecipeCapability.CAP, result.capability());
        assertEquals(IO.IN, result.io());
        assertEquals("gtceu.recipe.fail.voltage.tier", ((TranslatableContents) result.reason().getContents()).getKey());
        assertNotNull(machine.getRecipeLogic().getBestFailureReason());
    }
}
