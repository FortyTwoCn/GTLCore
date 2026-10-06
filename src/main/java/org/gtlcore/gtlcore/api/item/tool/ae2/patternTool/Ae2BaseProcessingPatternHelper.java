package org.gtlcore.gtlcore.api.item.tool.ae2.patternTool;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.crafting.pattern.EncodedPatternItem;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

public class Ae2BaseProcessingPatternHelper {

    // 乘或除 输入解码后样板，输出编码后样板
    public static @Nullable ItemStack multiplyScale(int scale, boolean div, AEProcessingPattern patternDetail, long maxItemStack, long maxFluidStack) {
        if (scale <= 0) return null;
        var input = patternDetail.getSparseInputs();
        var output = patternDetail.getSparseOutputs();
        if (checkModify(input, scale, div, maxItemStack, maxFluidStack) && checkModify(output, scale, div, maxItemStack, maxFluidStack)) {
            // Rewrite only the encoded pattern component; retain its name, lore and other item data.
            var result = patternDetail.getDefinition().toStack();
            AEProcessingPattern.encode(result, modifyStacks(input, scale, div), modifyStacks(output, scale, div));
            return result;
        }
        return null;
    }

    // 从样板物品解码样板
    public static @Nullable AEProcessingPattern decodeToAEProcessingPattern(ItemStack patternStack, @Nullable ServerPlayer serverPlayer) {
        if (patternStack.getItem() instanceof EncodedPatternItem<?> patternItem &&
                patternItem.decode(AEItemKey.of(patternStack), serverPlayer == null ? null : serverPlayer.level()) instanceof AEProcessingPattern processing) {
            return processing;
        }
        return null;
    }

    // 转换到list方便操作
    public static List<GenericStack> transGenericStackArrayToList(GenericStack[] genericStackArray) {
        return new ArrayList<>(Arrays.asList(genericStackArray));
    }

    // 转换回[]，构造AE2 API参数
    public static GenericStack[] transGenericStackListToArray(List<GenericStack> genericStackList) {
        GenericStack[] genericStackArrayList = new GenericStack[genericStackList.size()];
        return genericStackList.toArray(genericStackArrayList);
    }

    // 返回排除黑名单物品后的列表
    public static List<GenericStack> GenericStackListBlackFilter(List<GenericStack> inputGenericStacks, List<Item> matchItemList) {
        return inputGenericStacks.stream().filter(
                genericStack -> genericStack != null && !itemMatch(genericStack, matchItemList)).toList();
    }

    private static boolean itemMatch(GenericStack genericStack, List<Item> matchItemList) {
        // Configured circuits and named/damaged molds still represent the same blacklisted item.
        return genericStack.what() instanceof AEItemKey key && matchItemList.contains(key.getItem());
    }

    private static boolean checkModify(List<GenericStack> stacks, int scale, boolean div, long maxItemStack, long maxFluidStack) {
        Map<AEKey, Long> totals = new HashMap<>();
        for (var stack : stacks) {
            if (stack == null) continue;
            if (stack.amount() <= 0) return false;
            if (div && stack.amount() % scale != 0) return false;
            if (!div && stack.amount() > Long.MAX_VALUE / scale) return false;
            long amount = div ? stack.amount() / scale : stack.amount() * scale;
            if (!div) {
                long maximum = stack.what().getType().equals(AEKeyType.fluids()) ? maxFluidStack :
                        stack.what().getType().equals(AEKeyType.items()) ? maxItemStack : Long.MAX_VALUE;
                int unit = stack.what().getAmountPerUnit();
                if (maximum <= 0 || unit <= 0) return false;
                long upper = maximum > Long.MAX_VALUE / unit ? Long.MAX_VALUE : maximum * unit;
                if (amount > upper) return false;
            }
            // AE2 condenses repeated keys when decoding. That sum must not overflow either.
            long previous = totals.getOrDefault(stack.what(), 0L);
            if (amount > Long.MAX_VALUE - previous) return false;
            totals.put(stack.what(), previous + amount);
        }
        return true;
    }

    private static List<GenericStack> modifyStacks(List<GenericStack> stacks, int scale, boolean div) {
        List<GenericStack> result = new ArrayList<>(stacks.size());
        for (var stack : stacks) {
            result.add(stack == null ? null : new GenericStack(stack.what(),
                    div ? stack.amount() / scale : stack.amount() * scale));
        }
        return result;
    }
}
