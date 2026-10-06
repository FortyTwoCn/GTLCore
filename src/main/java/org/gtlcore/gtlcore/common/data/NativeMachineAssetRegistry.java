package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.builder.MachineBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.provider.GTBlockstateProvider;
import com.tterrag.registrate.providers.DataGenContext;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.item.CreativeModeTab;
import com.tterrag.registrate.util.entry.RegistryEntry;
import java.util.*;

/** Keeps the finalized native model producers for Core's expanded and modified machine registrations. */
public final class NativeMachineAssetRegistry {
    public static final Set<String> NAMESPACES = Set.of("gtceu", "gtlcore", "gtladditions", "gtmthings");
    private static final Map<ResourceLocation, Asset> ASSETS = new LinkedHashMap<>();
    private static final Map<MachineBuilder<?, ?, ?>, RegistryEntry<CreativeModeTab, ? extends CreativeModeTab>> TABS = new IdentityHashMap<>();
    private NativeMachineAssetRegistry() {}

    public static void rememberTab(MachineBuilder<?, ?, ?> builder) {
        TABS.put(builder, builder.getOwner().creativeModeTab());
    }

    public static void capture(MachineBuilder<?, ?, ?> builder, MachineDefinition definition, MachineDefinition.Properties properties) {
        if (!NAMESPACES.contains(definition.getId().getNamespace())) {
            TABS.remove(builder);
            return;
        }
        ASSETS.put(definition.getId(), new Asset(definition, properties.model(), properties.blockModel()));
        // Machine items are created in a later registry event, after the owner's selected tab changed.
        if (TABS.containsKey(builder)) {
            var tab = TABS.remove(builder);
            if (properties.itemHolder() instanceof RegistryEntry<?, ?> item) builder.getOwner().setCreativeTab(item, tab);
        }
    }

    public static Collection<Asset> assets() { return Collections.unmodifiableCollection(ASSETS.values()); }

    public record Asset(MachineDefinition definition, MachineBuilder.ModelInitializer model,
                        NonNullBiConsumer<DataGenContext<Block, ? extends Block>, GTBlockstateProvider> blockModel) {}
}
