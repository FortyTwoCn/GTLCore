package org.gtlcore.gtlcore.network;

import org.gtlcore.gtlcore.network.packet.CSetNoClip;
import org.gtlcore.gtlcore.network.packet.SSetNoClip;
import org.gtlcore.gtlcore.network.packet.SStructureDetectHighlight;

import com.glodblock.github.glodium.network.NetworkHandler;

public class GTLNetworkHandler extends NetworkHandler {

    public static final String MOD_ID = "gtlcore";

    public static final GTLNetworkHandler INSTANCE = new GTLNetworkHandler();

    public GTLNetworkHandler() {
        super(MOD_ID);
    }

    public void init() {
        registerPacket(CSetNoClip::new);
        registerPacket(SSetNoClip::new);
        registerPacket(SStructureDetectHighlight::new);
    }
}
