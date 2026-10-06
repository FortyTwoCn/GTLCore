package org.gtlcore.gtlcore.mixin.ldlib;

import org.gtlcore.gtlcore.common.data.GTLCompassLinks;
import com.lowdragmc.lowdraglib.compass.CompassManager;
import com.lowdragmc.lowdraglib.compass.CompassNode;
import com.lowdragmc.lowdraglib.compass.CompassSection;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.LinkedHashMap;

/** Reapply addon item links after LDLib clears and reloads its resource-defined guide. */
@Mixin(value = CompassManager.class, remap = false)
public abstract class CompassManagerMixin {
    @Inject(method = "onResourceManagerReload", at = @At("RETURN"), require = 1)
    private void gtlcore$restoreMachineGuides(ResourceManager resources, CallbackInfo ci) {
        var manager = (CompassManager) (Object) this;
        var links = GTLCompassLinks.links().stream().sorted(java.util.Comparator.comparing(link -> link.node().toString())).toList();
        for (var link : links) {
            var item = BuiltInRegistries.ITEM.get(link.item());
            if (item == Items.AIR) continue;
            CompassSection section = manager.getSectionByName(link.section());
            if (section == null) {
                var config = new JsonObject();
                config.add("button_texture", gtlcore$itemTexture(link.item()));
                section = new CompassSection(link.section(), config);
                manager.sections.computeIfAbsent(link.section().getNamespace(), ignored -> new LinkedHashMap<>())
                        .put(link.section(), section);
            }
            CompassNode node = manager.getNodeByName(link.node());
            if (node == null) {
                var config = new JsonObject();
                config.addProperty("section", link.section().toString());
                config.addProperty("page", "gtlcore:machines");
                config.add("button_texture", gtlcore$itemTexture(link.item()));
                var position = new JsonArray();
                int index = section.nodes.size();
                position.add((index % 5) * 70); position.add((index / 5) * 70);
                config.add("position", position);
                node = new CompassNode(link.node(), config);
                node.setSection(section); section.addNode(node);
                manager.nodes.computeIfAbsent(link.node().getNamespace(), ignored -> new LinkedHashMap<>())
                        .put(link.node(), node);
            }
            manager.registerItemLookup(item, link.node());
        }
    }

    private static JsonObject gtlcore$itemTexture(ResourceLocation item) {
        var texture = new JsonObject(); texture.addProperty("type", "item"); texture.addProperty("res", item.toString());
        return texture;
    }
}
