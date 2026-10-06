package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.integration.ae2.graph.GraphJobCodec;
import org.gtlcore.gtlcore.integration.ae2.graph.core.*;
import appeng.api.stacks.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeGraphJobCodecIntegrationTest {
    private static final RegistryAccess REGISTRIES = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
    private static AEKey item(Item item, String name) {
        var stack = new ItemStack(item); stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)); return AEItemKey.of(stack);
    }
    private static AEKey water() {
        var stack = new FluidStack(Fluids.WATER, 1); stack.set(DataComponents.CUSTOM_NAME, Component.literal("graph coolant")); return AEFluidKey.of(stack);
    }
    private static GraphJobRuntime<AEKey> runtime(long runs) {
        var iron = item(Items.IRON_INGOT, "graph input"); var fluid = water(); var gold = item(Items.GOLD_INGOT, "graph output");
        var recipe = new GraphRecipe<AEKey>("component_recipe", "real_pattern_binding",
                List.of(new GraphRecipe.Slot<>(iron, 1, 0), new GraphRecipe.Slot<>(fluid, 1000, 1)), Map.of(gold, 1L));
        var fluidDemand = BigInteger.valueOf(runs).multiply(BigInteger.valueOf(1000));
        var plan = new GraphPlan<AEKey>(gold, runs, false, new PlanStep.Batch("component_recipe", runs),
                Map.of("component_recipe", recipe), Map.of(iron, BigInteger.valueOf(runs), fluid, fluidDemand),
                Map.of(), Map.of(), GraphPlan.Result.FEASIBLE, 1, 1);
        return new GraphJobRuntime<>(plan, Map.of(iron, runs, fluid, 1000L), Map.of(),
                runs == 1 ? Map.of() : Map.of(fluid, fluidDemand.subtract(BigInteger.valueOf(1000))));
    }
    @Test void nativeRegistryCodecKeepsComponentKeysExactDeferredAmountsAndSuspension() {
        var original = runtime(9_007_199_254_740_999L); original.suspend(true); var before = original.snapshot();
        var encoded = GraphJobCodec.write(before, REGISTRIES); var restored = GraphJobCodec.read(encoded, REGISTRIES);
        assertEquals(before.plan().target(), restored.plan().target()); assertEquals(before.plan().amount(), restored.plan().amount());
        assertEquals(before.plan().initialExact(), restored.plan().initialExact()); assertEquals(before.owned(), restored.owned());
        assertEquals(before.deferredExternal(), restored.deferredExternal()); assertTrue(restored.suspended());
        assertEquals(before.plan().recipes().get("component_recipe").slots(), restored.plan().recipes().get("component_recipe").slots());
        var attached = new GraphJobRuntime<>(restored); assertEquals(before.remainingDelivery(), attached.snapshot().remainingDelivery());
        assertEquals(encoded, GraphJobCodec.write(attached.snapshot(), REGISTRIES));
    }
    @Test void nativeRegistryCodecRejectsDuplicatedPhysicalOwnershipWithoutChangingTheOriginalSnapshot() {
        var state = runtime(1).snapshot(); var valid = GraphJobCodec.write(state, REGISTRIES); var broken = valid.copy();
        var owned = broken.getList("owned", net.minecraft.nbt.Tag.TAG_COMPOUND); owned.add(owned.getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> GraphJobCodec.read(broken, REGISTRIES));
        assertEquals(valid, GraphJobCodec.write(state, REGISTRIES));
    }
}
