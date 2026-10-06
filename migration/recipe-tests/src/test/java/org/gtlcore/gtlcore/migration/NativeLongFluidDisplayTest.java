package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.integration.xei.ILongFluidDisplay;
import com.gtladd.gtladditions.integration.xei.LongFluidStack;
import appeng.api.stacks.AEFluidKey;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies AE key identity, precision, copies and the boundary between display and physical quantities. */
class NativeLongFluidDisplayTest {
    private static FluidStack componentWater() {
        var stack = new FluidStack(Fluids.WATER, 17);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("component fluid"));
        return stack;
    }

    @Test void amountsBeyondDoublePrecisionAndLongMaxPreserveTheNativeComponentKey() {
        var physical = componentWater();
        var originalKey = AEFluidKey.of(physical);
        for (long amount : new long[] {9_007_199_254_740_993L, Long.MAX_VALUE}) {
            var display = LongFluidStack.create(physical.getFluidHolder(), amount, physical.getComponentsPatch());
            assertEquals(amount, LongFluidStack.getAmount(display));
            assertEquals(Integer.MAX_VALUE, display.getAmount());
            assertEquals(originalKey, AEFluidKey.of(display));
            assertNotEquals(AEFluidKey.of(new FluidStack(Fluids.WATER, 1)), AEFluidKey.of(display));
            assertEquals(physical.getComponentsPatch(), display.getComponentsPatch());
        }
        assertEquals(17, physical.getAmount());
    }

    @Test void nativeCopyAndPresentationCopiesRetainExactAmountsIndependently() {
        var physical = componentWater();
        var original = LongFluidStack.create(physical.getFluidHolder(), Long.MAX_VALUE, physical.getComponentsPatch());
        var copied = original.copy();
        assertNotSame(original, copied);
        assertEquals(Long.MAX_VALUE, LongFluidStack.getAmount(copied));
        var resized = LongFluidStack.copyWithAmount(copied, 4_000_000_007L);
        assertEquals(4_000_000_007L, LongFluidStack.getAmount(resized));
        assertEquals(Long.MAX_VALUE, LongFluidStack.getAmount(original));
        assertEquals(Long.MAX_VALUE, LongFluidStack.getAmount(copied));
        assertEquals(AEFluidKey.of(original), AEFluidKey.of(resized));
    }

    @Test void physicalNativeAmountChangesCannotRetainPhantomDisplayStock() {
        var display = LongFluidStack.create(Fluids.WATER.builtInRegistryHolder(), Long.MAX_VALUE);
        var physical = display.copyWithAmount(37);
        assertEquals(37L, LongFluidStack.getAmount(physical));
        assertFalse(((ILongFluidDisplay) (Object) physical).gtladditions$hasDisplayAmount());
        assertEquals(Long.MAX_VALUE, LongFluidStack.getAmount(display));
        display.setAmount(21);
        assertEquals(21L, LongFluidStack.getAmount(display));
        assertFalse(((ILongFluidDisplay) (Object) display).gtladditions$hasDisplayAmount());
        display.shrink(4);
        assertEquals(17L, LongFluidStack.getAmount(display));
    }

    @Test void exactContainmentDoesNotTreatTwoSaturatedStacksAsIdentical() {
        var greater = LongFluidStack.create(Fluids.WATER.builtInRegistryHolder(), Long.MAX_VALUE);
        var smaller = LongFluidStack.create(Fluids.WATER.builtInRegistryHolder(), Long.MAX_VALUE - 1);
        assertTrue(greater.containsFluid(smaller));
        assertFalse(smaller.containsFluid(greater));
        assertFalse(greater.isFluidStackIdentical(smaller));
        assertTrue(greater.isFluidStackIdentical(greater.copy()));
        var differentComponents = componentWater();
        var named = LongFluidStack.create(differentComponents.getFluidHolder(), 3_000_000_000L,
                differentComponents.getComponentsPatch());
        assertFalse(greater.containsFluid(named));
        assertFalse(named.containsFluid(greater));
    }

    @Test void emptyAndInvalidDisplaysCannotMutateTheSharedEmptyStack() {
        assertSame(FluidStack.EMPTY, LongFluidStack.create(Fluids.WATER.builtInRegistryHolder(), 0));
        assertSame(FluidStack.EMPTY, LongFluidStack.create(Fluids.EMPTY.builtInRegistryHolder(), Long.MAX_VALUE));
        assertSame(FluidStack.EMPTY, LongFluidStack.copyWithAmount(FluidStack.EMPTY, 123));
        assertEquals(0L, LongFluidStack.getAmount(FluidStack.EMPTY));
        assertFalse(((ILongFluidDisplay) (Object) FluidStack.EMPTY).gtladditions$hasDisplayAmount());
        assertThrows(IllegalArgumentException.class,
                () -> LongFluidStack.create(Fluids.WATER.builtInRegistryHolder(), -1));
        assertThrows(IllegalArgumentException.class,
                () -> LongFluidStack.copyWithAmount(new FluidStack(Fluids.WATER, 1), -1));
    }
}
