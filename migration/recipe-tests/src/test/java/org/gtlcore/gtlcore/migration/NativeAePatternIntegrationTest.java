package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.item.VirtualIngredientBehavior;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import com.gregtechceu.gtceu.api.machine.trait.MachineTrait;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.nbt.ListTag;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;
import appeng.api.stacks.*;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeAePatternIntegrationTest {
    private static final long LARGE = 9_007_199_254_740_993L;
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static MEPatternBufferPartMachine create() {
        var definition = NativeAePatternFixtures.BUFFER;
        return (MEPatternBufferPartMachine) definition.getBlockEntityType().create(BlockPos.ZERO, definition.getBlock().defaultBlockState());
    }
    private static ItemStack item() { var value = new ItemStack(Items.IRON_INGOT); value.set(DataComponents.CUSTOM_NAME, Component.literal("pattern input")); return value; }
    private static FluidStack fluid() { var value = new FluidStack(Fluids.WATER, 1000); value.set(DataComponents.CUSTOM_NAME, Component.literal("pattern fluid")); return value; }
    private static KeyCounter counter(AEKey key, long amount) { var value = new KeyCounter(); value.add(key, amount); return value; }

    @Test void nativePatternRecipeChildrenAttachOnceAndUseTheSizedCapabilityContract() {
        var machine = create(); var pair = machine.getMERecipeHandlerTraits();
        assertSame(machine, ((MachineTrait) pair.left()).getMachine());
        assertSame(machine, ((MachineTrait) pair.right()).getMachine());
        assertEquals(IO.BOTH, pair.left().getIo()); assertEquals(IO.BOTH, pair.right().getIo());
        assertSame(machine, machine.getSharedCatalystInventory().getMachine());
        assertSame(machine, machine.getSharedCatalystTank().getMachine()); assertSame(machine, machine.getSharedCircuitInventory().getMachine());
        var recipe = com.gregtechceu.gtceu.common.data.GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_pattern_prepare").duration(2).build();
        pair.left().initMEHandleContents(recipe, List.of(LongIngredient.create(item(), LARGE)), false);
        assertEquals(LARGE, pair.left().getPreparedMEHandleContents().values().iterator().nextLong());
        pair.left().initMEHandleContents(recipe, List.of(), false); assertTrue(pair.left().getPreparedMEHandleContents().isEmpty());
    }

    @Test void longInternalInventoriesAndFluidComponentsUseNativeSaveFieldsWithoutIntTruncation() {
        var machine = create(); var slot = machine.getInternalInventory()[26];
        var item = AEItemKey.of(item()); var fluid = AEFluidKey.of(fluid());
        assertTrue(slot.pushPattern(new KeyCounter[]{counter(item, LARGE), counter(fluid, LARGE)}));
        assertEquals(LARGE, slot.getItemStackInputMap().values().iterator().nextLong());
        assertEquals(LARGE, slot.getFluidStackInputMap().values().iterator().nextLong());
        var fluidTemplate = slot.getLimitFluidStackInput().getFirst();
        assertEquals(Integer.MAX_VALUE, fluidTemplate.getAmount()); assertEquals(fluid, AEFluidKey.of(fluidTemplate));
        slot.getCacheManager().setCircuitCache(0); machine.setCustomName("saved pattern buffer");
        machine.getMERecipeHandlerTraits().left().meHandleRecipeOutput(List.of(LongIngredient.create(item(), LARGE)), false);
        var copy = create(); copy.loadAdditional(machine.saveWithoutMetadata(REGISTRIES), REGISTRIES);
        assertEquals(LARGE, copy.getInternalInventory()[26].getItemInventory().getLong(item));
        assertEquals(LARGE, copy.getInternalInventory()[26].getFluidInventory().getLong(fluid));
        assertEquals(0, copy.getInternalInventory()[26].getCacheManager().getCircuitCache());
        assertEquals(LARGE, copy.getBuffer().getLong(item)); assertEquals("saved pattern buffer", copy.getCustomName());
    }

    @Test void overlappingItemPredicatesReserveTogetherAndRejectedConsumptionLeavesAllStockUntouched() {
        var machine = create(); var slot = machine.getInternalInventory()[0]; var key = AEItemKey.of(item());
        slot.add(key, 10);
        var requirements = new Object2LongOpenHashMap<SizedIngredient>();
        requirements.put(new SizedIngredient(Ingredient.of(Items.IRON_INGOT), 8), 8);
        requirements.put(LongIngredient.create(item(), 4), 4);
        assertFalse(slot.handleItemInternal(requirements, -1, true)); assertEquals(10, slot.getItemInventory().getLong(key));
        assertFalse(slot.handleItemInternal(requirements, -1, false)); assertEquals(2, requirements.size()); assertEquals(10, slot.getItemInventory().getLong(key));
        slot.add(key, 2); assertTrue(slot.handleItemInternal(requirements, -1, true));
        assertEquals(12, slot.getItemInventory().getLong(key)); assertTrue(slot.handleItemInternal(requirements, -1, false));
        assertTrue(slot.getItemInventory().isEmpty()); assertTrue(requirements.isEmpty());
    }

    @Test void overlappingFluidPredicatesKeepComponentKeysAndCommitOnlyAfterEveryDemandMatches() {
        var machine = create(); var slot = machine.getInternalInventory()[0]; var key = AEFluidKey.of(fluid());
        slot.add(key, 10);
        var requirements = new Object2LongOpenHashMap<SizedFluidIngredient>();
        requirements.put(new SizedFluidIngredient(FluidIngredient.single(Fluids.WATER), 8), 8);
        requirements.put(LongFluidIngredient.create(fluid(), 4), 4);
        assertFalse(slot.handleFluidInternal(requirements, true)); assertFalse(slot.handleFluidInternal(requirements, false));
        assertEquals(10, slot.getFluidInventory().getLong(key)); assertEquals(2, requirements.size());
        slot.add(key, 2); assertTrue(slot.handleFluidInternal(requirements, false));
        assertTrue(slot.getFluidInventory().isEmpty()); assertTrue(requirements.isEmpty());
    }

    @Test void multiKeyPushAndNativeOutputsRejectOverflowBeforeAcknowledgingOrChangingAnyOtherKey() {
        var machine = create(); var slot = machine.getInternalInventory()[0];
        var full = AEItemKey.of(item()); var other = AEFluidKey.of(fluid());
        slot.add(full, Long.MAX_VALUE);
        assertFalse(slot.pushPattern(new KeyCounter[]{counter(other, 500), counter(full, 1)}));
        assertEquals(Long.MAX_VALUE, slot.getItemInventory().getLong(full)); assertTrue(slot.getFluidInventory().isEmpty());
        var handler = machine.getMERecipeHandlerTraits().left();
        handler.meHandleRecipeOutput(List.of(LongIngredient.create(item(), Long.MAX_VALUE)), false);
        var tooMuch = List.of(LongIngredient.create(item(), 1), LongIngredient.create(new ItemStack(Items.GOLD_INGOT), 5));
        assertEquals(tooMuch, handler.meHandleRecipeOutput(tooMuch, true));
        assertEquals(tooMuch, handler.meHandleRecipeOutput(tooMuch, false));
        assertEquals(Long.MAX_VALUE, machine.getBuffer().getLong(full)); assertFalse(machine.getBuffer().containsKey(AEItemKey.of(Items.GOLD_INGOT)));
    }

    @Test void circuitZeroIsMatchedAndSharedCircuitUsesNativeComponentConfiguration() {
        var machine = create(); var slot = machine.getInternalInventory()[0];
        assertFalse(slot.handleItemInternal(new Object2LongOpenHashMap<>(), 0, true));
        slot.getCacheManager().setCircuitCache(0);
        assertTrue(slot.handleItemInternal(new Object2LongOpenHashMap<>(), 0, true));
        assertFalse(slot.handleItemInternal(new Object2LongOpenHashMap<>(), 1, true));
        assertEquals(0, IntCircuitBehaviour.getCircuitConfiguration(slot.getCacheManager().getCircuitStack()));
    }

    @Test void duplicatedPersistedKeysMergeExactlyAndOverflowDoesNotPartiallyReplaceOrAppendStock() {
        var key = AEItemKey.of(item()); var source = new Object2LongOpenHashMap<AEItemKey>(); source.put(key, LARGE);
        var saved = AEUtils.createListTag(k -> k.toTag(REGISTRIES), source); saved.add(saved.getCompound(0).copy());
        var target = new Object2LongOpenHashMap<AEItemKey>(); target.put(AEItemKey.of(Items.GOLD_INGOT), 7);
        AEUtils.loadInventory(saved, tag -> AEItemKey.fromTag(REGISTRIES, tag), target);
        assertEquals(2 * LARGE, target.getLong(key)); assertEquals(1, target.size());
        var overflow = new ListTag(); var entry = saved.getCompound(0).copy(); entry.putLong("real", Long.MAX_VALUE);
        overflow.add(entry); overflow.add(saved.getCompound(0).copy()); var before = new Object2LongOpenHashMap<>(target);
        assertThrows(ArithmeticException.class, () -> AEUtils.loadInventory(overflow, tag -> AEItemKey.fromTag(REGISTRIES, tag), target));
        assertEquals(before, target);
        target.put(key, Long.MAX_VALUE);
        assertThrows(ArithmeticException.class, () -> AEUtils.appendPersistedInventory(saved, tag -> AEItemKey.fromTag(REGISTRIES, tag), target));
        assertEquals(Long.MAX_VALUE, target.getLong(key));
    }

    @Test void virtualItemAndFluidTokensKeepTheirPayloadComponentsAcrossNativeSerialization() {
        var item = VirtualIngredientBehavior.wrap(item()); var fluid = VirtualIngredientBehavior.wrap(fluid());
        assertTrue(VirtualIngredientBehavior.isBound(item)); assertTrue(VirtualIngredientBehavior.isBound(fluid));
        assertTrue(VirtualIngredientBehavior.isMarked(item)); assertTrue(VirtualIngredientBehavior.isMarked(fluid));
        for (var token : List.of(item, fluid)) {
            var restored = ItemStack.parse(REGISTRIES, token.save(REGISTRIES)).orElseThrow();
            assertEquals(VirtualIngredientBehavior.payloadItemKey(token), VirtualIngredientBehavior.payloadItemKey(restored));
            assertEquals(VirtualIngredientBehavior.payloadFluidKey(token), VirtualIngredientBehavior.payloadFluidKey(restored));
            assertTrue(ItemStack.matches(token, VirtualIngredientBehavior.canonicalStack(restored)));
        }
        assertEquals(AEItemKey.of(item()), VirtualIngredientBehavior.payloadItemKey(item));
        assertEquals(AEFluidKey.of(fluid()), VirtualIngredientBehavior.payloadFluidKey(fluid));
        assertThrows(IllegalArgumentException.class, () -> VirtualIngredientBehavior.wrap(item));
        var mixed = item.copy(); VirtualIngredientBehavior.saveFluidStorage(mixed, VirtualIngredientBehavior.getFluidStorage(fluid));
        assertFalse(VirtualIngredientBehavior.isBound(mixed)); assertTrue(VirtualIngredientBehavior.canonicalStack(mixed).isEmpty());
    }
}
