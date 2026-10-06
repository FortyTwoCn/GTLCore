package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLIngredientTypes;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

/** Runs production quantity mixins with the actual pinned GTCEu mod. */
@Mod("gtlcore_recipe_test")
public class RecipeMigrationHarness {
    public RecipeMigrationHarness(IEventBus modBus) {
        org.gtlcore.gtlcore.config.ConfigHolder.init();
        org.gtlcore.gtlcore.api.recipe.chance.LongChanceLogic.init();
        org.gtlcore.gtlcore.common.data.GTLCallbackMaterials.init();
        org.gtlcore.gtlcore.common.data.GTLWorldgenMaterials.init();
        modBus.addListener(org.gtlcore.gtlcore.data.GTLWorldgenData::gatherData);
        org.gtlcore.gtlcore.common.data.GTLSoundEntries.init();
        org.gtlcore.gtlcore.common.data.GTLRecipeTypes.init();
        org.gtlcore.gtlcore.common.data.GTLCovers.init();
        com.gtladd.gtladditions.api.registry.GTLAddRegistration.REGISTRATE.registerEventListeners(modBus);
        com.gtladd.gtladditions.common.modify.GTLAddSoundEntries.INSTANCE.init();
        com.gtladd.gtladditions.common.recipe.GTLAddRecipesTypes.INSTANCE.init();
        org.gtlcore.gtlcore.common.data.machines.GTLGeneratorMachines.init();
        org.gtlcore.gtlcore.common.data.machines.GCyMMachines.init();
        org.gtlcore.gtlcore.common.data.GTLRecipeConditions.init();
        GTLIngredientTypes.register(modBus);
        org.gtlcore.gtlcore.common.data.GTLDataComponents.register(modBus);
        org.gtlcore.gtlcore.api.recipe.ingredient.NativeRecipeSearchIndex.register(modBus);
        NativeMolecularFixtures.init();
        org.gtlcore.gtlcore.integration.ae2.gui.MolecularAssemblerUIFactory.register();
        NativeAeGridFixtures.init();
        NativeMaintenanceFixtures.init();
        NativeComputationFixtures.init(modBus);
        NativeBedrockDrillingFixtures.init();
        NativeCellFixtures.init(modBus);
        NativeWirelessDataFixtures.init();
        NativeCloudFixtures.init();
        NativeAmbientGeneratorFixtures.init();
        NativeGreenhouseFixtures.init();
        NativeSlaughterhouseFixtures.init();
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(org.gtlcore.gtlcore.common.util.BlockStateWatcher::onLevelUnload);
        NativeAePatternFixtures.init(modBus);
        NativeMaterialRendererFixtures.init();
        NativeDynamicRenderFixtures.init();
        if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
            org.gtlcore.gtlcore.client.renderer.machine.NativeDynamicRenderClientFixtures.register(modBus);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(org.gtlcore.gtlcore.client.gui.NativePatternModifierClientEvents::onPatternModifierReplaceAmountMousePressed);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(org.gtlcore.gtlcore.client.gui.NativePatternModifierClientEvents::onPatternModifierReplaceSlotTooltip);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST, org.gtlcore.gtlcore.client.gui.NativePatternModifierClientEvents::onSetReplaceAmountScreenInit);
        }
        NativeTerminalFixtures.init();
        NativePerformanceFixtures.init();
        NativeHarmonyFixtures.init();
        NativeDysonFixtures.init(modBus);
        NativeWildcardFixtures.init(modBus);
        NativeGtOutputFixtures.init();
        NativeCatalystFixtures.init(modBus);
        NativeSuperPatternFixtures.init(modBus);
        NativeCoverTransferFixtures.init();
        NativeFluidRigFixtures.init();
        NativeAdvancedFluidRigFixtures.init();
        NativeRockBreakerFixtures.init();
        NativeFissionFixtures.init();
        NativeThroughputFixtures.init(modBus);
        org.gtlcore.gtlcore.integration.ftbu.AreaShape.register();
        NativeMachineFixtures.register(modBus);
        NativeQueuedMachineFixtures.init();
    }
}
