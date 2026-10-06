package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLBedrockFluids;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.*;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeBedrockFluidGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeDatapackLoadsAllThirtyDepositsAndMatchesProductionBootstrap(GameTestHelper helper) {
        var access = helper.getLevel().registryAccess(); var registry = access.registryOrThrow(GTRegistries.Keys.BEDROCK_FLUID);
        var generated = new RegistrySetBuilder().add(GTRegistries.Keys.BEDROCK_FLUID, GTLBedrockFluids::bootstrap)
                .build(RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY))
                .lookupOrThrow(GTRegistries.Keys.BEDROCK_FLUID);
        var ops = RegistryOps.create(JsonOps.INSTANCE, access);
        helper.assertTrue(GTLBedrockFluids.keys().size() == 30, "Core lost a bedrock fluid definition");
        for (var key : GTLBedrockFluids.keys()) {
            var loaded = registry.getHolderOrThrow(key).value(); var expected = generated.getOrThrow(key).value();
            helper.assertTrue(BedrockFluidDefinition.DIRECT_CODEC.encodeStart(ops, loaded).getOrThrow()
                    .equals(BedrockFluidDefinition.DIRECT_CODEC.encodeStart(ops, expected).getOrThrow()), "Native datapack/bootstrap disagree for " + key.location());
        }
        // Core's planetary/void deposits must not leak into the ordinary overworld.
        var veins = new BedrockFluidVeinSavedData(helper.getLevel());
        for (int i = 0; i < 16; i++) {
            var vein = veins.getFluidVeinWorldEntry(i, -i);
            helper.assertTrue(vein.getDefinition() == null || !GTLBedrockFluids.keys().contains(vein.getDefinition().unwrapKey().orElseThrow()), "Core dimension filter was ignored");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeVeinExtractionAndSavedDataRestoreKeepAllCoreDepositKeys(GameTestHelper helper) {
        var level = helper.getLevel(); var registry = level.registryAccess().registryOrThrow(GTRegistries.Keys.BEDROCK_FLUID);
        var veins = new BedrockFluidVeinSavedData(level); boolean oldInfinite = ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids;
        try {
            ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids = false; int x = 0;
            for (var key : GTLBedrockFluids.keys()) {
                var holder = registry.getHolderOrThrow(key); int yield = holder.value().getMinimumYield();
                veins.veinFluids.put(new ChunkPos(x, 0), new FluidVeinWorldEntry(holder, yield, 10));
                veins.depleteVein(x, 0, 0, false);
                helper.assertTrue(veins.getOperationsRemaining(x, 0) == 9 && veins.getFluidYield(x, 0) == yield &&
                        veins.getFluidInChunk(x, 0) == holder.value().getStoredFluid(), "Native extraction lost Core deposit data");
                veins.depleteVein(x, 0, 20, true);
                helper.assertTrue(veins.getOperationsRemaining(x, 0) == 0 && veins.getDepletedFluidYield(x, 0) == holder.value().getDepletedYield(), "Core vein depletion failed"); x++;
            }
            var restored = new BedrockFluidVeinSavedData(level, veins.save(new CompoundTag(), level.registryAccess()));
            helper.assertTrue(restored.veinFluids.size() == 30, "Saving lost Core deposits");
            for (var entry : veins.veinFluids.entrySet()) {
                var copy = restored.veinFluids.get(entry.getKey()); var original = entry.getValue();
                helper.assertTrue(copy.getDefinition().unwrapKey().equals(original.getDefinition().unwrapKey()) &&
                        copy.getFluidYield() == original.getFluidYield() && copy.getOperationsRemaining() == 0, "Native SavedData did not restore a Core deposit");
            }
        } finally { ConfigHolder.INSTANCE.worldgen.oreVeins.infiniteBedrockOresFluids = oldInfinite; }
        helper.succeed();
    }
}
