package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;

/** Production material definition shared by material registration and native worldgen. */
public final class GTLWorldgenMaterials {
    private GTLWorldgenMaterials() {}

    public static Material UnknowWater;

    public static void init() {
        if (UnknowWater != null) return;
        UnknowWater = new Material.Builder(GTCEu.id("unknowwater"))
                .fluid()
                .color(0x3322aa)
                .iconSet(MaterialIconSet.DULL)
                .buildAndRegister().setFormula("?");
    }
}
