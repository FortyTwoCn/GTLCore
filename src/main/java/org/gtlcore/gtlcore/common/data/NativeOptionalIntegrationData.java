package org.gtlcore.gtlcore.common.data;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import org.gtlcore.gtlcore.GTLCore;

/** Native conditions for optional dependency content, above unconditional upstream mod data. */
@EventBusSubscriber(modid = GTLCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class NativeOptionalIntegrationData {
    @SubscribeEvent
    public static void packs(AddPackFindersEvent event) {
        event.addPackFinders(GTLCore.id("resourcepacks/optional_integrations"), PackType.SERVER_DATA,
                Component.literal("GTL optional integrations"), PackSource.BUILT_IN, true, Pack.Position.TOP);
    }
}
