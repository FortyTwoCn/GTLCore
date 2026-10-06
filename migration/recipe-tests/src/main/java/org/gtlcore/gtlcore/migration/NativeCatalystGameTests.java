package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.BallHatchPartMachine;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.core.definitions.AEBlocks;
import brachy.modularui.value.sync.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.context.*;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.*;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeCatalystGameTests {
    private static ItemStack item() { var stack = new ItemStack(Items.DIAMOND, 2); stack.set(DataComponents.CUSTOM_NAME, Component.literal("shared native catalyst")); return stack; }
    private static FluidStack fluid() { var stack = new FluidStack(Fluids.WATER, 1000); stack.set(DataComponents.CUSTOM_NAME, Component.literal("shared native fluid catalyst")); return stack; }
    private static ItemStack ball() { var stack = NativeCatalystFixtures.BALL.asStack(3); stack.set(DataComponents.CUSTOM_NAME, Component.literal("native grinding-ball ownership")); return stack; }
    private static NoEnergyMultiblockMachine build(GameTestHelper helper, MultiblockMachineDefinition definition) {
        var pattern = (BlockPattern) definition.getStructurePatterns().get("main").get(); var repeats = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        for (int index = 0; index < pattern.getSlices().length; index++) repeats.put(index, pattern.getSlices()[index].getMinRepeats());
        var blocks = new LinkedHashMap<BlockPos, BlockInfo>(); AbstractStructureHelper.blockPattern(repeats).populate(blocks, pattern, null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var entry : blocks.entrySet()) { var pos = new BlockPos(4, 4, 4).offset(entry.getKey()); helper.setBlock(pos, entry.getValue().getBlockState()); if (entry.getValue().getBlockState().is(definition.getBlock())) controller = pos; }
        var machine = (NoEnergyMultiblockMachine) helper.getBlockEntity(controller); machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Actual native catalyst/grinding-ball controller did not form"); return machine;
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeSharedCatalystsSatisfyRealPatternControllerRecipeWithoutBeingConsumed(GameTestHelper helper) {
        var controller = build(helper, NativeAePatternFixtures.CONTROLLER.get()); var part = (MEPatternBufferPartMachine) controller.getParts().getFirst();
        helper.getLevel().setBlockAndUpdate(part.getBlockPos().north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        part.getSharedCatalystInventory().storage.setStackInSlot(8, item()); part.getSharedCatalystTank().getStorages()[8].setFluid(fluid());
        var encoded = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 2)), List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)));
        part.getTerminalPatternInventory().setItemDirect(0, encoded);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline() && !part.getAvailablePatterns().isEmpty(), "Native catalyst fixture has no published AE pattern"))
                .thenExecute(() -> {
                    var pattern = part.getAvailablePatterns().getFirst(); var counters = new KeyCounter[pattern.getInputs().length];
                    for (int index = 0; index < counters.length; index++) { counters[index] = new KeyCounter(); var input = pattern.getInputs()[index]; var stack = input.getPossibleInputs()[0]; counters[index].add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier())); }
                    helper.assertTrue(part.pushPattern(pattern, counters), "Native shared-catalyst processing input was rejected");
                    var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_real_shared_catalyst_recipe").inputItems(Items.COPPER_INGOT, 2)
                            .notConsumable(item()).notConsumableFluid(fluid().copyWithAmount(500)).outputItems(Items.GOLD_INGOT).duration(3).build();
                    recipe.tickInputs.remove(EURecipeCapability.CAP);
                    helper.assertTrue(controller.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Real controller could not match shared native catalysts"); controller.getRecipeLogic().setSuspendAfterFinish(true);
                }).thenWaitUntil(() -> helper.assertTrue(controller.getRecipeLogic().isSuspend(), "Shared-catalyst recipe never finished"))
                .thenExecute(() -> {
                    helper.assertTrue(part.getInternalInventory()[0].getItemInventory().isEmpty() && part.getBuffer().getLong(AEItemKey.of(Items.GOLD_INGOT)) == 1, "Native shared-catalyst recipe lost consumed input or output ownership");
                    helper.assertTrue(part.getSharedCatalystInventory().getStackInSlot(8).getCount() == 2 && ItemStack.isSameItemSameComponents(item(), part.getSharedCatalystInventory().getStackInSlot(8)), "Actual recipe consumed or changed the shared item catalyst");
                    helper.assertTrue(part.getSharedCatalystTank().getFluidInTank(8).getAmount() == 1000 && FluidStack.isSameFluidSameComponents(fluid(), part.getSharedCatalystTank().getFluidInTank(8)), "Actual recipe consumed or changed the shared fluid catalyst");
                }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void nativeSharedCatalystWorldReloadRefreshesCacheAndHidesExternalCapabilities(GameTestHelper helper) {
        var pos = new BlockPos(4, 4, 4); helper.setBlock(pos, NativeAePatternFixtures.BUFFER.getBlock()); var host = (MEPatternBufferPartMachine) helper.getBlockEntity(pos);
        host.setFrontFacing(Direction.NORTH); helper.setBlock(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        host.getSharedCatalystInventory().storage.setStackInSlot(8, item()); host.getSharedCatalystTank().getStorages()[8].setFluid(fluid());
        var saved = host.saveWithoutMetadata(helper.getLevel().registryAccess()); var copy = (MEPatternBufferPartMachine) NativeAePatternFixtures.BUFFER.getBlockEntityType().create(host.getBlockPos(), host.getBlockState());
        helper.getLevel().removeBlockEntity(host.getBlockPos()); copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(copy.isOnline() && copy.getMainNode().getNode() != null && copy.getMainNode().getNode().isActive(), "Reloaded native catalyst owner did not complete its load lifecycle"))
                .thenExecute(() -> {
                    var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_catalyst_reload").notConsumable(item()).notConsumableFluid(fluid()).duration(3).build();
                    var items = new ArrayList<>(recipe.getInputContents(ItemRecipeCapability.CAP).stream().map(content -> ItemRecipeCapability.CAP.of(content.content())).toList());
                    var fluids = new ArrayList<>(recipe.getInputContents(FluidRecipeCapability.CAP).stream().map(content -> FluidRecipeCapability.CAP.of(content.content())).toList());
                    copy.getSharedCatalystInventory().handleRecipeInner(IO.IN, recipe, items, true); copy.getSharedCatalystTank().handleRecipeInner(IO.IN, recipe, fluids, true);
                    helper.assertTrue(items.isEmpty() && fluids.isEmpty(), "Native entity reload left stale shared-catalyst quantity caches");
                    for (var side : Direction.values()) helper.assertTrue(!copy.getSharedCatalystInventory().hasCapability(side) && !copy.getSharedCatalystTank().hasCapability(side), "Shared native catalyst became externally accessible");
                    copy.getSharedCatalystInventory().storage.setStackInSlot(8, ItemStack.EMPTY); copy.getSharedCatalystTank().getStorages()[8].setFluid(FluidStack.EMPTY);
                    items.add(LongIngredient.create(item(), 2)); fluids.add(LongFluidIngredient.create(fluid(), 1000));
                    copy.getSharedCatalystInventory().handleRecipeInner(IO.IN, recipe, items, true); copy.getSharedCatalystTank().handleRecipeInner(IO.IN, recipe, fluids, true);
                    helper.assertTrue(items.size() == 1 && fluids.size() == 1, "Native catalyst clearing did not invalidate cached reusable quantities");
                }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeGrindingBallControllerCallbacksPreserveBallAndApplyActivePlayerDamage(GameTestHelper helper) {
        var machine = build(helper, NativeCatalystFixtures.CONTROLLER.get()); var part = (BallHatchPartMachine) machine.getParts().getFirst(); part.getInventory().storage.setStackInSlot(0, ball());
        var input = machine.getAllTraits().stream().filter(trait -> trait instanceof NotifiableItemStackHandler handler && handler.getHandlerIO() == IO.IN).map(trait -> (NotifiableItemStackHandler) trait).findFirst().orElseThrow();
        var output = machine.getAllTraits().stream().filter(trait -> trait instanceof NotifiableItemStackHandler handler && handler.getHandlerIO() == IO.OUT).map(trait -> (NotifiableItemStackHandler) trait).findFirst().orElseThrow();
        input.storage.setStackInSlot(0, new ItemStack(Items.COPPER_INGOT)); var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_grinding_ball_callbacks").inputItems(Items.COPPER_INGOT).outputItems(Items.GOLD_INGOT).duration(5).build(); recipe.tickInputs.remove(EURecipeCapability.CAP);
        helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe) && part.isWorking(), "Native controller did not call the grinding-ball start callback"); machine.getRecipeLogic().setSuspendAfterFinish(true);
        // The vanilla mock Player executes the real Player/LivingEntity damage path without
        // a synthetic network login; FakePlayer explicitly disables damage altogether.
        var player = helper.makeMockPlayer(GameType.SURVIVAL); player.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); player.setHealth(100);
        var hit = new BlockHitResult(part.getBlockPos().getCenter(), Direction.NORTH, part.getBlockPos(), false);
        helper.assertTrue(part.onUse(new ExtendedUseOnContext(player, InteractionHand.MAIN_HAND, hit)) == InteractionResult.FAIL && player.getHealth() == 60, "Active native ball hatch lost the original forty-point turbine damage rule");
        var creative = helper.makeMockPlayer(GameType.CREATIVE); creative.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); creative.setHealth(100);
        helper.assertTrue(part.onUse(new ExtendedUseOnContext(creative, InteractionHand.MAIN_HAND, hit)) == InteractionResult.PASS && creative.getHealth() == 100, "Native grinding-ball use changed the creative exemption");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(machine.getRecipeLogic().isSuspend(), "Native grinding-ball controller recipe never finished"))
                .thenExecute(() -> {
                    helper.assertTrue(!part.isWorking() && input.getStackInSlot(0).isEmpty() && output.getStackInSlot(0).is(Items.GOLD_INGOT) && part.getInventory().getStackInSlot(0).getCount() == 3, "Native grinding-ball finish callback or inventory ownership failed");
                    part.beforeWorking(machine); machine.invalidateStructure("main"); helper.assertTrue(!part.isWorking(), "Native detached grinding-ball part retained active working state");
                }).thenSucceed();
    }
    @GameTest(template = "gcym_empty", batch = "gtl_native_multiblocks", timeoutTicks = 200)
    public static void nativeGrindingBallMenuReloadAndDestructionRetainTheOriginalIdleAndActiveDropRules(GameTestHelper helper) {
        var definition = NativeCatalystFixtures.PART;
        for (int index = 0; index < 2; index++) {
            var pos = new BlockPos(4 + index * 6, 4, 4); helper.setBlock(pos, definition.getBlock()); var host = (BallHatchPartMachine) helper.getBlockEntity(pos);
            host.getInventory().storage.setStackInSlot(0, ball()); if (index == 1) host.beforeWorking(null);
            var copy = (BallHatchPartMachine) definition.getBlockEntityType().create(host.getBlockPos(), host.getBlockState()); var saved = host.saveWithoutMetadata(helper.getLevel().registryAccess());
            helper.getLevel().removeBlockEntity(host.getBlockPos()); copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
            helper.assertTrue(copy.isWorking() == (index == 1) && ItemStack.isSameItemSameComponents(ball(), copy.getInventory().getStackInSlot(0)), "Native ball world reload lost its working phase or components");
            var player = FakePlayerFactory.get(helper.getLevel(), new GameProfile(UUID.randomUUID(), "nativeBallMenu")); player.setPos(copy.getBlockPos().getCenter());
            var settings = new brachy.modularui.screen.UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY); var sync = new ModularSyncManager(false); var manager = new PanelSyncManager(sync, true);
            var data = new brachy.modularui.factory.PosGuiData(player, copy.getBlockPos()); var panel = com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory.INSTANCE.createPanel(data, manager, settings);
            brachy.modularui.widget.WidgetTree.collectSyncValues(manager, panel); var menu = new brachy.modularui.screen.ModularContainerMenu(56); menu.construct(player, sync, settings, panel.getName(), data);
            helper.assertTrue(manager.findSyncHandler("grindBallInventory", ItemSlotSyncHandler.class).getSlot().getItem().getCount() == 3, "Native ball menu did not bind the actual saved inventory");
            var dropArea = new AABB(copy.getBlockPos()).inflate(2);
            var existingDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropArea).stream().map(ItemEntity::getUUID).collect(java.util.stream.Collectors.toSet());
            helper.getLevel().destroyBlock(copy.getBlockPos(), true);
            var newDrops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, dropArea).stream().filter(entity -> !existingDrops.contains(entity.getUUID())).toList();
            var ballDrops = newDrops.stream().filter(entity -> entity.getItem().is(NativeCatalystFixtures.BALL.get())).toList();
            helper.assertTrue(ballDrops.stream().mapToInt(entity -> entity.getItem().getCount()).sum() == (index == 0 ? 3 : 0), "Native ball destruction changed idle/active ownership rules or dropped the inventory twice");
            var drops = newDrops.stream().filter(entity -> entity.getItem().is(definition.asStack().getItem())).toList(); helper.assertTrue(drops.size() == 1 && drops.getFirst().getItem().getCount() == 1, "Native ball hatch destruction dropped the wrong number of machines: " + drops);
            var dropped = drops.getFirst().getItem().copy(); drops.getFirst().discard(); var ground = helper.absolutePos(new BlockPos(4 + index * 6, 2, 10)); helper.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState());
            player.closeContainer(); player.setPos(ground.getCenter().add(2, 2, 0)); player.setItemInHand(InteractionHand.MAIN_HAND, dropped); var hit = new BlockHitResult(ground.getCenter().add(0, 0.5, 0), Direction.UP, ground, false);
            helper.assertTrue(((BlockItem) dropped.getItem()).place(new BlockPlaceContext(new UseOnContext(player, InteractionHand.MAIN_HAND, hit))).consumesAction(), "Native dropped ball hatch could not be placed");
            var placed = (BallHatchPartMachine) helper.getLevel().getBlockEntity(ground.above()); helper.assertTrue(placed.getInventory().isEmpty(), "Native ball machine item duplicated an independently dropped inventory");
        }
        helper.succeed();
    }
}
