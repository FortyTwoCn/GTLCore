package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;
import org.gtlcore.gtlcore.integration.ae2.gui.MolecularAssemblerUIFactory;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import brachy.modularui.screen.ModularContainerMenu;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.ItemSlotSyncHandler;
import brachy.modularui.value.sync.ModularSyncManager;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.WidgetTree;
import io.netty.buffer.Unpooled;

/** Native menus and vanilla clicks use the actual production IO and pattern-container inventories. */
@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeMolecularMenuGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeMolecularMenuOpeningPayloadAndSecondPageVanillaClicksAgree(GameTestHelper helper) {
        var controller = NativeMolecularGameTests.build(helper, true);
        var io = (MEMolecularAssemblerIOPartMachine) controller.getParts().stream()
                .filter(MEMolecularAssemblerIOPartMachine.class::isInstance).findFirst().orElseThrow();
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.getMinecraft(helper.getLevel());
        player.setPos(io.getBlockPos().getX() + .5, io.getBlockPos().getY() + .5, io.getBlockPos().getZ() + .5);
        var data = new MolecularAssemblerUIFactory.Data(player, io.getBlockPos(), 108, io.getPatternBindingRevision());
        var factory = MolecularAssemblerUIFactory.INSTANCE;
        var serverSync = new ModularSyncManager(false); var clientSync = new ModularSyncManager(true);
        var server = new PanelSyncManager(serverSync, true); var client = new PanelSyncManager(clientSync, true);
        var serverSettings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var clientSettings = new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var serverPanel = factory.createPanel(data, server, serverSettings);
        var clientPanel = factory.createPanel(data, client, clientSettings);
        WidgetTree.collectSyncValues(server, serverPanel); WidgetTree.collectSyncValues(client, clientPanel);
        var serverMenu = new ModularContainerMenu(37); var clientMenu = new ModularContainerMenu(37);
        serverMenu.construct(player, serverSync, serverSettings, serverPanel.getName(), data);
        clientMenu.construct(player, clientSync, clientSettings, clientPanel.getName(), data);
        helper.assertTrue(serverMenu.slots.size() == clientMenu.slots.size() && serverMenu.slots.size() == 144,
                "Native molecular menus disagree on their complete vanilla slot table");
        for (int i = 0; i < 108; i++) {
            var a = server.findSyncHandler("molecularPatternSlot", i, ItemSlotSyncHandler.class).getSlot();
            var b = client.findSyncHandler("molecularPatternSlot", i, ItemSlotSyncHandler.class).getSlot();
            helper.assertTrue(serverMenu.slots.indexOf(a) == clientMenu.slots.indexOf(b) && a.getSlotIndex() == i && b.getSlotIndex() == i && a.getItemHandler() != b.getItemHandler(),
                    "A native molecular client slot targets a different server inventory index: " + i);
        }
        var registries = helper.getLevel().registryAccess();
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            factory.writeGuiData(data, packet);
            helper.assertTrue(packet.readBlockPos().equals(io.getBlockPos()) && packet.readVarInt() == 108 && !packet.isReadable(),
                    "Molecular GUI opening payload omitted its authoritative slot count");
            packet.clear(); packet.writeBlockPos(io.getBlockPos()); packet.writeVarInt(1);
            var recalculated = (MolecularAssemblerUIFactory.Data) factory.readGuiData(player, packet);
            helper.assertTrue(recalculated.slotCount() == 108, "Client opening data overrode the production server slot count");
            packet.clear(); packet.writeVarInt(1);
            server.receiveWidgetUpdate("molecularPageIndex:0", false, 0, packet);
            packet.clear(); packet.writeVarInt(1);
            client.findSyncHandler("molecularPageIndex", IntSyncValue.class).readOnClient(0, packet);
            helper.assertTrue(server.findSyncHandler("molecularPageIndex", IntSyncValue.class).getIntValue() == 1,
                    "Second-page native C2S selection did not reach its server handler");
            var pattern = NativeMolecularGameTests.craftingPattern(helper);
            var last = server.findSyncHandler("molecularPatternSlot", 107, ItemSlotSyncHandler.class).getSlot();
            helper.assertTrue(last.isActive() && !server.findSyncHandler("molecularPatternSlot", 0, ItemSlotSyncHandler.class).getSlot().isActive(),
                    "Native molecular selection enabled the wrong page slots");
            serverMenu.setCarried(pattern.copy()); serverMenu.clicked(serverMenu.slots.indexOf(last), 0, ClickType.PICKUP, player);
            helper.assertTrue(serverMenu.getCarried().isEmpty() && ItemStack.matches(io.getTerminalPatternInventory().getStackInSlot(107), pattern) &&
                    io.getAvailablePatterns().size() == 1, "Vanilla slot click did not write and refresh the production last pattern slot");
            clientMenu.initializeContents(0, serverMenu.getItems(), serverMenu.getCarried());
            helper.assertTrue(ItemStack.matches(client.findSyncHandler("molecularPatternSlot", 107, ItemSlotSyncHandler.class).getSlot().getItem(), pattern),
                    "Native vanilla contents sync lost the second-page pattern components");
            packet.clear(); brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, "synced native molecular name", Short.MAX_VALUE - 74);
            server.receiveWidgetUpdate("molecularName:0", false, 0, packet);
            packet.clear(); packet.writeBoolean(true);
            server.receiveWidgetUpdate("molecularHidden:0", false, 0, packet);
            helper.assertTrue(!io.isVisibleInTerminal() && io.getTerminalGroup().name().getString().equals("synced native molecular name"),
                    "Native rename or terminal-visibility C2S values did not update the production machine");
            helper.assertTrue(factory.canInteractWith(player, data), "A valid molecular menu was rejected");
            var oldRevision = io.getPatternBindingRevision();
            io.init(java.util.Set.of());
            helper.assertTrue(io.getPatternBindingRevision() != oldRevision && !factory.canInteractWith(player, data),
                    "Changed pattern sources left an obsolete native molecular menu usable");
        } finally { packet.release(); }
        helper.succeed();
    }
}
