package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEPatternBufferProxyRecipeHandler;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.NotifiableMERecipeHandlerTrait;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.integration.ae2.AEUtils;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.utils.ISubscription;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.crafting.SizedIngredient;
import it.unimi.dsi.fastutil.ints.*;
import it.unimi.dsi.fastutil.objects.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class NativeMeProxyIntegrationTest {
    private static NativeAeGridFixtures.Part machine() {
        return (NativeAeGridFixtures.Part) NativeAeGridFixtures.PART.getBlockEntityType().create(BlockPos.ZERO,
                NativeAeGridFixtures.PART.getBlock().defaultBlockState());
    }
    private static final class Handler extends NotifiableMERecipeHandlerTrait<SizedIngredient, ItemStack> {
        Object2LongMap<SizedIngredient> prepared = Object2LongMaps.emptyMap();
        @Override public RecipeCapability<SizedIngredient> getCapability() { return ItemRecipeCapability.CAP; }
        @Override public IO getIo() { return IO.IN; }
        @Override public int getPriority() { return 123; }
        @Override public boolean outputHasFilter() { return true; }
        @Override public int[] getActiveSlots() { return new int[]{7, 11}; }
        @Override public Int2ObjectMap<List<ItemStack>> getActiveAndUnCachedSlotsLimitContentsMap() { return Int2ObjectMaps.emptyMap(); }
        @Override public Object2LongMap<ItemStack> getStackMapFromFirstAvailableSlot(IntCollection slots) { return Object2LongMaps.emptyMap(); }
        @Override public boolean meHandleRecipeInner(GTRecipe recipe, Object2LongMap<SizedIngredient> left, boolean simulate, int slot) { return slot == 11; }
        @Override public List<SizedIngredient> meHandleRecipeOutputInner(List<SizedIngredient> left, boolean simulate) { return left; }
        @Override public void prepareMEHandleContents(GTRecipe recipe, List<SizedIngredient> left, boolean simulate) { prepared = AEUtils.ingredientsMap(left); }
        @Override public Object2LongMap<SizedIngredient> getPreparedMEHandleContents() { return prepared; }
    }

    @Test void nativeAttachedProxyForwardsSizedIngredientsAndClearsPreviousDemandsForEmptyInput() {
        var owner = machine(); var target = owner.attachTrait(new Handler());
        var proxy = owner.attachTrait(new MEPatternBufferProxyRecipeHandler<SizedIngredient, ItemStack>(ItemRecipeCapability.CAP));
        assertSame(owner, target.getMachine()); assertSame(owner, proxy.getMachine());
        assertEquals(IO.NONE, proxy.getIo()); assertFalse(proxy.meHandleRecipe(null, true, 11));
        proxy.setHandler(target);
        assertEquals(IO.IN, proxy.getIo()); assertEquals(123, proxy.getPriority()); assertTrue(proxy.outputHasFilter());
        assertArrayEquals(new int[]{7, 11}, proxy.getActiveSlots());
        long amount = 9_007_199_254_740_993L;
        proxy.initMEHandleContents(null, List.of(LongIngredient.create(new ItemStack(Items.IRON_INGOT), amount)), true);
        assertEquals(amount, proxy.getPreparedMEHandleContents().values().iterator().nextLong());
        assertTrue(proxy.meHandleRecipe(null, true, 11)); assertFalse(proxy.meHandleRecipe(null, true, 7));
        proxy.initMEHandleContents(null, List.of(), true);
        assertTrue(target.prepared.isEmpty());
        proxy.setHandler(null); assertFalse(proxy.outputHasFilter()); assertEquals(0, proxy.getPriority());
        assertTrue(proxy.getPreparedMEHandleContents().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> proxy.setHandler(proxy));
    }

    @Test void callbacksCanUnsubscribeDuringNotificationAndDoNotSurviveNativeMachineUnload() {
        var owner = machine(); var target = owner.attachTrait(new Handler());
        var order = new java.util.ArrayList<String>(); var counter = new AtomicInteger();
        ISubscription[] subscription = new ISubscription[1];
        subscription[0] = target.addChangedListener(() -> { order.add("normal"); subscription[0].unsubscribe(); });
        target.addBufferChangedListener(() -> order.add("buffer")); target.addChangedListener(counter::incrementAndGet);
        target.notifyListeners(); target.notifyListeners();
        assertEquals(List.of("buffer", "normal", "buffer"), order); assertEquals(2, counter.get());
        target.onMachineUnload(); target.notifyListeners(); assertEquals(2, counter.get());
    }
}
