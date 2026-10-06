package org.gtlcore.gtlcore.client;

import org.gtlcore.gtlcore.GTLCore;
import org.gtlcore.gtlcore.common.data.NativeMachineAssetRegistry;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.common.data.models.GTMachineModels;
import com.gregtechceu.gtceu.common.block.MaterialFluidBlock;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import com.gregtechceu.gtceu.data.pack.event.RegisterDynamicResourcesEvent;
import com.gregtechceu.gtceu.data.pack.GTPackSource;
import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.registry.GTRegistration;
import com.gregtechceu.gtceu.api.registry.registrate.provider.GTBlockstateProvider;
import com.gregtechceu.gtceu.data.pack.GTDynamicResourcePack;
import com.gregtechceu.gtceu.utils.data.RuntimeExistingFileHelper;
import com.tterrag.registrate.providers.DataGenContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.client.model.generators.ModelBuilder;
import net.neoforged.neoforge.client.model.generators.ModelProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Uses the reference GT model API on each reload, including registrations absent from its static assets. */
@EventBusSubscriber(modid = GTLCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class NativeMachineAssets {
    @SubscribeEvent
    public static void packs(AddPackFindersEvent event) {
        if (event.getPackType() == PackType.CLIENT_RESOURCES) event.addRepositorySource(new GTPackSource(
                "gtlcore:native_machine_assets", PackType.CLIENT_RESOURCES, Pack.Position.TOP, NativeMachineAssetPack::new));
    }
    @SubscribeEvent
    public static void generate(RegisterDynamicResourcesEvent event) {
        NativeMachineAssetPack.clear();
        var provider = new MachineProvider();
        int count = 0;
        for (var asset : NativeMachineAssetRegistry.assets()) {
            var definition = asset.definition();
            var context = new DataGenContext<Block, MetaMachineBlock>(definition::getBlock, definition.getName(), definition.getId());
            if (asset.blockModel() != null) asset.blockModel().accept(context, provider);
            else if (asset.model() != null) GTMachineModels.createMachineModel(asset.model()).accept(context, provider);
            else throw new IllegalStateException("Native machine has no model producer: " + definition.getId());
            provider.itemModels().withExistingParent(definition.getId().toString(), definition.getId().withPrefix("block/machine/"));
            count++;
        }
        int fluids = 0;
        for (var block : BuiltInRegistries.BLOCK) {
            var id = BuiltInRegistries.BLOCK.getKey(block);
            if (!(block instanceof MaterialFluidBlock) || !NativeMachineAssetRegistry.NAMESPACES.contains(id.getNamespace())) continue;
            // LiquidBlock renders through native fluid extensions. Its empty baked model supplies the
            // real fluid particle texture and covers every LEVEL state without a visible solid cube.
            var still = IClientFluidTypeExtensions.of(block.defaultBlockState().getFluidState()).getStillTexture();
            provider.simpleBlock(block, provider.models().getBuilder(id.withPrefix("block/").toString()).texture("particle", still));
            fluids++;
        }
        int resources = provider.write();
        GTLCore.LOGGER.info("Generated native machine assets: machines={} fluid_blocks={} resources={}", count, fluids, resources);
    }

    private static final class MachineProvider extends GTBlockstateProvider {
        MachineProvider() {
            super(GTRegistration.REGISTRATE, new PackOutput(GTCEu.GTCEU_FOLDER), RuntimeExistingFileHelper.INSTANCE);
        }
        @Override
        protected void registerStatesAndModels() {}

        int write() {
            int count = writeModels(models()) + writeModels(itemModels());
            for (var entry : registeredBlocks.entrySet()) {
                writeAsset(GTDynamicResourcePack.BLOCKSTATE_ID_CONVERTER.idToFile(BuiltInRegistries.BLOCK.getKey(entry.getKey())),
                        entry.getValue().toJson());
                count++;
            }
            return count;
        }

        private <T extends ModelBuilder<T>> int writeModels(ModelProvider<T> models) {
            for (T model : models.generatedModels.values()) {
                writeAsset(model.getLocation().withPrefix("models/"), model.toJson());
            }
            return models.generatedModels.size();
        }
    }

    private static void writeAsset(net.minecraft.resources.ResourceLocation id, com.google.gson.JsonElement json) {
        NativeMachineAssetPack.add(id.getPath().endsWith(".json") ? id : id.withSuffix(".json"), json);
    }
}
