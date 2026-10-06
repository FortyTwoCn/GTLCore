package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.patternrelay.PatternRelayPart;
import com.gregtechceu.gtceu.common.data.item.GTItemAbilities;
import appeng.api.util.AEColor;
import appeng.blockentity.networking.CableBusBlockEntity;
import appeng.core.definitions.*;
import appeng.util.SettingsFrom;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.UUID;

@GameTestHolder("gtlcore_recipe_test") @PrefixGameTestTemplate(false)
public final class NativePatternRelayGameTests {
    private static CableBusBlockEntity host(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, AEBlocks.CABLE_BUS.block()); var host = (CableBusBlockEntity) helper.getBlockEntity(pos);
        host.addPart(AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT), null, null);
        host.addPart(NativeThroughputFixtures.RELAY.get(), Direction.NORTH, null); return host;
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_pattern_relay", timeoutTicks = 240)
    public static void nativeRelayWrenchAndMemoryCardModesRestoreOnActualCableHostReload(GameTestHelper helper) {
        var firstHost = host(helper, new BlockPos(4, 12, 4)); var secondHost = host(helper, new BlockPos(6, 12, 4));
        helper.setBlock(new BlockPos(5, 12, 4), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var first = (PatternRelayPart) firstHost.getPart(Direction.NORTH); var second = (PatternRelayPart) secondHost.getPart(Direction.NORTH);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(first.isActive() && second.isActive(), "Native pattern relays lack actual powered AE channels")).thenExecute(() -> {
            var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "native-relay"));
            var wrench = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("gtceu:steel_wrench")));
            helper.assertTrue(!wrench.isEmpty() && wrench.canPerformAction(GTItemAbilities.WRENCH_ROTATE), "Actual native GT steel wrench is missing its registered tool component");
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench);
            helper.assertTrue(!first.isAccessMode() && first.onUseItemOn(wrench, player, InteractionHand.MAIN_HAND, Vec3.ZERO) && first.isAccessMode(),
                    "Native relay ordinary wrench interaction did not enable access mode");
            player.setShiftKeyDown(true);
            helper.assertTrue(first.onUseItemOn(wrench, player, InteractionHand.MAIN_HAND, Vec3.ZERO) && !first.isAccessMode(),
                    "Native relay alternate wrench interaction did not restore supplier mode");
            player.setShiftKeyDown(false); first.onUseItemOn(wrench, player, InteractionHand.MAIN_HAND, Vec3.ZERO);
            var builder = DataComponentMap.builder(); first.exportSettings(SettingsFrom.MEMORY_CARD, builder);
            second.importSettings(SettingsFrom.MEMORY_CARD, builder.build(), player);
            helper.assertTrue(second.isAccessMode(), "Native memory card component map did not apply the relay mode to another real part");
            var saved = secondHost.saveWithoutMetadata(helper.getLevel().registryAccess()); var state = secondHost.getBlockState(); var pos = secondHost.getBlockPos();
            helper.getLevel().removeBlockEntity(pos); helper.getLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()); helper.getLevel().setBlockAndUpdate(pos, state);
            var restoredHost = (CableBusBlockEntity) helper.getLevel().getBlockEntity(pos); restoredHost.loadWithComponents(saved, helper.getLevel().registryAccess()); restoredHost.onLoad();
            var restored = (PatternRelayPart) restoredHost.getPart(Direction.NORTH);
            helper.assertTrue(restored != null && restored.isAccessMode() && player.getMainHandItem().getCount() == 1,
                    "Native cable-host recreation lost the real relay mode or consumed the wrench"); helper.succeed();
        });
    }
}
