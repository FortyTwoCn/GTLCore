package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.*;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.*;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.blockentity.storage.DriveBlockEntity;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeAePatternGameTests {
    private record Menu(brachy.modularui.value.sync.ModularSyncManager sync,
                        brachy.modularui.value.sync.PanelSyncManager main,
                        brachy.modularui.screen.ModularContainerMenu container) {}
    private static Menu menu(GameTestHelper helper, MEPatternBufferPartMachine part) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(part.getBlockPos().getCenter());
        var settings = new brachy.modularui.screen.UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var sync = new brachy.modularui.value.sync.ModularSyncManager(false);
        var manager = new brachy.modularui.value.sync.PanelSyncManager(sync, true);
        var data = new brachy.modularui.factory.PosGuiData(player, part.getBlockPos());
        var panel = com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory.INSTANCE.createPanel(data, manager, settings);
        brachy.modularui.widget.WidgetTree.collectSyncValues(manager, panel);
        var container = new brachy.modularui.screen.ModularContainerMenu(45);
        container.construct(player, sync, settings, panel.getName(), data);
        return new Menu(sync, manager, container);
    }
    private static brachy.modularui.value.sync.PanelSyncManager popup(Menu menu, String key, String name) {
        ((brachy.modularui.value.sync.PanelSyncHandler) menu.main.findPanelHandlerNullable(key)).openPanel();
        return menu.sync.getPanelSyncManager(name);
    }
    private static void text(brachy.modularui.value.sync.PanelSyncManager manager,
                             net.minecraft.network.RegistryFriendlyByteBuf packet, String key, String value) {
        packet.clear(); brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, value, Short.MAX_VALUE - 74);
        manager.receiveWidgetUpdate(key + ":0", false, 0, packet);
    }
    private static ItemStack item() { var value = new ItemStack(Items.IRON_INGOT); value.set(DataComponents.CUSTOM_NAME, Component.literal("real buffered input")); return value; }
    private static FluidStack fluid() { var value = new FluidStack(Fluids.WATER, 750); value.set(DataComponents.CUSTOM_NAME, Component.literal("real buffered fluid")); return value; }
    private static ItemStack output() { var value = new ItemStack(Items.GOLD_INGOT, 2); value.set(DataComponents.CUSTOM_NAME, Component.literal("real buffered output")); return value; }
    private static ItemStack encoded() {
        return PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(IntCircuitBehaviour.stack(0)), 1),
                        new GenericStack(AEItemKey.of(item()), 3), new GenericStack(AEFluidKey.of(fluid()), 750)),
                List.of(new GenericStack(AEItemKey.of(output()), 2), new GenericStack(AEItemKey.of(Items.GOLD_NUGGET), 1)));
    }
    private static KeyCounter[] inputs(IPatternDetails pattern) {
        var result = new KeyCounter[pattern.getInputs().length];
        for (int i = 0; i < result.length; i++) {
            result[i] = new KeyCounter(); var input = pattern.getInputs()[i]; var stack = input.getPossibleInputs()[0];
            result[i].add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier()));
        }
        return result;
    }
    private static MEPatternBufferPartMachine place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, NativeAePatternFixtures.BUFFER.getBlock().defaultBlockState());
        var part = (MEPatternBufferPartMachine) helper.getBlockEntity(pos); part.setFrontFacing(Direction.NORTH); return part;
    }
    private static void connect(GameTestHelper helper, MEPatternBufferPartMachine part, boolean cells) {
        var pos = part.getBlockPos();
        helper.getLevel().setBlockAndUpdate(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        if (cells) {
            helper.getLevel().setBlockAndUpdate(pos.north(2), AEBlocks.DRIVE.block().defaultBlockState());
            var drive = (DriveBlockEntity) helper.getLevel().getBlockEntity(pos.north(2));
            drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            drive.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack());
        }
    }
    private static NoEnergyMultiblockMachine build(GameTestHelper helper) {
        return build(helper, NativeAePatternFixtures.CONTROLLER.get());
    }
    private static NoEnergyMultiblockMachine build(GameTestHelper helper, com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition definition) {
        var pattern = (BlockPattern) definition.getStructurePatterns().get("main").get();
        var repetitions = new Int2IntOpenHashMap();
        for (int i = 0; i < pattern.getSlices().length; i++) repetitions.put(i, pattern.getSlices()[i].getMinRepeats());
        var preview = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(repetitions).populate(preview, pattern, null, Direction.NORTH, Direction.UP, false);
        BlockPos controllerPos = null;
        for (var entry : preview.entrySet()) {
            var pos = new BlockPos(4, 4, 4).offset(entry.getKey()); var state = entry.getValue().getBlockState();
            helper.setBlock(pos, state); if (state.is(definition.getBlock())) controllerPos = pos;
        }
        var machine = (NoEnergyMultiblockMachine) helper.getBlockEntity(controllerPos);
        machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed() && machine.getParts().size() == 1, "Native controller did not discover the production pattern buffer");
        return machine;
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 500)
    public static void actualPatternProviderPublishesCircuitFreePatternAndCompletesNativeControllerRecipe(GameTestHelper helper) {
        var controller = build(helper); var part = (MEPatternBufferPartMachine) controller.getParts().getFirst();
        connect(helper, part, true); part.getTerminalPatternInventory().setItemDirect(0, encoded());
        var logic = controller.getRecipeLogic();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline() &&
                part.getGrid().getCraftingService().isCraftable(AEItemKey.of(output())), "Production buffer did not publish its native processing pattern"))
                .thenExecute(() -> {
                    var pattern = part.getAvailablePatterns().getFirst();
                    helper.assertTrue(pattern.getInputs().length == 2 && pattern.getOutputs().size() == 1 &&
                            IntCircuitBehaviour.getCircuitConfiguration(part.getCircuitForRecipe(0)) == 0,
                            "Native pattern reconstruction lost circuit zero or disabled byproduct behavior");
                    helper.assertTrue(((IRecipeCapabilityMachine) controller).getMEPatternRecipeHandleParts().size() == 1 &&
                            part.pushPattern(pattern, inputs(pattern)), "Controller routing or actual AE pattern dispatch rejected the production buffer");
                    var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_pattern_buffer_cycle")
                            .inputItems(item().copyWithCount(3)).inputFluids(fluid()).circuitMeta(0).outputItems(output()).duration(3).build();
                    recipe.tickInputs.remove(com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP);
                    var searchDb = new com.gregtechceu.gtceu.api.recipe.lookup.RecipeDB();
                    var staging = new com.gregtechceu.gtceu.api.recipe.lookup.StagingRecipeDB(); staging.add(recipe); staging.populateDB(searchDb);
                    var search = org.gtlcore.gtlcore.api.recipe.IRecipeIterator.search(recipe.recipeType, searchDb, controller,
                            (IRecipeCapabilityMachine) controller, candidate -> org.gtlcore.gtlcore.api.recipe.RecipeRunnerHelper.matchRecipeInput(controller, candidate));
                    helper.assertTrue(search.hasNext() && search.next().id.equals(recipe.id),
                            "Native database search omitted dispatched component-aware pattern-slot inputs, shared catalysts or circuit zero");
                    helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe) && part.getInternalInventory()[0].getItemInventory().isEmpty() &&
                            part.getInternalInventory()[0].getFluidInventory().isEmpty(), "Native recipe did not atomically consume the dispatched slot inputs");
                    logic.setSuspendAfterFinish(true);
                }).thenWaitUntil(() -> helper.assertTrue(logic.isSuspend(), "Native controller did not finish the buffered recipe"))
                .thenWaitUntil(() -> helper.assertTrue(part.getGrid().getStorageService().getInventory().extract(AEItemKey.of(output()), 3,
                        Actionable.SIMULATE, IActionSource.empty()) == 2 && part.getBuffer().isEmpty(), "Production buffer did not flush its exact output into the native AE drive"))
                .thenIdle(30).thenExecute(() -> helper.assertTrue(part.getGrid().getStorageService().getInventory().extract(AEItemKey.of(output()), 3,
                        Actionable.MODULATE, IActionSource.empty()) == 2, "Buffered recipe delivered duplicate outputs")).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativePatternBufferReloadRestoresLargeInputsCatalystsCircuitAndRecipeIds(GameTestHelper helper) {
        var part = place(helper, new BlockPos(4, 4, 4)); connect(helper, part, false);
        part.getTerminalPatternInventory().setItemDirect(26, encoded());
        long amount = 9_007_199_254_740_993L;
        part.getInternalInventory()[26].add(AEItemKey.of(item()), amount);
        part.getInternalInventory()[26].add(AEFluidKey.of(fluid()), amount);
        part.getSharedCatalystInventory().storage.setStackInSlot(8, new ItemStack(Items.DIAMOND, 7));
        part.getSharedCatalystTank().getStorages()[8].setFluid(fluid());
        part.getSharedCircuitInventory().storage.setStackInSlot(0, IntCircuitBehaviour.stack(17));
        part.getMERecipeHandlerTraits().left().meHandleRecipeOutput(List.of(org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient.create(output(), amount)), false);
        part.setCustomName("world restored pattern buffer");
        var restored = new AtomicReference<MEPatternBufferPartMachine>();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline(), "Production pattern buffer did not become online"))
                .thenExecute(() -> {
                    var recipe = helper.getLevel().getRecipeManager().getAllRecipesFor(GTRecipeTypes.CHEMICAL_RECIPES.get()).getFirst().value();
                    part.getMETrait().setSlotCacheRecipe(26, recipe);
                    var registry = helper.getLevel().registryAccess(); var saved = part.saveWithoutMetadata(registry);
                    var copy = (MEPatternBufferPartMachine) NativeAePatternFixtures.BUFFER.getBlockEntityType().create(part.getBlockPos(), part.getBlockState());
                    helper.getLevel().removeBlockEntity(part.getBlockPos()); copy.loadAdditional(saved, registry); helper.getLevel().setBlockEntity(copy); restored.set(copy);
                }).thenWaitUntil(() -> helper.assertTrue(restored.get().isOnline() && restored.get().getMETrait().hasCacheInSlot(26),
                        "Native saved recipe ID or AE node was not restored"))
                .thenExecute(() -> {
                    var copy = restored.get(); var slot = copy.getInternalInventory()[26];
                    helper.assertTrue(slot.getItemInventory().getLong(AEItemKey.of(item())) == amount &&
                            slot.getFluidInventory().getLong(AEFluidKey.of(fluid())) == amount &&
                            copy.getBuffer().getLong(AEItemKey.of(output())) == amount && slot.getCacheManager().getCircuitCache() == 0,
                            "Native save truncated large buffers, components or circuit zero");
                    helper.assertTrue(copy.getSharedCatalystInventory().getStackInSlot(8).getCount() == 7 &&
                            AEFluidKey.of(copy.getSharedCatalystTank().getFluidInTank(8)).equals(AEFluidKey.of(fluid())) &&
                            IntCircuitBehaviour.getCircuitConfiguration(copy.getSharedCircuitInventory().storage.getStackInSlot(0)) == 17 &&
                            copy.getCustomName().equals("world restored pattern buffer") && copy.getAvailablePatterns().size() == 1,
                            "Native save lost shared catalysts, circuit, name or pattern inventory");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativePatternMenuPreservesRealCatalystSlotsCircuitActionsAndSavedLimits(GameTestHelper helper) {
        var part = place(helper, new BlockPos(4, 4, 4));
        part.getTerminalPatternInventory().setItemDirect(26, encoded());
        var menu = menu(helper, part); var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        helper.assertTrue(menu.container.slots.size() == 63, "Native pattern buffer menu lost pattern or player slots");
        var catalysts = popup(menu, "patternCatalysts26", "patternCatalystsPanel26");
        var slot = catalysts.findSyncHandler("patternCatalystItem", 8, brachy.modularui.value.sync.ItemSlotSyncHandler.class).getSlot();
        helper.assertTrue(!slot.isPhantom() && slot.getSlotIndex() == 8 && menu.container.slots.size() == 72,
                "Native catalyst popup lost consumable slots or attached them to the wrong inventory");
        menu.container.setCarried(new ItemStack(Items.DIAMOND, 7));
        menu.container.clicked(menu.container.slots.indexOf(slot), 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
        var tank = catalysts.findSyncHandler("patternCatalystFluid", 8, brachy.modularui.value.sync.FluidSlotSyncHandler.class);
        helper.assertTrue(!tank.phantom(), "Native catalyst tank became a ghost filter"); tank.setValue(fluid(), true, false);
        var packet = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            text(catalysts, packet, "patternRecipeCacheCount", "999");
            catalysts.findSyncHandler("patternRecipeCacheCount", brachy.modularui.value.sync.StringSyncValue.class).updateCacheFromSource(false);
            helper.assertTrue(catalysts.findSyncHandler("patternRecipeCacheCount", brachy.modularui.value.sync.StringSyncValue.class).getStringValue().equals("127"),
                    "Native cache limit accepted overflow from its C2S field");
            text(catalysts, packet, "patternRecipeCacheCount", "5");
            var circuit = popup(menu, "patternCircuitConfig", "patternCircuitConfigPanel");
            text(circuit, packet, "embeddedPatternCircuit", "999");
            packet.clear(); packet.writeBoolean(false); circuit.receiveWidgetUpdate("skipExistingPatternCircuit:0", false, 0, packet);
            packet.clear(); circuit.receiveWidgetUpdate("applyPatternCircuit", true, 0, packet);
            helper.assertTrue(IntCircuitBehaviour.getCircuitConfiguration(part.getCircuitForRecipe(26)) == 32,
                    "Native apply action failed to replace the existing circuit or bound its range");
            packet.clear(); circuit.receiveWidgetUpdate("clearPatternCircuits", true, 0, packet);
            helper.assertTrue(part.getCircuitForRecipe(26).isEmpty(), "Native clear action retained an obsolete circuit cache");
            var rename = popup(menu, "patternRename", "patternRenamePanel");
            text(rename, packet, "patternCustomName", "native GUI persisted name");
            packet.clear(); packet.writeBoolean(true); menu.main.receiveWidgetUpdate("patternKeepByProducts:0", false, 0, packet);
            packet.clear(); packet.writeBoolean(true); menu.main.receiveWidgetUpdate("patternHiddenTerminal:0", false, 0, packet);
            helper.assertTrue(part.getAvailablePatterns().getFirst().getOutputs().size() == 2 && !part.isVisibleInTerminal(),
                    "Native byproduct or terminal visibility packet did not change the production pattern buffer");
        } finally { packet.release(); }
        var registry = helper.getLevel().registryAccess(); var saved = part.saveWithoutMetadata(registry);
        var copy = (MEPatternBufferPartMachine) NativeAePatternFixtures.BUFFER.getBlockEntityType().create(part.getBlockPos(), part.getBlockState());
        helper.getLevel().removeBlockEntity(part.getBlockPos()); copy.loadAdditional(saved, registry); helper.getLevel().setBlockEntity(copy);
        helper.startSequence().thenIdle(3).thenExecute(() -> {
            var restored = menu(helper, copy);
            var panel = popup(restored, "patternCatalysts26", "patternCatalystsPanel26");
            panel.findSyncHandler("patternCatalystFluid", 8, brachy.modularui.value.sync.FluidSlotSyncHandler.class).updateCacheFromSource(true);
            helper.assertTrue(panel.findSyncHandler("patternRecipeCacheCount", brachy.modularui.value.sync.StringSyncValue.class).getStringValue().equals("5") &&
                            panel.findSyncHandler("patternCatalystItem", 8, brachy.modularui.value.sync.ItemSlotSyncHandler.class).getSlot().getItem().getCount() == 7 &&
                            AEFluidKey.of(fluid()).equals(AEFluidKey.of(panel.findSyncHandler("patternCatalystFluid", 8, brachy.modularui.value.sync.FluidSlotSyncHandler.class).getValue())) &&
                            copy.getCustomName().equals("native GUI persisted name") && !copy.isVisibleInTerminal(),
                    "Native save lost GUI-edited catalyst, cache limit, name or terminal visibility");
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeProxyRebindingAndReloadRetainTargetWithoutDuplicatedSubscriptions(GameTestHelper helper) {
        var first = place(helper, new BlockPos(4, 4, 4)); var second = place(helper, new BlockPos(7, 4, 4));
        var pos = new BlockPos(4, 4, 7); helper.setBlock(pos, NativeAePatternFixtures.PROXY.getBlock().defaultBlockState());
        var proxy = (MEPatternBufferProxyPartMachine) helper.getBlockEntity(pos);
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var stick = com.gregtechceu.gtceu.common.data.GTItems.TOOL_DATA_STICK.asStack();
        first.onDataStickUse(player, stick); proxy.onDataStickUse(player, stick);
        helper.assertTrue(first.getBlockPos().equals(stick.get(com.gregtechceu.gtceu.common.data.item.GTDataComponents.DATA_COPY_POS)) &&
                proxy.getBuffer() == first && first.getProxies().contains(proxy), "Native data-stick components failed to bind the production proxy");
        var changes = new AtomicInteger();
        var listener = proxy.getMERecipeHandlerTraits().left().addChangedListener(changes::incrementAndGet);
        changes.set(0); first.getInternalInventory()[0].getOnContentsChanged().run();
        helper.assertTrue(changes.get() == 1, "Initial native proxy binding duplicated its delegated listener");
        second.onDataStickUse(player, stick); proxy.onDataStickUse(player, stick);
        changes.set(0); first.getInternalInventory()[0].getOnContentsChanged().run();
        helper.assertTrue(changes.get() == 0 && !first.getProxies().contains(proxy), "Rebound proxy retained the old buffer's listener or membership");
        second.getInternalInventory()[0].getOnContentsChanged().run();
        helper.assertTrue(changes.get() == 1 && proxy.getBuffer() == second && second.getProxies().contains(proxy), "Native proxy rebind lost its new handler");
        listener.unsubscribe();
        var registry = helper.getLevel().registryAccess(); var saved = proxy.saveWithoutMetadata(registry);
        var copy = (MEPatternBufferProxyPartMachine) NativeAePatternFixtures.PROXY.getBlockEntityType().create(proxy.getBlockPos(), proxy.getBlockState());
        helper.getLevel().removeBlockEntity(proxy.getBlockPos()); copy.loadAdditional(saved, registry); helper.getLevel().setBlockEntity(copy);
        changes.set(0); var restoredListener = copy.getMERecipeHandlerTraits().left().addChangedListener(changes::incrementAndGet);
        helper.startSequence().thenIdle(3).thenExecute(() -> {
            helper.assertTrue(copy.getBuffer() == second && copy.getBufferPos().equals(second.getBlockPos()) &&
                    second.getProxies().contains(copy) && !second.getProxies().contains(proxy), "Native proxy reload lost its persistent target or kept a removed runtime instance");
            changes.set(0); second.getInternalInventory()[0].getOnContentsChanged().run();
            helper.assertTrue(changes.get() == 1, "Native proxy reload registered duplicate subscriptions"); restoredListener.unsubscribe();
        }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeClipboardBehaviorsTransferInputsExactlyOnceAndRejectOccupiedCatalysts(GameTestHelper helper) {
        var source = place(helper, new BlockPos(4, 4, 4)); var target = place(helper, new BlockPos(7, 4, 4));
        var copied = place(helper, new BlockPos(10, 4, 4));
        source.getTerminalPatternInventory().setItemDirect(0, encoded()); source.getTerminalPatternInventory().setItemDirect(26, encoded());
        long amount = 9_007_199_254_740_993L;
        source.getInternalInventory()[26].add(AEItemKey.of(item()), amount); source.getInternalInventory()[26].add(AEFluidKey.of(fluid()), amount);
        source.getSharedCatalystInventory().storage.setStackInSlot(8, new ItemStack(Items.DIAMOND, 7));
        source.getSharedCatalystTank().getStorages()[8].setFluid(fluid()); source.setCustomName("native portable buffer");
        var proxyPos = new BlockPos(4, 4, 7); helper.setBlock(proxyPos, NativeAePatternFixtures.PROXY.getBlock().defaultBlockState());
        var proxy = (MEPatternBufferProxyPartMachine) helper.getBlockEntity(proxyPos); proxy.setBuffer(source.getBlockPos());
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.getInventory().clearContent();
        var copyTool = new ItemStack(Items.PAPER); var cutTool = new ItemStack(Items.PAPER);
        java.util.function.Function<MEPatternBufferPartMachine, net.minecraft.world.item.context.UseOnContext> context = machine ->
                new net.minecraft.world.item.context.UseOnContext(player, net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.phys.BlockHitResult(machine.getBlockPos().getCenter(), Direction.NORTH, machine.getBlockPos(), false));
        player.setShiftKeyDown(true);
        org.gtlcore.gtlcore.common.item.MEPatternBufferCopyBehavior.INSTANCE.onItemUseFirst(copyTool, context.apply(source));
        helper.assertTrue(copyTool.has(DataComponents.CUSTOM_DATA) && copyTool.get(DataComponents.CUSTOM_DATA).copyTag().getCompound("tag").getList("patterns", 10).size() == 2,
                "Native copy behavior lost component data or duplicate physical pattern slots");
        player.setShiftKeyDown(false); player.getInventory().setItem(0, AEItems.BLANK_PATTERN.stack(3));
        org.gtlcore.gtlcore.common.item.MEPatternBufferCopyBehavior.INSTANCE.onItemUseFirst(copyTool, context.apply(copied));
        helper.assertTrue(!copied.getTerminalPatternInventory().getStackInSlot(0).isEmpty() && !copied.getTerminalPatternInventory().getStackInSlot(1).isEmpty() &&
                player.getInventory().getItem(0).getCount() == 1 && source.getInternalInventory()[26].getItemInventory().getLong(AEItemKey.of(item())) == amount,
                "Native template copy consumed source contents or charged the wrong blank-pattern count");
        player.setShiftKeyDown(true); org.gtlcore.gtlcore.common.item.MEPatternBufferCutBehavior.INSTANCE.onItemUseFirst(cutTool, context.apply(source));
        helper.assertTrue(org.gtlcore.gtlcore.common.item.MEPatternBufferCutBehavior.hasCutData(cutTool) && source.getInternalInventory()[26].getItemInventory().isEmpty() &&
                source.getTerminalPatternInventory().getStackInSlot(26).isEmpty() && source.getSharedCatalystInventory().isEmpty() && proxy.getBuffer() == null,
                "Native cut failed to detach its owned inputs, pattern, catalysts or proxy");
        target.getSharedCatalystInventory().storage.setStackInSlot(0, new ItemStack(Items.EMERALD, 3)); player.setShiftKeyDown(false);
        org.gtlcore.gtlcore.common.item.MEPatternBufferCutBehavior.INSTANCE.onItemUseFirst(cutTool, context.apply(target));
        helper.assertTrue(org.gtlcore.gtlcore.common.item.MEPatternBufferCutBehavior.hasCutData(cutTool) &&
                target.getSharedCatalystInventory().storage.getStackInSlot(0).getCount() == 3 && target.getTerminalPatternInventory().getStackInSlot(0).isEmpty(),
                "Rejected native paste consumed the cut or overwrote existing catalysts");
        target.getSharedCatalystInventory().storage.setStackInSlot(0, ItemStack.EMPTY);
        org.gtlcore.gtlcore.common.item.MEPatternBufferCutBehavior.INSTANCE.onItemUseFirst(cutTool, context.apply(target));
        helper.assertTrue(!org.gtlcore.gtlcore.common.item.MEPatternBufferCutBehavior.hasCutData(cutTool) &&
                target.getInternalInventory()[1].getItemInventory().getLong(AEItemKey.of(item())) == amount &&
                target.getInternalInventory()[1].getFluidInventory().getLong(AEFluidKey.of(fluid())) == amount &&
                target.getSharedCatalystInventory().storage.getStackInSlot(8).getCount() == 7 &&
                AEFluidKey.of(target.getSharedCatalystTank().getFluidInTank(8)).equals(AEFluidKey.of(fluid())) && proxy.getBuffer() == target,
                "Native paste lost long inputs, component-bearing catalysts or proxy binding");
        org.gtlcore.gtlcore.common.item.MEPatternBufferCutBehavior.INSTANCE.onItemUseFirst(cutTool, context.apply(target));
        helper.assertTrue(target.getInternalInventory()[1].getItemInventory().getLong(AEItemKey.of(item())) == amount &&
                target.getTerminalPatternInventory().getStackInSlot(2).isEmpty(), "Native cut tool replay duplicated transferred materials");
        player.setShiftKeyDown(false); helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 500)
    public static void nativeStockingBufferUsesConfiguredComponentKeysAndReturnsControllerOutputToRealCells(GameTestHelper helper) {
        var controller = build(helper, NativeAePatternFixtures.STOCKING_CONTROLLER.get());
        var part = (MEStockingPatternBufferPartMachine) controller.getParts().getFirst();
        connect(helper, part, true);
        part.getTerminalPatternInventory().setItemDirect(26, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(IntCircuitBehaviour.stack(0)), 1), new GenericStack(AEItemKey.of(item()), 1)),
                List.of(new GenericStack(AEItemKey.of(output()), 2))));
        part.configureStock(0, new GenericStack(AEItemKey.of(item()), 1));
        part.configureStock(1, new GenericStack(AEFluidKey.of(fluid()), 1000));
        var logic = controller.getRecipeLogic();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline(), "Native stocking buffer has no real powered AE network"))
                .thenExecute(() -> {
                    var storage = part.getGrid().getStorageService().getInventory();
                    helper.assertTrue(storage.insert(AEItemKey.of(item()), 2, Actionable.MODULATE, IActionSource.empty()) == 2 &&
                            storage.insert(AEFluidKey.of(fluid()), 750, Actionable.MODULATE, IActionSource.empty()) == 750,
                            "Native stocking fixture failed to populate component-bearing keys in real AE cells");
                    var pattern = part.getAvailablePatterns().getFirst();
                    helper.assertTrue(part.pushPattern(pattern, inputs(pattern)), "Native stocking buffer rejected its actual AE dispatch");
                    var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_stocking_pattern_buffer_cycle")
                            .inputItems(item().copyWithCount(3)).inputFluids(fluid()).circuitMeta(0).outputItems(output()).duration(3).build();
                    recipe.tickInputs.remove(com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP);
                    helper.assertTrue(logic.checkMatchedRecipeAvailable(recipe), "Native controller failed to draw configured inputs through its real stocking buffer");
                    helper.assertTrue(storage.extract(AEItemKey.of(item()), 3, Actionable.SIMULATE, IActionSource.empty()) == 0 &&
                            storage.extract(AEFluidKey.of(fluid()), 750, Actionable.SIMULATE, IActionSource.empty()) == 0,
                            "Native stocking input was acknowledged without consuming the real AE inventory");
                    logic.setSuspendAfterFinish(true);
                }).thenWaitUntil(() -> helper.assertTrue(logic.isSuspend(), "Native stocking controller did not complete its recipe"))
                .thenWaitUntil(() -> helper.assertTrue(part.getBuffer().isEmpty() && part.getGrid().getStorageService().getInventory()
                        .extract(AEItemKey.of(output()), 3, Actionable.SIMULATE, IActionSource.empty()) == 2,
                        "Native stocking buffer lost or duplicated component-bearing controller outputs"))
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeStockingOverlappingDemandsAndDuplicateConfigsNeverReuseRealNetworkStock(GameTestHelper helper) {
        var pos = new BlockPos(4, 4, 4); helper.setBlock(pos, NativeAePatternFixtures.STOCKING.getBlock().defaultBlockState());
        var part = (MEStockingPatternBufferPartMachine) helper.getBlockEntity(pos); part.setFrontFacing(Direction.NORTH); connect(helper, part, true);
        part.getTerminalPatternInventory().setItemDirect(0, encoded()); part.setMEUpdateInterval(1);
        part.configureStock(0, new GenericStack(AEItemKey.of(item()), 1)); part.configureStock(31, new GenericStack(AEItemKey.of(item()), 1));
        part.configureStock(1, new GenericStack(AEFluidKey.of(fluid()), 1000));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline(), "Native stock reservation test has no real AE network"))
                .thenExecute(() -> {
                    var storage = part.getGrid().getStorageService().getInventory();
                    storage.insert(AEItemKey.of(item()), 10, Actionable.MODULATE, IActionSource.empty());
                    storage.insert(AEFluidKey.of(fluid()), 10, Actionable.MODULATE, IActionSource.empty());
                    var slot = part.getInternalInventory()[0]; slot.add(AEItemKey.of(item()), 1); slot.add(AEFluidKey.of(fluid()), 1);
                }).thenIdle(3).thenExecute(() -> {
                    var storage = part.getGrid().getStorageService().getInventory(); var slot = part.getInternalInventory()[0];
                    helper.assertTrue(part.getStockingItemMap().values().iterator().nextLong() == 10, "Duplicate configuration counted the same AE inventory twice");
                    var items = new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<net.neoforged.neoforge.common.crafting.SizedIngredient>();
                    items.put(new net.neoforged.neoforge.common.crafting.SizedIngredient(net.minecraft.world.item.crafting.Ingredient.of(Items.IRON_INGOT), 8), 8);
                    items.put(org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient.create(item(), 4), 4);
                    var fluids = new it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient>();
                    fluids.put(new net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient(net.neoforged.neoforge.fluids.crafting.FluidIngredient.single(Fluids.WATER), 8), 8);
                    fluids.put(org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient.create(fluid(), 4), 4);
                    helper.assertTrue(!slot.handleItemInternal(items, -1, true) && !slot.handleItemInternal(items, -1, false) &&
                            !slot.handleFluidInternal(fluids, true) && !slot.handleFluidInternal(fluids, false) && items.size() == 2 && fluids.size() == 2 &&
                            slot.getItemInventory().getLong(AEItemKey.of(item())) == 1 && slot.getFluidInventory().getLong(AEFluidKey.of(fluid())) == 1 &&
                            storage.extract(AEItemKey.of(item()), 11, Actionable.SIMULATE, IActionSource.empty()) == 10 &&
                            storage.extract(AEFluidKey.of(fluid()), 11, Actionable.SIMULATE, IActionSource.empty()) == 10,
                            "Rejected native stocking recipe consumed inputs or reused stock across overlapping predicates");
                    storage.insert(AEItemKey.of(item()), 1, Actionable.MODULATE, IActionSource.empty());
                    storage.insert(AEFluidKey.of(fluid()), 1, Actionable.MODULATE, IActionSource.empty());
                    helper.assertTrue(slot.handleItemInternal(items, -1, false) && slot.handleFluidInternal(fluids, false) &&
                            slot.getItemInventory().isEmpty() && slot.getFluidInventory().isEmpty() && items.isEmpty() && fluids.isEmpty() &&
                            storage.extract(AEItemKey.of(item()), 1, Actionable.SIMULATE, IActionSource.empty()) == 0 &&
                            storage.extract(AEFluidKey.of(fluid()), 1, Actionable.SIMULATE, IActionSource.empty()) == 0,
                            "Native stocking reservation lost or duplicated successfully consumed input");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeStockingConfigActionsKeepThirtyTwoSharedPositionsAndSurviveWorldReload(GameTestHelper helper) {
        var pos = new BlockPos(4, 4, 4); helper.setBlock(pos, NativeAePatternFixtures.STOCKING.getBlock().defaultBlockState());
        var part = (MEStockingPatternBufferPartMachine) helper.getBlockEntity(pos); var menu = menu(helper, part);
        var items = popup(menu, "patternStockItems", "patternStockItemsPanel");
        var fluids = popup(menu, "patternStockFluids", "patternStockFluidsPanel");
        var interval = popup(menu, "patternStockInterval", "patternStockIntervalPanel");
        var packet = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            packet.writeVarInt(31); packet.writeBoolean(false); ItemStack.STREAM_CODEC.encode(packet, item());
            items.receiveWidgetUpdate("ae_config_set_ghost", true, 0, packet);
            helper.assertTrue(part.getConfiguredStock(31).what().equals(AEItemKey.of(item())), "Native stocking ghost action lost item components or last configuration position");
            packet.clear(); packet.writeVarInt(31); packet.writeBoolean(true); FluidStack.STREAM_CODEC.encode(packet, fluid());
            fluids.receiveWidgetUpdate("ae_config_set_ghost", true, 0, packet);
            helper.assertTrue(part.getConfiguredStock(31).what().equals(AEFluidKey.of(fluid())), "Native stocking fluid action failed to replace the same shared position");
            packet.clear(); packet.writeVarInt(0); packet.writeBoolean(false); ItemStack.STREAM_CODEC.encode(packet, item());
            items.receiveWidgetUpdate("ae_config_set_ghost", true, 0, packet);
            packet.clear(); packet.writeVarInt(32); items.receiveWidgetUpdate("ae_config_clear", true, 0, packet);
            text(interval, packet, "stockUpdateInterval", "7");
            helper.assertTrue(part.getConfiguredStock(32) == null && part.getConfiguredStock(31).what().equals(AEFluidKey.of(fluid())) &&
                    part.getMEUpdateInterval() == 7 && menu.container.slots.size() == 63,
                    "Native stock configuration accepted an invalid slot or exposed real consumable ghost slots");
        } finally { packet.release(); }
        var registry = helper.getLevel().registryAccess(); var saved = part.saveWithoutMetadata(registry);
        var copy = (MEStockingPatternBufferPartMachine) NativeAePatternFixtures.STOCKING.getBlockEntityType().create(part.getBlockPos(), part.getBlockState());
        helper.getLevel().removeBlockEntity(part.getBlockPos()); copy.loadAdditional(saved, registry); helper.getLevel().setBlockEntity(copy);
        helper.startSequence().thenIdle(3).thenExecute(() -> helper.assertTrue(copy.getMEUpdateInterval() == 7 &&
                copy.getConfiguredStock(0) != null && copy.getConfiguredStock(0).what().equals(AEItemKey.of(item())) &&
                copy.getConfiguredStock(31) != null && copy.getConfiguredStock(31).what().equals(AEFluidKey.of(fluid())),
                "Native world reload lost stock configuration component keys or update interval")).thenSucceed();
    }
}
