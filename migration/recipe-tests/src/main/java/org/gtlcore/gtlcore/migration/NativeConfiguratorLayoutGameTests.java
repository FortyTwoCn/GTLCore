package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanel;
import com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.*;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import brachy.modularui.widgets.layout.Flow;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeConfiguratorLayoutGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void actualPatternCacheFlagsSyncThroughNativeDataHolderWithoutLosingTheFollowingField(GameTestHelper helper) {
        var pos = new BlockPos(4, 3, 4); helper.setBlock(pos, NativeAePatternFixtures.LARGE_BUFFER.getBlock());
        var host = (org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine) helper.getBlockEntity(pos);
        var copy = (org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine)
                NativeAePatternFixtures.LARGE_BUFFER.getBlockEntityType().create(host.getBlockPos(), host.getBlockState());
        var fields = com.gregtechceu.gtceu.api.sync_system.ClassSyncData.getClassData(host.getClass()).getClientSyncFields();
        var flags = fields.get("cacheRecipe"); var circuit = fields.get("embeddedCircuitConfig");
        var source = (boolean[]) flags.handle.get(host); var target = (boolean[]) flags.handle.get(copy);
        source[0] = true; source[107] = true; target[54] = true;
        circuit.handle.set(host, 7);
        host.getSyncDataHolder().markClientSyncFieldDirty("cacheRecipe");
        host.getSyncDataHolder().markClientSyncFieldDirty("embeddedCircuitConfig");
        var packet = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            host.getSyncDataHolder().writeClientPacket(helper.getLevel().registryAccess(), packet);
            int start = packet.readerIndex(), fieldCount = packet.readVarInt(); packet.readerIndex(start);
            helper.assertTrue(fieldCount >= 2, "Primitive cache serialization dropped the whole native update");
            copy.getSyncDataHolder().readClientPacket(helper.getLevel().registryAccess(), packet);
            helper.assertTrue(!packet.isReadable() && flags.handle.get(copy) == target && java.util.Arrays.equals(source, target)
                            && (int) circuit.handle.get(copy) == 7 && !host.getSyncDataHolder().needsSync(),
                    "Native pattern cache packet lost a flag, final-array identity, another sync field or dirty-field completion");
        } finally { packet.release(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void actualPatternBufferRetainsItsNativeControlsCoverSettingsAndPopupHandlers(GameTestHelper helper) {
        var pos = new BlockPos(4, 3, 4); helper.setBlock(pos, NativeAePatternFixtures.BUFFER.getBlock());
        var host = (org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine) helper.getBlockEntity(pos);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(host.getBlockPos().getCenter());
        var settings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var sync = new ModularSyncManager(false); var manager = new PanelSyncManager(sync, true);
        var data = new PosGuiData(player, host.getBlockPos());
        var panel = (MachineUIPanel) MachineUIFactory.INSTANCE.createPanel(data, manager, settings);
        var columns = (Flow) panel.getLeftConfiguratorPanel().getChildren().getFirst();
        helper.assertTrue(columns.getName().equals("gtlcoreConfiguratorColumns") && columns.getChildren().size() == 3,
                "Production native pattern buffer did not form three configurator columns");
        var all = columns.getChildren().stream().map(Flow.class::cast).flatMap(column -> column.getChildren().stream()).toList();
        helper.assertTrue(all.size() == 10 && new java.util.HashSet<>(all).size() == 10
                        && columns.getChildren().stream().map(Flow.class::cast).allMatch(column -> column.getChildren().size() <= 4),
                "Native layout duplicated, omitted or overflowed an actual configuration control");
        WidgetTree.collectSyncValues(manager, panel);
        var menu = new ModularContainerMenu(93); menu.construct(player, sync, settings, panel.getName(), data); player.containerMenu = menu;
        try {
            for (var key : java.util.List.of("patternSharedItems", "patternSharedFluids", "patternSharedCircuit", "patternBufferProxyPositions", "patternCircuitConfig", "patternRename", "gtlcoreCoverSettings"))
                helper.assertTrue(manager.findPanelHandlerNullable(key) != null, "Native layout lost a production popup handler: " + key);
            helper.assertTrue(manager.findSyncHandler("patternHiddenTerminal", 0, BooleanSyncValue.class) != null
                            && manager.findSyncHandler("patternKeepByProducts", 0, BooleanSyncValue.class) != null,
                    "Native layout lost real value sync handlers");
        } finally { player.closeContainer(); }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void aCustomCoilAboveVanillaTiersSyncsItsTextureAndBuildsANativeMenu(GameTestHelper helper) {
        var pos = new BlockPos(3, 2, 3);
        helper.setBlock(pos.west(), NativeAdvancedFluidRigFixtures.COIL.get());
        helper.setBlock(pos.east(), NativeAdvancedFluidRigFixtures.COIL.get());
        helper.setBlock(pos, NativeMachineFixtures.COIL_MENU.getBlock());
        var host = (com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine) helper.getBlockEntity(pos);
        host.setFrontFacing(net.minecraft.core.Direction.NORTH);
        host.checkAndFormStructure();
        helper.assertTrue(host.isFormed() && host.getCoilTier() >= com.gregtechceu.gtceu.common.block.CoilBlock.CoilType.values().length,
                "Fixture did not form with a custom tier above the native enum range");
        var copy = (com.gregtechceu.gtceu.api.machine.multiblock.CoilWorkableElectricMultiblockMachine)
                NativeMachineFixtures.COIL_MENU.getBlockEntityType().create(host.getBlockPos(), host.getBlockState());
        copy.setLevel(helper.getLevel());
        var field = com.gregtechceu.gtceu.api.sync_system.ClassSyncData.getClassData(host.getClass()).getClientSyncFields().get("gtlcore$coilTexture");
        helper.assertTrue(field != null && field.transformer != null && field.handle.get(host).equals(host.getCoilType().getTexture()),
                "The actual custom texture or its native sync codec was not registered: field=" + field
                        + ", transformer=" + (field == null ? null : field.transformer)
                        + ", synchronized=" + (field == null ? null : field.handle.get(host))
                        + ", actual=" + host.getCoilType().getTexture());
        helper.assertTrue(!field.handle.get(copy).equals(host.getCoilType().getTexture()),
                "A default texture would hide an omitted texture in the native client packet");
        var packet = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            host.getSyncDataHolder().writeClientPacket(helper.getLevel().registryAccess(), packet);
            copy.getSyncDataHolder().readClientPacket(helper.getLevel().registryAccess(), packet);
            helper.assertTrue(!packet.isReadable() && copy.getCoilTier() == host.getCoilTier() && field.handle.get(copy).equals(host.getCoilType().getTexture()),
                    "The custom coil menu texture or native tier was lost in the client packet");
        } finally { packet.release(); }
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var manager = new PanelSyncManager(new ModularSyncManager(true), true);
        var main = Flow.column();
        copy.buildMainUI(main, new PosGuiData(player, host.getBlockPos()), manager, new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY));
        helper.assertTrue(!main.getChildren().isEmpty(), "The custom coil menu produced no native contents");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeCoverSlotsInstallSaveAndExtractOneCoverWithoutDuplicatingItems(GameTestHelper helper) {
        var pos = new BlockPos(4, 3, 4);
        var entry = com.gregtechceu.gtceu.common.data.GTMachines.ITEM_IMPORT_BUS[1];
        helper.setBlock(pos, entry.getBlock());
        var host = (com.gregtechceu.gtceu.api.machine.MetaMachine) helper.getBlockEntity(pos);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setPos(host.getBlockPos().getCenter());
        var inventory = new org.gtlcore.gtlcore.api.gui.NativeCoverInventory(host.getCoverContainer(), () -> player);
        int slot = net.minecraft.core.Direction.WEST.get3DDataValue();
        var covers = com.gregtechceu.gtceu.common.data.GTItems.COVER_SHUTTER.asStack(5);
        helper.assertTrue(inventory.insertItem(slot, covers, true).getCount() == 4 && inventory.getStackInSlot(slot).isEmpty(),
                "Cover insertion simulation mutated the actual machine");
        helper.assertTrue(inventory.insertItem(slot, covers, false).getCount() == 4 && covers.getCount() == 5 && inventory.getStackInSlot(slot).getCount() == 1,
                "Native GUI cover installation lost items or changed the supplied stack");
        var tag = host.saveCustomOnly(helper.getLevel().registryAccess());
        var copy = (com.gregtechceu.gtceu.api.machine.MetaMachine) entry.getBlockEntityType().create(host.getBlockPos(), host.getBlockState());
        copy.setLevel(helper.getLevel());
        copy.loadWithComponents(tag, helper.getLevel().registryAccess());
        helper.assertTrue(copy.getCoverContainer().getCoverAtSide(net.minecraft.core.Direction.WEST) != null,
                "The GUI-installed cover did not survive native block-entity serialization");
        helper.assertTrue(inventory.extractItem(slot, 64, true).getCount() == 1 && !inventory.getStackInSlot(slot).isEmpty(),
                "Cover extraction simulation removed the installed cover");
        helper.assertTrue(inventory.extractItem(slot, 64, false).getCount() == 1 && inventory.getStackInSlot(slot).isEmpty() && inventory.extractItem(slot, 1, false).isEmpty(),
                "A GUI-installed cover was lost or extracted more than once");
        helper.assertTrue(!inventory.isItemValid(slot, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND)),
                "The real cover slot accepted a non-cover item");
        helper.succeed();
    }
}
