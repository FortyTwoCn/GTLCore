package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.fluids.FluidBuilder;

import static com.gregtechceu.gtceu.common.data.GTMaterials.Water;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.DISABLE_DECOMPOSITION;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet.*;

/** Native definitions shared by the production material registration and recipe callback validation. */
public final class GTLCallbackMaterials {
    private GTLCallbackMaterials() {}
    private static boolean initialized;
    public static Material Photoresist;
    public static Material EuvPhotoresist;
    public static Material GradePurifiedWater8;
    public static Material GradePurifiedWater16;
    public static Material MutatedLivingSolder;
    public static Material SuperMutatedLivingSolder;
    public static Material SupercriticalSteam;

    public static void init() {
        if (initialized) return;
        Photoresist = new Material.Builder(GTCEu.id("photoresist"))
                .fluid()
                .color(0x2f4f4f)
                .iconSet(BRIGHT)
                .buildAndRegister();

        EuvPhotoresist = new Material.Builder(GTCEu.id("euv_photoresist"))
                .fluid()
                .color(0x4b0082)
                .iconSet(BRIGHT)
                .buildAndRegister();

        GradePurifiedWater8 = new Material.Builder(GTCEu.id("grade_8_purified_water"))
                .fluid()
                .components(Water, 1)
                .color(0x0058cd)
                .iconSet(FLUID)
                .flags(DISABLE_DECOMPOSITION)
                .buildAndRegister();

        GradePurifiedWater16 = new Material.Builder(GTCEu.id("grade_16_purified_water"))
                .fluid()
                .components(Water, 1)
                .color(0x0058cd)
                .iconSet(FLUID)
                .flags(DISABLE_DECOMPOSITION)
                .buildAndRegister();

        MutatedLivingSolder = new Material.Builder(GTCEu.id("mutated_living_solder"))
                .fluid()
                .color(0xC18FCC).iconSet(METALLIC)
                .radioactiveHazard(1)
                .buildAndRegister();

        SuperMutatedLivingSolder = new Material.Builder(GTCEu.id("super_mutated_living_solder"))
                .fluid()
                .color(0xB662FF).iconSet(METALLIC)
                .radioactiveHazard(2)
                .buildAndRegister();

        SupercriticalSteam = new Material.Builder(GTCEu.id("supercritical_steam"))
                .gas(new FluidBuilder().temperature(1000))
                .color(0xC4C4C4)
                .components(Water, 1)
                .flags(DISABLE_DECOMPOSITION)
                .buildAndRegister();
        initialized = true;
    }
}
