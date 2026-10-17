package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.config.ConfigHolder;
import dev.ftb.mods.ftbultimine.FTBUltiminePlayerData;
import dev.ftb.mods.ftbultimine.shape.*;
import dev.ftb.mods.ftbultimine.api.shape.Shape;
import dev.ftb.mods.ftbultimine.api.shape.ShapeContext;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test") @PrefixGameTestTemplate(false)
public final class NativeUltimineGameTests {
    private static net.minecraft.server.level.ServerPlayer player(GameTestHelper helper) {
        var player = net.neoforged.neoforge.common.util.FakePlayerFactory.get(helper.getLevel(),
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "native-ultimine"));
        player.giveExperiencePoints(10_000);
        return player;
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_ultimine")
    public static void registeredProductionAreaUsesConfiguredRangeAndNativeShapeContext(GameTestHelper helper) {
        int previousRange = ConfigHolder.INSTANCE.ftbUltimineRange;
        try {
            Shape area = null;
            for (int i = 0; i < ShapeRegistry.INSTANCE.shapeCount(); i++) {
                if (ShapeRegistry.INSTANCE.getShape(i).getName().equals(net.minecraft.resources.ResourceLocation.parse("gtlcore:area"))) area = ShapeRegistry.INSTANCE.getShape(i);
            }
            helper.assertTrue(area instanceof org.gtlcore.gtlcore.integration.ftbu.AreaShape,
                    "Production area shape was not registered in the native Ultimine registry");
            var center = new BlockPos(4, 12, 4); var other = center.offset(2, 0, 0);
            helper.setBlock(center, Blocks.STONE); helper.setBlock(other, Blocks.STONE);
            helper.setBlock(center.offset(0, 0, 2), Blocks.DIRT);
            var absolute = helper.absolutePos(center); var far = helper.absolutePos(other);
            var context = new ShapeContext(player(helper), absolute, Direction.UP,
                    Blocks.STONE.defaultBlockState(), BlockMatchers.MATCH_BY_BLOCK, 8);
            ConfigHolder.INSTANCE.ftbUltimineRange = 1;
            helper.assertTrue(area.getBlocks(context).equals(List.of(absolute)),
                    "Area traversal crossed a gap larger than the configured native range");
            ConfigHolder.INSTANCE.ftbUltimineRange = 2;
            var expanded = area.getBlocks(context);
            helper.assertTrue(expanded.equals(List.of(absolute, far)),
                    "Changing the range did not refresh native area traversal, distance ordering or block matching");
            var limited = new ShapeContext(context.player(), absolute, Direction.UP,
                    context.origState(), context.matcher(), 1);
            helper.assertTrue(area.getBlocks(limited).equals(List.of(absolute)),
                    "Production area ignored the native maximum-block limit");
            helper.succeed();
        } finally {
            ConfigHolder.INSTANCE.ftbUltimineRange = previousRange;
        }
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_ultimine")
    public static void strictNativeUltimineMixinProtectsBlacklistAndRetainsAllowedBlockSelection(GameTestHelper helper) {
        String[] previousBlacklist = ConfigHolder.INSTANCE.blackBlockList;
        try {
            var relative = new BlockPos(4, 12, 4); var absolute = helper.absolutePos(relative);
            var player = player(helper); var data = new FTBUltiminePlayerData(player.getUUID());
            var protectedBlock = appeng.core.definitions.AEBlocks.FLUIX_BLOCK.block();
            helper.setBlock(relative, protectedBlock); helper.setBlock(relative.east(), protectedBlock);
            ConfigHolder.INSTANCE.blackBlockList = new String[] { "ae2:*" };
            var context = data.updateBlocks(player, absolute, Direction.UP, false, 4);
            helper.assertTrue(context != null && data.cachedPositions().isEmpty() && !context.check(absolute),
                    "The production strict Mixin did not reject a wildcard-blacklisted block in real native player data");
            helper.setBlock(relative, Blocks.STONE); helper.setBlock(relative.east(), Blocks.STONE);
            helper.setBlock(relative.south(), Blocks.DIRT);
            ConfigHolder.INSTANCE.blackBlockList = new String[] { "minecraft:grass_block" };
            context = data.updateBlocks(player, absolute, Direction.UP, false, 4);
            helper.assertTrue(context != null && data.cachedPositions().contains(absolute) &&
                    data.cachedPositions().contains(absolute.east()) && !data.cachedPositions().contains(absolute.south()),
                    "Native Ultimine selection lost allowed blocks or accepted a different block under the original protection rule");
            ConfigHolder.INSTANCE.blackBlockList = new String[0];
            context = data.updateBlocks(player, absolute, Direction.UP, false, 4);
            helper.assertTrue(context != null && data.cachedPositions().contains(absolute),
                    "An empty blacklist did not retain native Ultimine matching");
            helper.succeed();
        } finally {
            ConfigHolder.INSTANCE.blackBlockList = previousBlacklist;
        }
    }
}
