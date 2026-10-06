package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.common.machine.multiblock.part.*;
import com.gtladd.gtladditions.integration.ae2.*;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.*;
import com.gregtechceu.gtceu.api.machine.*;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.common.data.item.GTDataComponents;
import com.gregtechceu.gtceu.common.item.behavior.DataItemBehavior;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import appeng.api.crafting.*;
import appeng.api.stacks.*;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.core.definitions.*;
import appeng.blockentity.storage.DriveBlockEntity;
import brachy.modularui.value.sync.*;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import java.util.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeSuperPatternGameTests {
    private static ItemStack item() { var value = new ItemStack(Items.COPPER_INGOT); value.set(DataComponents.CUSTOM_NAME, Component.literal("native super pattern input")); return value; }
    private static ItemStack output() { var value = new ItemStack(Items.GOLD_INGOT); value.set(DataComponents.CUSTOM_NAME, Component.literal("native FOA output")); return value; }
    private static ItemStack encoded() { return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(IntCircuitBehaviour.stack(0)), 1), new GenericStack(AEItemKey.of(item()), 1)), List.of(new GenericStack(AEItemKey.of(output()), 1))); }
    private static KeyCounter[] inputs(IPatternDetails pattern) {
        var result = new KeyCounter[pattern.getInputs().length];
        for (int index = 0; index < result.length; index++) { var input = pattern.getInputs()[index]; var stack = input.getPossibleInputs()[0]; result[index] = new KeyCounter(); result[index].add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier())); }
        return result;
    }
    private static NoEnergyMultiblockMachine build(GameTestHelper helper, MultiblockMachineDefinition definition) {
        var pattern = (BlockPattern) definition.getStructurePatterns().get("main").get(); var repeats = new it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap();
        for (int index = 0; index < pattern.getSlices().length; index++) repeats.put(index, pattern.getSlices()[index].getMinRepeats());
        var preview = new LinkedHashMap<BlockPos, BlockInfo>(); AbstractStructureHelper.blockPattern(repeats).populate(preview, pattern, null, Direction.NORTH, Direction.UP, false); BlockPos controller = null;
        for (var entry : preview.entrySet()) { var pos = new BlockPos(4, 4, 4).offset(entry.getKey()); helper.setBlock(pos, entry.getValue().getBlockState()); if (entry.getValue().getBlockState().is(definition.getBlock())) controller = pos; }
        var machine = (NoEnergyMultiblockMachine) helper.getBlockEntity(controller); machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure(); helper.assertTrue(machine.isFormed(), "Native super pattern controller did not form"); return machine;
    }
    private static MESuperPatternBufferPartMachine place(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, NativeSuperPatternFixtures.BUFFER.getBlock()); var part = (MESuperPatternBufferPartMachine) helper.getBlockEntity(pos); part.setFrontFacing(Direction.NORTH); return part;
    }
    private static void connect(GameTestHelper helper, MESuperPatternBufferPartMachine part, boolean cells) {
        helper.getLevel().setBlockAndUpdate(part.getBlockPos().north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        if (cells) { helper.getLevel().setBlockAndUpdate(part.getBlockPos().north(2), AEBlocks.DRIVE.block().defaultBlockState()); var drive = (DriveBlockEntity) helper.getLevel().getBlockEntity(part.getBlockPos().north(2)); drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack()); }
    }
    private record Menu(ModularSyncManager sync, PanelSyncManager main, brachy.modularui.screen.ModularContainerMenu container) {}
    private static Menu menu(GameTestHelper helper, MetaMachine host) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(host.getBlockPos().getCenter()); var settings = new brachy.modularui.screen.UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var sync = new ModularSyncManager(false); var manager = new PanelSyncManager(sync, true); var data = new brachy.modularui.factory.PosGuiData(player, host.getBlockPos());
        var panel = com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory.INSTANCE.createPanel(data, manager, settings); brachy.modularui.widget.WidgetTree.collectSyncValues(manager, panel);
        var container = new brachy.modularui.screen.ModularContainerMenu(65); container.construct(player, sync, settings, panel.getName(), data); return new Menu(sync, manager, container);
    }
    private static void text(PanelSyncManager manager, RegistryFriendlyByteBuf packet, String value) {
        packet.clear(); brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, value, Short.MAX_VALUE - 74); manager.receiveWidgetUpdate("superPatternFOAMultiplier:0", false, 0, packet);
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 400)
    public static void nativeSuperPatternPublishesFOAAndCompletesRealControllerRecipeIntoAECells(GameTestHelper helper) {
        var controller = build(helper, NativeSuperPatternFixtures.CONTROLLER.get()); var part = (MESuperPatternBufferPartMachine) controller.getParts().getFirst(); connect(helper, part, true); part.setFOAModeEnabled(true); part.getTerminalPatternInventory().setItemDirect(161, encoded());
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline() && !part.getAvailablePatterns().isEmpty() && part.getGrid().getCraftingService().isCraftable(AEItemKey.of(output())), "Native super provider did not publish its FOA pattern"))
                .thenExecute(() -> {
                    var pattern = part.getAvailablePatterns().getFirst(); helper.assertTrue(pattern.getOutputs().getFirst().amount() == 15 && pattern.getInputs().length == 1 && IntCircuitBehaviour.getCircuitConfiguration(part.getCircuitForRecipe(161)) == 0, "FOA publication lost its multiplier, reusable circuit or final slot");
                    helper.assertTrue(part.pushPattern(pattern, inputs(pattern)), "Native FOA dispatch failed");
                    var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_super_pattern_recipe").inputItems(item()).circuitMeta(0).outputItems(output().copyWithCount(15)).duration(3).build(); recipe.tickInputs.remove(EURecipeCapability.CAP);
                    helper.assertTrue(controller.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Real controller could not consume native FOA dispatch"); controller.getRecipeLogic().setSuspendAfterFinish(true);
                }).thenWaitUntil(() -> helper.assertTrue(controller.getRecipeLogic().isSuspend() && part.getGrid().getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(output())) == 15, "Native FOA recipe did not deliver exactly fifteen output items"))
                .thenIdle(12).thenExecute(() -> helper.assertTrue(part.getInternalInventory()[161].getItemInventory().isEmpty() && part.getBuffer().isEmpty() && part.getGrid().getStorageService().getInventory().getAvailableStacks().get(AEItemKey.of(output())) == 15, "Native FOA recipe duplicated output or retained consumed input")).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeSuperPatternMenuPacketsAndWorldReloadRetainSettingsAndAll162Slots(GameTestHelper helper) {
        var part = place(helper, new BlockPos(4, 4, 4)); connect(helper, part, false); part.getTerminalPatternInventory().setItemDirect(161, encoded());
        var menu = menu(helper, part); helper.assertTrue(menu.main.findSyncHandler("molecularPatternSlot", 161, ItemSlotSyncHandler.class).getSlot().getItem().getItem() instanceof appeng.crafting.pattern.EncodedPatternItem, "Native super menu lost its final encoded pattern");
        var packet = new RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), helper.getLevel().registryAccess());
        try {
            packet.writeVarInt(2); menu.main.receiveWidgetUpdate("molecularPageIndex:0", false, 0, packet); helper.assertTrue(menu.main.findSyncHandler("molecularPatternSlot", 161, ItemSlotSyncHandler.class).getSlot().isActive(), "Native super page packet did not enable its final actual slot");
            ((PanelSyncHandler) menu.main.findPanelHandlerNullable("superPatternFOAConfig")).openPanel(); var popup = menu.sync.getPanelSyncManager("superPatternFOAConfigPanel");
            packet.clear(); packet.writeBoolean(true); popup.receiveWidgetUpdate("superPatternFOAMode:0", false, 0, packet); text(popup, packet, "999"); helper.assertTrue(part.isFOAModeEnabled() && part.getFOAPatternOutputMultiplier() == 30, "Native FOA menu did not clamp or apply its C2S settings");
            text(popup, packet, "malformed"); helper.assertTrue(part.getFOAPatternOutputMultiplier() == 30, "Malformed native FOA text changed the authoritative multiplier"); text(popup, packet, "-1"); helper.assertTrue(part.getFOAPatternOutputMultiplier() == 1, "Negative native FOA input was not bounded"); text(popup, packet, "17");
        } finally { packet.release(); }
        var key = AEItemKey.of(item()); part.getInternalInventory()[161].add(key, 9_007_199_254_740_993L);
        var saved = part.saveWithoutMetadata(helper.getLevel().registryAccess()); var copy = (MESuperPatternBufferPartMachine) NativeSuperPatternFixtures.BUFFER.getBlockEntityType().create(part.getBlockPos(), part.getBlockState()); helper.getLevel().removeBlockEntity(part.getBlockPos()); copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(copy.isOnline() && copy.getAvailablePatterns().size() == 1, "Reloaded native super provider did not rebuild its pattern"))
                .thenExecute(() -> {
                    helper.assertTrue(copy.isFOAModeEnabled() && copy.getFOAPatternOutputMultiplier() == 17 && copy.getAvailablePatterns().getFirst().getOutputs().getFirst().amount() == 17, "Native super reload lost FOA mode/multiplier or republished stale quantities");
                    helper.assertTrue(copy.getInternalInventory()[161].getItemInventory().getLong(key) == 9_007_199_254_740_993L, "Native super reload lost exact pending input ownership");
                    var restored = menu(helper, copy); ((PanelSyncHandler) restored.main.findPanelHandlerNullable("superPatternFOAConfig")).openPanel(); var popup = restored.sync.getPanelSyncManager("superPatternFOAConfigPanel"); helper.assertTrue(popup.findSyncHandler("superPatternFOAMode", BooleanSyncValue.class).getBoolValue() && popup.findSyncHandler("superPatternFOAMultiplier", StringSyncValue.class).getStringValue().equals("17"), "Reloaded native FOA popup did not reflect saved authoritative settings");
                }).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 400)
    public static void nativeSuperProxyDataStickBindingAndControllerRecipeSurviveRebindingAndEntityReload(GameTestHelper helper) {
        var controller = build(helper, NativeSuperPatternFixtures.PROXY_CONTROLLER.get()); var proxy = (MESuperPatternBufferProxyPartMachine) controller.getParts().getFirst(); var part = place(helper, new BlockPos(10, 4, 4)); connect(helper, part, false); part.setFOAModeEnabled(true); part.setFOAPatternOutputMultiplier(3); part.getTerminalPatternInventory().setItemDirect(0, encoded());
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(part.getBlockPos().getCenter()); var stick = GTItems.TOOL_DATA_STICK.asStack(); CustomData.update(DataComponents.CUSTOM_DATA, stick, tag -> tag.putString("other", "retained")); player.setItemInHand(InteractionHand.OFF_HAND, stick);
        var sourceHit = new BlockHitResult(part.getBlockPos().getCenter(), Direction.NORTH, part.getBlockPos(), false); var targetHit = new BlockHitResult(proxy.getBlockPos().getCenter(), Direction.NORTH, proxy.getBlockPos(), false);
        helper.assertTrue(DataItemBehavior.INSTANCE.onItemUseFirst(stick, new UseOnContext(player, InteractionHand.OFF_HAND, sourceHit)).consumesAction() && !stick.has(GTDataComponents.DATA_COPY_POS), "Native offhand data-stick entrance did not create an independent super binding");
        helper.assertTrue(DataItemBehavior.INSTANCE.onItemUseFirst(stick, new UseOnContext(player, InteractionHand.OFF_HAND, targetHit)).consumesAction() && proxy.getBuffer() == part && part.getProxies().contains(proxy), "Native super proxy did not bind through the actual data-stick behavior");
        var ordinaryPos = new BlockPos(3, 4, 10); helper.setBlock(ordinaryPos, NativeAePatternFixtures.BUFFER.getBlock()); var ordinary = (MEPatternBufferPartMachine) helper.getBlockEntity(ordinaryPos); ordinary.onDataStickUse(player, stick); helper.assertTrue(SuperPatternDataStick.read(stick, helper.getLevel().dimension().location()) == null && proxy.onDataStickUse(player, stick) == InteractionResult.PASS && proxy.getBuffer() == part, "Ordinary/super native bindings became mixed");
        part.onDataStickUse(player, stick); SuperPatternDataStick.write(stick, part.getBlockPos(), Level.NETHER.location()); helper.assertTrue(proxy.onDataStickUse(player, stick) == InteractionResult.PASS && proxy.getBuffer() == part, "Native super proxy accepted a cross-dimension binding"); part.onDataStickUse(player, stick);
        var tooltips = new ArrayList<Component>(); GTItems.TOOL_DATA_STICK.get().appendHoverText(stick, Item.TooltipContext.of(helper.getLevel()), tooltips, TooltipFlag.NORMAL); helper.assertTrue(tooltips.stream().anyMatch(component -> component.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents translated && translated.getKey().equals("gtceu.tooltip.super_proxy_bind")), "Native data-stick tooltip lost super proxy coordinates");
        var saved = proxy.saveWithoutMetadata(helper.getLevel().registryAccess()); var copy = (MESuperPatternBufferProxyPartMachine) NativeSuperPatternFixtures.PROXY.getBlockEntityType().create(proxy.getBlockPos(), proxy.getBlockState()); controller.invalidateStructure("main"); helper.getLevel().removeBlockEntity(proxy.getBlockPos()); copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline() && !part.getAvailablePatterns().isEmpty() && copy.getBuffer() == part, "Native super proxy did not restore its source binding"))
                .thenExecute(() -> {
                    controller.checkAndFormStructure(); helper.assertTrue(controller.isFormed() && controller.getParts().contains(copy), "Native super proxy replacement did not re-form the real controller");
                    helper.assertTrue(menu(helper, copy).main.findSyncHandler("molecularPatternSlot", 161, ItemSlotSyncHandler.class) != null, "Native super proxy did not delegate its source menu");
                    var pattern = part.getAvailablePatterns().getFirst(); helper.assertTrue(part.pushPattern(pattern, inputs(pattern)), "Native super proxy source rejected processing inputs");
                    var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_super_proxy_cycle").inputItems(item()).circuitMeta(0).outputItems(output().copyWithCount(3)).duration(3).build(); recipe.tickInputs.remove(EURecipeCapability.CAP);
                    helper.assertTrue(!((org.gtlcore.gtlcore.api.machine.trait.IRecipeCapabilityMachine) controller).getMEPatternRecipeHandleParts().isEmpty(), "Native controller lost its proxy pattern-handler registration");
                    helper.assertTrue(copy.getMERecipeHandlerTraits().left().getActiveSlots().length == 1 && part.getInternalInventory()[0].getItemInventory().getLong(AEItemKey.of(item())) == 1, "Native proxy did not see its source active slot and owned dispatch");
                    helper.assertTrue(IntCircuitBehaviour.getCircuitConfiguration(part.getCircuitForRecipe(0)) == 0, "Native proxy source lost its reusable circuit");
                    helper.assertTrue(controller.getCapabilitiesProxy().isEmpty() && controller.hasCapabilityProxies(), "Native capability precheck ignored the registered slot-aware proxy handlers");
                    var matched = com.gregtechceu.gtceu.api.recipe.RecipeHelper.matchContents(controller, recipe);
                    helper.assertTrue(matched.isSuccess(), "Native RecipeHelper rejected the complete proxy recipe: " + matched.reason().getString());
                    helper.assertTrue(controller.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Native super proxy did not forward the real sized recipe demands"); controller.getRecipeLogic().setSuspendAfterFinish(true);
                }).thenWaitUntil(() -> helper.assertTrue(controller.getRecipeLogic().isSuspend() && part.getBuffer().getLong(AEItemKey.of(output())) == 3, "Native super proxy controller did not return its exact output to the source"))
                .thenExecute(() -> helper.assertTrue(part.getInternalInventory()[0].getItemInventory().isEmpty() && stick.get(DataComponents.CUSTOM_DATA).copyTag().getString("other").equals("retained"), "Native super proxy lost input ownership or unrelated data-stick components")).thenSucceed();
    }
    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeFOASpecialRulesUseActualRecipeHoldersAndFreshComponentFluidSignatures(GameTestHelper helper) {
        var manager = helper.getLevel().getRecipeManager(); var previous = List.copyOf(manager.getRecipes());
        var paths = List.of("stellar_forge/contained_exotic_matter", "stellar_forge/extremely_durable_plasma_cell", "stellar_forge/contained_kerr_newmann_singularity"); var items = List.of(Items.COPPER_INGOT, Items.DIAMOND, Items.IRON_INGOT);
        try {
            var recipes = new ArrayList<RecipeHolder<?>>(); var patterns = new ArrayList<AEProcessingPattern>();
            for (int index = 0; index < paths.size(); index++) {
                var fluid = new FluidStack(Fluids.WATER, 100); fluid.set(DataComponents.CUSTOM_NAME, Component.literal("native FOA rule " + index));
                var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_foa_rule_" + index).inputItems(items.get(index)).inputFluids(fluid).outputItems(Items.GOLD_INGOT).duration(3).build(); recipes.add(new RecipeHolder<>(ResourceLocation.fromNamespaceAndPath("kubejs", paths.get(index)), recipe));
                patterns.add((AEProcessingPattern) PatternDetailsHelper.decodePattern(PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(items.get(index)), 1), new GenericStack(AEFluidKey.of(fluid), 100)), List.of(new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1))), helper.getLevel()));
            }
            manager.replaceRecipes(recipes);
            for (int index = 0; index < patterns.size(); index++) {
                helper.assertTrue(manager.byKey(recipes.get(index).id()).isPresent(), "Native FOA recipe holder did not retain the original rule ID");
                var extra = ForgePatternRules.INSTANCE.matchingSpecialInput(patterns.get(index).getSparseInputs(), helper.getLevel()); var expected = index == 1 ? NativeSuperPatternFixtures.DURABLE_CELL : NativeSuperPatternFixtures.TIME_CELL;
                helper.assertTrue(extra != null && extra.what().equals(AEItemKey.of(expected.asStack())), "Native FOA special rule lost its original recipe/container mapping");
                var adjusted = (AEProcessingPattern) MEBufferPatternHelperExtensions.rewriteForgeOfTheAntichristPattern(patterns.get(index), helper.getLevel(), 15, extra);
                helper.assertTrue(adjusted.getSparseInputs().stream().filter(Objects::nonNull).anyMatch(input -> input.what().equals(extra.what()) && input.amount() == 14) && adjusted.getOutputs().getFirst().amount() == 15, "Native special FOA container count did not match its extra fourteen operations");
            }
            var mismatch = List.of(new GenericStack(AEItemKey.of(Items.COPPER_INGOT), 1), new GenericStack(AEFluidKey.of(Fluids.WATER), 100)); helper.assertTrue(ForgePatternRules.INSTANCE.matchingSpecialInput(mismatch, helper.getLevel()) == null, "FOA rules ignored native fluid components");
            manager.replaceRecipes(previous); helper.assertTrue(ForgePatternRules.INSTANCE.matchingSpecialInput(patterns.getFirst().getSparseInputs(), helper.getLevel()) == null, "FOA rules reused a stale map after native recipe replacement");
        } finally { manager.replaceRecipes(previous); }
        helper.succeed();
    }
}
