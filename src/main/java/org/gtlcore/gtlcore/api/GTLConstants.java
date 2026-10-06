package org.gtlcore.gtlcore.api;

import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Shared identities do not initialize the mod entry point or its server storage. */
public final class GTLConstants {
    public static final String MOD_ID = "gtlcore";
    public static final Logger LOGGER = LogManager.getLogger("org.gtlcore.gtlcore.GTLCore");
    private GTLConstants() {}
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(MOD_ID, path); }
}
