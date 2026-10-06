package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.BallHatchPartMachine;
import org.gtlcore.gtlcore.api.recipe.ingredient.*;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystItemStackHandler;
import com.hepdd.gtmthings.common.block.machine.trait.CatalystFluidStackHandler;
import net.minecraft.core.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeCatalystIntegrationTest {
    private static RegistryAccess registry() { return RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); }
    private static MEPatternBufferPartMachine create() { var d = NativeAePatternFixtures.BUFFER; return (MEPatternBufferPartMachine) d.getBlockEntityType().create(BlockPos.ZERO, d.getBlock().defaultBlockState()); }
    private static ItemStack item(String name, int count) { var stack = new ItemStack(Items.DIAMOND, count); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack; }
    private static FluidStack fluid(String name, int count) { var stack = new FluidStack(Fluids.WATER, count); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return stack; }
    private static GTRecipe recipe() { return GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_shared_catalysts")
            .notConsumable(LongIngredient.create(item("required", 1), 1).ingredient())
            .notConsumable(LongIngredient.create(item("required", 1), 5).ingredient())
            .notConsumable(LongIngredient.create(item("other", 1), 1).ingredient())
            .notConsumableFluid(LongFluidIngredient.create(fluid("required", 1), 500))
            .notConsumableFluid(LongFluidIngredient.create(fluid("required", 1), 2000))
            .notConsumableFluid(LongFluidIngredient.create(fluid("other", 1), 500)).duration(3).build(); }
    private static List<net.neoforged.neoforge.common.crafting.SizedIngredient> itemInputs(GTRecipe recipe) { return new ArrayList<>(recipe.getInputContents(ItemRecipeCapability.CAP).stream().map(content -> ItemRecipeCapability.CAP.of(content.content())).toList()); }
    private static List<net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient> fluidInputs(GTRecipe recipe) { return new ArrayList<>(recipe.getInputContents(FluidRecipeCapability.CAP).stream().map(content -> FluidRecipeCapability.CAP.of(content.content())).toList()); }

    @Test void actualItemCatalystCacheSeparatesComponentsAndDoesNotAliasDifferentReusableQuantities() {
        var part = create(); var handler = part.getSharedCatalystInventory(); var recipe = recipe();
        handler.storage.setStackInSlot(0, item("required", 2)); handler.storage.setStackInSlot(1, item("required", 2));
        var left = itemInputs(recipe); handler.handleRecipeInner(IO.IN, recipe, left, true);
        assertEquals(2, left.size()); assertEquals(5, LongIngredient.amount(left.getFirst())); assertEquals(1, LongIngredient.amount(left.getLast()));
        handler.storage.setStackInSlot(1, item("required", 3)); left = itemInputs(recipe); handler.handleRecipeInner(IO.IN, recipe, left, true);
        assertEquals(1, left.size()); assertFalse(left.getFirst().test(item("required", 1)));
        var actual = itemInputs(recipe); handler.handleRecipeInner(IO.IN, recipe, actual, false);
        assertEquals(3, actual.size()); assertEquals(2, handler.getStackInSlot(0).getCount()); assertEquals(3, handler.getStackInSlot(1).getCount());
        handler.storage.setStackInSlot(0, ItemStack.EMPTY); handler.storage.setStackInSlot(1, ItemStack.EMPTY);
        left = itemInputs(recipe); handler.handleRecipeInner(IO.IN, recipe, left, true); assertEquals(3, left.size());
    }
    @Test void actualFluidCatalystCacheAggregatesNativeStacksAndRetainsLargeRejectedDemandsExactly() {
        var part = create(); var handler = part.getSharedCatalystTank(); var recipe = recipe();
        handler.getStorages()[0].setFluid(fluid("required", 500)); handler.getStorages()[1].setFluid(fluid("required", 750));
        var left = fluidInputs(recipe); handler.handleRecipeInner(IO.IN, recipe, left, true); assertEquals(2, left.size()); assertEquals(2000, LongFluidIngredient.amount(left.getFirst()));
        handler.getStorages()[1].setFluid(fluid("required", 1500)); left = fluidInputs(recipe); handler.handleRecipeInner(IO.IN, recipe, left, true); assertEquals(1, left.size());
        long large = 9_007_199_254_740_993L;
        var oversized = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_large_catalyst").notConsumableFluid(LongFluidIngredient.create(fluid("required", 1), large)).duration(3).build();
        var rejected = fluidInputs(oversized); handler.handleRecipeInner(IO.IN, oversized, rejected, true); assertEquals(large, LongFluidIngredient.amount(rejected.getFirst()));
        var actual = fluidInputs(recipe); handler.handleRecipeInner(IO.IN, recipe, actual, false); assertEquals(3, actual.size()); assertEquals(1500, handler.getFluidInTank(1).getAmount());
        handler.getStorages()[0].setFluid(FluidStack.EMPTY); handler.getStorages()[1].setFluid(FluidStack.EMPTY);
        left = fluidInputs(recipe); handler.handleRecipeInner(IO.IN, recipe, left, true); assertEquals(3, left.size());
    }
    @Test void consumedInputsCannotUseSharedCatalystStockAndExternalCapabilitiesStayHidden() {
        var part = create(); var items = part.attachTrait(new CatalystItemStackHandler(2, IO.IN, IO.BOTH)); var fluids = part.attachTrait(new CatalystFluidStackHandler(2, 4000, IO.IN, IO.BOTH));
        items.storage.setStackInSlot(0, item("required", 5)); fluids.getStorages()[0].setFluid(fluid("required", 2000));
        var recipe = GTRecipeTypes.CHEMICAL_RECIPES.recipeBuilder("native_consumed_catalyst_overlap")
                .inputItems(item("required", 1)).notConsumable(item("required", 1)).inputFluids(fluid("required", 500)).notConsumableFluid(fluid("required", 500)).duration(3).build();
        var itemLeft = itemInputs(recipe); var fluidLeft = fluidInputs(recipe);
        items.handleRecipeInner(IO.IN, recipe, itemLeft, true); fluids.handleRecipeInner(IO.IN, recipe, fluidLeft, true);
        assertEquals(2, itemLeft.size()); assertEquals(2, fluidLeft.size());
        for (var side : Direction.values()) { assertFalse(items.hasCapability(side)); assertFalse(fluids.hasCapability(side)); }
        assertFalse(items.hasCapability(null)); assertFalse(fluids.hasCapability(null));
        assertEquals(5, items.getStackInSlot(0).getCount()); assertEquals(2000, fluids.getFluidInTank(0).getAmount());
    }
    @Test void nativeSharedCatalystReloadRebuildsExactComponentCacheBeforeTheFirstRecipeCheck() {
        var part = create(); part.getSharedCatalystInventory().storage.setStackInSlot(8, item("required", 5)); part.getSharedCatalystTank().getStorages()[8].setFluid(fluid("required", 2000));
        var saved = part.saveWithoutMetadata(registry()); var copy = create(); copy.loadAdditional(saved, registry());
        copy.getSharedCatalystInventory().onMachineLoad(); copy.getSharedCatalystTank().onMachineLoad();
        var recipe = recipe(); var items = itemInputs(recipe); var fluids = fluidInputs(recipe);
        copy.getSharedCatalystInventory().handleRecipeInner(IO.IN, recipe, items, true); copy.getSharedCatalystTank().handleRecipeInner(IO.IN, recipe, fluids, true);
        assertEquals(1, items.size()); assertEquals(1, fluids.size()); assertEquals(5, copy.getSharedCatalystInventory().getStackInSlot(8).getCount()); assertEquals(2000, copy.getSharedCatalystTank().getFluidInTank(8).getAmount());
        assertTrue(ItemStack.isSameItemSameComponents(item("required", 1), copy.getSharedCatalystInventory().getStackInSlot(8)));
        assertTrue(FluidStack.isSameFluidSameComponents(fluid("required", 1), copy.getSharedCatalystTank().getFluidInTank(8)));
    }
    @Test void nativeGrindingBallPartAttachesOneFilteredInventoryAndPersistsWorkingStateAndComponents() {
        var definition = NativeCatalystFixtures.PART;
        var part = (BallHatchPartMachine) definition.getBlockEntityType().create(BlockPos.ZERO, definition.getBlock().defaultBlockState());
        var ball = NativeCatalystFixtures.BALL.asStack(3); ball.set(DataComponents.CUSTOM_NAME, Component.literal("native saved grinding ball"));
        assertTrue(part.getInventory().isItemValid(0, ball)); assertFalse(part.getInventory().isItemValid(0, new ItemStack(Items.DIAMOND)));
        assertSame(part, part.getInventory().getMachine()); assertEquals(1, part.getAllTraits().stream().filter(trait -> trait == part.getInventory()).count());
        part.getInventory().storage.setStackInSlot(0, ball); part.beforeWorking(null);
        var copy = (BallHatchPartMachine) definition.getBlockEntityType().create(BlockPos.ZERO, definition.getBlock().defaultBlockState()); copy.loadAdditional(part.saveWithoutMetadata(registry()), registry());
        assertTrue(copy.isWorking()); assertEquals(3, copy.getInventory().getStackInSlot(0).getCount()); assertTrue(ItemStack.isSameItemSameComponents(ball, copy.getInventory().getStackInSlot(0)));
        copy.afterWorking(null); assertFalse(copy.isWorking());
    }
}
