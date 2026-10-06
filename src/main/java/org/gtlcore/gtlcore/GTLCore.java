package org.gtlcore.gtlcore;

import org.gtlcore.gtlcore.client.ClientProxy;
import org.gtlcore.gtlcore.common.CommonProxy;
import org.gtlcore.gtlcore.utils.StorageManager;
import org.gtlcore.gtlcore.data.recipe.CraftingComponentAddition;
import com.gregtechceu.gtceu.data.recipe.event.CraftingComponentModificationEvent;

import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


@Mod(GTLCore.MOD_ID)
public class GTLCore {

    public static final String MOD_ID = org.gtlcore.gtlcore.api.GTLConstants.MOD_ID;
    public static final Logger LOGGER = org.gtlcore.gtlcore.api.GTLConstants.LOGGER;
    public static final String VERSION = "1.0.2";
    public static StorageManager STORAGE_INSTANCE = new StorageManager();

    public static ResourceLocation id(String name) {
        return org.gtlcore.gtlcore.api.GTLConstants.id(name);
    }

    public GTLCore(IEventBus modBus) {
        org.gtlcore.gtlcore.common.data.GTLDataComponents.register(modBus);
        if (FMLEnvironment.dist == Dist.CLIENT) {
            new ClientProxy(modBus);
        } else {
            new CommonProxy(modBus);
        }
        NeoForge.EVENT_BUS.addListener(GTLCore::serverStarted);
        NeoForge.EVENT_BUS.addListener(GTLCore::serverStopped);
        NeoForge.EVENT_BUS.addListener((CraftingComponentModificationEvent event) -> CraftingComponentAddition.init());
    }

    private static void serverStarted(ServerStartedEvent event) {
        STORAGE_INSTANCE = StorageManager.getInstance(event.getServer());
    }

    private static void serverStopped(ServerStoppedEvent event) {
        // A second integrated-server world must not keep the previous world's disk data.
        STORAGE_INSTANCE = new StorageManager();
    }
}
