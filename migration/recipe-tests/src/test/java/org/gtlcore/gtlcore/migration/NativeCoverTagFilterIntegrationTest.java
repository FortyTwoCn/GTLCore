package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.cover.filter.TagFilter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class NativeCoverTagFilterIntegrationTest {
    private static TagKey<Item> itemTag(String name) {
        return TagKey.create(Registries.ITEM, ResourceLocation.parse(name));
    }
    private static TagKey<Fluid> fluidTag(String name) {
        return TagKey.create(Registries.FLUID, ResourceLocation.parse(name));
    }
    @Test void nativeItemFilterPreservesNamespaceIndependentExpressionsAndInvalidatesItsOwnResultCache() {
        var filter = new TagFilter<ItemStack, Item>(ItemStack::getItem,
                stack -> Stream.of(itemTag(stack.is(Items.COPPER_INGOT) ? "legacy:ingots/copper" : "c:ingots/gold")));
        var changed = new AtomicInteger(); filter.setOnUpdated(ignored -> changed.incrementAndGet());
        filter.setFilterString("ingots/* & !ingots/gold");
        assertTrue(filter.test(new ItemStack(Items.COPPER_INGOT)));
        assertFalse(filter.test(new ItemStack(Items.GOLD_INGOT)));
        filter.setFilterString("c:ingots/*");
        assertFalse(filter.test(new ItemStack(Items.COPPER_INGOT)));
        assertTrue(filter.test(new ItemStack(Items.GOLD_INGOT)));
        filter.setFilterString("legacy:ingots/copper");
        assertTrue(filter.test(new ItemStack(Items.COPPER_INGOT)));
        assertEquals(3, changed.get());
    }
    @Test void nativeFluidFilterUsesTheSameOriginalTagOperatorsAndFreshExpressionAfterChanges() {
        var filter = new TagFilter<FluidStack, Fluid>(FluidStack::getFluid,
                stack -> Stream.of(fluidTag(stack.getFluid() == Fluids.WATER ? "minecraft:water" : "legacy:hot/lava")));
        var water = new FluidStack(Fluids.WATER, 100);
        var lava = new FluidStack(Fluids.LAVA, 100);
        filter.setFilterString("water | hot/*"); assertTrue(filter.test(water)); assertTrue(filter.test(lava));
        filter.setFilterString("!hot/* & water"); assertTrue(filter.test(water)); assertFalse(filter.test(lava));
        filter.setFilterString("minecraft:water ^ water"); assertFalse(filter.test(water));
        filter.setFilterString("hot/*"); assertTrue(filter.test(lava)); assertFalse(filter.test(water));
    }
    @Test void invalidAndEmptyNativeExpressionsAndUntaggedStacksRejectWithoutCrashing() {
        var tagged = new TagFilter<ItemStack, Item>(ItemStack::getItem, stack -> Stream.of(itemTag("legacy:ingots/copper")));
        var untagged = new TagFilter<ItemStack, Item>(ItemStack::getItem, stack -> Stream.empty());
        var stack = new ItemStack(Items.COPPER_INGOT);
        for (String invalid : new String[] {"", "(", "ingots/* &", "!", "ingots/* | | ingots/gold"}) {
            tagged.setFilterString(invalid); assertFalse(tagged.test(stack), invalid);
        }
        untagged.setFilterString("!ingots/gold"); assertFalse(untagged.test(stack));
    }
}
