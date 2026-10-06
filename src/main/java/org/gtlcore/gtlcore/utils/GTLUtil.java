package org.gtlcore.gtlcore.utils;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.recipe.*;

import net.minecraft.nbt.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * @author EasterFG on 2025/3/21
 */
public class GTLUtil {

    public static ItemStack loadItemStack(CompoundTag compoundTag, HolderLookup.Provider registries) {
        return ItemStack.parseOptional(registries, compoundTag);
    }

    /**
     * 代码参考自gto
     * &#064;line <a href="https://github.com/GregTech-Odyssey/GTOCore">...</a>
     */

    public static Tag serializeNBT(GTRecipe recipe, HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", recipe.id.toString());
        tag.put("recipe", GTRecipeSerializer.CODEC.codec().encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), recipe).getOrThrow());
        return tag;
    }

    public static @Nullable GTRecipe deserializeNBT(Tag tag, HolderLookup.Provider registries) {
        if (tag instanceof CompoundTag ctag) {
            var id = ResourceLocation.tryParse(ctag.getString("id"));
            if (id == null || !ctag.contains("recipe")) return null;
            var recipe = GTRecipeSerializer.CODEC.codec().parse(registries.createSerializationContext(NbtOps.INSTANCE), ctag.get("recipe")).result().orElse(null);
            if (recipe == null || id == null) return null;
            recipe.id = id;
            return recipe;
        }
        return null;
    }
}
