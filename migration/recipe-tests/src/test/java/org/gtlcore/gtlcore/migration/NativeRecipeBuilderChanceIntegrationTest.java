package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.NativeContentState;
import org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.recipe.GTLRecipeContents;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.GTRecipeSerializer;
import com.gregtechceu.gtceu.common.data.GTMaterials;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;

import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Native recipe registration, copies, codecs and actual boosted chance rolls for migrated production calls. */
class NativeRecipeBuilderChanceIntegrationTest {
    private static GTRecipe source() {
        var builder = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_boosted_builder").duration(20).EUt(32);
        GTLRecipeContents.chancedInput(builder, new ItemStack(Items.IRON_INGOT, 2), 2500, -500);
        GTLRecipeContents.chancedOutput(builder, TagPrefix.dust, GTMaterials.Iron, 3, 5000, 2500);
        GTLRecipeContents.chancedOutput(builder, LongFluidIngredient.create(Fluids.WATER, 3_000_000_001L), 5000, 500);
        return builder.build();
    }

    private static void assertContents(GTRecipe recipe) {
        var input = recipe.inputs.get(ItemRecipeCapability.CAP).getFirst();
        var output = recipe.outputs.get(ItemRecipeCapability.CAP).getFirst();
        var fluid = recipe.outputs.get(FluidRecipeCapability.CAP).getFirst();
        assertEquals(2, LongIngredient.amount(ItemRecipeCapability.CAP.of(input.content())));
        assertEquals(2500, input.chance()); assertEquals(-500, NativeContentState.of(input).gtlcore$getTierChanceBoost());
        assertEquals(3, LongIngredient.amount(ItemRecipeCapability.CAP.of(output.content())));
        assertTrue(ItemRecipeCapability.CAP.of(output.content()).test(ChemicalHelper.get(TagPrefix.dust, GTMaterials.Iron, 3)));
        assertEquals(5000, output.chance()); assertEquals(2500, NativeContentState.of(output).gtlcore$getTierChanceBoost());
        assertEquals(3_000_000_001L, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(fluid.content())));
        assertEquals(500, NativeContentState.of(fluid).gtlcore$getTierChanceBoost());
    }

    @Test void registeredBoostsSurviveNativeJsonPacketAndIndependentRecipeCopy() {
        var recipe = source(); assertContents(recipe);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var codec = GTRecipeSerializer.CODEC.codec();
        assertContents(codec.parse(ops, codec.encodeStart(ops, recipe).getOrThrow()).getOrThrow());
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            GTRecipeSerializer.STREAM_CODEC.encode(buffer, recipe);
            assertContents(GTRecipeSerializer.STREAM_CODEC.decode(buffer));
            assertEquals(0, buffer.readableBytes());
        } finally { buffer.release(); }
        var copy = recipe.copy(); assertContents(copy);
        NativeContentState.of(copy.outputs.get(ItemRecipeCapability.CAP).getFirst()).gtlcore$setTierChanceBoost(0);
        assertContents(recipe);
    }

    @Test void migratedChanceRegistrationChangesRealNativeRollsAtTheSelectedTier() {
        var content = source().outputs.get(ItemRecipeCapability.CAP).getFirst();
        var cache = new Object2IntOpenHashMap<Object>(); cache.put(content.content(), 0);
        var rolled = LongChanceLogic.OR.roll(ItemRecipeCapability.CAP, List.of(content), cache, 2, 1, 3);
        assertEquals(6, LongIngredient.amount(ItemRecipeCapability.CAP.of(rolled.getFirst().content())));
        assertEquals(5000, content.chance()); assertEquals(3, LongIngredient.amount(ItemRecipeCapability.CAP.of(content.content())));
    }

    @Test void itemAndFluidDataComponentsRemainPartOfTheirExactMatchingIngredients() {
        var item = new ItemStack(Items.DIAMOND, 2); item.set(DataComponents.CUSTOM_NAME, Component.literal("Core catalyst"));
        var fluid = new FluidStack(Fluids.WATER, 123); fluid.set(DataComponents.CUSTOM_NAME, Component.literal("Core coolant"));
        var builder = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_component_chance");
        GTLRecipeContents.chancedOutput(builder, item, 3000, 0);
        GTLRecipeContents.chancedOutput(builder, fluid, 3000, 0);
        var recipe = builder.build();
        var itemIngredient = ItemRecipeCapability.CAP.of(recipe.outputs.get(ItemRecipeCapability.CAP).getFirst().content());
        var fluidIngredient = FluidRecipeCapability.CAP.of(recipe.outputs.get(FluidRecipeCapability.CAP).getFirst().content());
        assertTrue(itemIngredient.test(item)); assertFalse(itemIngredient.test(new ItemStack(Items.DIAMOND, 2)));
        assertTrue(fluidIngredient.test(fluid)); assertFalse(fluidIngredient.test(new FluidStack(Fluids.WATER, 123)));
        assertEquals(2, itemIngredient.count()); assertEquals(123, fluidIngredient.amount());
        assertEquals(Component.literal("Core catalyst"), item.get(DataComponents.CUSTOM_NAME));
        assertEquals(Component.literal("Core coolant"), fluid.get(DataComponents.CUSTOM_NAME));
    }

    @Test void perTickInputAndOutputKeepTheirOwnBoostsAndDoNotLeakBuilderChanceState() {
        var builder = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_tick_chance").perTick(true);
        GTLRecipeContents.chancedInput(builder, LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 3_000_000_001L), 1000, -100);
        GTLRecipeContents.chancedOutput(builder, new FluidStack(Fluids.WATER, 10), 2000, 200);
        builder.perTick(false).outputItems(Items.DIAMOND);
        var recipe = builder.build();
        assertEquals(-100, NativeContentState.of(recipe.tickInputs.get(ItemRecipeCapability.CAP).getFirst()).gtlcore$getTierChanceBoost());
        assertEquals(200, NativeContentState.of(recipe.tickOutputs.get(FluidRecipeCapability.CAP).getFirst()).gtlcore$getTierChanceBoost());
        var ordinary = recipe.outputs.get(ItemRecipeCapability.CAP).getFirst();
        assertEquals(ordinary.maxChance(), ordinary.chance()); assertEquals(0, NativeContentState.of(ordinary).gtlcore$getTierChanceBoost());
        assertTrue(recipe.inputs.isEmpty());
    }

    @Test void invalidChanceDoesNotMutateEarlierOutputs() {
        var builder = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_invalid_chance");
        GTLRecipeContents.chancedOutput(builder, new ItemStack(Items.DIAMOND), 5000, 200);
        assertThrows(IllegalArgumentException.class, () -> GTLRecipeContents.chancedOutput(builder, new ItemStack(Items.IRON_INGOT), 0, 9000));
        assertEquals(1, builder.output.get(ItemRecipeCapability.CAP).size());
        assertEquals(200, NativeContentState.of(builder.output.get(ItemRecipeCapability.CAP).getFirst()).gtlcore$getTierChanceBoost());
    }

    @Test void fractionalOreByproductsKeepExactDenominatorsBoostsAndBuilderState() {
        var builder = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_fraction_byproducts");
        GTLRecipeContents.chancedOutput(builder, TagPrefix.dust, GTMaterials.Iron, 3, "1/9", 0);
        builder.perTick(true);
        GTLRecipeContents.chancedOutput(builder, new ItemStack(Items.DIAMOND, 2), "1/3", 7);
        builder.perTick(false).outputItems(Items.GOLD_INGOT);
        var recipe = builder.build();
        var first = recipe.outputs.get(ItemRecipeCapability.CAP).getFirst();
        assertEquals(1.0 / 9, (double) first.chance() / first.maxChance(), 0);
        assertEquals(3, ItemRecipeCapability.CAP.of(first.content()).count());
        var tick = recipe.tickOutputs.get(ItemRecipeCapability.CAP).getFirst();
        assertEquals(1.0 / 3, (double) tick.chance() / tick.maxChance(), 0);
        assertEquals(7, NativeContentState.of(tick).gtlcore$getTierChanceBoost());
        var ordinary = recipe.outputs.get(ItemRecipeCapability.CAP).getLast();
        assertEquals(ordinary.maxChance(), ordinary.chance());
        assertEquals(0, NativeContentState.of(ordinary).gtlcore$getTierChanceBoost());
    }

    @Test void absentOptionalByproductsKeepTheExistingOutputsAndChanceState() {
        var builder = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_absent_byproducts").outputItems(Items.DIAMOND);
        GTLRecipeContents.chancedOutput(builder, ItemStack.EMPTY, 1400, 850);
        GTLRecipeContents.chancedOutput(builder, ItemStack.EMPTY, "1/9", 0);
        GTLRecipeContents.chancedInput(builder, FluidStack.EMPTY, 1400, 850);
        var recipe = builder.build();
        assertEquals(1, recipe.outputs.get(ItemRecipeCapability.CAP).size());
        assertTrue(recipe.inputs.isEmpty());
        var content = recipe.outputs.get(ItemRecipeCapability.CAP).getFirst();
        assertEquals(content.maxChance(), content.chance());
        assertEquals(0, NativeContentState.of(content).gtlcore$getTierChanceBoost());
    }

    @Test void dynamicMaterialFluidQuantitiesRetainTheNativeFluidAndExactLongAmount() {
        long amount = 3_000_000_001L;
        var ordinary = GTLRecipeContents.fluid(GTMaterials.DistilledWater, 100);
        var large = GTLRecipeContents.fluid(GTMaterials.DistilledWater, amount);
        assertNull(LongFluidIngredient.get(ordinary));
        assertEquals(100, ordinary.amount());
        assertEquals(amount, LongFluidIngredient.amount(large));
        assertTrue(LongFluidIngredient.matchingIngredient(large).test(GTMaterials.DistilledWater.getFluid(1)));
        assertFalse(LongFluidIngredient.matchingIngredient(large).test(new FluidStack(Fluids.LAVA, 1)));
        assertThrows(IllegalArgumentException.class, () -> GTLRecipeContents.fluid(GTMaterials.DistilledWater, 0));
        var recipe = GTRecipeTypes.VACUUM_RECIPES.recipeBuilder("native_dynamic_fluid")
                .inputFluids(large).outputFluids(ordinary).build();
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        var codec = GTRecipeSerializer.CODEC.codec();
        var decoded = codec.parse(ops, codec.encodeStart(ops, recipe).getOrThrow()).getOrThrow();
        assertEquals(amount, LongFluidIngredient.amount(FluidRecipeCapability.CAP.of(
                decoded.inputs.get(FluidRecipeCapability.CAP).getFirst().content())));
    }
}
