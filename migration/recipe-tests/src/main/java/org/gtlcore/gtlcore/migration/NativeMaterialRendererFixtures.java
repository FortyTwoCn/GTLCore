package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.data.chemical.material.info.GTLMaterialIconSet;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.item.TagPrefixItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

/** Actual native material items and icon sets; these fixture IDs are not release registrations. */
public final class NativeMaterialRendererFixtures {
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gtlcore_recipe_test", path); }
    public static final Material ROTATING = new Material.Builder(id("native_rotating_material"))
            .ingot().color(0xffc050).iconSet(GTLMaterialIconSet.CUSTOM_TRANSCENDENT_MENTAL).buildAndRegister();
    public static final ItemEntry<TagPrefixItem> ROTATING_ITEM = NativeMachineFixtures.REGISTRATE
            .item("native_rotating_ingot", properties -> new TagPrefixItem(properties, TagPrefix.ingot, ROTATING)).register();
    public static final List<MaterialIconSet> SPECIAL = List.of("infinity", "cosmic", "cosmicneutronium", "eternity", "magmatter")
            .stream().map(name -> new MaterialIconSet(id(name), MaterialIconSet.METALLIC)).toList();
    public static void init() {}
}
