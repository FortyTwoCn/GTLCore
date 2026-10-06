package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.electric.GreenhouseMachine;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeGreenhouseGameTests {
    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 180)
    public static void actualTemperedGlassLightGatesInputsAndRecoversAfterOpeningTheEnclosure(GameTestHelper helper) {
        var entry = NativeGreenhouseFixtures.GREENHOUSE; var blocks = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap(Map.of(0, 1)))
                .populate(blocks, entry.get().getStructurePatterns().get("main").get(), null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var block : blocks.entrySet()) {
            var pos = new BlockPos(4, 3, 4).offset(block.getKey()); helper.setBlock(pos, block.getValue().getBlockState());
            if (block.getValue().getBlockState().is(entry.getBlock())) controller = pos;
        }
        var machine = (GreenhouseMachine) helper.getBlockEntity(Objects.requireNonNull(controller)); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Native greenhouse fixture did not form");
        var level = helper.getLevel(); var glass = machine.getBlockPos().offset(1, 2, 0); var sample = glass.above();
        level.setBlockAndUpdate(glass, GTBlocks.CASING_TEMPERED_GLASS.getDefaultState());
        var input = machine.getTraitsFromParts(NotifiableItemStackHandler.class).stream().filter(trait -> trait.getHandlerIO() == IO.IN).findFirst().orElseThrow();
        input.setStackInSlot(0, Items.IRON_INGOT.getDefaultInstance());
        var recipe = GTLRecipeTypes.GREENHOUSE_RECIPES.recipeBuilder("native_greenhouse_light_gate")
                .inputItems(Items.IRON_INGOT).outputItems(Items.IRON_NUGGET).duration(40).EUt(0).build();
        var enclosure = new ArrayList<BlockPos>();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.beforeWorking(recipe), "Open tempered glass did not admit skylight"))
                .thenExecute(() -> {
                    for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = -1; y <= 2; y++) {
                        var pos = glass.offset(x, y, z); if (pos.equals(glass) || pos.equals(sample)) continue;
                        enclosure.add(pos); level.setBlockAndUpdate(pos, Blocks.OBSIDIAN.defaultBlockState());
                    }
                }).thenWaitUntil(() -> helper.assertTrue(level.getBrightness(LightLayer.SKY, sample) == 0, "Closed real enclosure is not dark yet"))
                .thenExecute(() -> {
                    helper.assertTrue(!machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe) && input.getStackInSlot(0).getCount() == 1,
                            "Dark greenhouse consumed a physical recipe input");
                    for (var pos : enclosure) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }).thenWaitUntil(() -> helper.assertTrue(machine.beforeWorking(recipe), "Restored skylight left the greenhouse permanently blocked"))
                .thenSucceed();
    }
}
