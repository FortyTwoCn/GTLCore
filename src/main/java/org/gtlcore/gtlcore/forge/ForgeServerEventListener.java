package org.gtlcore.gtlcore.forge;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.integration.ae2.async.AEWriteService;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = GTLCore.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.DEDICATED_SERVER)
public class ForgeServerEventListener {

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        AEWriteService.INSTANCE.shutDownGracefully();
    }
}
