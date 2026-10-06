package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.gui.MachineModeConfigurator;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.*;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import io.netty.buffer.Unpooled;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeRecipeModeGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeMachineModePanelValidatesServerSelectionsSynchronizesAndPreservesSavedModes(GameTestHelper helper) {
        var pos = new BlockPos(4, 3, 4); helper.setBlock(pos, NativeTerminalFixtures.RECIPE_MODES.getBlock());
        var host = (WorkableElectricMultiblockMachine) helper.getBlockEntity(pos);
        host.checkAndFormStructure(); host.setWorkingEnabled(false);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(host.getBlockPos().getCenter());
        var settings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var sync = new ModularSyncManager(false); var manager = new PanelSyncManager(sync, true);
        var data = new PosGuiData(player, host.getBlockPos());
        var panel = MachineUIFactory.INSTANCE.createPanel(data, manager, settings); WidgetTree.collectSyncValues(manager, panel);
        helper.assertTrue(WidgetTree.findFirstWithName(panel, "gtlcoreRecipeModeButton") != null,
                "Native machine panel bypassed the production mode factory Mixin");
        var menu = new ModularContainerMenu(92); menu.construct(player, sync, settings, panel.getName(), data); player.containerMenu = menu;
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            manager.findPanelHandlerNullable(MachineModeConfigurator.PANEL).openPanel();
            var modes = sync.getPanelSyncManager(MachineModeConfigurator.PANEL);
            var current = modes.findSyncHandler(MachineModeConfigurator.CURRENT, 0, IntSyncValue.class);
            helper.assertTrue(!current.isAllowC2S(), "Recipe mode display accepted unvalidated C2S writes");
            for (int index = 0; index < host.getRecipeTypes().length; index++) {
                packet.clear(); packet.writeVarInt(index); modes.receiveWidgetUpdate(MachineModeConfigurator.SELECT, true, 0, packet);
                helper.assertTrue(host.getActiveRecipeType() == index && host.getRecipeType() == host.getRecipeTypes()[index]
                                && current.getIntValue() == index,
                        "Native mode C2S changed the wrong type or failed to update its S2C value");
            }
            for (int invalid : new int[] {-1, 7, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                packet.clear(); packet.writeVarInt(invalid); modes.receiveWidgetUpdate(MachineModeConfigurator.SELECT, true, 0, packet);
                helper.assertTrue(host.getActiveRecipeType() == 6 && current.getIntValue() == 6, "Invalid native mode escaped the server bounds");
            }
            player.setPos(host.getBlockPos().getCenter().add(9, 0, 0));
            packet.clear(); packet.writeVarInt(1); modes.receiveWidgetUpdate(MachineModeConfigurator.SELECT, true, 0, packet);
            helper.assertTrue(host.getActiveRecipeType() == 6, "Remote native mode request ignored machine interaction range");
            player.setPos(host.getBlockPos().getCenter());
            var saved = host.saveWithoutMetadata(helper.getLevel().registryAccess()); host.setActiveRecipeType(0);
            host.loadAdditional(saved, helper.getLevel().registryAccess());
            helper.assertTrue(host.getActiveRecipeType() == 6, "Native SaveField reload lost the selected recipe mode");
            helper.getLevel().setBlockAndUpdate(host.getBlockPos(), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            packet.clear(); packet.writeVarInt(1); modes.receiveWidgetUpdate(MachineModeConfigurator.SELECT, true, 0, packet);
            helper.assertTrue(host.getActiveRecipeType() == 6, "Obsolete native menu changed a removed machine's mode");
        } finally { packet.release(); player.closeContainer(); }
        helper.succeed();
    }
}
