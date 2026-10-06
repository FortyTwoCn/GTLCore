package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.tterrag.registrate.util.nullness.NonNullConsumer;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Registration data shared with LDLib's native client guide, without loading client classes on a server. */
public final class GTLCompassLinks {
    private static final Map<ResourceLocation, Link> LINKS = new ConcurrentHashMap<>();
    private GTLCompassLinks() {}

    public record Link(ResourceLocation item, ResourceLocation section, ResourceLocation node) {}

    public static NonNullConsumer<MachineDefinition> node(String section, String name) {
        return definition -> link(definition, section, name);
    }

    public static NonNullConsumer<MachineDefinition> self(String section) {
        return definition -> link(definition, section, definition.getId().getPath());
    }

    private static void link(MachineDefinition definition, String section, String name) {
        var sectionId = ResourceLocation.fromNamespaceAndPath("gtceu", section);
        var nodeId = ResourceLocation.fromNamespaceAndPath("gtceu", section + "/" + name);
        LINKS.put(definition.getId(), new Link(definition.getId(), sectionId, nodeId));
    }

    public static List<Link> links() { return List.copyOf(LINKS.values()); }
}
