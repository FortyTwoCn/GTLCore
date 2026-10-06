package org.gtlcore.gtlcore.client;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.data.NativeMachineAssetRegistry;
import com.gregtechceu.gtceu.data.pack.GTDynamicPackContents;
import com.google.gson.JsonElement;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.resources.IoSupplier;
import org.jetbrains.annotations.Nullable;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Native generated machine assets must take precedence over GT's static states from before Core's changes. */
public final class NativeMachineAssetPack implements PackResources {
    private static final GTDynamicPackContents CONTENTS = new GTDynamicPackContents();
    private final PackLocationInfo info;
    public NativeMachineAssetPack(PackLocationInfo info) { this.info = info; }
    public static void clear() { CONTENTS.clearData(); }
    public static void add(ResourceLocation id, JsonElement json) {
        CONTENTS.addToData(id, json.toString().getBytes(StandardCharsets.UTF_8));
    }
    @Override public @Nullable IoSupplier<InputStream> getRootResource(String... path) {
        return path.length == 1 && path[0].equals("pack.png") ? () -> GTLCore.class.getResourceAsStream("/icon.png") : null;
    }
    @Override public @Nullable IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        return type == PackType.CLIENT_RESOURCES ? CONTENTS.getResource(id) : null;
    }
    @Override public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type == PackType.CLIENT_RESOURCES) CONTENTS.listResources(namespace, path, output);
    }
    @Override public Set<String> getNamespaces(PackType type) {
        return type == PackType.CLIENT_RESOURCES ? NativeMachineAssetRegistry.NAMESPACES : Set.of();
    }
    @Override @SuppressWarnings("unchecked") public <T> @Nullable T getMetadataSection(MetadataSectionSerializer<T> serializer) {
        return serializer == PackMetadataSection.TYPE ? (T) new PackMetadataSection(Component.literal("GTL native machine assets"),
                SharedConstants.getCurrentVersion().getPackVersion(PackType.CLIENT_RESOURCES)) : null;
    }
    @Override public PackLocationInfo location() { return info; }
    @Override public void close() {}
}
