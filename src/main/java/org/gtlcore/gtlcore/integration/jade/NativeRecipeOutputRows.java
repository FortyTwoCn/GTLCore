package org.gtlcore.gtlcore.integration.jade;

import org.gtlcore.gtlcore.api.recipe.ingredient.LongIngredient;
import org.gtlcore.gtlcore.api.recipe.ingredient.LongFluidIngredient;
import org.gtlcore.gtlcore.utils.NumberUtils;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEFluidKey;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import java.math.BigInteger;
import java.util.*;

/** Long Jade output rows use native stack codecs while grouping by all item/fluid components. */
public final class NativeRecipeOutputRows {
    public static final String ITEMS = "GTLRecipeOutputItems", FLUIDS = "GTLRecipeOutputFluids";
    private NativeRecipeOutputRows() {}
    public record ItemRow(ItemStack stack, long amount) {}
    public record FluidRow(FluidStack stack, long amount) {}

    public static boolean isLong(RecipeCapability<?> capability, Content content) {
        if (capability == ItemRecipeCapability.CAP) return LongIngredient.get(ItemRecipeCapability.CAP.of(content.content())) != null;
        if (capability == FluidRecipeCapability.CAP) return LongFluidIngredient.get(FluidRecipeCapability.CAP.of(content.content())) != null;
        return false;
    }

    public static void append(GTRecipe recipe, HolderLookup.Provider registries, CompoundTag data) {
        if (recipe == null) return;
        var items = new LinkedHashMap<AEItemKey, Long>();
        var fluids = new LinkedHashMap<AEFluidKey, Long>();
        for (var content : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
            if (!isLong(ItemRecipeCapability.CAP, content)) continue;
            var ingredient = ItemRecipeCapability.CAP.of(content.content()); var stacks = ingredient.getItems();
            if (stacks.length == 0 || stacks[0].isEmpty()) continue;
            var key = AEItemKey.of(stacks[0]);
            items.merge(key, displayAmount(LongIngredient.amount(ingredient), content, recipe.getTotalRuns()), NumberUtils::saturatedAdd);
        }
        for (var content : recipe.getOutputContents(FluidRecipeCapability.CAP)) {
            if (!isLong(FluidRecipeCapability.CAP, content)) continue;
            var ingredient = FluidRecipeCapability.CAP.of(content.content()); var stacks = ingredient.getFluids();
            if (stacks.length == 0 || stacks[0].isEmpty()) continue;
            var key = AEFluidKey.of(stacks[0]);
            fluids.merge(key, displayAmount(LongFluidIngredient.amount(ingredient), content, recipe.getTotalRuns()), NumberUtils::saturatedAdd);
        }
        var itemRows = new ListTag();
        items.forEach((key, amount) -> {
            var row = new CompoundTag(); row.put("stack", key.toStack(1).save(registries)); row.putLong("amount", amount); itemRows.add(row);
        });
        var fluidRows = new ListTag();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        fluids.forEach((key, amount) -> {
            var row = new CompoundTag(); row.put("stack", FluidStack.CODEC.encodeStart(ops, key.toStack(1)).getOrThrow()); row.putLong("amount", amount); fluidRows.add(row);
        });
        if (!itemRows.isEmpty()) data.put(ITEMS, itemRows);
        if (!fluidRows.isEmpty()) data.put(FLUIDS, fluidRows);
    }

    // Follow the native provider's expected chance/batch display without converting large counts to double.
    private static long displayAmount(long amount, Content content, int runs) {
        if (content.chance() >= content.maxChance()) return amount;
        if (content.maxChance() <= 0) return amount;
        var numerator = BigInteger.valueOf(amount).multiply(BigInteger.valueOf(runs)).multiply(BigInteger.valueOf(content.chance()));
        var divisor = BigInteger.valueOf(content.maxChance());
        var rounded = numerator.add(divisor.divide(BigInteger.TWO)).divide(divisor);
        return rounded.max(BigInteger.ONE).min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
    }

    public static List<ItemRow> readItems(CompoundTag data, HolderLookup.Provider registries) {
        var result = new ArrayList<ItemRow>();
        for (var tag : data.getList(ITEMS, Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) tag; long amount = row.getLong("amount");
            var stack = ItemStack.parseOptional(registries, row.getCompound("stack"));
            if (amount > 0 && !stack.isEmpty()) result.add(new ItemRow(stack, amount));
        }
        return result;
    }

    public static List<FluidRow> readFluids(CompoundTag data, HolderLookup.Provider registries) {
        var result = new ArrayList<FluidRow>(); var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        for (var tag : data.getList(FLUIDS, Tag.TAG_COMPOUND)) {
            var row = (CompoundTag) tag; long amount = row.getLong("amount");
            var stack = FluidStack.CODEC.parse(ops, row.getCompound("stack")).result().orElse(FluidStack.EMPTY);
            if (amount > 0 && !stack.isEmpty()) result.add(new FluidRow(stack, amount));
        }
        return result;
    }
}
