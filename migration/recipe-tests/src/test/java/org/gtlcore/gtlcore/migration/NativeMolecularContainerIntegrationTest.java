package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MECraftPatternContainerPartMachine;
import org.gtlcore.gtlcore.integration.ae2.MolecularAssemblerPatternHelper;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import appeng.core.definitions.AEItems;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.ModularSyncManager;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.slot.ItemSlot;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NativeMolecularContainerIntegrationTest {
    private static MECraftPatternContainerPartMachine container() {
        var definition = NativeMolecularFixtures.PATTERNS;
        return (MECraftPatternContainerPartMachine) definition.getBlockEntityType()
                .create(new BlockPos(5, 2, 7), definition.getBlock().defaultBlockState());
    }

    @Test void nativeInventoryKeepsAll108SlotsComponentsAndClientSync() {
        var server = container(); var client = container(); var restored = container();
        assertEquals(new BlockPos(5, 2, 7), server.getBlockPos());
        assertEquals(108, server.getSlots());
        var pattern = AEItems.SMITHING_TABLE_PATTERN.stack();
        pattern.set(DataComponents.CUSTOM_NAME, Component.literal("last native molecular slot"));
        server.getPatternInventory().setStackInSlot(107, pattern);
        var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        restored.loadAdditional(server.saveWithoutMetadata(registries), registries);
        assertTrue(ItemStack.matches(pattern, restored.getPatternInventory().getStackInSlot(107)));
        assertEquals(108, restored.getSlots());
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            server.getSyncDataHolder().writeClientPacket(registries, packet);
            client.getSyncDataHolder().readClientPacket(registries, packet);
            assertTrue(ItemStack.matches(pattern, client.getPatternInventory().getStackInSlot(107)));
        } finally { packet.release(); }
        assertFalse(server.getPatternInventory().isItemValid(0, new ItemStack(Items.IRON_INGOT)));
        assertFalse(MolecularAssemblerPatternHelper.molecularFilter(AEItems.CRAFTING_PATTERN.stack(), null));
        assertTrue(server.getPatternInventory().isItemValid(0, AEItems.STONECUTTING_PATTERN.stack()));
    }

    @Test void nativeGuiBindsEveryRealSlotAndFormedPartsDeferToTheirIoMenu() {
        var part = container(); var root = new ParentWidget<>();
        part.buildMainUI(root, null, new PanelSyncManager(new ModularSyncManager(false), true), new UISettings());
        var grid = root.getChildren().getFirst();
        assertEquals(108, grid.getChildren().size());
        for (int i = 0; i < 108; i++) {
            var widget = (ItemSlot) grid.getChildren().get(i);
            assertSame(part.getPatternInventory(), widget.getSlot().getItemHandler());
            assertEquals(i, widget.getSlot().getSlotIndex());
            assertEquals(14, widget.getSlot().getSlotGroup().getRowSize());
            assertFalse(widget.getSyncHandler().isPhantom());
        }
        assertTrue(part.shouldOpenUI(null, null, null));
        var controller = (MultiblockControllerMachine) NativeMolecularFixtures.ASSEMBLER.getBlockEntityType()
                .create(BlockPos.ZERO, NativeMolecularFixtures.ASSEMBLER.getBlock().defaultBlockState());
        assertFalse(part.canShared(controller, "main"));
        part.addedToController(controller, "main");
        assertFalse(part.shouldOpenUI(null, null, null));
        part.removedFromController(controller);
        assertTrue(part.shouldOpenUI(null, null, null));
    }
}
