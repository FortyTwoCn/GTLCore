package org.gtlcore.gtlcore.client.ae2.wireless;

import org.gtlcore.gtlcore.integration.ae2.patternrelay.PatternRelayItem;
import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;

import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import appeng.api.util.AEColor;
import appeng.client.render.StaticItemColor;
import appeng.init.client.InitScreens;

public final class WirelessAeClient {

    private WirelessAeClient() {}

    public static void register(IEventBus modBus) {
        modBus.addListener(WirelessAeClient::onClientSetup);
        modBus.addListener(WirelessAeClient::onRegisterItemColors);
        modBus.addListener(WirelessAeClient::onRegisterMenuScreens);
        modBus.addListener(WirelessNetworkCoreRenderer::registerAdditionalModels);
        WirelessAeScreenHooks.register();
    }

    private static void onRegisterItemColors(RegisterColorHandlersEvent.Item event) {
        event.register(
                new StaticItemColor(AEColor.TRANSPARENT),
                GTLWirelessAeContent.THROUGHPUT_MONITOR_TERMINAL.get(),
                GTLWirelessAeContent.EMITTER_MANAGER_TERMINAL.get(),
                GTLWirelessAeContent.ME_CHAMBER_MANAGER_TERMINAL.get());
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(
                    GTLWirelessAeContent.PATTERN_RELAY.get(),
                    PatternRelayItem.ACCESS_MODEL_PROPERTY,
                    (stack, level, entity, seed) -> PatternRelayItem.getAccessModelProperty(stack));
            ItemBlockRenderTypes.setRenderLayer(GTLWirelessAeContent.WIRELESS_NETWORK_BOOKMARK.get(), RenderType.cutout());
        });
    }

    private static void onRegisterMenuScreens(RegisterMenuScreensEvent event) {
        event.register(GTLWirelessAeContent.WIRELESS_NETWORK_CORE_MENU.get(), WirelessNetworkCoreScreen::new);
        event.register(GTLWirelessAeContent.WIRELESS_NETWORK_BOOKMARK_MENU.get(), WirelessNetworkBookmarkScreen::new);
        event.register(GTLWirelessAeContent.WIRELESS_AE_TARGET_MENU.get(), WirelessAeTargetScreen::new);
        event.register(GTLWirelessAeContent.PATTERN_QUICK_UPLOAD_SELECTION_MENU.get(), PatternQuickUploadSelectionScreen::new);
        InitScreens.register(
                event,
                GTLWirelessAeContent.TAG_VIEW_CELL_MENU.get(),
                TagViewCellScreen::new,
                "/screens/tag_view_cell.json");
        InitScreens.register(
                event,
                GTLWirelessAeContent.THROUGHPUT_MONITOR_TERMINAL_MENU.get(),
                ThroughputMonitorTerminalScreen::new,
                "/screens/throughput_monitor_terminal.json");
        InitScreens.register(
                event,
                GTLWirelessAeContent.WIRELESS_THROUGHPUT_MONITOR_TERMINAL_MENU.get(),
                ThroughputMonitorTerminalScreen::new,
                "/screens/throughput_monitor_terminal.json");
        InitScreens.register(
                event,
                GTLWirelessAeContent.EMITTER_MANAGER_TERMINAL_MENU.get(),
                EmitterManagerTerminalScreen::new,
                "/screens/emitter_manager_terminal.json");
        InitScreens.register(
                event,
                GTLWirelessAeContent.WIRELESS_EMITTER_MANAGER_TERMINAL_MENU.get(),
                EmitterManagerTerminalScreen::new,
                "/screens/emitter_manager_terminal.json");
        InitScreens.register(
                event,
                GTLWirelessAeContent.ME_CHAMBER_MANAGER_TERMINAL_MENU.get(),
                MEChamberManagerTerminalScreen::new,
                "/screens/me_chamber_manager_terminal.json");
        InitScreens.register(
                event,
                GTLWirelessAeContent.WIRELESS_ME_CHAMBER_MANAGER_TERMINAL_MENU.get(),
                MEChamberManagerTerminalScreen::new,
                "/screens/me_chamber_manager_terminal.json");
    }
}
