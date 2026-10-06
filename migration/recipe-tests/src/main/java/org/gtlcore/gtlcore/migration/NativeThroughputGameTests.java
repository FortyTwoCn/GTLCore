package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.throughput.METhroughputMonitorPart;
import org.gtlcore.gtlcore.integration.ae2.throughput.ThroughputMonitorStorageTracker;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.*;
import appeng.hooks.ticking.TickHandler;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test") @PrefixGameTestTemplate(false)
public final class NativeThroughputGameTests {
    private record Rig(CableBusBlockEntity host, METhroughputMonitorPart monitor, NativeAeGridFixtures.Part source) {}
    private static Rig build(GameTestHelper helper) {
        var pos = new BlockPos(4, 12, 4); helper.setBlock(pos, AEBlocks.CABLE_BUS.block());
        var host = (CableBusBlockEntity) helper.getBlockEntity(pos);
        host.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT), null, null);
        var monitor = host.addPart(NativeThroughputFixtures.MONITOR.get(), Direction.NORTH, null);
        helper.setBlock(pos.east(), AEBlocks.CREATIVE_ENERGY_CELL.block()); helper.setBlock(pos.west(), AEBlocks.DRIVE.block());
        ((DriveBlockEntity) helper.getBlockEntity(pos.west())).getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
        helper.setBlock(pos.south(), NativeAeGridFixtures.PART.getBlock());
        var source = (NativeAeGridFixtures.Part) helper.getBlockEntity(pos.south()); source.setFrontFacing(Direction.NORTH);
        return new Rig(host, monitor, source);
    }
    private static AEItemKey key() {
        var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal("native throughput component key")); return AEItemKey.of(stack);
    }
    private static void active(GameTestHelper helper, Rig rig) {
        helper.assertTrue(rig.monitor.isActive() && rig.source.isOnline() && rig.monitor.getMainNode().isActive(), "Real AE throughput monitor has no powered channel/native source node");
    }
    private static void configure(GameTestHelper helper, METhroughputMonitorPart monitor, AEItemKey key) {
        var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "throughput-selection"), net.minecraft.server.level.ClientInformation.createDefault());
        var stack = key.toStack(); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        helper.assertTrue(monitor.onUseItemOn(stack, player, InteractionHand.MAIN_HAND, Vec3.ZERO) && key.equals(monitor.getDisplayed()),
                "Native AE item interaction did not configure the actual monitor and its watchers");
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_throughput", timeoutTicks = 240)
    public static void nativeThroughputActualPoweredDriveTransactionsKeepComponentsSimulationsAndMachineSources(GameTestHelper helper) {
        var rig = build(helper); var locations = new ArrayList<ThroughputMonitorStorageTracker.SourceLocation>(); var deltas = new ArrayList<Long>();
        var listener = (ThroughputMonitorStorageTracker.AllListener) (key, delta, tick, source) -> { deltas.add(delta); locations.add(source); };
        helper.startSequence().thenWaitUntil(() -> active(helper, rig)).thenExecute(() -> {
            var monitor = rig.monitor; var key = key(); configure(helper, monitor, key); long tick = TickHandler.instance().getCurrentTick();
            var storage = monitor.getMainNode().getGrid().getStorageService().getInventory();
            ThroughputMonitorStorageTracker.registerAll(storage, listener);
            try {
                helper.assertTrue(storage.insert(key, 37, Actionable.SIMULATE, rig.source.source()) == 37 && deltas.isEmpty(), "Native AE throughput recorded or committed simulated insert");
                helper.assertTrue(storage.insert(key, 37, Actionable.MODULATE, rig.source.source()) == 37 && storage.extract(key, 7, Actionable.MODULATE, rig.source.source()) == 7,
                        "Native throughput grid did not commit real drive transactions");
                helper.assertTrue(storage.extract(AEItemKey.of(Items.IRON_INGOT), 30, Actionable.MODULATE, rig.source.source()) == 0 && deltas.equals(List.of(37L, -7L)),
                        "Native throughput lost component matching or double-counted real transactions");
                monitor.tickingRequest(monitor.getMainNode().getNode(), 20);
                double expected = 37.0 / Math.max(1, TickHandler.instance().getCurrentTick() - tick + 1) * 20;
                helper.assertTrue(monitor.getThroughputPerSecond() == expected && monitor.getThroughputText().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text && "+".equals(text.getArgs()[0]),
                        "Actual AE throughput sampling changed its original display period or signed value: expected " + expected + ", got " + monitor.getThroughputPerSecond());
                helper.assertTrue(locations.size() == 2 && locations.stream().allMatch(location -> location != null && location.pos().equals(rig.source.getBlockPos())
                        && location.side() == Direction.NORTH && location.dimension().equals(helper.getLevel().dimension().location())),
                        "Native GT machine source was swallowed by its generic BlockEntity base or lost its facing");
            } finally { ThroughputMonitorStorageTracker.unregisterAll(listener); }
            helper.succeed();
        });
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_throughput", timeoutTicks = 240)
    public static void nativeThroughputPartKeepsRegistryAwareComponentStreamVisualStateAndNativeWorldReload(GameTestHelper helper) {
        var rig = build(helper);
        helper.startSequence().thenWaitUntil(() -> active(helper, rig)).thenExecute(() -> {
            var monitor = rig.monitor; var key = key(); configure(helper, monitor, key); monitor.recordThroughput(40, TickHandler.instance().getCurrentTick());
            monitor.tickingRequest(monitor.getMainNode().getNode(), 20); var registries = helper.getLevel().registryAccess();
            var saved = new CompoundTag(); monitor.writeToNBT(saved, registries);
            var copy = NativeThroughputFixtures.MONITOR.get().createPart(); copy.readFromNBT(saved, registries);
            helper.assertTrue(key.equals(copy.getDisplayed()) && copy.getThroughput() == monitor.getThroughput(), "Native throughput NBT lost component key or last reported value");
            var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
            try {
                monitor.writeToStream(buffer); var clientCopy = NativeThroughputFixtures.MONITOR.get().createPart(); clientCopy.readFromStream(buffer);
                helper.assertTrue(key.equals(clientCopy.getDisplayed()) && clientCopy.getThroughput() == monitor.getThroughput() && buffer.readableBytes() == 0,
                        "Native throughput registry-aware stream lost component key, value or left unconsumed bytes");
            } finally { buffer.release(); }
            var visual = new CompoundTag(); monitor.writeVisualStateToNBT(visual); copy.readVisualStateFromNBT(visual);
            helper.assertTrue(copy.getThroughput() == monitor.getThroughput(), "Native throughput visual state lost its value");
            var pos = rig.host.getBlockPos(); var state = rig.host.getBlockState(); var hostData = rig.host.saveWithoutMetadata(registries); double savedValue = monitor.getThroughput();
            helper.getLevel().removeBlockEntity(pos); helper.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()); helper.getLevel().setBlockAndUpdate(pos, state);
            var host = (CableBusBlockEntity) helper.getLevel().getBlockEntity(pos); host.loadWithComponents(hostData, registries); host.onLoad();
            var restored = (METhroughputMonitorPart) host.getPart(Direction.NORTH);
            helper.assertTrue(restored != null && key.equals(restored.getDisplayed()) && restored.getThroughput() == savedValue && monitor.getThroughput() == 0,
                    "Native AE cable host recreation lost its real throughput part, component key or saved value"); helper.succeed();
        });
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_throughput", timeoutTicks = 240)
    public static void nativeThroughputConfiguratorUsesNativeItemInteractionAndPreservesRoutineOnReload(GameTestHelper helper) {
        var rig = build(helper);
        helper.startSequence().thenWaitUntil(() -> active(helper, rig)).thenExecute(() -> {
            var monitor = rig.monitor; configure(helper, monitor, key());
            var player = new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "throughput-test"), net.minecraft.server.level.ClientInformation.createDefault());
            var stack = NativeThroughputFixtures.CONFIGURATOR.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            var before = new CompoundTag(); monitor.writeToNBT(before, helper.getLevel().registryAccess());
            helper.assertTrue(monitor.onUseItemOn(stack, player, InteractionHand.MAIN_HAND, Vec3.ZERO), "Native configurator item interaction failed on a powered AE monitor");
            var after = new CompoundTag(); monitor.writeToNBT(after, helper.getLevel().registryAccess());
            helper.assertTrue(after.getInt("routine") != before.getInt("routine") && player.getMainHandItem().getCount() == 1,
                    "Native throughput configurator did not cycle its routine or consumed the tool");
            var copy = NativeThroughputFixtures.MONITOR.get().createPart(); copy.readFromNBT(after, helper.getLevel().registryAccess());
            var restored = new CompoundTag(); copy.writeToNBT(restored, helper.getLevel().registryAccess());
            helper.assertTrue(restored.getInt("routine") == after.getInt("routine") && key().equals(copy.getDisplayed()), "Native throughput saved routine failed to restore with component selection"); helper.succeed();
        });
    }
}
