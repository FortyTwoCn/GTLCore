package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior;
import org.gtlcore.gtlcore.api.pattern.AdvancedBlockPattern;
import org.gtlcore.gtlcore.integration.terminal.TerminalMaterials;
import org.gtlcore.gtlcore.common.block.BlockMap;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.common.util.Lazy;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import appeng.api.stacks.*;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.factory.PlayerInventoryUIFactory;
import brachy.modularui.factory.inventory.InventoryTypes;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.*;
import brachy.modularui.widget.WidgetTree;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeTerminalGameTests {
    private static net.neoforged.neoforge.common.util.FakePlayer player(GameTestHelper helper, String test) {
        var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.nameUUIDFromBytes(test.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "terminal_" + test));
        player.getInventory().clearContent(); player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); return player;
    }
    private static MultiblockControllerMachine controller(GameTestHelper helper, boolean fluid) {
        var pos = new BlockPos(4, 2, 5); helper.setBlock(pos, (fluid ? NativeTerminalFixtures.FLUID : NativeTerminalFixtures.REPEATING).getBlock());
        var controller = (MultiblockControllerMachine) helper.getBlockEntity(pos); controller.setFrontFacing(Direction.NORTH); return controller;
    }
    private static UseOnContext context(net.minecraft.world.entity.player.Player player, MultiblockControllerMachine controller, InteractionHand hand) {
        return new UseOnContext(player, hand, new BlockHitResult(controller.getBlockPos().getCenter(), Direction.UP, controller.getBlockPos(), false));
    }
    private static int count(net.minecraft.world.entity.player.Player player, net.minecraft.world.item.Item item) {
        int count = 0; for (var stack : player.getInventory().items) if (stack.is(item)) count += stack.getCount(); return count;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void offhandMirroredSurvivalBuildAndPartialDismantleKeepPhysicalMaterials(GameTestHelper helper) {
        var player = player(helper, "build"); var controller = controller(helper, false);
        var terminal = NativeAePatternFixtures.ULTIMATE_TERMINAL.asStack();
        var previous = BlockMap.tierBlockMap.get("sc");
        try {
            BlockMap.tierBlockMap.put("sc", Lazy.of(() -> new net.minecraft.world.level.block.Block[]{Blocks.IRON_BLOCK, Blocks.GOLD_BLOCK}));
            UltimateTerminalBehavior.setTierSelection(terminal, "sc", 1); UltimateTerminalBehavior.setRepeatCount(terminal, 2);
            UltimateTerminalBehavior.setMode(terminal, "IsFlipped", true);
            player.setItemInHand(InteractionHand.OFF_HAND, terminal); player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STICK));
            player.getInventory().setItem(1, new ItemStack(Items.GOLD_BLOCK, 5)); player.setPos(controller.getBlockPos().getCenter()); player.setShiftKeyDown(true);
            var behavior = new UltimateTerminalBehavior(); behavior.useOn(context(player, controller, InteractionHand.OFF_HAND));
            var plan = AdvancedBlockPattern.getAdvancedBlockPattern(controller.getDefaultStructurePattern()).plan(controller.getBlockPos(), Direction.NORTH, controller.getUpwardsFacing(), UltimateTerminalBehavior.getAutoBuildSetting(terminal));
            helper.assertTrue(controller.isFormed() && controller.isFlipped(), "Mirrored survival structure did not form");
            helper.assertTrue(count(player, Items.GOLD_BLOCK) == 0 && player.getMainHandItem().is(Items.STICK), "Build lost its offhand target or failed to charge exactly five physical blocks");
            behavior.useOn(context(player, controller, InteractionHand.OFF_HAND));
            var removed = plan.keySet().stream().filter(pos -> !pos.equals(controller.getBlockPos())).findFirst().orElseThrow();
            helper.getLevel().removeBlock(removed, false); controller.checkAndFormStructure();
            UltimateTerminalBehavior.setMode(terminal, "DismantleMode", true); behavior.useOn(context(player, controller, InteractionHand.OFF_HAND));
            helper.assertTrue(count(player, Items.GOLD_BLOCK) == 4, "Partial mirrored dismantle returned an incorrect number of blocks: " + count(player, Items.GOLD_BLOCK) + "; remaining=" + plan.keySet().stream().filter(pos -> helper.getLevel().getBlockState(pos).is(Blocks.GOLD_BLOCK)).count());
            for (var pos : plan.keySet()) if (!pos.equals(controller.getBlockPos())) helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "Partial layout left a matching casing in the world");
            helper.assertTrue(helper.getLevel().getBlockEntity(controller.getBlockPos()) == controller, "Dismantle removed its controller");
        } finally { if (previous == null) BlockMap.tierBlockMap.remove("sc"); else BlockMap.tierBlockMap.put("sc", previous); player.setShiftKeyDown(false); }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void failedVanillaPlacementRefundsTheReservationAndRestoresTheWorld(GameTestHelper helper) {
        var player = player(helper, "failure"); var relative = new BlockPos(4, 2, 5);
        helper.setBlock(relative, NativeTerminalFixtures.FAILED_PLACEMENT.getBlock());
        var controller = (MultiblockControllerMachine) helper.getBlockEntity(relative); controller.setFrontFacing(Direction.NORTH);
        var terminal = NativeAePatternFixtures.ULTIMATE_TERMINAL.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        player.getInventory().setItem(1, new ItemStack(Items.TORCH));
        var options = UltimateTerminalBehavior.getAutoBuildSetting(terminal);
        var pattern = AdvancedBlockPattern.getAdvancedBlockPattern(controller.getDefaultStructurePattern());
        pattern.autoBuild(player, controller, options, terminal);
        helper.assertTrue(!controller.isFormed() && count(player, Items.TORCH) == 1, "Failed placement consumed or created a material");
        for (var pos : pattern.plan(controller.getBlockPos(), Direction.NORTH, controller.getUpwardsFacing(), options).keySet())
            if (!pos.equals(controller.getBlockPos())) helper.assertTrue(helper.getLevel().getBlockState(pos).isAir(), "Failed placement left a block in the world");
        helper.assertTrue(!terminal.getOrDefault(DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY).copyTag().contains(TerminalMaterials.MATERIALS), "Failed placement retained an already returned material");
        player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL); helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void fluidContainersAndPersistedRefusedRefundsDoNotDuplicateWorldSources(GameTestHelper helper) {
        var player = player(helper, "fluid"); var controller = controller(helper, true);
        var terminal = NativeAePatternFixtures.ULTIMATE_TERMINAL.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        player.getInventory().setItem(1, new ItemStack(Items.WATER_BUCKET)); var options = UltimateTerminalBehavior.getAutoBuildSetting(terminal);
        var pattern = AdvancedBlockPattern.getAdvancedBlockPattern(controller.getDefaultStructurePattern());
        pattern.autoBuild(player, controller, options, terminal);
        helper.assertTrue(controller.isFormed() && count(player, Items.WATER_BUCKET) == 0 && count(player, Items.BUCKET) == 1, "Native bucket build did not exchange exactly one container");
        player.getInventory().setItem(1, ItemStack.EMPTY); options.setDismantleMode(true);
        pattern.dismantleMultiblock(controller, player, options, terminal);
        var restored = ItemStack.parse(helper.getLevel().registryAccess(), terminal.save(helper.getLevel().registryAccess())).orElseThrow();
        var materials = new TerminalMaterials(player, restored, null);
        helper.assertTrue(materials.amount(AEFluidKey.of(Fluids.WATER)) == 1000 && !controller.isFormed(), "Refused fluid refund was lost or recreated a water source after native item reload: pending=" + materials.amount(AEFluidKey.of(Fluids.WATER)) + "; formed=" + controller.isFormed() + "; buckets=" + count(player, Items.WATER_BUCKET));
        options.setDismantleMode(false); player.setItemInHand(InteractionHand.MAIN_HAND, restored);
        pattern.autoBuild(player, controller, options, restored);
        helper.assertTrue(controller.isFormed() && new TerminalMaterials(player, restored, null).amount(AEFluidKey.of(Fluids.WATER)) == 0 && count(player, Items.WATER_BUCKET) == 0, "Rebuilding duplicated the persisted fluid reservation");
        player.getInventory().setItem(1, new ItemStack(Items.BUCKET)); options.setDismantleMode(true);
        pattern.dismantleMultiblock(controller, player, options, restored);
        helper.assertTrue(count(player, Items.WATER_BUCKET) == 1 && new TerminalMaterials(player, restored, null).amount(AEFluidKey.of(Fluids.WATER)) == 0, "Native fluid refund did not refill exactly one available bucket");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeMenuPacketsClampValuesAndKeepSettingsOnTheOpenTerminal(GameTestHelper helper) {
        var player = player(helper, "menu"); var item = NativeAePatternFixtures.ULTIMATE_TERMINAL.asStack(); var independent = item.copy();
        player.getInventory().setItem(0, item); player.getInventory().setItem(1, independent);
        new TerminalMaterials(player, item, null).add(AEFluidKey.of(Fluids.WATER), 1000);
        var data = PlayerInventoryGuiData.of(player, InventoryTypes.PLAYER, null, 0);
        var manager = new PanelSyncManager(new ModularSyncManager(false), true); data.addSyncHandlers(manager);
        var panel = PlayerInventoryUIFactory.INSTANCE.createPanel(data, manager, new UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY)); WidgetTree.collectSyncValues(manager, panel);
        var materials = manager.findSyncHandler("terminalMaterials", 0, GenericSyncValue.class);
        helper.assertTrue(((Component) materials.getValue()).getString().contains("1000 mB"), "Native terminal menu hid persisted fluid materials");
        var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, "999999", Short.MAX_VALUE - 74); manager.receiveWidgetUpdate("terminalRepeat:0", false, 0, packet);
            packet.clear(); packet.writeBoolean(true); manager.receiveWidgetUpdate("terminal_AEMode:0", false, 0, packet);
            helper.assertTrue(UltimateTerminalBehavior.getAutoBuildSetting(item).getRepeatCount() == 648 && UltimateTerminalBehavior.getAutoBuildSetting(item).isAeMode(), "Native terminal C2S settings were not bounded and saved");
            helper.assertTrue(!UltimateTerminalBehavior.getAutoBuildSetting(independent).isAeMode() && UltimateTerminalBehavior.getAutoBuildSetting(independent).getRepeatCount() == 0, "Terminal packet changed another physical item");
            packet.clear(); brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, "invalid", Short.MAX_VALUE - 74); manager.receiveWidgetUpdate("terminalRepeat:0", false, 0, packet);
            helper.assertTrue(UltimateTerminalBehavior.getAutoBuildSetting(item).getRepeatCount() == 648, "Invalid text corrupted the terminal repeat count");
            var slot = manager.findSyncHandler("player", 0, ItemSlotSyncHandler.class).getSlot(); helper.assertTrue(!slot.mayPickup(player), "The open terminal is not locked in its native menu");
        } finally { packet.release(); }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void availableAlternateCasingBuildsWithoutAnExplicitTierSelection(GameTestHelper helper) {
        var player = player(helper, "alternate"); var controller = controller(helper, false);
        var terminal = NativeAePatternFixtures.ULTIMATE_TERMINAL.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        player.getInventory().setItem(1, new ItemStack(Items.GOLD_BLOCK, 3));
        AdvancedBlockPattern.getAdvancedBlockPattern(controller.getDefaultStructurePattern()).autoBuild(player, controller, new UltimateTerminalBehavior.AutoBuildSetting(), terminal);
        helper.assertTrue(controller.isFormed() && count(player, Items.GOLD_BLOCK) == 0, "Available alternative casing was ignored or charged incorrectly");
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void existingNativeHatchCountsTowardLimitsAndIsPreservedDuringDismantle(GameTestHelper helper) {
        var player = player(helper, "hatch"); var relative = new BlockPos(4, 2, 5); helper.setBlock(relative, NativeTerminalFixtures.EXISTING_HATCH.getBlock());
        var controller = (MultiblockControllerMachine) helper.getBlockEntity(relative); controller.setFrontFacing(Direction.NORTH);
        var terminal = NativeAePatternFixtures.ULTIMATE_TERMINAL.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        var options = new UltimateTerminalBehavior.AutoBuildSetting(); options.setNoHatchMode(false);
        var pattern = AdvancedBlockPattern.getAdvancedBlockPattern(controller.getDefaultStructurePattern());
        var plan = pattern.plan(controller.getBlockPos(), Direction.NORTH, controller.getUpwardsFacing(), options);
        var hatchPos = plan.entrySet().stream().filter(entry -> entry.getValue().getBlockState().is(Blocks.IRON_BLOCK)).findFirst().orElseThrow().getKey();
        helper.getLevel().setBlock(hatchPos, NativeAePatternFixtures.BUFFER.getBlock().defaultBlockState(), 3);
        var hatch = (org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine) helper.getLevel().getBlockEntity(hatchPos);
        var encoded = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(java.util.List.of(new GenericStack(AEItemKey.of(Items.IRON_INGOT), 9_007_199_254_740_993L)), java.util.List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1)));
        hatch.getTerminalPatternInventory().setItemDirect(26, encoded);
        player.getInventory().setItem(1, new ItemStack(Items.IRON_BLOCK)); player.getInventory().setItem(2, NativeAePatternFixtures.BUFFER.asStack());
        pattern.autoBuild(player, controller, options, terminal);
        helper.assertTrue(controller.isFormed() && count(player, Items.IRON_BLOCK) == 0 && count(player, NativeAePatternFixtures.BUFFER.asStack().getItem()) == 1, "Existing hatch was not included in the native count limits");
        options.setDismantleMode(true); options.setNoHatchMode(true); pattern.dismantleMultiblock(controller, player, options, terminal);
        helper.assertTrue(helper.getLevel().getBlockEntity(hatchPos) == hatch && ItemStack.matches(encoded, hatch.getTerminalPatternInventory().getStackInSlot(26)) && count(player, Items.IRON_BLOCK) == 1, "Preserve-hatch dismantle lost a hatch, its pattern components or the casing");
        helper.succeed();
    }
    private static NativeAeGridFixtures.Part network(GameTestHelper helper) {
        var relative = new BlockPos(8, 2, 5); helper.setBlock(relative, NativeAeGridFixtures.PART.getBlock());
        var part = (NativeAeGridFixtures.Part) helper.getBlockEntity(relative); part.setFrontFacing(Direction.NORTH);
        var pos = part.getBlockPos();
        helper.getLevel().setBlockAndUpdate(pos.north(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos.north(2), appeng.core.definitions.AEBlocks.DRIVE.block().defaultBlockState());
        var drive = (appeng.blockentity.storage.DriveBlockEntity) helper.getLevel().getBlockEntity(pos.north(2));
        drive.getInternalInventory().setItemDirect(0, appeng.core.definitions.AEItems.ITEM_CELL_1K.stack());
        drive.getInternalInventory().setItemDirect(1, appeng.core.definitions.AEItems.FLUID_CELL_1K.stack());
        return part;
    }
    private static UseOnContext on(net.minecraft.world.entity.player.Player player, BlockPos pos) {
        return new UseOnContext(player, InteractionHand.MAIN_HAND, new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 120)
    public static void boundNativeMeGridPaysForBuildingAndReceivesExactDismantleDrops(GameTestHelper helper) {
        var player = player(helper, "ae_items"); var controller = controller(helper, false); var part = network(helper);
        var terminal = NativeAePatternFixtures.ULTIMATE_TERMINAL.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        UltimateTerminalBehavior.setMode(terminal, "AEMode", true); player.setShiftKeyDown(true);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(part.getMainNode().isActive(), "Native terminal source grid did not become active");
            var storage = part.getMainNode().getGrid().getStorageService().getInventory(); var source = appeng.api.networking.security.IActionSource.ofPlayer(player);
            var material = AEItemKey.of(Items.GOLD_BLOCK);
            helper.assertTrue(storage.insert(material, 3, appeng.api.config.Actionable.MODULATE, source) == 3, "Native item cell refused build materials");
            var behavior = new UltimateTerminalBehavior(); behavior.useOn(on(player, part.getBlockPos())); behavior.useOn(on(player, controller.getBlockPos()));
            helper.assertTrue(controller.isFormed() && storage.extract(material, 99, appeng.api.config.Actionable.SIMULATE, source) == 0 && count(player, Items.GOLD_BLOCK) == 0, "Bound grid did not pay for exactly three real casing placements");
            UltimateTerminalBehavior.setMode(terminal, "DismantleMode", true); behavior.useOn(on(player, controller.getBlockPos()));
            helper.assertTrue(!controller.isFormed() && storage.extract(material, 99, appeng.api.config.Actionable.SIMULATE, source) == 3 && count(player, Items.GOLD_BLOCK) == 0, "Dismantle lost or duplicated native ME item refunds");
            helper.succeed();
        });
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 120)
    public static void nativeMeFluidBuildAndRebindingReturnPersistedMaterialsWithoutContainers(GameTestHelper helper) {
        var player = player(helper, "ae_fluids"); var controller = controller(helper, true); var part = network(helper);
        var terminal = NativeAePatternFixtures.ULTIMATE_TERMINAL.asStack(); player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
        UltimateTerminalBehavior.setMode(terminal, "AEMode", true); player.setShiftKeyDown(true);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(part.getMainNode().isActive(), "Native terminal fluid grid did not become active");
            var storage = part.getMainNode().getGrid().getStorageService().getInventory(); var source = appeng.api.networking.security.IActionSource.ofPlayer(player);
            var material = AEFluidKey.of(Fluids.WATER);
            helper.assertTrue(storage.insert(material, 1000, appeng.api.config.Actionable.MODULATE, source) == 1000, "Native fluid cell refused one bucket of water");
            var behavior = new UltimateTerminalBehavior(); behavior.useOn(on(player, part.getBlockPos())); behavior.useOn(on(player, controller.getBlockPos()));
            helper.assertTrue(controller.isFormed() && storage.extract(material, 2000, appeng.api.config.Actionable.SIMULATE, source) == 0 && count(player, Items.WATER_BUCKET) == 0, "Native ME fluid build required a container or failed to charge exactly 1000 mB");
            UltimateTerminalBehavior.setMode(terminal, "AEMode", false); UltimateTerminalBehavior.setMode(terminal, "DismantleMode", true);
            behavior.useOn(on(player, controller.getBlockPos()));
            var restored = ItemStack.parse(helper.getLevel().registryAccess(), terminal.save(helper.getLevel().registryAccess())).orElseThrow();
            player.setItemInHand(InteractionHand.MAIN_HAND, restored); behavior.useOn(on(player, part.getBlockPos()));
            helper.assertTrue(new TerminalMaterials(player, restored, null).amount(material) == 1000 && storage.extract(material, 2000, appeng.api.config.Actionable.SIMULATE, source) == 0, "Disabled AE mode lost or returned the held fluid reservation");
            UltimateTerminalBehavior.setMode(restored, "AEMode", true); behavior.useOn(on(player, part.getBlockPos()));
            helper.assertTrue(new TerminalMaterials(player, restored, null).amount(material) == 0 && storage.extract(material, 2000, appeng.api.config.Actionable.SIMULATE, source) == 1000 && !controller.isFormed(), "Rebinding duplicated a world source or failed to return persisted fluid to native ME storage");
            helper.succeed();
        });
    }
}
