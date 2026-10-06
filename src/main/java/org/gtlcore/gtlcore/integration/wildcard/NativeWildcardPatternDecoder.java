package org.gtlcore.gtlcore.integration.wildcard;

import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlag;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;
import com.gregtechceu.gtceu.api.fluids.store.FluidStorageKey;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.*;

/**
 * Native adapter for the pinned wildcard GTL branch's in/out/filter component protocol.
 * Settings live in CUSTOM_DATA; stack components use AE2's registry-aware 1.21 codecs.
 * This does not provide the external mod's item editor or install its Forge implementation.
 */
public final class NativeWildcardPatternDecoder {
    private static final ResourceLocation ITEM_ID = ResourceLocation.fromNamespaceAndPath("wildcard_pattern", "wildcard_pattern");
    private static final List<PropertyKey<?>> PROPERTIES = List.of(PropertyKey.EMPTY, PropertyKey.BLAST,
            PropertyKey.ALLOY_BLAST, PropertyKey.DUST, PropertyKey.FLUID_PIPE, PropertyKey.FLUID, PropertyKey.GEM,
            PropertyKey.INGOT, PropertyKey.POLYMER, PropertyKey.ITEM_PIPE, PropertyKey.ORE, PropertyKey.TOOL,
            PropertyKey.ARMOR, PropertyKey.ROTOR, PropertyKey.WIRE, PropertyKey.WOOD, PropertyKey.HAZARD);
    private NativeWildcardPatternDecoder() {}

    public static boolean isWildcardPattern(ItemStack stack) {
        return !stack.isEmpty() && ITEM_ID.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public static List<IPatternDetails> expand(ItemStack stack, Level level) {
        if (!isWildcardPattern(stack) || level == null) return List.of();
        return expand(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(), level.registryAccess(),
                GTRegistries.MATERIALS.stream().toList(), encoded -> PatternDetailsHelper.decodePattern(encoded, level));
    }

    static List<IPatternDetails> expand(CompoundTag settings, HolderLookup.Provider registries,
                                      Collection<Material> materials, Function<ItemStack, IPatternDetails> decoder) {
        var inputs = io(settings, "in", registries);
        var outputs = io(settings, "out", registries);
        var filters = filters(settings);
        if (inputs.isEmpty() || outputs.isEmpty()) return List.of();
        var result = new ArrayList<IPatternDetails>();
        for (var material : materials) {
            if (filters.stream().anyMatch(filter -> !filter.test(material))) continue;
            var in = apply(inputs, material); var out = apply(outputs, material);
            if (in == null || out == null) continue;
            var pattern = decoder.apply(PatternDetailsHelper.encodeProcessingPattern(in, out));
            if (pattern != null) result.add(pattern);
        }
        return List.copyOf(result);
    }

    private static @Nullable List<GenericStack> apply(List<Function<Material, GenericStack>> components, Material material) {
        var result = new ArrayList<GenericStack>(components.size());
        for (var component : components) {
            var stack = component.apply(material);
            if (stack == null || stack.amount() <= 0) return null;
            result.add(stack);
        }
        return result;
    }

    private static List<Function<Material, GenericStack>> io(CompoundTag settings, String key, HolderLookup.Provider registries) {
        var result = new ArrayList<Function<Material, GenericStack>>();
        for (var value : settings.getList(key, Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value; var data = entry.getCompound("data");
            switch (entry.getString("type")) {
                case "simple" -> {
                    var stack = GenericStack.readTag(registries, data);
                    // The real editor persists an empty newly added ghost slot as an empty AE stack tag.
                    // Keep the unsatisfied component so the whole pattern stays unavailable until configured.
                    if (stack == null && data.isEmpty()) {
                        result.add(material -> null);
                        break;
                    }
                    if (stack == null || stack.amount() <= 0) throw new IllegalArgumentException("Invalid wildcard stack");
                    if (stack.what() instanceof AEItemKey item && item.getItem() instanceof BucketItem bucket) {
                        var fluid = AEFluidKey.of(bucket.content);
                        result.add(material -> new GenericStack(fluid, stack.amount()));
                    } else result.add(material -> stack);
                }
                case "tag_prefix" -> {
                    long amount = data.getLong("amount");
                    if (amount <= 0) throw new IllegalArgumentException("Invalid wildcard tag quantity");
                    var tag = data.getCompound("tag");
                    if (tag.contains("item", Tag.TAG_STRING)) {
                        var prefix = TagPrefix.get(tag.getString("item"));
                        result.add(material -> {
                            if (prefix == null) return null;
                            var item = ChemicalHelper.get(prefix, material);
                            return item.isEmpty() ? null : new GenericStack(AEItemKey.of(item), amount);
                        });
                    } else {
                        var id = ResourceLocation.tryParse(tag.getString("fluid"));
                        var storage = id == null ? null : FluidStorageKey.getByName(id);
                        result.add(material -> {
                            if (storage == null || !material.hasProperty(PropertyKey.FLUID)) return null;
                            var fluid = material.getFluid(storage);
                            return fluid == null ? null : new GenericStack(AEFluidKey.of(fluid), amount);
                        });
                    }
                }
                default -> throw new IllegalArgumentException("Unknown wildcard IO component: " + entry.getString("type"));
            }
        }
        return result;
    }

    private static List<Predicate<Material>> filters(CompoundTag settings) {
        var result = new ArrayList<Predicate<Material>>();
        for (var value : settings.getList("filter", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) value; var data = entry.getCompound("data");
            boolean whitelist = data.getBoolean("whitelist");
            switch (entry.getString("type")) {
                case "simple" -> {
                    var id = ResourceLocation.tryParse(data.getString("material"));
                    var selected = id == null ? null : GTRegistries.MATERIALS.get(id);
                    result.add(material -> whitelist == (material == selected));
                }
                case "flag" -> {
                    var flag = MaterialFlag.getByName(data.getString("flag"));
                    result.add(material -> flag == null || whitelist == material.hasFlag(flag));
                }
                case "property" -> {
                    var property = PROPERTIES.stream().filter(p -> p.toString().equalsIgnoreCase(data.getString("flag")))
                            .findFirst().orElse(PropertyKey.EMPTY);
                    result.add(material -> whitelist == material.hasProperty(property));
                }
                default -> throw new IllegalArgumentException("Unknown wildcard filter component: " + entry.getString("type"));
            }
        }
        return result;
    }
}
