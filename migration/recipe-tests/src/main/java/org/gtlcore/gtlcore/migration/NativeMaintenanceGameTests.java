package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.GTLCleanroomType;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.*;
import org.gtlcore.gtlcore.common.recipe.condition.GravityCondition;
import org.gtlcore.gtlcore.mixin.gtm.api.machine.trait.CleanroomReceiverTraitAccessor;
import com.gregtechceu.gtceu.api.machine.multiblock.CleanroomType;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.machine.multiblock.part.MaintenanceHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomProviderTrait;
import com.gregtechceu.gtceu.common.machine.trait.CleanroomReceiverTrait;
import com.gregtechceu.gtceu.common.recipe.condition.CleanroomCondition;
import com.gregtechceu.gtceu.config.ConfigHolder;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.*;
import brachy.modularui.utils.MouseData;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.LinkedHashMap;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeMaintenanceGameTests {
    private static NoEnergyMultiblockMachine build(GameTestHelper helper) {
        var entry = NativeMaintenanceFixtures.CONTROLLER;
        var pattern = (BlockPattern) entry.get().getStructurePatterns().get("main").get();
        var repeats = new Int2IntOpenHashMap();
        for (int i = 0; i < pattern.getSlices().length; i++) repeats.put(i, pattern.getSlices()[i].getMinRepeats());
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(repeats).populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        var origin = new BlockPos(4, 4, 4); BlockPos controller = null;
        for (var block : blocks.entrySet()) {
            var pos = origin.offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        helper.assertTrue(controller != null, "Native maintenance preview lost its controller");
        var machine = (NoEnergyMultiblockMachine) helper.getBlockEntity(controller);
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Native maintenance structure failed to form"); return machine;
    }
    private static GravityCleaningConfigurationMaintenancePartMachine part(NoEnergyMultiblockMachine controller) {
        return controller.getParts().stream().filter(GravityCleaningConfigurationMaintenancePartMachine.class::isInstance)
                .map(GravityCleaningConfigurationMaintenancePartMachine.class::cast).findFirst().orElseThrow();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeControllerDiscoversAutomaticMaintenanceAndChecksCleanroomAndGravity(GameTestHelper helper) {
        var controller = build(helper); var maintenance = part(controller); var logic = controller.getRecipeLogic();
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_maintenance_controller").duration(103).build();
        boolean oldMaintenance = ConfigHolder.INSTANCE.machines.enableMaintenance;
        boolean oldCleanroom = ConfigHolder.INSTANCE.machines.enableCleanroom, oldCleanMultiblocks = ConfigHolder.INSTANCE.machines.cleanMultiblocks;
        try {
            ConfigHolder.INSTANCE.machines.enableMaintenance = true; ConfigHolder.INSTANCE.machines.enableCleanroom = true; ConfigHolder.INSTANCE.machines.cleanMultiblocks = false;
            helper.assertTrue(controller.getParts().stream().filter(MaintenanceHatchPartMachine.class::isInstance).count() == 1 && maintenance.isFullAuto() && !maintenance.hasMaintenanceProblems(), "Native controller could not discover Core automatic maintenance");
            maintenance.setDurationMultiplier(0.2f);
            helper.assertTrue(controller.doModifyRecipe(recipe).duration == 20 && recipe.duration == 103, "Controller did not apply Core maintenance without mutating its source recipe");
            for (var type : ICleaningRoom.LAW_DUMMY_CLEANROOM) helper.assertTrue(new CleanroomCondition(false, type).check(recipe, logic), "Native receiver rejected a lower cleanroom type");
            var provider = maintenance.getTrait(CleanroomProviderTrait.class); provider.setActive(false);
            helper.assertTrue(!new CleanroomCondition(false, GTLCleanroomType.LAW_CLEANROOM).check(recipe, logic) && new CleanroomCondition(true, GTLCleanroomType.LAW_CLEANROOM).check(recipe, logic), "Native cleanroom activity or reversed condition was ignored"); provider.setActive(true);
            maintenance.setCurrentGravity(0); helper.assertTrue(new GravityCondition(true).check(recipe, logic) && !new GravityCondition(false).check(recipe, logic), "Native zero gravity part did not affect recipe conditions");
            maintenance.setCurrentGravity(100); helper.assertTrue(new GravityCondition(false).check(recipe, logic) && !new GravityCondition(true).check(recipe, logic) && new GravityCondition(true, true).check(recipe, logic), "Native normal or reversed gravity condition failed");
        } finally { ConfigHolder.INSTANCE.machines.enableMaintenance = oldMaintenance; ConfigHolder.INSTANCE.machines.enableCleanroom = oldCleanroom; ConfigHolder.INSTANCE.machines.cleanMultiblocks = oldCleanMultiblocks; }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeCleaningProviderUnbindsItsOwnerAndPreservesReplacementProviders(GameTestHelper helper) {
        var controller = build(helper); var maintenance = part(controller); var receiver = controller.getTrait(CleanroomReceiverTrait.class);
        var accessor = (CleanroomReceiverTraitAccessor) receiver; var owned = maintenance.getTrait(CleanroomProviderTrait.class);
        helper.assertTrue(accessor.gtlcore$getCleanroomProvider() == owned, "Controller did not receive its maintenance provider");
        controller.invalidateStructure(); helper.assertTrue(accessor.gtlcore$getCleanroomProvider() == null && controller.getParts().isEmpty(), "Invalidating structure left an attached maintenance cleanroom");
        controller.checkAndFormStructure(); helper.assertTrue(controller.isFormed() && accessor.gtlcore$getCleanroomProvider() == owned, "Reforming did not restore the owned native provider");
        var otherPos = new BlockPos(9, 4, 4); helper.setBlock(otherPos, NativeMaintenanceFixtures.CLEAN.getBlock());
        var other = (CleaningConfigurationMaintenanceHatchPartMachine) helper.getBlockEntity(otherPos); var replacement = other.getTrait(CleanroomProviderTrait.class);
        receiver.setCleanroomProvider(replacement); controller.invalidateStructure();
        helper.assertTrue(accessor.gtlcore$getCleanroomProvider() == replacement && receiver.hasActiveCleanroom(CleanroomType.STERILE_CLEANROOM), "Removing one maintenance part erased another native cleanroom provider");
        helper.getLevel().setBlockAndUpdate(maintenance.getBlockPos(), Blocks.AIR.defaultBlockState()); controller.checkAndFormStructure();
        helper.assertTrue(!controller.isFormed(), "Controller formed with its required maintenance part missing");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeMaintenanceMenuAppliesModifiedClicksClampsGravityAndReloadsSavedFields(GameTestHelper helper) {
        var pos = new BlockPos(4, 3, 4); helper.setBlock(pos, NativeMaintenanceFixtures.GRAVITY.getBlock());
        var host = (GravityCleaningConfigurationMaintenancePartMachine) helper.getBlockEntity(pos);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(host.getBlockPos().getCenter());
        var settings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY); var sync = new ModularSyncManager(false); var manager = new PanelSyncManager(sync, true);
        var data = new PosGuiData(player, host.getBlockPos()); var panel = MachineUIFactory.INSTANCE.createPanel(data, manager, settings); WidgetTree.collectSyncValues(manager, panel);
        var menu = new ModularContainerMenu(84); menu.construct(player, sync, settings, panel.getName(), data); player.containerMenu = menu;
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            new MouseData(Dist.DEDICATED_SERVER, 0, true, false, false).writeToPacket(packet); manager.receiveWidgetUpdate("maintenanceDecrease:0", false, 1, packet);
            helper.assertTrue(Math.abs(host.getDurationMultiplier() - 0.9f) < 0.00001f, "Shift maintenance click lost its 0.1 step");
            packet.clear(); new MouseData(Dist.DEDICATED_SERVER, 1, false, true, false).writeToPacket(packet); manager.receiveWidgetUpdate("maintenanceDecrease:0", false, 1, packet);
            helper.assertTrue(Math.abs(host.getDurationMultiplier() - 0.9f) < 0.00001f, "Right click changed maintenance duration");
            packet.clear(); new MouseData(Dist.DEDICATED_SERVER, 0, false, true, false).writeToPacket(packet); manager.receiveWidgetUpdate("maintenanceDecrease:0", false, 1, packet);
            helper.assertTrue(host.getDurationMultiplier() == 0.2f, "Ctrl maintenance decrease bypassed its lower bound");
            packet.clear(); new MouseData(Dist.DEDICATED_SERVER, 0, false, true, false).writeToPacket(packet); manager.receiveWidgetUpdate("maintenanceIncrease:0", false, 1, packet);
            helper.assertTrue(host.getDurationMultiplier() == 1.2f, "Ctrl maintenance increase bypassed its upper bound");
            packet.clear(); packet.writeVarInt(10000); manager.receiveWidgetUpdate("maintenanceGravity:0", false, 0, packet); helper.assertTrue(host.getCurrentGravity() == 100, "Gravity server setter trusted an out-of-range C2S value");
            packet.clear(); packet.writeVarInt(-1); manager.receiveWidgetUpdate("maintenanceGravity:0", false, 0, packet); helper.assertTrue(host.getCurrentGravity() == 0, "Gravity server setter accepted a negative value");
            packet.clear(); packet.writeVarInt(73); manager.receiveWidgetUpdate("maintenanceGravity:0", false, 0, packet); host.setDurationMultiplier(0.37f);
            var registries = helper.getLevel().registryAccess(); var saved = host.saveWithoutMetadata(registries);
            var copy = (GravityCleaningConfigurationMaintenancePartMachine) NativeMaintenanceFixtures.GRAVITY.getBlockEntityType().create(host.getBlockPos(), host.getBlockState());
            helper.getLevel().removeBlockEntity(host.getBlockPos()); copy.loadAdditional(saved, registries); helper.getLevel().setBlockEntity(copy);
            helper.assertTrue(copy.getCurrentGravity() == 73 && copy.getDurationMultiplier() == 0.37f && copy.getTrait(CleanroomProviderTrait.class).isActive(), "Native maintenance SaveField reload lost gravity, duration or provider");
        } finally { packet.release(); player.closeContainer(); }
        helper.succeed();
    }
}
