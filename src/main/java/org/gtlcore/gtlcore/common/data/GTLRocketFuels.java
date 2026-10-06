package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.common.data.GTMaterials;
import earth.terrarium.adastra.common.registry.ModFluids;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.material.Fluid;

/** The same fuel must be accepted before placement and by the corresponding rocket entity. */
public final class GTLRocketFuels {
    private GTLRocketFuels() {}

    public static Fluid forTier(int tier) {
        return switch (tier) {
            case 1 -> GTMaterials.RocketFuel.getFluid();
            case 2 -> GTLMaterials.RocketFuelRp1.getFluid();
            case 3 -> GTLMaterials.DenseHydrazineFuelMixture.getFluid();
            case 4 -> GTLMaterials.RocketFuelCn3h7o3.getFluid();
            case 5 -> GTLMaterials.RocketFuelH8n4c2o4.getFluid();
            case 6 -> ModFluids.CRYO_FUEL.get();
            case 7 -> GTLMaterials.StellarEnergyRocketFuel.getFluid();
            default -> throw new IllegalArgumentException("Unsupported GTL rocket tier: " + tier);
        };
    }

    public static int tier(EntityType<?> type) {
        return switch (BuiltInRegistries.ENTITY_TYPE.getKey(type).toString()) {
            case "ad_astra:tier_1_rocket" -> 1;
            case "ad_astra:tier_2_rocket" -> 2;
            case "ad_astra:tier_3_rocket" -> 3;
            case "ad_astra:tier_4_rocket" -> 4;
            case "ad_astra:tier_5_rocket" -> 5;
            case "ad_astra:tier_6_rocket" -> 6;
            case "ad_astra:tier_7_rocket" -> 7;
            default -> 0;
        };
    }
}
