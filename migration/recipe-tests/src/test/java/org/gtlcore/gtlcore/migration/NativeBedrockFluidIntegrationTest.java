package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLBedrockFluids;
import org.gtlcore.gtlcore.common.data.GTLWorldgenMaterials;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class NativeBedrockFluidIntegrationTest {
    private static JsonObject read(String path) throws IOException {
        try (var stream = NativeBedrockFluidIntegrationTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
    @Test void nativeDepositKeysAndResourcesRetainAllThirtyBaselineDefinitions() throws IOException {
        var entries = read("/bedrock-fluids.json").getAsJsonArray("entries");
        assertEquals(30, entries.size()); assertEquals(30, GTLBedrockFluids.keys().size());
        var ids = GTLBedrockFluids.keys().stream().map(key -> key.location().toString()).collect(Collectors.toSet());
        for (var entry : entries) {
            var expected = entry.getAsJsonObject(); String id = expected.get("id").getAsString();
            assertTrue(ids.remove(id), id);
            var key = GTLBedrockFluids.keys().stream().filter(k -> k.location().toString().equals(id)).findFirst().orElseThrow();
            assertEquals(GTRegistries.Keys.BEDROCK_FLUID, key.registryKey());
            assertEquals(expected.get("native"), read("/data/gtceu/gtceu/bedrock_fluid/" + key.location().getPath() + ".json"));
        }
        assertTrue(ids.isEmpty()); assertThrows(UnsupportedOperationException.class, () -> GTLBedrockFluids.keys().clear());
    }
    @Test void nativeDepositCodecKeepsFluidsDimensionsYieldsAndDepletionAcrossJsonAndNbt() throws IOException {
        var registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        var jsonOps = RegistryOps.create(JsonOps.INSTANCE, registry); var nbtOps = RegistryOps.create(NbtOps.INSTANCE, registry);
        for (var key : GTLBedrockFluids.keys()) {
            var json = read("/data/gtceu/gtceu/bedrock_fluid/" + key.location().getPath() + ".json");
            var definition = BedrockFluidDefinition.DIRECT_CODEC.parse(jsonOps, json).getOrThrow();
            var nbt = BedrockFluidDefinition.DIRECT_CODEC.encodeStart(nbtOps, definition).getOrThrow();
            var restored = BedrockFluidDefinition.DIRECT_CODEC.parse(nbtOps, nbt).getOrThrow();
            assertEquals(json, BedrockFluidDefinition.DIRECT_CODEC.encodeStart(jsonOps, restored).getOrThrow().getAsJsonObject());
            assertTrue(restored.canGenerate()); assertTrue(restored.getBiomeWeightModifier().isEmpty());
        }
    }
    @Test void barnardaDepositUsesTheSharedProductionUnknownWaterMaterial() throws IOException {
        assertSame(GTLWorldgenMaterials.UnknowWater.getFluid(), BuiltInRegistries.FLUID.get(ResourceLocation.parse("gtceu:unknowwater")));
        assertEquals("?", GTLWorldgenMaterials.UnknowWater.getChemicalFormula());
        assertEquals("gtceu:unknowwater", read("/data/gtceu/gtceu/bedrock_fluid/unknowwater.json").get("fluid").getAsString());
    }
}
