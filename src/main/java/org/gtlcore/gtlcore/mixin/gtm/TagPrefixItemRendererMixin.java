package org.gtlcore.gtlcore.mixin.gtm;

import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType;
import com.gregtechceu.gtceu.client.model.runtimegen.TagPrefixItemModelGenerator;

import net.minecraft.world.item.Item;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(value = TagPrefixItemModelGenerator.class, remap = false)
public abstract class TagPrefixItemRendererMixin {

    @Unique
    private static final Set<String> SPECIAL_ICON_SETS = Set.of("infinity", "cosmic", "cosmicneutronium", "eternity", "magmatter");

    // The native runtime pack also overrides packaged item models. Keep the original
    // Avaritia-backed models for these icon sets instead of replacing them with delegates.
    @Inject(method = "add(Lnet/minecraft/world/item/Item;Lcom/gregtechceu/gtceu/api/data/chemical/material/info/MaterialIconType;Lcom/gregtechceu/gtceu/api/data/chemical/material/info/MaterialIconSet;)V", at = @At("HEAD"), cancellable = true)
    private static void create(Item item, MaterialIconType type, MaterialIconSet iconSet, CallbackInfo ci) {
        if (SPECIAL_ICON_SETS.contains(iconSet.name)) {
            ci.cancel();
        }
    }
}
