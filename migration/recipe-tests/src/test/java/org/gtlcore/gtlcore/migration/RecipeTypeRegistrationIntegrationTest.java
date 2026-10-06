package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.data.GTLRecipes;
import org.gtlcore.gtlcore.common.data.GTLSoundEntries;
import org.gtlcore.gtlcore.common.recipe.GTLRecipeUIModifiers;
import org.gtlcore.gtlcore.config.ConfigHolder;

import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeHelper;
import com.gregtechceu.gtceu.api.registry.registrate.entry.GTRecipeTypeEntry;
import com.gregtechceu.gtceu.common.block.CoilBlock;

import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.recipes.RecipeOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.fluids.FluidStack;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Actual production recipe definitions registered by FML; the remaining Core registry is not loaded here. */
class RecipeTypeRegistrationIntegrationTest {
    @Test
    void everyEnabledTypePreservesItsBaselineIdGroupAndAllFourCapacities() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/recipe-types.json"), StandardCharsets.UTF_8)) {
            var records = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries");
            assertEquals(81, records.size());
            int registered = 0;
            for (var record : records) {
                JsonObject entry = record.getAsJsonObject();
                var nativeEntry = (GTRecipeTypeEntry) GTLRecipeTypes.class.getField(entry.get("field").getAsString()).get(null);
                if (nativeEntry == null) {
                    assertEquals("PRIMITIVE_VOID_ORE_RECIPES", entry.get("field").getAsString());
                    assertFalse(ConfigHolder.INSTANCE.enablePrimitiveVoidOre);
                    continue;
                }
                var type = nativeEntry.value();
                var id = ResourceLocation.parse(entry.get("id").getAsString());
                assertEquals(id, BuiltInRegistries.RECIPE_TYPE.getKey(type));
                assertEquals(id, BuiltInRegistries.RECIPE_SERIALIZER.getKey(type.getSerializer()));
                assertEquals(entry.get("group").getAsString().toLowerCase(), type.group);
                var sizes = entry.getAsJsonArray("max_io_size");
                // The joint harness applies the production Additions capacity overrides.
                int[] override = switch (id.getPath()) {
                    case "door_of_create" -> new int[]{1, 1, 0, 0};
                    case "create_aggregation" -> new int[]{2, 1, 0, 0};
                    case "dimensionally_transcendent_mixer" -> new int[]{9, 1, 16, 1};
                    default -> null;
                };
                assertEquals(override == null ? sizes.get(0).getAsInt() : override[0], type.getMaxInputs(ItemRecipeCapability.CAP), id.toString());
                assertEquals(override == null ? sizes.get(1).getAsInt() : override[1], type.getMaxOutputs(ItemRecipeCapability.CAP), id.toString());
                assertEquals(override == null ? sizes.get(2).getAsInt() : override[2], type.getMaxInputs(FluidRecipeCapability.CAP), id.toString());
                assertEquals(override == null ? sizes.get(3).getAsInt() : override[3], type.getMaxOutputs(FluidRecipeCapability.CAP), id.toString());
                assertNotNull(type.getUiLayout());
                assertNotNull(type.getUiLayout().getProgressBar());
                registered++;
            }
            assertEquals(ConfigHolder.INSTANCE.enablePrimitiveVoidOre ? 81 : 80, registered);
        }
    }

    @Test
    void builderDefaultsResearchAndRecipeInformationAreRetained() {
        var recipe = GTLRecipeTypes.ELECTRIC_IMPLOSION_COMPRESSOR_RECIPES.recipeBuilder("native_defaults").build();
        assertEquals(1, recipe.duration);
        assertEquals(com.gregtechceu.gtceu.api.GTValues.VA[com.gregtechceu.gtceu.api.GTValues.UV],
                RecipeHelper.getRealEUt(recipe).getTotalEU());
        assertTrue(GTLRecipeTypes.SUPRACHRONAL_ASSEMBLY_LINE_RECIPES.value().isHasResearchSlot());
        assertEquals(3, GTLRecipeTypes.DIMENSIONALLY_TRANSCENDENT_PLASMA_FORGE_RECIPES.value()
                .getUiLayout().getRecipeUIModifiers().size());
        assertEquals(3, GTLRecipeTypes.NEUTRON_ACTIVATOR_RECIPES.value().getUiLayout().getRecipeUIModifiers().size());
    }

    @Test
    void customSoundsRetainTheirGtceuRegistryIds() {
        assertEquals(ResourceLocation.parse("gtceu:dtpf"), GTLSoundEntries.DTPF.getId());
        assertEquals(ResourceLocation.parse("gtceu:fusionloop"), GTLSoundEntries.FUSIONLOOP.getId());
        assertEquals(ResourceLocation.parse("gtceu:dtpf"),
                BuiltInRegistries.SOUND_EVENT.getKey(GTLSoundEntries.DTPF.value().getMainEvent()));
    }

    @Test
    void plasmaCoilChoicesKeepTheSpecial273KelvinThreshold() {
        ICoilType special = (ICoilType) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ICoilType.class},
                (proxy, method, args) -> method.getName().equals("getCoilTemperature") ? 273 : null);
        assertTrue(GTLRecipeUIModifiers.PLASMA_COILS.test(special, 32000));
        assertFalse(GTLRecipeUIModifiers.PLASMA_COILS.test(special, 32001));
        assertFalse(GTLRecipeUIModifiers.STANDARD_COILS.test(special, 1800));
        assertTrue(GTLRecipeUIModifiers.STANDARD_COILS.test(CoilBlock.CoilType.CUPRONICKEL, 1800));
        assertFalse(GTLRecipeUIModifiers.STANDARD_COILS.test(CoilBlock.CoilType.CUPRONICKEL, 1801));
    }

    private static class Output implements RecipeOutput {
        final Map<ResourceLocation, GTRecipe> recipes = new LinkedHashMap<>();
        @Override public Advancement.Builder advancement() { return Advancement.Builder.advancement(); }
        @Override public void accept(ResourceLocation id, Recipe<?> recipe, AdvancementHolder advancement, ICondition... conditions) {
            recipes.put(id, (GTRecipe) recipe);
        }
    }

    @Test
    void circuitLineHookProducesNativeDisassemblyWithLongItemsAndFluids() {
        GTLRecipes.DISASSEMBLY_RECORD.clear();
        var output = new Output();
        assertNotNull(GTLRecipeTypes.CIRCUIT_ASSEMBLY_LINE_RECIPES.value().getRecipeBuilder().onSave, "registered circuit hook");
        var source = GTLRecipeTypes.CIRCUIT_ASSEMBLY_LINE_RECIPES.recipeBuilder("native_disassembly");
        assertNotNull(source.onSave, "copied circuit hook");
        source
                .inputItems(LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 3_000_000_000L))
                .inputFluids(new FluidStack(Fluids.WATER, 1000)).outputItems(Items.DIAMOND).save(output);
        var disassembly = output.recipes.values().stream()
                .filter(recipe -> recipe.getType() == GTLRecipeTypes.DISASSEMBLY_RECIPES.value()).findFirst().orElseThrow(() -> new AssertionError(output.recipes.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue().getType().getId()).toList()));
        assertEquals(1, disassembly.duration);
        assertFalse(disassembly.getOutputContents(ItemRecipeCapability.CAP).isEmpty(), source.input.toString() + " original: " +
                source.input.get(ItemRecipeCapability.CAP).stream().map(content -> {
                    var ingredient = ItemRecipeCapability.CAP.of(content.content());
                    return content.chance() + ":" + ingredient.getItems()[0] + ":" + ingredient.getItems()[0].getComponentsPatch();
                }).toList());
        assertEquals(3_000_000_000L, LongIngredient.amount(ItemRecipeCapability.CAP.of(
                disassembly.getOutputContents(ItemRecipeCapability.CAP).getFirst().content())));
        assertEquals(1000, FluidRecipeCapability.CAP.of(
                disassembly.getOutputContents(FluidRecipeCapability.CAP).getFirst().content()).amount());
    }

    @Test
    void explicitObjectCountReplacesTheEmbeddedLongCountForInputsAndOutputs() {
        Object ingredient = LongIngredient.create(Ingredient.of(Items.IRON_INGOT), 3_000_000_000L);
        var recipe = GTLRecipeTypes.PRECISION_ASSEMBLER_RECIPES.recipeBuilder("native_explicit_count")
                .inputItems(ingredient, 7).outputItems(ingredient, 9).build();
        assertEquals(7, LongIngredient.amount(ItemRecipeCapability.CAP.of(
                recipe.getInputContents(ItemRecipeCapability.CAP).getFirst().content())));
        assertEquals(9, LongIngredient.amount(ItemRecipeCapability.CAP.of(
                recipe.getOutputContents(ItemRecipeCapability.CAP).getFirst().content())));
    }
}
