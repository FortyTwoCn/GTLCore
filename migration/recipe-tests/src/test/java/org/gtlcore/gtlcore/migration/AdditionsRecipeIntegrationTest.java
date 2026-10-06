package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.api.registry.GTLAddMultiBlockMachineBuilder;
import com.gtladd.gtladditions.api.registry.GTLAddRegistration;
import com.gtladd.gtladditions.common.modify.GTLAddSoundEntries;
import com.gtladd.gtladditions.common.recipe.GTLAddRecipesTypes;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;

import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.registry.registrate.entry.GTRecipeTypeEntry;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Actual Kotlin production entries registered alongside native Core recipe definitions. */
class AdditionsRecipeIntegrationTest {
    @Test
    void allTwentySixTypesPreserveTheirRegisteredIdsGroupsCapacitiesAndVisibility() throws Exception {
        try (var reader = new InputStreamReader(getClass().getResourceAsStream("/additions-recipe-types.json"), StandardCharsets.UTF_8)) {
            var entries = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("entries");
            assertEquals(26, entries.size());
            for (var record : entries) {
                var expected = record.getAsJsonObject();
                var entry = (GTRecipeTypeEntry) GTLAddRecipesTypes.class.getDeclaredField(expected.get("field").getAsString()).get(null);
                var type = entry.value();
                var id = ResourceLocation.parse(expected.get("id").getAsString());
                assertSame(type, BuiltInRegistries.RECIPE_TYPE.get(id));
                assertSame(type.getSerializer(), BuiltInRegistries.RECIPE_SERIALIZER.get(id));
                assertEquals(expected.get("group").getAsString().toLowerCase(), type.group);
                var capacities = expected.getAsJsonArray("max_io");
                assertEquals(capacities.get(0).getAsInt(), type.getMaxInputs(ItemRecipeCapability.CAP), id.toString());
                assertEquals(capacities.get(1).getAsInt(), type.getMaxOutputs(ItemRecipeCapability.CAP), id.toString());
                assertEquals(capacities.get(2).getAsInt(), type.getMaxInputs(FluidRecipeCapability.CAP), id.toString());
                assertEquals(capacities.get(3).getAsInt(), type.getMaxOutputs(FluidRecipeCapability.CAP), id.toString());
                assertEquals(expected.get("visible").getAsBoolean(), type.getCategory().isXEIVisible(), id.toString());
                var eu = expected.get("eu_io");
                assertEquals(!eu.isJsonNull() && eu.getAsString().equals("IN") ? 1 : 0, type.getMaxInputs(EURecipeCapability.CAP));
                assertEquals(!eu.isJsonNull() && eu.getAsString().equals("OUT") ? 1 : 0, type.getMaxOutputs(EURecipeCapability.CAP));
                assertNotNull(type.getUiLayout().getProgressWidgetSupplier());
            }
        }
    }

    @Test
    void coilCandidateLayoutsCatalystIconAndEveryOverlayAreRetained() {
        assertEquals(3, GTLAddRecipesTypes.STELLAR_LGNITION.value().getUiLayout().getRecipeUIModifiers().size());
        var alchemy = GTLAddRecipesTypes.CHAOTIC_ALCHEMY.value().getUiLayout();
        assertEquals(3, alchemy.getRecipeUIModifiers().size());
        for (int slot = 0; slot < 9; slot++) {
            assertSame(GTGuiTextures.FURNACE_OVERLAY_1, alchemy.capabilityInfo(ItemRecipeCapability.CAP).getOverlay(IO.IN, slot));
        }
        for (int slot = 0; slot < 3; slot++) {
            assertSame(GTGuiTextures.FURNACE_OVERLAY_2, alchemy.capabilityInfo(FluidRecipeCapability.CAP).getOverlay(IO.IN, slot));
        }
        assertSame(GTGuiTextures.FURNACE_OVERLAY_2, alchemy.capabilityInfo(FluidRecipeCapability.CAP).getOverlay(IO.OUT, 0));
        assertEquals(1, GTLAddRecipesTypes.MATTER_EXOTIC.value().getUiLayout().getRecipeUIModifiers().size());
    }

    @Test
    void nativeSoundsPreserveTheirIdsAndAttenuationDistances() {
        var sounds = Map.of("forgeofantichrist", GTLAddSoundEntries.INSTANCE.getFORGE_OF_THE_ANTICHRIST(),
                "quantumoscillation", GTLAddSoundEntries.INSTANCE.getQUANTUM_OSCILLATION(),
                "genesisengine", GTLAddSoundEntries.INSTANCE.getGENESIS_ENGINE(),
                "interstellar", GTLAddSoundEntries.INSTANCE.getINTER_STELLAR());
        sounds.forEach((name, entry) -> {
            var sound = entry.get();
            assertEquals(ResourceLocation.fromNamespaceAndPath("gtladditions", name), sound.getMainEvent().getLocation());
            assertSame(sound.getMainEvent(), BuiltInRegistries.SOUND_EVENT.get(sound.getMainEvent().getLocation()));
            var json = new com.google.gson.JsonObject();
            sound.write(json);
            assertEquals(name.equals("forgeofantichrist") ? 64 : name.equals("interstellar") ? 96 : 48,
                    json.getAsJsonObject(name).getAsJsonArray("sounds").get(0).getAsJsonObject().get("attenuation_distance").getAsInt());
        });
    }

    @Test
    void multiRecipeMachineTypesAreComparedAfterNativeEntriesBind() {
        assertEquals(3, GTLAddRecipesTypes.MULTIPLE_TYPE_RECIPES.size());
        assertTrue(GTLAddRecipesTypes.isMultiRecipeType(GTLAddRecipesTypes.FORGE_OF_THE_ANTICHRIST.value()));
        assertTrue(GTLAddRecipesTypes.isMultiRecipeType(GTLAddRecipesTypes.QUANTUM_OSCILLATION.value()));
        assertTrue(GTLAddRecipesTypes.isMultiRecipeType(GTLRecipeTypes.COSMOS_SIMULATION_RECIPES.value()));
        assertFalse(GTLAddRecipesTypes.isMultiRecipeType(GTLAddRecipesTypes.PHOTON_MATRIX_ETCH.value()));
    }

    @Test
    void nativeBuilderRotationHelpersKeepFacingAndFlipConstraints() {
        var builder = GTLAddRegistration.REGISTRATE.multiblock("unregistered_builder_fixture", WorkableElectricMultiblockMachine::new);
        assertSame(builder, GTLAddMultiBlockMachineBuilder.INSTANCE.nonYAxisRotation(builder));
        assertEquals(RotationState.NON_Y_AXIS, builder.getProperties().rotationState());
        assertFalse(builder.getProperties().allowExtendedFacing());
        GTLAddMultiBlockMachineBuilder.INSTANCE.noneRotation(builder);
        assertEquals(RotationState.NONE, builder.getProperties().rotationState());
        assertFalse(builder.getProperties().allowFlip());
        assertFalse(builder.getProperties().allowExtendedFacing());
    }
}
