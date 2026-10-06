package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.pattern.*;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.*;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.*;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.core.definitions.AEBlocks;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeQuickUploadGameTests {
    private record Rig(NoEnergyMultiblockMachine controller, MEPatternBufferPartMachine buffer) {}
    private static Rig build(GameTestHelper helper) {
        var definition = NativeAePatternFixtures.CONTROLLER.get(); var pattern = (BlockPattern) definition.getStructurePatterns().get("main").get();
        var repeats = new Int2IntOpenHashMap(); for (int i = 0; i < pattern.getSlices().length; i++) repeats.put(i, pattern.getSlices()[i].getMinRepeats());
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>(); AbstractStructureHelper.blockPattern(repeats).populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        BlockPos controllerPos = null;
        for (var block : blocks.entrySet()) { var pos = new BlockPos(4, 4, 4).offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState()); if (block.getValue().getBlockState().is(definition.getBlock())) controllerPos = pos; }
        var controller = (NoEnergyMultiblockMachine) helper.getBlockEntity(controllerPos); controller.setFrontFacing(Direction.NORTH); controller.setWorkingEnabled(false); controller.checkAndFormStructure();
        helper.assertTrue(controller.isFormed(), "Quick upload fixture failed to form"); var buffer = (MEPatternBufferPartMachine) controller.getParts().getFirst(); buffer.setFrontFacing(Direction.NORTH);
        helper.getLevel().setBlockAndUpdate(buffer.getBlockPos().north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState()); return new Rig(controller, buffer);
    }
    private static ItemStack pattern() {
        var stack = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 2)), List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 3)));
        PatternQuickUploadMetadata.writeRecipeTypeId(stack, GTRecipeTypes.CHEMICAL_RECIPES.value().id); return stack;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 150)
    public static void nativeQuickUploadDiscoversRealProviderAndInsertsDeduplicatesAndUndoesExactlyOnce(GameTestHelper helper) {
        var rig = build(helper); var buffer = rig.buffer();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(buffer.isOnline(), "Quick upload buffer AE node did not connect")).thenExecute(() -> {
            var player = FakePlayerFactory.getMinecraft(helper.getLevel()); var stack = pattern(); PatternEncoderMetadata.writeEncoder(stack, UUID.randomUUID(), "first encoder");
            var before = stack.getComponentsPatch(); var found = PatternQuickUploadService.findTargets(player, buffer.getMainNode().getNode(), stack);
            helper.assertTrue(found.failureMessage() == null && found.match().status() == PatternQuickUploadMatch.Status.UNIQUE, "Native rapid upload did not find a unique formed provider");
            var target = found.match().uniqueCandidate(); helper.assertTrue(target.bufferPos().equals(buffer.getBlockPos()), "Native rapid upload chose the wrong provider position");
            var inserted = PatternQuickUploadService.insertIntoTargetSlotResult(player, stack, target); helper.assertTrue(inserted.status() == PatternQuickUploadService.UploadStatus.INSERTED && inserted.slot() == 0 && ItemStack.matches(buffer.getTerminalPatternInventory().getStackInSlot(0), stack), "Native rapid upload did not insert the exact component pattern");
            var otherEncoder = stack.copy(); PatternEncoderMetadata.writeEncoder(otherEncoder, UUID.randomUUID(), "second encoder");
            helper.assertTrue(PatternQuickUploadService.insertIntoTargetSlotResult(player, otherEncoder, target).status() == PatternQuickUploadService.UploadStatus.DUPLICATE && buffer.getTerminalPatternInventory().getStackInSlot(1).isEmpty(), "Encoder metadata made a duplicate pattern insert again");
            helper.assertTrue(!PatternQuickUploadService.removeFromTarget(player, otherEncoder, target, 0) && PatternQuickUploadService.removeFromTarget(player, stack, target, 0) && !PatternQuickUploadService.removeFromTarget(player, stack, target, 0), "Rapid upload undo removed a different pattern or extracted twice");
            helper.assertTrue(stack.getComponentsPatch().equals(before) && stack.getCount() == 1 && buffer.getTerminalPatternInventory().getStackInSlot(0).isEmpty(), "Rapid upload mutated its source pattern or retained an undone pattern");
        }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 150)
    public static void nativeQuickUploadRejectsUnformedTargetsAndDoesNotLoadRemoteChunks(GameTestHelper helper) {
        var rig = build(helper); var buffer = rig.buffer();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(buffer.isOnline(), "Quick upload lifecycle AE node did not connect")).thenExecute(() -> {
            var player = FakePlayerFactory.getMinecraft(helper.getLevel()); var stack = pattern();
            rig.controller().invalidateStructure(); var missing = PatternQuickUploadService.findTargets(player, buffer.getMainNode().getNode(), stack);
            helper.assertTrue(missing.failureMessage() != null && missing.match().status() == PatternQuickUploadMatch.Status.NONE, "Rapid upload offered an unformed provider");
            rig.controller().checkAndFormStructure(); helper.assertTrue(PatternQuickUploadService.findTargets(player, buffer.getMainNode().getNode(), stack).match().status() == PatternQuickUploadMatch.Status.UNIQUE, "Rapid upload did not recover after reformation");
            var remote = new BlockPos(30_000_000 - 64, 80, 30_000_000 - 64); helper.assertTrue(!helper.getLevel().hasChunkAt(remote), "Quick upload remote fixture chunk is already loaded");
            helper.assertTrue(!PatternQuickUploadService.insertIntoTarget(player, stack, remote) && !helper.getLevel().hasChunkAt(remote), "Rapid upload loaded an unavailable remote chunk");
            helper.assertTrue(buffer.getTerminalPatternInventory().getStackInSlot(0).isEmpty(), "Rejected rapid upload changed the provider inventory");
        }).thenSucceed();
    }
}
