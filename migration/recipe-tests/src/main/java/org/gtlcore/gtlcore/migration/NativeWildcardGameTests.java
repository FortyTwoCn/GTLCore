package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.wildcard.*;
import org.gtlcore.gtlcore.client.gui.widget.PatternCycleWidget;
import org.gtlcore.gtlcore.api.item.tool.ae2.patternTool.RecipeStackHelper;
import org.gtlcore.gtlcore.api.machine.multiblock.NoEnergyMultiblockMachine;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import com.gregtechceu.gtceu.common.data.*;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import appeng.api.crafting.*;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.core.definitions.*;
import appeng.blockentity.storage.DriveBlockEntity;
import brachy.modularui.value.sync.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.gametest.framework.*;
import net.neoforged.neoforge.gametest.*;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import io.netty.buffer.Unpooled;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.math.BigInteger;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachineBase;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import static com.gregtechceu.gtceu.api.data.tag.TagPrefix.*;

@GameTestHolder("gtlcore_recipe_test")
@PrefixGameTestTemplate(false)
public final class NativeWildcardGameTests {
    private static CompoundTag component(String type, CompoundTag data) {
        var value = new CompoundTag(); value.putString("type", type); value.put("data", data); return value;
    }
    private static CompoundTag simple(GameTestHelper helper, GenericStack stack) {
        return component("simple", GenericStack.writeTag(helper.getLevel().registryAccess(), stack));
    }
    private static CompoundTag prefix(String key, String name, long amount) {
        var tag = new CompoundTag(); tag.putString(key, name); var data = new CompoundTag(); data.put("tag", tag); data.putLong("amount", amount);
        return component("tag_prefix", data);
    }
    private static CompoundTag filter(Material material) {
        var data = new CompoundTag(); data.putString("material", material.getResourceLocation().toString()); data.putBoolean("whitelist", true);
        return component("simple", data);
    }
    private static ItemStack wildcard(Material material, List<CompoundTag> input, List<CompoundTag> output) {
        var settings = new CompoundTag(); var in = new ListTag(); in.addAll(input); settings.put("in", in);
        var out = new ListTag(); out.addAll(output); settings.put("out", out);
        var filters = new ListTag(); filters.add(filter(material)); settings.put("filter", filters);
        var stack = NativeWildcardFixtures.PATTERN.asStack(); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(settings)); return stack;
    }
    private static ItemStack item() { var stack = new ItemStack(Items.IRON_INGOT); stack.set(DataComponents.CUSTOM_NAME, Component.literal("native wildcard input")); return stack; }
    private static FluidStack fluid() { var stack = new FluidStack(Fluids.WATER, 750); stack.set(DataComponents.CUSTOM_NAME, Component.literal("native wildcard fluid")); return stack; }
    private static ItemStack output() { return ChemicalHelper.get(ingot, GTMaterials.Copper, 2); }
    private static ItemStack pattern(GameTestHelper helper, long amount) {
        return wildcard(GTMaterials.Copper, List.of(simple(helper, new GenericStack(AEItemKey.of(IntCircuitBehaviour.stack(0)), 1)),
                        simple(helper, new GenericStack(AEItemKey.of(item()), amount)), simple(helper, new GenericStack(AEFluidKey.of(fluid()), 750))),
                List.of(prefix("item", ingot.name, 2)));
    }
    private static KeyCounter[] inputs(IPatternDetails pattern) {
        var result = new KeyCounter[pattern.getInputs().length];
        for (int i = 0; i < result.length; i++) { var input = pattern.getInputs()[i]; var stack = input.getPossibleInputs()[0];
            result[i] = new KeyCounter(); result[i].add(stack.what(), Math.multiplyExact(stack.amount(), input.getMultiplier())); }
        return result;
    }
    private static MEWildcardPatternBufferPartMachine place(GameTestHelper helper, boolean stocking) {
        var pos = new BlockPos(4, 4, 4); helper.setBlock(pos, (stocking ? NativeWildcardFixtures.STOCKING : NativeWildcardFixtures.BUFFER).getBlock());
        var machine = (MEWildcardPatternBufferPartMachine) helper.getBlockEntity(pos); machine.setFrontFacing(Direction.NORTH); return machine;
    }
    private static void connect(GameTestHelper helper, MEPatternBufferPartMachineBase machine, boolean cells) {
        var pos = machine.getBlockPos(); helper.getLevel().setBlockAndUpdate(pos.north(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        if (cells) { helper.getLevel().setBlockAndUpdate(pos.north(2), AEBlocks.DRIVE.block().defaultBlockState());
            var drive = (DriveBlockEntity) helper.getLevel().getBlockEntity(pos.north(2)); drive.getInternalInventory().setItemDirect(0, AEItems.ITEM_CELL_1K.stack());
            drive.getInternalInventory().setItemDirect(1, AEItems.FLUID_CELL_1K.stack()); }
    }
    private static NoEnergyMultiblockMachine controller(GameTestHelper helper, MultiblockMachineDefinition definition) {
        var pattern = (BlockPattern) definition.getStructurePatterns().get("main").get(); var repeats = new Int2IntOpenHashMap();
        for (int i = 0; i < pattern.getSlices().length; i++) repeats.put(i, pattern.getSlices()[i].getMinRepeats());
        var preview = new LinkedHashMap<BlockPos, BlockInfo>();
        AbstractStructureHelper.blockPattern(repeats).populate(preview, pattern, null, Direction.NORTH, Direction.UP, false);
        BlockPos controller = null;
        for (var entry : preview.entrySet()) { var pos = new BlockPos(4, 4, 4).offset(entry.getKey()); var state = entry.getValue().getBlockState();
            helper.setBlock(pos, state); if (state.is(definition.getBlock())) controller = pos; }
        var machine = (NoEnergyMultiblockMachine) helper.getBlockEntity(controller); machine.setFrontFacing(Direction.NORTH); machine.checkAndFormStructure();
        helper.assertTrue(machine.isFormed(), "Native wildcard controller did not form its real structure"); return machine;
    }
    private record Menu(ModularSyncManager sync, PanelSyncManager main) {}
    private static Menu menu(GameTestHelper helper, MEWildcardPatternBufferPartMachine part) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel()); player.setPos(part.getBlockPos().getCenter());
        var settings = new brachy.modularui.screen.UISettings(brachy.modularui.api.RecipeViewerSettings.DUMMY);
        var sync = new ModularSyncManager(false); var manager = new PanelSyncManager(sync, true);
        var data = new brachy.modularui.factory.PosGuiData(player, part.getBlockPos());
        var panel = com.gregtechceu.gtceu.api.mui.factory.MachineUIFactory.INSTANCE.createPanel(data, manager, settings);
        brachy.modularui.widget.WidgetTree.collectSyncValues(manager, panel);
        var container = new brachy.modularui.screen.ModularContainerMenu(46); container.construct(player, sync, settings, panel.getName(), data);
        return new Menu(sync, manager);
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 100)
    public static void nativeWildcardProtocolExpandsMaterialTagsFiltersAndLongComponentKeys(GameTestHelper helper) {
        long amount = 9_007_199_254_740_993L;
        var value = wildcard(GTMaterials.Copper, List.of(prefix("item", dust.name, 3), simple(helper, new GenericStack(AEItemKey.of(item()), amount))),
                List.of(prefix("item", ingot.name, 2), prefix("fluid", "gtceu:liquid", amount)));
        var settings = value.get(DataComponents.CUSTOM_DATA).copyTag(); var filters = settings.getList("filter", Tag.TAG_COMPOUND);
        var property = new CompoundTag(); property.putBoolean("whitelist", true); property.putString("flag", "ingot"); filters.add(component("property", property));
        var flag = new CompoundTag(); flag.putBoolean("whitelist", true); flag.putString("flag", "generate_plate"); filters.add(component("flag", flag));
        value.set(DataComponents.CUSTOM_DATA, CustomData.of(settings));
        var patterns = NativeWildcardPatternDecoder.expand(value, helper.getLevel());
        helper.assertTrue(patterns.size() == 1, "Native material/component filters failed to isolate Copper");
        var input = new HashMap<AEKey, Long>(); for (var i : patterns.getFirst().getInputs()) { var stack = i.getPossibleInputs()[0]; input.put(stack.what(), Math.multiplyExact(stack.amount(), i.getMultiplier())); }
        helper.assertTrue(input.get(AEItemKey.of(item())) == amount && input.get(AEItemKey.of(ChemicalHelper.get(dust, GTMaterials.Copper))) == 3 &&
                patterns.getFirst().getOutputs().stream().anyMatch(stack -> stack.what() instanceof AEFluidKey && stack.amount() == amount),
                "Native wildcard expansion truncated quantities, dropped components or resolved the wrong material tag");
        property.putBoolean("whitelist", false); filters.set(1, component("property", property)); value.set(DataComponents.CUSTOM_DATA, CustomData.of(settings));
        helper.assertTrue(NativeWildcardPatternDecoder.expand(value, helper.getLevel()).isEmpty(), "Native property blacklist was ignored"); helper.succeed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 400)
    public static void nativeWildcardDispatchRunsControllerRecipeAndDeliversExactOutputToAeCells(GameTestHelper helper) {
        var machine = controller(helper, NativeWildcardFixtures.CONTROLLER.get()); var part = (MEWildcardPatternBufferPartMachine) machine.getParts().getFirst();
        connect(helper, part, true); part.getTerminalPatternInventory().setItemDirect(0, pattern(helper, 3));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline(), "Wildcard buffer has no powered AE network"))
                .thenExecute(() -> {
                    helper.assertTrue(part.getAvailablePatterns().size() == 1 && part.pushPattern(part.getAvailablePatterns().getFirst(), inputs(part.getAvailablePatterns().getFirst())), "Native wildcard AE dispatch was rejected");
                    var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_wildcard_cycle").inputItems(item().copyWithCount(3)).inputFluids(fluid())
                            .circuitMeta(0).outputItems(output()).duration(3).build(); recipe.tickInputs.remove(com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP);
                    helper.assertTrue(part.getInternalSlot(0).getCacheManager().getCircuitCache() == 0 &&
                                    part.getInternalSlot(0).getItemInventory().getLong(AEItemKey.of(IntCircuitBehaviour.stack(0))) == 0,
                            "Wildcard circuit zero was not retained as a reusable recipe configuration");
                    helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Real controller did not consume wildcard inputs"); machine.getRecipeLogic().setSuspendAfterFinish(true);
                }).thenWaitUntil(() -> helper.assertTrue(machine.getRecipeLogic().isSuspend(), "Wildcard controller did not finish"))
                .thenWaitUntil(() -> helper.assertTrue(part.getBuffer().isEmpty() && part.getGrid().getStorageService().getInventory().extract(AEItemKey.of(output()), 3, Actionable.SIMULATE, IActionSource.empty()) == 2,
                        "Native wildcard output was lost or duplicated"))
                .thenIdle(25).thenExecute(() -> helper.assertTrue(part.getGrid().getStorageService().getInventory().extract(AEItemKey.of(output()), 3, Actionable.MODULATE, IActionSource.empty()) == 2,
                        "Wildcard output was delivered twice")).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 400)
    public static void nativeStockingWildcardControllerUsesLocalAndAdditionalAeInputsAndReturnsOutput(GameTestHelper helper) {
        var machine = controller(helper, NativeWildcardFixtures.STOCKING_CONTROLLER.get());
        var part = (MEStockingWildcardPatternBufferPartMachine) machine.getParts().getFirst(); connect(helper, part, true);
        part.getTerminalPatternInventory().setItemDirect(0, pattern(helper, 1));
        part.configureStock(0, new GenericStack(AEItemKey.of(item()), 1)); part.configureStock(1, new GenericStack(AEFluidKey.of(fluid()), 1000));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline(), "Stocking wildcard controller has no real AE network"))
                .thenExecute(() -> {
                    var storage = part.getGrid().getStorageService().getInventory();
                    helper.assertTrue(storage.insert(AEItemKey.of(item()), 2, Actionable.MODULATE, IActionSource.empty()) == 2 &&
                            storage.insert(AEFluidKey.of(fluid()), 250, Actionable.MODULATE, IActionSource.empty()) == 250, "Could not populate native AE stocking keys");
                    var expanded = part.getAvailablePatterns().getFirst(); helper.assertTrue(part.pushPattern(expanded, inputs(expanded)), "Stocking wildcard refused native AE dispatch");
                    var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_stocking_wildcard_cycle").inputItems(item().copyWithCount(3))
                            .inputFluids(fluid().copyWithAmount(1000)).circuitMeta(0).outputItems(output()).duration(3).build();
                    recipe.tickInputs.remove(com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability.CAP);
                    helper.assertTrue(machine.getRecipeLogic().checkMatchedRecipeAvailable(recipe), "Native controller failed to combine wildcard dispatch and configured AE stock");
                    helper.assertTrue(part.getInternalSlot(0).getItemInventory().isEmpty() && part.getInternalSlot(0).getFluidInventory().isEmpty() &&
                                    storage.extract(AEItemKey.of(item()), 1, Actionable.SIMULATE, IActionSource.empty()) == 0 && storage.extract(AEFluidKey.of(fluid()), 1, Actionable.SIMULATE, IActionSource.empty()) == 0,
                            "Stocking wildcard controller acknowledged unpaid or duplicated real inputs"); machine.getRecipeLogic().setSuspendAfterFinish(true);
                }).thenWaitUntil(() -> helper.assertTrue(machine.getRecipeLogic().isSuspend(), "Stocking wildcard controller did not finish"))
                .thenWaitUntil(() -> helper.assertTrue(part.getBuffer().isEmpty() && part.getGrid().getStorageService().getInventory().extract(AEItemKey.of(output()), 3, Actionable.SIMULATE, IActionSource.empty()) == 2,
                        "Stocking wildcard output was not returned exactly once")).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeStockingWildcardReservesDuplicateConfigurationsAndOverlappingPredicatesOnce(GameTestHelper helper) {
        var part = (MEStockingWildcardPatternBufferPartMachine) place(helper, true); connect(helper, part, true);
        part.getTerminalPatternInventory().setItemDirect(0, pattern(helper, 3)); part.setMEUpdateInterval(1);
        part.configureStock(0, new GenericStack(AEItemKey.of(item()), 1)); part.configureStock(31, new GenericStack(AEItemKey.of(item()), 1));
        part.configureStock(1, new GenericStack(AEFluidKey.of(fluid()), 1000));
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline(), "Stocking wildcard has no powered AE network"))
                .thenExecute(() -> {
                    var storage = part.getGrid().getStorageService().getInventory(); storage.insert(AEItemKey.of(item()), 10, Actionable.MODULATE, IActionSource.empty()); storage.insert(AEFluidKey.of(fluid()), 10, Actionable.MODULATE, IActionSource.empty());
                    part.getInternalSlot(0).add(AEItemKey.of(item()), 1); part.getInternalSlot(0).add(AEFluidKey.of(fluid()), 1);
                }).thenIdle(3).thenExecute(() -> {
                    var storage = part.getGrid().getStorageService().getInventory(); var slot = part.getInternalSlot(0);
                    helper.assertTrue(part.getStockingItemMap().values().iterator().nextLong() == 10, "Duplicate wildcard configuration inflated real stock");
                    var items = new Object2LongOpenHashMap<net.neoforged.neoforge.common.crafting.SizedIngredient>();
                    items.put(net.neoforged.neoforge.common.crafting.SizedIngredient.of(Items.IRON_INGOT, 8), 8); items.put(LongIngredient.create(item(), 4), 4);
                    var fluids = new Object2LongOpenHashMap<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient>();
                    fluids.put(net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient.of(Fluids.WATER, 8), 8); fluids.put(LongFluidIngredient.create(fluid(), 4), 4);
                    helper.assertTrue(!slot.handleItemInternal(items, -1, true) && !slot.handleItemInternal(items, -1, false) && !slot.handleFluidInternal(fluids, true) && !slot.handleFluidInternal(fluids, false) &&
                                    items.size() == 2 && fluids.size() == 2 && slot.getItemInventory().getLong(AEItemKey.of(item())) == 1 && slot.getFluidInventory().getLong(AEFluidKey.of(fluid())) == 1,
                            "Failed wildcard reservation consumed local ownership or reused matching stock");
                    helper.assertTrue(storage.extract(AEItemKey.of(item()), 11, Actionable.SIMULATE, IActionSource.empty()) == 10 && storage.extract(AEFluidKey.of(fluid()), 11, Actionable.SIMULATE, IActionSource.empty()) == 10,
                            "Rejected wildcard demand withdrew real AE stock");
                    storage.insert(AEItemKey.of(item()), 1, Actionable.MODULATE, IActionSource.empty()); storage.insert(AEFluidKey.of(fluid()), 1, Actionable.MODULATE, IActionSource.empty());
                    helper.assertTrue(slot.handleItemInternal(items, -1, false) && slot.handleFluidInternal(fluids, false) && items.isEmpty() && fluids.isEmpty() && slot.getItemInventory().isEmpty() && slot.getFluidInventory().isEmpty(),
                            "Native wildcard reservation failed to commit exact successful inputs");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeWildcardReloadPreservesPendingLongOwnershipCacheAndServerGuiState(GameTestHelper helper) {
        var part = place(helper, false); connect(helper, part, false); var restored = new AtomicReference<MEWildcardPatternBufferPartMachine>();
        long amount = 9_007_199_254_740_993L;
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline(), "Wildcard reload fixture has no AE node"))
                .thenExecute(() -> {
                    var recipe = helper.getLevel().getRecipeManager().getAllRecipesFor(GTRecipeTypes.CHEMICAL_RECIPES.get()).stream().map(holder -> holder.value())
                            .filter(value -> value.inputs.values().stream().flatMap(List::stream).allMatch(content -> content.chance() > 0))
                            .filter(value -> !RecipeStackHelper.getInputKeys(value).isEmpty() && !RecipeStackHelper.getOutputKeys(value).isEmpty()).findFirst().orElseThrow();
                    part.getTerminalPatternInventory().setItemDirect(0, wildcard(GTMaterials.Copper,
                            RecipeStackHelper.getInputKeys(recipe).stream().map(stack -> simple(helper, stack)).toList(), RecipeStackHelper.getOutputKeys(recipe).stream().map(stack -> simple(helper, stack)).toList()));
                    part.getInternalSlot(0).add(AEItemKey.of(item()), amount); part.getInternalSlot(0).add(AEFluidKey.of(fluid()), Long.MAX_VALUE);
                    part.getMETrait().setSlotCacheRecipe(0, recipe);
                    var menu = menu(helper, part); var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
                    try {
                        packet.writeBoolean(true); menu.main.receiveWidgetUpdate("wildcardHiddenTerminal:0", false, 0, packet);
                        ((PanelSyncHandler) menu.main.findPanelHandlerNullable("wildcardRename")).openPanel();
                        var rename = menu.sync.getPanelSyncManager("wildcardRenamePanel"); packet.clear(); brachy.modularui.utils.NetworkUtils.writeStringSafe(packet, "native wildcard saved name", Short.MAX_VALUE - 74);
                        rename.receiveWidgetUpdate("wildcardCustomName:0", false, 0, packet);
                        var preview = menu.main.findSyncHandler("wildcardCachedPreview", GenericSyncValue.class); preview.updateCacheFromSource(true);
                        helper.assertTrue(((PatternCycleWidget.Preview) preview.getValue()).outputs().size() > 0, "Native cached wildcard preview did not synchronize");
                    } finally { packet.release(); }
                    var registries = helper.getLevel().registryAccess(); var saved = part.saveWithoutMetadata(registries);
                    var copy = (MEWildcardPatternBufferPartMachine) NativeWildcardFixtures.BUFFER.getBlockEntityType().create(part.getBlockPos(), part.getBlockState());
                    helper.getLevel().removeBlockEntity(part.getBlockPos()); copy.loadAdditional(saved, registries);
                    var pending = copy.saveWithoutMetadata(registries); helper.assertTrue(pending.getCompound("wildcardDynamicState").equals(saved.getCompound("wildcardDynamicState")), "Detached native save lost pending wildcard ownership before delayed restoration");
                    helper.getLevel().setBlockEntity(copy); restored.set(copy);
                }).thenWaitUntil(() -> helper.assertTrue(restored.get().isOnline() && restored.get().getMETrait().hasCacheInSlot(0), "Native wildcard recipe ID or AE node did not restore"))
                .thenExecute(() -> { var copy = restored.get(); helper.assertTrue(copy.getInternalSlot(0).getItemInventory().getLong(AEItemKey.of(item())) == amount &&
                                copy.getInternalSlot(0).getFluidInventory().getLong(AEFluidKey.of(fluid())) == Long.MAX_VALUE && !copy.isVisibleInTerminal() && copy.getCustomName().equals("native wildcard saved name"),
                        "Native wildcard reload lost exact quantities, components, visibility or GUI-edited name"); }).thenSucceed();
    }

    private static ItemStack manyPatterns(GameTestHelper helper) {
        var stack = pattern(helper, 1); var settings = stack.get(DataComponents.CUSTOM_DATA).copyTag();
        var filters = new ListTag(); var property = new CompoundTag(); property.putString("flag", "ingot"); property.putBoolean("whitelist", true);
        filters.add(component("property", property)); settings.put("filter", filters); stack.set(DataComponents.CUSTOM_DATA, CustomData.of(settings)); return stack;
    }
    private static void ownTwoFullSlots(GameTestHelper helper, MEWildcardPatternBufferPartMachine part) {
        part.getTerminalPatternInventory().setItemDirect(0, manyPatterns(helper));
        helper.assertTrue(part.getInternalSlotCount() > 1, "Refund fixture did not expand multiple real material patterns");
        for (int i = 0; i < 2; i++) { part.getInternalSlot(i).add(AEItemKey.of(item()), Long.MAX_VALUE); part.getInternalSlot(i).add(AEFluidKey.of(fluid()), Long.MAX_VALUE); }
    }
    private static BigInteger queued(GameTestHelper helper, ListTag ledgers, AEKey key) {
        var total = BigInteger.ZERO;
        for (var value : ledgers) for (var element : ((CompoundTag) value).getList("entries", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            if (key.equals(AEKey.fromTagGeneric(helper.getLevel().registryAccess(), entry.getCompound("key")))) total = total.add(BigInteger.valueOf(entry.getLong("value")));
        }
        return total;
    }
    private static BigInteger owned(GameTestHelper helper, MEPatternBufferPartMachineBase part, AEKey key) {
        var result = BigInteger.valueOf(part.getBuffer().getLong(key)).add(queued(helper,
                part.saveWithoutMetadata(helper.getLevel().registryAccess()).getList("refundQueue", Tag.TAG_COMPOUND), key));
        var slots = part instanceof MEWildcardPatternBufferPartMachine wildcard ?
                java.util.stream.IntStream.range(0, wildcard.getInternalSlotCount()).mapToObj(wildcard::getInternalSlot).toList() :
                Arrays.asList(((MEPatternBufferPartMachine) part).getInternalInventory());
        for (var slot : slots) {
            result = result.add(BigInteger.valueOf(key instanceof AEItemKey item ? slot.getItemInventory().getLong(item) : slot.getFluidInventory().getLong((AEFluidKey) key)));
        }
        return result;
    }
    private static final BigInteger TWO_FULL_LONGS = BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO);

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeWildcardRemovalQueuesMoreThanOneLongReloadsAndPartiallyReturnsIntoActualAeCells(GameTestHelper helper) {
        var part = place(helper, false); connect(helper, part, false); var restored = new AtomicReference<MEWildcardPatternBufferPartMachine>();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline(), "Refund fixture has no real AE node"))
                .thenExecute(() -> {
                    ownTwoFullSlots(helper, part); part.getTerminalPatternInventory().setItemDirect(0, ItemStack.EMPTY);
                    helper.assertTrue(part.getInternalSlotCount() == 0 && owned(helper, part, AEItemKey.of(item())).equals(TWO_FULL_LONGS) && owned(helper, part, AEFluidKey.of(fluid())).equals(TWO_FULL_LONGS), "Removing wildcard slots lost overflow refunds");
                    var copy = (MEWildcardPatternBufferPartMachine) NativeWildcardFixtures.BUFFER.getBlockEntityType().create(part.getBlockPos(), part.getBlockState());
                    var saved = part.saveWithoutMetadata(helper.getLevel().registryAccess()); helper.getLevel().removeBlockEntity(part.getBlockPos());
                    copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy); restored.set(copy); connect(helper, copy, true);
                }).thenWaitUntil(() -> {
                    var copy = restored.get(); helper.assertTrue(copy.isOnline(), "Refund reload did not reconnect");
                    var storage = copy.getMainNode().getGrid().getStorageService().getInventory();
                    long items = storage.extract(AEItemKey.of(item()), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
                    long fluids = storage.extract(AEFluidKey.of(fluid()), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
                    helper.assertTrue(items > 0 && fluids > 0, "Queued refunds never reached actual AE storage cells");
                    helper.assertTrue(owned(helper, copy, AEItemKey.of(item())).add(BigInteger.valueOf(items)).equals(TWO_FULL_LONGS) &&
                            owned(helper, copy, AEFluidKey.of(fluid())).add(BigInteger.valueOf(fluids)).equals(TWO_FULL_LONGS), "Partial native refunds changed total owned quantities or components");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeStockingWildcardRemovalBeforeDeferredRestoreRefundsEachSavedSlotExactlyOnce(GameTestHelper helper) {
        var part = place(helper, true); connect(helper, part, false); var restored = new AtomicReference<MEWildcardPatternBufferPartMachine>();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline(), "Pending stocking refund has no AE node"))
                .thenExecute(() -> {
                    ownTwoFullSlots(helper, part); var saved = part.saveWithoutMetadata(helper.getLevel().registryAccess());
                    var copy = (MEWildcardPatternBufferPartMachine) NativeWildcardFixtures.STOCKING.getBlockEntityType().create(part.getBlockPos(), part.getBlockState());
                    helper.getLevel().removeBlockEntity(part.getBlockPos()); copy.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(copy);
                    copy.getTerminalPatternInventory().setItemDirect(0, ItemStack.EMPTY); restored.set(copy);
                    helper.assertTrue(owned(helper, copy, AEItemKey.of(item())).equals(TWO_FULL_LONGS) && owned(helper, copy, AEFluidKey.of(fluid())).equals(TWO_FULL_LONGS), "Pending dynamic slots were combined into an overflowing refund map");
                }).thenIdle(25).thenExecute(() -> {
                    var copy = restored.get(); helper.assertTrue(copy.getInternalSlotCount() == 0 && owned(helper, copy, AEItemKey.of(item())).equals(TWO_FULL_LONGS) &&
                            owned(helper, copy, AEFluidKey.of(fluid())).equals(TWO_FULL_LONGS), "Deferred restore refunded pending ownership twice");
                }).thenSucceed();
    }

    private static MEPatternBufferPartMachineBase breakAndPlace(GameTestHelper helper, MEPatternBufferPartMachineBase part, BlockPos groundRelative) {
        var definition = part.getDefinition(); helper.getLevel().destroyBlock(part.getBlockPos(), true);
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(part.getBlockPos()).inflate(2)).stream()
                .filter(entity -> entity.getItem().is(definition.asStack().getItem())).toList();
        helper.assertTrue(drops.size() == 1, "Pattern buffer destruction did not drop exactly one machine");
        var dropped = drops.getFirst().getItem().copy(); drops.getFirst().discard();
        var owner = FakePlayerFactory.getMinecraft(helper.getLevel()); owner.closeContainer(); var ground = helper.absolutePos(groundRelative);
        helper.getLevel().setBlockAndUpdate(ground, Blocks.STONE.defaultBlockState()); owner.setPos(ground.getCenter().add(2, 2, 0)); owner.setItemInHand(InteractionHand.MAIN_HAND, dropped);
        var hit = new BlockHitResult(ground.getCenter().add(0, 0.5, 0), Direction.UP, ground, false);
        helper.assertTrue(((BlockItem) dropped.getItem()).place(new BlockPlaceContext(new UseOnContext(owner, InteractionHand.MAIN_HAND, hit))).consumesAction(), "Actual pattern buffer BlockItem placement failed");
        return (MEPatternBufferPartMachineBase) helper.getLevel().getBlockEntity(ground.above());
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeWildcardActualDestructionAndPlacementPreserveActiveAndPendingLongInputsWithoutRefundDuplication(GameTestHelper helper) {
        var first = place(helper, false); connect(helper, first, true);
        helper.setBlock(new BlockPos(8, 4, 4), NativeWildcardFixtures.STOCKING.getBlock());
        var second = (MEWildcardPatternBufferPartMachine) helper.getBlockEntity(new BlockPos(8, 4, 4)); second.setFrontFacing(Direction.NORTH); connect(helper, second, true);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(first.isOnline() && second.isOnline(), "Drop fixtures have no real AE nodes"))
                .thenExecute(() -> {
                    ownTwoFullSlots(helper, first); var storage = first.getMainNode().getGrid().getStorageService().getInventory();
                    var restored = breakAndPlace(helper, first, new BlockPos(4, 2, 10));
                    helper.assertTrue(owned(helper, restored, AEItemKey.of(item())).equals(TWO_FULL_LONGS) && owned(helper, restored, AEFluidKey.of(fluid())).equals(TWO_FULL_LONGS), "Dropped wildcard machine lost active long ownership");
                    helper.assertTrue(storage.extract(AEItemKey.of(item()), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()) == 0 && storage.extract(AEFluidKey.of(fluid()), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()) == 0, "Destruction refunded ownership that was already serialized into the dropped machine");
                    ownTwoFullSlots(helper, second); var saved = second.saveWithoutMetadata(helper.getLevel().registryAccess());
                    var pending = (MEWildcardPatternBufferPartMachine) NativeWildcardFixtures.STOCKING.getBlockEntityType().create(second.getBlockPos(), second.getBlockState());
                    helper.getLevel().removeBlockEntity(second.getBlockPos()); pending.loadAdditional(saved, helper.getLevel().registryAccess()); helper.getLevel().setBlockEntity(pending);
                    var placed = breakAndPlace(helper, pending, new BlockPos(9, 2, 10));
                    helper.assertTrue(owned(helper, placed, AEItemKey.of(item())).equals(TWO_FULL_LONGS) && owned(helper, placed, AEFluidKey.of(fluid())).equals(TWO_FULL_LONGS), "Machine broken before deferred restore lost persisted inputs");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "gtl_native_multiblocks", timeoutTicks = 300)
    public static void nativeOrdinaryPatternBufferDestructionCarriesOwnedInputsAndOutputsWithoutReturningThemAgain(GameTestHelper helper) {
        helper.setBlock(new BlockPos(4, 4, 4), NativeAePatternFixtures.BUFFER.getBlock());
        var part = (MEPatternBufferPartMachine) helper.getBlockEntity(new BlockPos(4, 4, 4)); part.setFrontFacing(Direction.NORTH); connect(helper, part, true);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(part.isOnline(), "Ordinary drop fixture has no AE node"))
                .thenExecute(() -> {
                    part.getTerminalPatternInventory().setItemDirect(0, appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(AEItemKey.of(item()), 1)), List.of(new GenericStack(AEItemKey.of(output()), 2))));
                    part.getInternalInventory()[0].add(AEItemKey.of(item()), Long.MAX_VALUE); part.getInternalInventory()[0].add(AEFluidKey.of(fluid()), Long.MAX_VALUE);
                    part.getBuffer().put(AEItemKey.of(Items.GOLD_INGOT), 17); var storage = part.getMainNode().getGrid().getStorageService().getInventory();
                    var restored = breakAndPlace(helper, part, new BlockPos(7, 2, 10));
                    helper.assertTrue(owned(helper, restored, AEItemKey.of(item())).equals(BigInteger.valueOf(Long.MAX_VALUE)) && owned(helper, restored, AEFluidKey.of(fluid())).equals(BigInteger.valueOf(Long.MAX_VALUE)) &&
                            owned(helper, restored, AEItemKey.of(Items.GOLD_INGOT)).equals(BigInteger.valueOf(17)), "Ordinary machine drop did not preserve owned inputs and output ledger");
                    helper.assertTrue(storage.extract(AEItemKey.of(item()), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()) == 0 && storage.extract(AEFluidKey.of(fluid()), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty()) == 0, "Ordinary pattern removal returned dropped ownership a second time");
                }).thenSucceed();
    }
}
