package org.gtlcore.gtlcore.api.item.tool.ae2.patternTool;

import com.gregtechceu.gtceu.common.data.GTItems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import com.tterrag.registrate.util.entry.ItemEntry;

import java.util.ArrayList;
import java.util.List;

public class Ae2BaseProcessingPattern {

    public ItemStack patternStack; // 样板itemStack
    public int scale; // scale是此样板相对于配方的倍数，不能乱改！！！
    public ServerPlayer serverPlayer;
    /*
     * 此处一个对象就是一个样板，因此过滤器决定某些物品/流体会不会在此样板中出现
     */
    public List<Item> DefaultBlackItem = new ArrayList<>(List.of(
            GTItems.PROGRAMMED_CIRCUIT.asItem()));

    public void setDefaultFilter() {
        // 解码，转换为list方便操作
        AEProcessingPattern aeProcessingPattern = Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(patternStack, serverPlayer);
        if (aeProcessingPattern == null) return;
        List<GenericStack> inputGenericStacks = aeProcessingPattern.getSparseInputs();
        List<GenericStack> outputGenericStacks = aeProcessingPattern.getSparseOutputs();

        inputGenericStacks = Ae2BaseProcessingPatternHelper.GenericStackListBlackFilter(inputGenericStacks, DefaultBlackItem);
        outputGenericStacks = Ae2BaseProcessingPatternHelper.GenericStackListBlackFilter(outputGenericStacks, DefaultBlackItem);

        // 重新编码，并更新此对象
        if (inputGenericStacks.isEmpty() || outputGenericStacks.isEmpty()) return;
        var filtered = patternStack.copy();
        AEProcessingPattern.encode(filtered, inputGenericStacks, outputGenericStacks);
        patternStack = filtered;
    }

    public void useSetScale(int newScale, boolean div, long maxItemStack, long maxFluidStack) {
        var decoded = Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(patternStack, serverPlayer);
        if (decoded == null) return;
        // 乘或除到新的份数
        ItemStack newPatternStack = Ae2BaseProcessingPatternHelper.multiplyScale(
                newScale,
                div,
                decoded,
                maxItemStack,
                maxFluidStack);
        if (newPatternStack != null && !newPatternStack.isEmpty()) {
            this.patternStack = newPatternStack;
        }
    }

    public void setScale(int newScale, boolean div, long maxItemStack, long maxFluidStack) {
        useSetScale(newScale, div, maxItemStack, maxFluidStack);
    }

    public void setScale(int newScale, boolean div) {
        long maxItemStack = 9999999L;
        long maxFluidStack = 9999999L;
        useSetScale(newScale, div, maxItemStack, maxFluidStack);
    }

    public void setLore(Component component) {
        this.patternStack.set(DataComponents.LORE, new ItemLore(List.of(component)));
    }

    public ItemStack getPatternItemStack() {
        return this.patternStack;
    }

    public Ae2BaseProcessingPattern(ItemStack patternStack,
                                    ServerPlayer serverPlayer) {
        this.patternStack = patternStack;
        this.serverPlayer = serverPlayer;

        // 不获取返回值，仅检测是否可解码
        Ae2BaseProcessingPatternHelper.decodeToAEProcessingPattern(patternStack, serverPlayer);

        for (ItemEntry<Item> shapeMold : GTItems.SHAPE_MOLDS) {
            if (shapeMold != null) {
                this.DefaultBlackItem.add(shapeMold.asItem());
            }
        }
        for (ItemEntry<Item> shapeMold : GTItems.SHAPE_EXTRUDERS) {
            if (shapeMold != null) {
                this.DefaultBlackItem.add(shapeMold.asItem());
            }
        }
    }
}
