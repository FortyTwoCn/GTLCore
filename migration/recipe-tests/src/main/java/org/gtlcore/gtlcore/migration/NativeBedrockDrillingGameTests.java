package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.BedrockDrillingRig;
import org.gtlcore.gtlcore.common.util.BlockStateWatcher;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeBedrockDrillingGameTests {
    static BedrockDrillingRig form(GameTestHelper helper) {
        var entry = NativeBedrockDrillingFixtures.DRILLER;
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(new Int2IntOpenHashMap(Map.of(0, 1)))
                .populate(blocks, entry.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var block : blocks.entrySet()) {
            var pos = new BlockPos(4, 12, 4).offset(block.getKey());
            helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        var machine = (BedrockDrillingRig) helper.getBlockEntity(Objects.requireNonNull(controller));
        machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getParts().size() == 3, "Driller did not discover physical native hatches");
        return machine;
    }

    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void bedrockChangesGatePhysicalRecipesAndWatcherRebindsAfterEntityRecreation(GameTestHelper helper) {
        var machine = form(helper);
        helper.runAfterDelay(2, () -> {
            var target = machine.getBlockPos().below(9);
            var level = helper.getLevel();
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            var input = machine.getTraitsFromParts(NotifiableItemStackHandler.class).stream()
                    .filter(trait -> trait.getHandlerIO() == IO.IN).findFirst().orElseThrow();
            var output = machine.getTraitsFromParts(NotifiableItemStackHandler.class).stream()
                    .filter(trait -> trait.getHandlerIO() == IO.OUT).findFirst().orElseThrow();
            input.setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
            machine.getEnergyContainer().changeEnergy(10000);
            var recipe = GTLRecipeTypes.BEDROCK_DRILLING_RIG_RECIPES.recipeBuilder("native_bedrock_drilling_cycle")
                    .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET, 9).duration(2).EUt(8).build();
            var logic = machine.getRecipeLogic();
            helper.assertTrue(!logic.checkMatchedRecipeAvailable(recipe) && input.getStackInSlot(0).getCount() == 1,
                    "Driller consumed inputs without bedrock nine blocks below its controller");
            level.setBlockAndUpdate(target, Blocks.BEDROCK.defaultBlockState());
            helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe), "Real LevelChunk change did not wake the drilling gate");
            logic.setSuspendAfterFinish(true);
            int duration = logic.getLastRecipe().duration;
            for (int tick = 0; tick < duration; tick++) logic.serverTick();
            helper.assertTrue(input.getStackInSlot(0).isEmpty() && output.getStackInSlot(0).getCount() == 9,
                    "Native drilling cycle lost its physical outputs");
            level.setBlockAndUpdate(target, Blocks.STONE.defaultBlockState());
            helper.assertTrue(!machine.beforeWorking(recipe), "Removing bedrock left the drilling gate enabled");

            var saved = machine.saveWithoutMetadata(level.registryAccess());
            var pos = machine.getBlockPos(); var state = machine.getBlockState();
            machine.invalidateStructure("main");
            level.setBlockAndUpdate(target, Blocks.BEDROCK.defaultBlockState());
            helper.assertTrue(!machine.beforeWorking(recipe), "Invalidated controller retained its target watcher");
            level.removeBlockEntity(pos);
            var restored = (BedrockDrillingRig) NativeBedrockDrillingFixtures.DRILLER.getBlockEntityType().create(pos, state);
            level.setBlockEntity(restored); restored.loadAdditional(saved, level.registryAccess()); restored.checkAndFormStructure();
            helper.assertTrue(restored.isFormed() && restored.beforeWorking(recipe), "Native save/load did not rebind the bedrock watcher");
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            helper.assertTrue(!restored.beforeWorking(recipe), "Recreated controller ignored a real target change");
            restored.onUnload();
            level.setBlockAndUpdate(target, Blocks.BEDROCK.defaultBlockState());
            helper.assertTrue(!restored.beforeWorking(recipe), "Unloaded controller retained a live watcher");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeBlockNotificationsAllowSelfRemovalAndIgnoreNoOpWrites(GameTestHelper helper) {
        var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(4, 3, 4));
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        var once = new AtomicInteger(); var persistent = new AtomicInteger();
        var own = new BlockStateWatcher.WatcherHandle[1];
        own[0] = BlockStateWatcher.addWatcher(level, pos, state -> { once.incrementAndGet(); own[0].remove(); });
        var other = BlockStateWatcher.addWatcher(level, pos, state -> persistent.incrementAndGet());
        try {
            level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
            level.setBlockAndUpdate(pos, Blocks.BEDROCK.defaultBlockState());
            level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
            helper.assertTrue(once.get() == 1 && persistent.get() == 2, "Self-removal skipped another watcher or no-op writes fired notifications");
            other.remove(); other.remove();
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            helper.assertTrue(persistent.get() == 2, "Removed watcher received another block change");
            helper.succeed();
        } finally {
            own[0].remove(); other.remove();
        }
    }
}
