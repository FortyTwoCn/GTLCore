package org.gtlcore.gtlcore.common;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.data.*;
import org.gtlcore.gtlcore.common.data.machines.GeneratorMachine;
import org.gtlcore.gtlcore.config.ConfigHolder;
import org.gtlcore.gtlcore.integration.ae2.storage.FastInfinityCellHandler;
import org.gtlcore.gtlcore.integration.ae2.storage.InfinityCellHandler;
import org.gtlcore.gtlcore.integration.ae2.wireless.CuriosTerminalLocator;
import org.gtlcore.gtlcore.integration.ae2.wireless.GTLWirelessAeContent;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAeNetworkRuntime;
import org.gtlcore.gtlcore.integration.ae2.wireless.WirelessAePackets;
import org.gtlcore.gtlcore.integration.ftbu.AreaShape;
import org.gtlcore.gtlcore.network.GTLNetworkHandler;

import com.gregtechceu.gtceu.api.recipe.GTRecipeType;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;

import appeng.api.storage.StorageCells;
import appeng.core.AELog;

import static org.gtlcore.gtlcore.api.registries.GTLRegistration.REGISTRATE;

public class CommonProxy {

    public CommonProxy(IEventBus eventBus) {
        CommonProxy.init();
        REGISTRATE.registerEventListeners(eventBus);
        GTLStats.register(eventBus);
        GTLIngredientTypes.register(eventBus);
        GTLDimensionMarkers.init(eventBus);
        org.gtlcore.gtlcore.api.recipe.ingredient.NativeRecipeSearchIndex.register(eventBus);
        GTLWirelessAeContent.register(eventBus);
        WirelessAeNetworkRuntime.register(NeoForge.EVENT_BUS);
        eventBus.addListener(WirelessAePackets::register);
        GTLNetworkHandler.INSTANCE.init();
        org.gtlcore.gtlcore.integration.ae2.gui.MolecularAssemblerUIFactory.register();
        eventBus.addListener(GTLNetworkHandler.INSTANCE::onRegister);
        CuriosTerminalLocator.register();
        eventBus.addListener(this::commonSetup);
        eventBus.addListener(this::clientSetup);
        eventBus.addListener(org.gtlcore.gtlcore.data.GTLWorldgenData::gatherData);
        // GTCEu 8 prepares content during mod construction and queues native registry entries.
        var addon = new org.gtlcore.gtlcore.GTLGTAddon();
        addon.registerElements();
        addon.registerTagPrefixes();
        GTLMaterials.init();
        addon.registerSounds();
        GTLRecipeConditions.init();
        org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic.init();
        GTLRecipeTypes.init();
        addon.initializeAddon();
        addon.registerCovers();
        GTLRetainedGTBlocks.init();
        org.gtlcore.gtlcore.common.data.machines.GTLRetainedGTMachines.init();
        org.gtlcore.gtlcore.common.data.machines.GCyMMachines.init();
        GTLMachines.init();
    }

    public static void init() {
        GTLCreativeModeTabs.init();
        ConfigHolder.init();
        AreaShape.register();
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        StorageCells.addCellHandler(InfinityCellHandler.INSTANCE);
        StorageCells.addCellHandler(FastInfinityCellHandler.INSTANCE);
        event.enqueueWork(() -> {
            org.gtlcore.gtlcore.common.recipe.RecipeModify.init();
            postRegistrationInitialization();
        }).whenComplete((res, err) -> {
            if (err != null) {
                AELog.warn(err);
            }
        });
    }

    public void postRegistrationInitialization() {
        org.gtlcore.gtlcore.common.data.machines.ShapeInfosModify.init();
        GTLItems.InitUpgrades();
    }

    protected void clientSetup(final FMLClientSetupEvent event) {}

}
