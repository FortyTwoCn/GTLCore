package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior;
import org.gtlcore.gtlcore.api.pattern.AdvancedBlockPattern;
import org.gtlcore.gtlcore.common.block.BlockMap;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.util.Lazy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

class NativeTerminalIntegrationTest {
    @Test void perItemModesBoundsAndTierGroupsSurviveTheNativeItemCodec() {
        var previous = BlockMap.tierBlockMap.get("sc");
        try {
            BlockMap.tierBlockMap.put("sc", Lazy.of(() -> new net.minecraft.world.level.block.Block[]{Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK}));
            var item = NativeAePatternFixtures.ULTIMATE_TERMINAL.asStack(); var independent = item.copy();
            var unrelated = new net.minecraft.nbt.CompoundTag(); unrelated.putString("other", "preserved"); item.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));
            UltimateTerminalBehavior.setRepeatCount(item, 999); UltimateTerminalBehavior.setMode(item, "AEMode", true);
            UltimateTerminalBehavior.setTierSelection(item, "sc", 1);
            var registries = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
            var restored = ItemStack.parse(registries, item.save(registries)).orElseThrow();
            var settings = UltimateTerminalBehavior.getAutoBuildSetting(restored);
            assertEquals(648, settings.getRepeatCount()); assertTrue(settings.isAeMode()); assertTrue(settings.isNoHatchMode());
            assertSame(Blocks.GOLD_BLOCK, settings.selectedReplacement(Blocks.IRON_BLOCK));
            assertEquals("preserved", restored.get(DataComponents.CUSTOM_DATA).copyTag().getString("other"));
            assertFalse(UltimateTerminalBehavior.getAutoBuildSetting(independent).isAeMode());
            assertThrows(IllegalArgumentException.class, () -> UltimateTerminalBehavior.setTierSelection(item, "sc", 2));
            assertThrows(IllegalArgumentException.class, () -> UltimateTerminalBehavior.setMode(item, "unknown", true));
            UltimateTerminalBehavior.setTierSelection(item, "sc", -1); assertNull(UltimateTerminalBehavior.getSelectedTierBlock(item, "sc"));
        } finally { if (previous == null) BlockMap.tierBlockMap.remove("sc"); else BlockMap.tierBlockMap.put("sc", previous); }
    }
    @Test void nativePlansUseTheMatchersOffsetsForEveryFacingUpDirectionAndMirror() {
        var pattern = (BlockPattern) MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("IS", "II")
                .where('S', controller(NativeTerminalFixtures.REPEATING.get())).where('I', blocks(Blocks.IRON_BLOCK)).build();
        var options = new UltimateTerminalBehavior.AutoBuildSetting(); options.setNoHatchMode(false);
        var center = new BlockPos(37, 91, -23);
        for (var front : Direction.values()) for (var up : Direction.values()) {
            if (front.getAxis() == up.getAxis()) continue;
            for (boolean flipped : new boolean[]{false, true}) {
                options.setFlipped(flipped);
                var planned = AdvancedBlockPattern.getAdvancedBlockPattern(pattern).plan(center, front, up, options);
                var layout = AdvancedBlockPattern.layout(pattern, center, front, up, flipped, AdvancedBlockPattern.configuredRepetitions(pattern, 0));
                assertEquals(layout.keySet(), planned.keySet(), front + "/" + up + "/" + flipped);
                assertSame(NativeTerminalFixtures.REPEATING.getBlock(), planned.get(center).getBlockState().getBlock());
            }
        }
    }
    @Test void nativeRepeatedSlicePlansKeepTierPreferencesAndClampEachSliceIndependently() {
        var block = (BlockPattern) NativeTerminalFixtures.REPEATING.get().getStructurePatterns().get(com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine.DEFAULT_STRUCTURE).get();
        var settings = new UltimateTerminalBehavior.AutoBuildSetting(); settings.setRepeatCount(99);
        settings.addTierSelection(new net.minecraft.world.level.block.Block[]{Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK}, Blocks.GOLD_BLOCK);
        var repeats = AdvancedBlockPattern.configuredRepetitions(block, settings.getRepeatCount());
        assertEquals(1, repeats.get(0)); assertEquals(3, repeats.get(1));
        var plan = AdvancedBlockPattern.getAdvancedBlockPattern(block).plan(BlockPos.ZERO, Direction.NORTH, Direction.UP, settings);
        assertEquals(8, plan.size()); assertEquals(7, plan.values().stream().filter(info -> info.getBlockState().is(Blocks.GOLD_BLOCK)).count());
    }
}
