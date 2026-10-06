package org.gtlcore.gtlcore.client;

import org.gtlcore.gtlcore.api.event.SourceTooltipRegistrationEvent;
import org.gtlcore.gtlcore.client.ae2.wireless.WirelessAeClient;
import org.gtlcore.gtlcore.client.renderer.machine.HarmonyAnimationRenderer;
import org.gtlcore.gtlcore.client.renderer.machine.GTLDynamicRenders;
import org.gtlcore.gtlcore.common.CommonProxy;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.bus.api.IEventBus;

@OnlyIn(Dist.CLIENT)
public class ClientProxy extends CommonProxy {

    private final IEventBus modBus;

    public ClientProxy(IEventBus modBus) {
        super(modBus);
        this.modBus = modBus;
        init(modBus);
    }

    public static void init(IEventBus modBus) {
        CraftingUnitModelProvider.initCraftingUnitModels(modBus);
        WirelessAeClient.register(modBus);
        HarmonyAnimationRenderer.register(modBus);
        GTLDynamicRenders.register(modBus);
    }

    @Override
    protected void clientSetup(final FMLClientSetupEvent event) {
        super.clientSetup(event);
        event.enqueueWork(() -> {
            // Fire event to allow mods to register their own source tooltips
            modBus.post(new SourceTooltipRegistrationEvent());
        });
    }
}
