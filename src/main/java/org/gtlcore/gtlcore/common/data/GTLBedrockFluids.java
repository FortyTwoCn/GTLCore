package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidDefinition;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.data.worldgen.BootstrapContext;
import java.util.Set;
import java.util.function.Consumer;

@SuppressWarnings("unused")
public class GTLBedrockFluids {

    private static final Map<ResourceKey<BedrockFluidDefinition>, Consumer<BedrockFluidDefinition.Builder>> DEFINITIONS = new LinkedHashMap<>();

    public static final ResourceKey<BedrockFluidDefinition> VOID_HEAVY_OIL = create(GTCEu.id("void_heavy_oil_deposit"),
            builder -> builder
                    .fluid(GTMaterials.HeavyOil.getFluid())
                    .weight(15)
                    .yield(100, 200)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(20)
                    .dimensions(getDim("kubejs", "void")));

    public static final ResourceKey<BedrockFluidDefinition> VOID_LIGHT_OIL = create(GTCEu.id("void_light_oil_deposit"),
            builder -> builder
                    .fluid(GTMaterials.LightOil.getFluid())
                    .weight(25)
                    .yield(175, 300)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(25)
                    .dimensions(getDim("kubejs", "void")));

    public static final ResourceKey<BedrockFluidDefinition> VOID_NATURAL_GAS = create(GTCEu.id("void_natural_gas_deposit"),
            builder -> builder
                    .fluid(GTMaterials.NaturalGas.getFluid())
                    .weight(15)
                    .yield(100, 175)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(20)
                    .dimensions(getDim("kubejs", "void")));

    public static final ResourceKey<BedrockFluidDefinition> VOID_OIL = create(GTCEu.id("void_oil_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Oil.getFluid())
                    .weight(20)
                    .yield(175, 300)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(25)
                    .dimensions(getDim("kubejs", "void")));

    public static final ResourceKey<BedrockFluidDefinition> VOID_RAW_OIL = create(GTCEu.id("void_raw_oil_deposit"),
            builder -> builder
                    .fluid(GTMaterials.RawOil.getFluid())
                    .weight(20)
                    .yield(200, 300)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(25)
                    .dimensions(getDim("kubejs", "void")));

    public static final ResourceKey<BedrockFluidDefinition> VOID_ALT_WATER = create(GTCEu.id("void_salt_water_deposit"),
            builder -> builder
                    .fluid(GTMaterials.SaltWater.getFluid())
                    .weight(10)
                    .yield(50, 100)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(15)
                    .dimensions(getDim("kubejs", "void")));

    public static final ResourceKey<BedrockFluidDefinition> FLAT_HEAVY_OIL = create(GTCEu.id("flat_heavy_oil_deposit"),
            builder -> builder
                    .fluid(GTMaterials.HeavyOil.getFluid())
                    .weight(15)
                    .yield(100, 200)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(20)
                    .dimensions(getDim("kubejs", "flat")));

    public static final ResourceKey<BedrockFluidDefinition> FLAT_LIGHT_OIL = create(GTCEu.id("flat_light_oil_deposit"),
            builder -> builder
                    .fluid(GTMaterials.LightOil.getFluid())
                    .weight(25)
                    .yield(175, 300)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(25)
                    .dimensions(getDim("kubejs", "flat")));

    public static final ResourceKey<BedrockFluidDefinition> FLAT_NATURAL_GAS = create(GTCEu.id("flat_natural_gas_deposit"),
            builder -> builder
                    .fluid(GTMaterials.NaturalGas.getFluid())
                    .weight(15)
                    .yield(100, 175)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(20)
                    .dimensions(getDim("kubejs", "flat")));

    public static final ResourceKey<BedrockFluidDefinition> FLAT_OIL = create(GTCEu.id("flat_oil_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Oil.getFluid())
                    .weight(20)
                    .yield(175, 300)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(25)
                    .dimensions(getDim("kubejs", "flat")));

    public static final ResourceKey<BedrockFluidDefinition> FLAT_RAW_OIL = create(GTCEu.id("flat_raw_oil_deposit"),
            builder -> builder
                    .fluid(GTMaterials.RawOil.getFluid())
                    .weight(20)
                    .yield(200, 300)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(25)
                    .dimensions(getDim("kubejs", "flat")));

    public static final ResourceKey<BedrockFluidDefinition> FLAT_ALT_WATER = create(GTCEu.id("flat_salt_water_deposit"),
            builder -> builder
                    .fluid(GTMaterials.SaltWater.getFluid())
                    .weight(10)
                    .yield(50, 100)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(15)
                    .dimensions(getDim("kubejs", "flat")));

    public static final ResourceKey<BedrockFluidDefinition> HELIUM_3 = create(GTCEu.id("helium3_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Helium3.getFluid())
                    .weight(10)
                    .yield(50, 180)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("ad_astra", "moon")));

    public static final ResourceKey<BedrockFluidDefinition> HELIUM = create(GTCEu.id("helium_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Helium.getFluid())
                    .weight(20)
                    .yield(50, 300)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("ad_astra", "moon")));

    public static final ResourceKey<BedrockFluidDefinition> SULFURIC_ACID = create(GTCEu.id("sulfuric_acid_deposit"),
            builder -> builder
                    .fluid(GTMaterials.SulfuricAcid.getFluid())
                    .weight(20)
                    .yield(100, 250)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("ad_astra", "venus")));

    public static final ResourceKey<BedrockFluidDefinition> DEUTERIUM = create(GTCEu.id("deuterium_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Deuterium.getFluid())
                    .weight(15)
                    .yield(80, 300)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("ad_astra", "mercury")));

    public static final ResourceKey<BedrockFluidDefinition> RADON = create(GTCEu.id("radon_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Radon.getFluid())
                    .weight(20)
                    .yield(50, 80)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("ad_astra", "mars")));

    public static final ResourceKey<BedrockFluidDefinition> CERES_RADON = create(GTCEu.id("ceres_radon_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Radon.getFluid())
                    .weight(15)
                    .yield(100, 250)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "ceres")));

    public static final ResourceKey<BedrockFluidDefinition> METHANE = create(GTCEu.id("methane_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Methane.getFluid())
                    .weight(20)
                    .yield(100, 250)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "titan")));

    public static final ResourceKey<BedrockFluidDefinition> BENZENE = create(GTCEu.id("benzene_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Benzene.getFluid())
                    .weight(15)
                    .yield(60, 160)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "titan")));

    public static final ResourceKey<BedrockFluidDefinition> CHARCOAL_BYPRODUCTS = create(GTCEu.id("charcoal_byproducts"),
            builder -> builder
                    .fluid(GTMaterials.CharcoalByproducts.getFluid())
                    .weight(10)
                    .yield(80, 260)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "titan")));

    public static final ResourceKey<BedrockFluidDefinition> COAL_GAS = create(GTCEu.id("coal_gas_deposit"),
            builder -> builder
                    .fluid(GTMaterials.CoalGas.getFluid())
                    .weight(20)
                    .yield(100, 300)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "io")));

    public static final ResourceKey<BedrockFluidDefinition> NITRIC_ACID = create(GTCEu.id("nitric_acid_deposit"),
            builder -> builder
                    .fluid(GTMaterials.NitricAcid.getFluid())
                    .weight(20)
                    .yield(80, 300)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "pluto")));

    public static final ResourceKey<BedrockFluidDefinition> HYDROCHLORIC_ACID = create(GTCEu.id("hydrochloric_acid_deposit"),
            builder -> builder
                    .fluid(GTMaterials.HydrochloricAcid.getFluid())
                    .weight(20)
                    .yield(100, 350)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "ganymede")));

    public static final ResourceKey<BedrockFluidDefinition> CERES_XENON = create(GTCEu.id("ceres_xenon_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Xenon.getFluid())
                    .weight(20)
                    .yield(100, 250)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "ceres")));

    public static final ResourceKey<BedrockFluidDefinition> CERES_KRYPTON = create(GTCEu.id("ceres_krypton_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Krypton.getFluid())
                    .weight(20)
                    .yield(100, 250)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "ceres")));

    public static final ResourceKey<BedrockFluidDefinition> CERES_NEON = create(GTCEu.id("ceres_neon_deposit"),
            builder -> builder
                    .fluid(GTMaterials.Neon.getFluid())
                    .weight(20)
                    .yield(100, 250)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "ceres")));

    public static final ResourceKey<BedrockFluidDefinition> FLUORINE = create(GTCEu.id("fluorine"),
            builder -> builder
                    .fluid(GTMaterials.Fluorine.getFluid())
                    .weight(10)
                    .yield(180, 320)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "enceladus")));

    public static final ResourceKey<BedrockFluidDefinition> CHLORINE = create(GTCEu.id("chlorine"),
            builder -> builder
                    .fluid(GTMaterials.Chlorine.getFluid())
                    .weight(20)
                    .yield(180, 420)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "enceladus")));

    public static final ResourceKey<BedrockFluidDefinition> UNKNOWWATER = create(GTCEu.id("unknowwater"),
            builder -> builder
                    .fluid(GTLWorldgenMaterials.UnknowWater.getFluid())
                    .weight(20)
                    .yield(40, 60)
                    .depletionAmount(1)
                    .depletionChance(100)
                    .depletedYield(40)
                    .dimensions(getDim("kubejs", "barnarda")));

    /** The native registry is loaded from the checked-in datapack; generation uses the same definitions. */
    public static void bootstrap(BootstrapContext<BedrockFluidDefinition> context) {
        DEFINITIONS.forEach((key, consumer) -> {
            var builder = BedrockFluidDefinition.builder(context.lookup(Registries.BIOME));
            consumer.accept(builder);
            context.register(key, builder.build());
        });
    }

    public static Set<ResourceKey<BedrockFluidDefinition>> keys() {
        return Set.copyOf(DEFINITIONS.keySet());
    }

    private static ResourceKey<BedrockFluidDefinition> create(ResourceLocation id,
                                                             Consumer<BedrockFluidDefinition.Builder> consumer) {
        var key = ResourceKey.create(GTRegistries.Keys.BEDROCK_FLUID, id);
        if (DEFINITIONS.putIfAbsent(key, consumer) != null) throw new IllegalArgumentException("Duplicate bedrock fluid " + id);
        return key;
    }

    public static Set<ResourceKey<Level>> getDim(String namespace, String path) {
        return Set.of(ResourceKey.create(Registries.DIMENSION,
                ResourceLocation.fromNamespaceAndPath(namespace, path)));
    }
}
