package org.gtlcore.gtlcore.client.ae2.wireless;

import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ModelEvent;

/** The six native blockstate variants rotate the original animated-front cube model. */
public final class WirelessNetworkCoreRenderer {

    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("gtlcore", "block/wireless_network_core");

    private WirelessNetworkCoreRenderer() {}

    public static void registerAdditionalModels(ModelEvent.RegisterAdditional event) {
        event.register(ModelResourceLocation.standalone(MODEL));
    }
}
