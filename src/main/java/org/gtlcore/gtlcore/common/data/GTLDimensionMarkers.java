package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.api.data.DimensionMarker;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.common.data.GTDimensionMarkers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Native deferred dimension markers retain the original dimension IDs and display ordering. */
public final class GTLDimensionMarkers {
    private static final DeferredRegister<DimensionMarker> AD_ASTRA =
            DeferredRegister.create(GTRegistries.Keys.DIMENSION_MARKER, "ad_astra");
    private static final DeferredRegister<DimensionMarker> KUBEJS =
            DeferredRegister.create(GTRegistries.Keys.DIMENSION_MARKER, "kubejs");

    static {
        register(AD_ASTRA, "moon", 1, "moon_stone");
        register(AD_ASTRA, "mars", 2, "mars_stone");
        register(AD_ASTRA, "venus", 3, "venus_stone");
        register(AD_ASTRA, "mercury", 3, "mercury_stone");
        register(AD_ASTRA, "glacio", 7, "glacio_stone");
        register(KUBEJS, "ancient_world", 0, "reactor_core");
        register(KUBEJS, "titan", 6, "titanstone");
        register(KUBEJS, "pluto", 6, "plutostone");
        register(KUBEJS, "io", 5, "iostone");
        register(KUBEJS, "ganymede", 5, "ganymedestone");
        register(KUBEJS, "enceladus", 6, "enceladusstone");
        register(KUBEJS, "ceres", 4, "ceresstone");
        register(KUBEJS, "barnarda", 8, "barnarda_wood");
        register(KUBEJS, "create", 10, "dimension_creation_casing");
        register(KUBEJS, "void", 0, "minecraft:obsidian");
        register(KUBEJS, "flat", 0, "minecraft:crying_obsidian");
    }

    private GTLDimensionMarkers() {}

    private static void register(DeferredRegister<DimensionMarker> register, String dimension, int tier, String icon) {
        var iconId = icon.indexOf(':') >= 0 ? ResourceLocation.parse(icon) :
                ResourceLocation.fromNamespaceAndPath(register.getNamespace(), icon);
        register.register(dimension, () -> new DimensionMarker(tier, () -> BuiltInRegistries.ITEM.get(iconId),
                Component.translatable("dimension." + register.getNamespace() + "." + dimension)));
    }

    public static void init(IEventBus modBus) {
        AD_ASTRA.register(modBus); KUBEJS.register(modBus);
        modBus.addListener(GTLDimensionMarkers::commonSetup);
    }

    private static void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            GTDimensionMarkers.NETHER.value().setTier(3);
            GTDimensionMarkers.END.value().setTier(6);
        });
    }
}
