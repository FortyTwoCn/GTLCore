package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.trait.MEPart.IModifiableSyncOffset;
import com.gregtechceu.gtceu.common.data.machines.GTAEMachines;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import appeng.api.config.Actionable;
import appeng.api.networking.GridFlags;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.ArrayList;
import java.util.List;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeAeGridGameTests {
    private static NativeAeGridFixtures.Part build(GameTestHelper helper) {
        var pos = new BlockPos(4, 4, 4);
        helper.setBlock(pos, NativeAeGridFixtures.PART.getBlock().defaultBlockState());
        var part = (NativeAeGridFixtures.Part) helper.getBlockEntity(pos);
        part.setFrontFacing(Direction.NORTH);
        helper.setBlock(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.setBlock(pos.north(2), AEBlocks.DRIVE.block().defaultBlockState());
        var drive = (DriveBlockEntity) helper.getBlockEntity(pos.north(2));
        drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        return part;
    }

    private static void assertActive(GameTestHelper helper, NativeAeGridFixtures.Part part) {
        helper.assertTrue(part.isOnline() && part.getMainNode().isActive(), "Production ME IO part has no powered active AE node");
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeMePartConnectsStoresWakesAndHonorsAllFacingOnRotation(GameTestHelper helper) {
        var part = build(helper); var sleepingTicks = new AtomicInteger();
        helper.startSequence().thenWaitUntil(() -> assertActive(helper, part)).thenExecute(() -> {
            var node = part.getMainNode().getNode();
            helper.assertTrue(node.getOwner() == part && node.hasFlag(GridFlags.REQUIRE_CHANNEL) &&
                    node.meetsChannelRequirements() && node.getConnectedSides().contains(Direction.NORTH),
                    "AE node owner/channel/front-facing connection differs from production configuration");
            var stack = new ItemStack(Items.IRON_INGOT);
            stack.set(DataComponents.CUSTOM_NAME, Component.literal("real AE grid component key"));
            var key = AEItemKey.of(stack); var inventory = node.getGrid().getStorageService().getInventory();
            helper.assertTrue(inventory.insert(key, 37, Actionable.SIMULATE, part.source()) == 37 &&
                    inventory.extract(key, 37, Actionable.SIMULATE, part.source()) == 0 &&
                    inventory.insert(key, 37, Actionable.MODULATE, part.source()) == 37 &&
                    inventory.extract(AEItemKey.of(Items.IRON_INGOT), 37, Actionable.MODULATE, part.source()) == 0 &&
                    inventory.extract(key, 37, Actionable.MODULATE, part.source()) == 37,
                    "Real AE drive transactions lost components or simulation changed its inventory");
            part.sleep();
        }).thenIdle(5).thenExecute(() -> sleepingTicks.set(part.ticks)).thenIdle(5).thenExecute(() -> {
            helper.assertTrue(part.ticks == sleepingTicks.get(), "Sleeping production AE device continued ticking");
            part.getMETrait().notifySelfIO();
        }).thenWaitUntil(() -> helper.assertTrue(part.ticks > sleepingTicks.get(), "ME IO notification did not wake the actual AE tick service"))
                .thenExecute(() -> part.setFrontFacing(Direction.SOUTH))
                .thenWaitUntil(() -> helper.assertTrue(!part.isOnline(), "Rotating away from the cable retained an active connection"))
                .thenExecute(() -> {
                    part.getPersistentData().putBoolean("isAllFacing", true);
                    part.setFrontFacing(Direction.WEST);
                }).thenWaitUntil(() -> assertActive(helper, part)).thenExecute(() -> part.setFrontFacing(Direction.EAST))
                .thenIdle(5).thenExecute(() -> assertActive(helper, part)).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeMeNodeIsDestroyedAndRecreatedWithSavedOwnerAndIoTrait(GameTestHelper helper) {
        var part = build(helper); var restored = new AtomicReference<NativeAeGridFixtures.Part>();
        helper.startSequence().thenWaitUntil(() -> assertActive(helper, part)).thenExecute(() -> {
            part.getMainNode().setOwningPlayerId(17);
            part.checkpointTrait().checkpoint = 91;
            part.getPersistentData().putBoolean("isAllFacing", true);
            part.setFrontFacing(Direction.WEST);
            var registries = helper.getLevel().registryAccess(); var saved = part.saveWithoutMetadata(registries);
            var replacement = (NativeAeGridFixtures.Part) NativeAeGridFixtures.PART.getBlockEntityType()
                    .create(part.getBlockPos(), part.getBlockState());
            helper.getLevel().removeBlockEntity(part.getBlockPos());
            helper.assertTrue(part.getMainNode().getNode() == null && !part.isOnline(),
                    "Unloading the production ME part left its grid node or online state alive");
            replacement.loadAdditional(saved, registries); helper.getLevel().setBlockEntity(replacement); restored.set(replacement);
        }).thenWaitUntil(() -> assertActive(helper, restored.get())).thenExecute(() -> {
            var copy = restored.get();
            helper.assertTrue(copy.getMainNode().getNode().getOwningPlayerId() == 17 && copy.checkpointTrait().checkpoint == 91 &&
                    copy.getFrontFacing() == Direction.WEST && copy.getMainNode().getNode().getConnectedSides().contains(Direction.NORTH),
                    "Native ME node owner, dynamic IO trait or all-facing persisted state did not survive entity recreation");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeGtMeBusAndHatchKeepRefreshAndAllFacingAfterReload(GameTestHelper helper) {
        var definitions = List.of(GTAEMachines.ITEM_EXPORT_BUS_ME, GTAEMachines.FLUID_EXPORT_HATCH_ME);
        var hosts = new ArrayList<MetaMachine>();
        for (int i = 0; i < definitions.size(); i++) {
            var pos = new BlockPos(3 + i * 6, 4, 4);
            helper.setBlock(pos, definitions.get(i).getBlock().defaultBlockState());
            var host = (MetaMachine) helper.getBlockEntity(pos);
            host.setFrontFacing(Direction.NORTH); hosts.add(host);
            helper.setBlock(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        }
        helper.startSequence().thenWaitUntil(() -> {
            for (var host : hosts) helper.assertTrue(((IGridConnectedMachine) host).isOnline(), "Native GT ME bus/hatch did not join its AE network");
        }).thenExecute(() -> {
            for (var host : hosts) {
                host.getPersistentData().putBoolean("isAllFacing", true);
                host.setFrontFacing(Direction.WEST);
                ((IModifiableSyncOffset) host).setMEUpdateInterval(23);
            }
        }).thenIdle(5).thenExecute(() -> {
            var registries = helper.getLevel().registryAccess();
            for (int i = 0; i < hosts.size(); i++) {
                var host = hosts.get(i); var gridHost = (IGridConnectedMachine) host;
                helper.assertTrue(gridHost.isOnline() && gridHost.getMainNode().getNode().getConnectedSides().contains(Direction.NORTH),
                        "Native GT rotation discarded all-facing cable exposure");
                var copy = (MetaMachine) definitions.get(i).getBlockEntityType().create(host.getBlockPos(), host.getBlockState());
                var saved = host.saveWithoutMetadata(registries);
                helper.getLevel().removeBlockEntity(host.getBlockPos()); copy.loadAdditional(saved, registries);
                helper.getLevel().setBlockEntity(copy); hosts.set(i, copy);
            }
        }).thenWaitUntil(() -> {
            for (var host : hosts) helper.assertTrue(((IGridConnectedMachine) host).isOnline(), "Reloaded native GT ME part lost all-facing AE exposure");
        }).thenExecute(() -> {
            for (var host : hosts) {
                var gridHost = (IGridConnectedMachine) host;
                helper.assertTrue(((IModifiableSyncOffset) host).getMEUpdateInterval() == 23 &&
                        gridHost.shouldSyncME() == (host.getOffsetTimer() % 23 == 0) && host.getFrontFacing() == Direction.WEST,
                        "Native GT ME saved refresh interval or tick staggering changed after reload");
            }
        }).thenSucceed();
    }
}
