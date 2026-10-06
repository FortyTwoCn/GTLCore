package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.StructureDetectBehavior;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewGeometry;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.error.BlockMatchingError;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.BasicSliceStrategy;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeStructureDetectGameTests {
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void physicalOffhandToolKeepsMirroredModeAndUnrelatedComponentsAcrossNativeSave(GameTestHelper helper) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "StructureDetect"));
        var item = NativeAePatternFixtures.STRUCTURE_DETECT.asStack(); var other = item.copy();
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("unrelated", "preserved");
        item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag)); player.getInventory().setItem(40, item);
        player.getInventory().setItem(0, new ItemStack(Items.APPLE));
        var pos = helper.absolutePos(new BlockPos(1, 1, 1)); helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
        var context = new UseOnContext(player, InteractionHand.OFF_HAND, new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(!StructureDetectBehavior.isMirrored(other) && !other.has(DataComponents.CUSTOM_DATA), "Reading mode mutated another tool");
        helper.assertTrue(StructureDetectBehavior.INSTANCE.onItemUseFirst(item, context) == InteractionResult.SUCCESS &&
                StructureDetectBehavior.isMirrored(item), "Offhand use failed to switch mirror mode");
        var loaded = ItemStack.parse(helper.getLevel().registryAccess(), item.save(helper.getLevel().registryAccess())).orElseThrow();
        helper.assertTrue(StructureDetectBehavior.isMirrored(loaded) && loaded.getComponentsPatch().equals(item.getComponentsPatch()), "Native item save lost detection mode/components");
        StructureDetectBehavior.INSTANCE.onItemUseFirst(item, context);
        helper.assertTrue(!StructureDetectBehavior.isMirrored(item) && item.get(DataComponents.CUSTOM_DATA).copyTag().getString("unrelated").equals("preserved") &&
                !StructureDetectBehavior.isMirrored(other) && player.getMainHandItem().is(Items.APPLE), "Mode toggle damaged other data or the other hand");
        helper.succeed();
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void nativeDiagnosticsFindExactCandidatesInAll48PosesAndEverySubstructure(GameTestHelper helper) {
        var definition = NativeTerminalFixtures.DETECTION.get(); var origin = helper.absolutePos(new BlockPos(8, 5, 8));
        var main = NativePreviewGeometry.fromPattern(definition, definition.getStructurePatterns().get("main").get(),
                new Int2IntOpenHashMap(), List.of(), Direction.NORTH, Direction.UP, false);
        var auxiliary = NativePreviewGeometry.fromPattern(definition, definition.getStructurePatterns().get("auxiliary").get(),
                new Int2IntOpenHashMap(), List.of(), Direction.NORTH, Direction.UP, false);
        for (var front : Direction.values()) for (var up : Direction.values()) {
            if (front.getAxis() == up.getAxis()) continue;
            for (boolean mirrored : new boolean[] { false, true }) {
                var placed = new HashSet<BlockPos>();
                for (var geometry : List.of(main.oriented(front, up, mirrored), auxiliary.oriented(front, up, mirrored)))
                    geometry.states().forEach((pos, state) -> { helper.getLevel().setBlockAndUpdate(origin.offset(pos), state); placed.add(origin.offset(pos)); });
                var controller = (MultiblockControllerMachine) helper.getLevel().getBlockEntity(origin);
                helper.assertTrue(StructureDetectBehavior.INSTANCE.detect(controller, mirrored).isEmpty(), "Valid native pose was rejected: " + front + "/" + up + "/" + mirrored);
                var missing = main.oriented(front, up, mirrored).states().entrySet().stream().filter(entry -> entry.getValue().is(Blocks.GOLD_BLOCK))
                        .map(entry -> origin.offset(entry.getKey())).findFirst().orElseThrow();
                helper.getLevel().setBlockAndUpdate(missing, Blocks.AIR.defaultBlockState());
                var errors = StructureDetectBehavior.INSTANCE.detect(controller, mirrored);
                helper.assertTrue(errors.stream().anyMatch(error -> error.structure().equals("main") && error.pos().equals(missing) &&
                        error.error() instanceof BlockMatchingError blocks && blocks.getBlocks().contains(Blocks.GOLD_BLOCK)), "Native diagnostic lost exact missing position or gold candidate");
                var auxPos = auxiliary.oriented(front, up, mirrored).states().entrySet().stream().filter(entry -> entry.getValue().is(Blocks.REDSTONE_BLOCK))
                        .map(entry -> origin.offset(entry.getKey())).findFirst().orElseThrow();
                helper.getLevel().setBlockAndUpdate(auxPos, Blocks.AIR.defaultBlockState());
                helper.assertTrue(StructureDetectBehavior.INSTANCE.detect(controller, mirrored).stream().anyMatch(error -> error.structure().equals("auxiliary") && error.pos().equals(auxPos)), "Auxiliary structure was omitted");
                placed.forEach(pos -> helper.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            }
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void diagnosingMirroredRepeatableStructurePreservesLiveFormationCacheAndAcceptedDimensions(GameTestHelper helper) {
        var definition = NativeTerminalFixtures.REPEATING.get(); var origin = helper.absolutePos(new BlockPos(4, 3, 4));
        var pattern = (BlockPattern) definition.getStructurePatterns().get("main").get();
        var geometry = NativePreviewGeometry.fromPattern(definition, pattern, new Int2IntOpenHashMap(Map.of(1, 2)), List.of(), Direction.NORTH, Direction.UP, false).oriented(Direction.NORTH, Direction.UP, false);
        geometry.states().forEach((pos, state) -> helper.getLevel().setBlockAndUpdate(origin.offset(pos), state));
        var controller = (MultiblockControllerMachine) helper.getLevel().getBlockEntity(origin); controller.checkAndFormStructure();
        helper.assertTrue(controller.isFormed(), "Native repeating structure did not form");
        var live = controller.getDefaultPatternState(); var cache = new HashMap<>(live.getCache()); var liveErrors = live.getErrors();
        int[] repeats = Arrays.stream(pattern.getSlices()).mapToInt(slice -> slice.getActualRepeats()).toArray();
        var strategy = (BasicSliceStrategy) pattern.getSliceStrategy(); int group = strategy.getMultiSliceRepeats(1);
        helper.assertTrue(!StructureDetectBehavior.INSTANCE.detect(controller, true).isEmpty(), "Mirrored mode accepted an asymmetric normal structure");
        helper.assertTrue(controller.isFormed() && controller.getDefaultPatternState() == live && live.getCache().equals(cache) && live.getErrors() == liveErrors &&
                Arrays.equals(repeats, Arrays.stream(pattern.getSlices()).mapToInt(slice -> slice.getActualRepeats()).toArray()) &&
                strategy.getMultiSliceRepeats(1) == group, "Diagnostic changed live formation/context/repetitions");
        helper.assertTrue(StructureDetectBehavior.INSTANCE.detect(controller, false).isEmpty(), "Normal mode failed after mirrored diagnosis");
        helper.succeed();
    }
}
