package org.gtlcore.gtlcore.mixin.gtm.registry;

import com.gregtechceu.gtceu.data.recipe.CustomTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Arrays;

/** Core's level-ten vibranium tools and material blocks retain native drop-rule tiers. */
@Mixin(CustomTags.class)
public abstract class CustomTagsMixin {
    @Shadow(remap = false) @Final @Mutable public static TagKey<Block>[] TOOL_TIERS;
    @Shadow(remap = false) @Final @Mutable public static TagKey<Block>[] INCORRECT_TOOL_TIERS;
    @Inject(method = "<clinit>", at = @At("TAIL"), remap = false, require = 1)
    private static void gtlcore$extendNativeHarvestTiers(CallbackInfo ci) {
        TOOL_TIERS = gtlcore$extend(TOOL_TIERS, "needs_tool_level_");
        INCORRECT_TOOL_TIERS = gtlcore$extend(INCORRECT_TOOL_TIERS, "incorrect_for_tool_level_");
    }
    @Unique private static TagKey<Block>[] gtlcore$extend(TagKey<Block>[] nativeTags, String prefix) {
        var result = Arrays.copyOf(nativeTags, Math.max(nativeTags.length, 11));
        for (int tier = nativeTags.length; tier < result.length; tier++)
            result[tier] = TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("gtlcore", prefix + tier));
        return result;
    }
}
