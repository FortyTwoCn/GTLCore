package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.*;
import org.gtlcore.gtlcore.common.recipe.condition.GravityCondition;
import org.gtlcore.gtlcore.api.machine.multiblock.GTLCleanroomType;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomProviderTrait;
import com.gregtechceu.gtceu.config.ConfigHolder;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
import com.mojang.serialization.JsonOps;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeMaintenanceIntegrationTest {
    private static AutoConfigurationMaintenanceHatchPartMachine auto() { return (AutoConfigurationMaintenanceHatchPartMachine) NativeMaintenanceFixtures.AUTO.getBlockEntityType().create(BlockPos.ZERO, NativeMaintenanceFixtures.AUTO.getBlock().defaultBlockState()); }
    private static GravityCleaningConfigurationMaintenancePartMachine gravity(boolean configurable) {
        var entry = configurable ? NativeMaintenanceFixtures.GRAVITY : NativeMaintenanceFixtures.GRAVITY_ONLY;
        return (GravityCleaningConfigurationMaintenancePartMachine) entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
    }
    private static RegistryAccess registries() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    @Test void nativeAutomaticMaintenanceRetainsBoundsFloorRoundingAndUnchangedSourceRecipes() {
        var machine = auto(); assertTrue(machine.isFullAuto()); assertFalse(machine.hasMaintenanceProblems()); assertEquals(0, machine.getTimeActive());
        boolean previous = ConfigHolder.INSTANCE.machines.enableMaintenance;
        try {
            ConfigHolder.INSTANCE.machines.enableMaintenance = true; var source = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_maintenance_rounding").duration(103).build();
            machine.setDurationMultiplier(-100); assertEquals(0.2f, machine.getDurationMultiplier()); assertEquals(20, machine.modifyRecipe(source).duration); assertEquals(103, source.duration);
            assertEquals(17f, machine.getTimeMultiplier()); machine.setDurationMultiplier(Float.NaN); assertEquals(0.2f, machine.getDurationMultiplier()); machine.setDurationMultiplier(Float.POSITIVE_INFINITY); assertEquals(0.2f, machine.getDurationMultiplier());
            machine.setDurationMultiplier(100); assertEquals(1.2f, machine.getDurationMultiplier()); assertEquals(123, machine.modifyRecipe(source).duration); assertEquals(-0.6f, machine.getTimeMultiplier());
            machine.setDurationMultiplier(1); assertSame(source, machine.modifyRecipe(source)); ConfigHolder.INSTANCE.machines.enableMaintenance = false; machine.setDurationMultiplier(0.2f); assertSame(source, machine.modifyRecipe(source));
        } finally { ConfigHolder.INSTANCE.machines.enableMaintenance = previous; }
    }
    @Test void nativeGravityOnlyPartCannotModifyDurationAndClampsGravity() {
        var only = gravity(false); only.setDurationMultiplier(0.2f); assertEquals(1f, only.getDurationMultiplier()); only.setCurrentGravity(-1); assertEquals(0, only.getCurrentGravity()); only.setCurrentGravity(101); assertEquals(100, only.getCurrentGravity());
        var both = gravity(true); both.setDurationMultiplier(0.3f); assertEquals(0.3f, both.getDurationMultiplier());
    }
    @Test void cleaningPartsOwnTheirNativeProvidersAndRetainAllLowerCleanroomTypes() {
        var entry = NativeMaintenanceFixtures.CLEAN; var first = entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState()); var second = entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
        var a = first.getTrait(CleanroomProviderTrait.class); var b = second.getTrait(CleanroomProviderTrait.class); assertNotSame(a, b); assertTrue(a.isActive()); assertEquals(ICleaningRoom.LAW_DUMMY_CLEANROOM, a.getProvidedTypes());
        assertTrue(a.getProvidedTypes().contains(CleanroomType.STERILE_CLEANROOM)); assertTrue(a.getProvidedTypes().contains(CleanroomType.CLEANROOM)); assertEquals(2, GTLCleanroomType.LAW_CLEANROOM.getTier());
        assertThrows(UnsupportedOperationException.class, () -> ICleaningRoom.LAW_DUMMY_CLEANROOM.clear());
    }
    @Test void maintenanceAndGravityNativeSaveFieldsAndClientPacketsRoundTripExactly() {
        var first = gravity(true); var second = gravity(true); first.setDurationMultiplier(0.37f); first.setCurrentGravity(73);
        second.loadAdditional(first.saveWithoutMetadata(registries()), registries()); assertEquals(0.37f, second.getDurationMultiplier()); assertEquals(73, second.getCurrentGravity());
        first.setDurationMultiplier(0.83f); first.setCurrentGravity(41); var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries());
        try { first.getSyncDataHolder().writeClientPacket(registries(), packet); second.getSyncDataHolder().readClientPacket(registries(), packet); assertEquals(0.83f, second.getDurationMultiplier()); assertEquals(41, second.getCurrentGravity()); }
        finally { packet.release(); }
    }
    @Test void nativeGravityConditionCodecRetainsReverseAndAltitudeInJsonNbtAndNetwork() {
        var condition = new GravityCondition(true, true, 419);
        var json = RecipeCondition.CODEC.encodeStart(RegistryOps.create(JsonOps.INSTANCE, registries()), condition).getOrThrow();
        assertEquals("gtceu:gravity", json.getAsJsonObject().get("type").getAsString());
        var nbt = RecipeCondition.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries()), condition).getOrThrow();
        var decoded = (GravityCondition) RecipeCondition.CODEC.parse(RegistryOps.create(NbtOps.INSTANCE, registries()), nbt).getOrThrow(); assertTrue(decoded.isReverse()); assertTrue(decoded.isZeroGravity()); assertEquals(419, decoded.getHeightThreshold());
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries());
        try { condition.toNetwork(packet); var copy = (GravityCondition) RecipeCondition.fromNetwork(packet); assertTrue(copy.isReverse()); assertTrue(copy.isZeroGravity()); assertEquals(419, copy.getHeightThreshold()); }
        finally { packet.release(); }
    }
    @Test void gravityEnvironmentKeepsOrbitSkyblockAndBothIndependentAltitudeThresholds() {
        assertTrue(GravityCondition.testEnvironment(true, 419, false, "ad_astra:moon_orbit", 0)); assertFalse(GravityCondition.testEnvironment(false, 419, true, "ad_astra:moon_orbit", 1000));
        assertFalse(GravityCondition.testEnvironment(true, 419, true, "minecraft:overworld", 418)); assertTrue(GravityCondition.testEnvironment(true, 419, true, "minecraft:overworld", 419));
        assertFalse(GravityCondition.testEnvironment(true, 419, false, "minecraft:overworld", 1000));
        for (var dimension : java.util.List.of("kubejs:void", "kubejs:flat")) { assertFalse(GravityCondition.testEnvironment(true, 419, true, dimension, 504)); assertTrue(GravityCondition.testEnvironment(true, 419, true, dimension, 505)); }
        assertFalse(GravityCondition.testEnvironment(true, 419, true, "minecraft:the_end", 1000));
    }
}
