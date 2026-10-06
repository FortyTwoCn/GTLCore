package org.gtlcore.gtlcore.api.recipe.ingredient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.common.crafting.ICustomIngredient;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.neoforged.neoforge.common.crafting.SizedIngredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import java.util.stream.Stream;

/** Immutable long quantity carried inside NeoForge's final, int-sized recipe wrapper. */
public final class LongIngredient implements ICustomIngredient {

    // The old GTCEu.id("Long") normalized its argument to this same registry ID.
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("gtceu", "long");
    public static final MapCodec<LongIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Ingredient.CODEC_NONEMPTY.fieldOf("ingredient").forGetter(LongIngredient::getInner),
            Codec.LONG.validate(value -> value > 0 ? DataResult.success(value) :
                    DataResult.error(() -> "Recipe quantity must be positive"))
                    .fieldOf("actualAmount").forGetter(LongIngredient::getActualAmount)
    ).apply(instance, LongIngredient::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, LongIngredient> STREAM_CODEC = StreamCodec.composite(
            Ingredient.CONTENTS_STREAM_CODEC, LongIngredient::getInner,
            ByteBufCodecs.VAR_LONG, LongIngredient::getActualAmount,
            LongIngredient::new);
    public static final IngredientType<LongIngredient> INGREDIENT_TYPE = new IngredientType<>(CODEC, STREAM_CODEC);

    private final Ingredient inner;
    private final long actualAmount;

    private LongIngredient(Ingredient inner, long actualAmount) {
        this.inner = Objects.requireNonNull(inner, "inner");
        if (actualAmount <= 0) throw new IllegalArgumentException("Recipe quantity must be positive");
        this.actualAmount = actualAmount;
    }

    public Ingredient getInner() { return inner; }
    public long getActualAmount() { return actualAmount; }

    public static SizedIngredient create(Ingredient inner, long amount) {
        var nested = get(inner);
        if (nested != null) inner = nested.inner;
        return new SizedIngredient(new LongIngredient(inner, amount).toVanilla(), displayCount(amount));
    }

    public static SizedIngredient create(Ingredient inner) { return create(inner, 1); }
    public static SizedIngredient create(TagKey<Item> tag, long amount) { return create(Ingredient.of(tag), amount); }
    public static SizedIngredient create(ItemStack stack) { return create(stack, 1); }

    public static SizedIngredient create(ItemStack stack, long amount) {
        Ingredient ingredient = stack.getComponentsPatch().isEmpty() ? Ingredient.of(stack) :
                DataComponentIngredient.of(true, stack);
        return create(ingredient, amount);
    }

    public static @Nullable LongIngredient get(SizedIngredient ingredient) {
        return get(ingredient.ingredient());
    }

    public static @Nullable LongIngredient get(Ingredient ingredient) {
        return ingredient.getCustomIngredient() instanceof LongIngredient value ? value : null;
    }

    public static long amount(SizedIngredient ingredient) {
        var value = get(ingredient);
        return value == null ? ingredient.count() : value.actualAmount;
    }

    public static Ingredient matchingIngredient(SizedIngredient ingredient) {
        var value = get(ingredient);
        return value == null ? ingredient.ingredient() : value.inner;
    }

    public static SizedIngredient withAmount(SizedIngredient ingredient, long amount) {
        return get(ingredient) != null || amount > Integer.MAX_VALUE ?
                create(matchingIngredient(ingredient), amount) : new SizedIngredient(ingredient.ingredient(), (int) amount);
    }

    public static SizedIngredient copy(SizedIngredient ingredient) {
        return withAmount(ingredient, amount(ingredient));
    }

    /** Preserve integer precision above 2^53 and saturate overflow rather than wrapping. */
    public static SizedIngredient modify(SizedIngredient ingredient, double multiplier, double addition) {
        if (!Double.isFinite(multiplier) || !Double.isFinite(addition)) {
            throw new IllegalArgumentException("Recipe quantity modifiers must be finite");
        }
        BigInteger result = BigDecimal.valueOf(amount(ingredient)).multiply(BigDecimal.valueOf(multiplier))
                .add(BigDecimal.valueOf(addition)).toBigInteger();
        long amount = result.signum() <= 0 ? 1 : result.min(BigInteger.valueOf(Long.MAX_VALUE)).longValueExact();
        return withAmount(ingredient, amount);
    }

    private static int displayCount(long amount) { return (int) Math.min(Integer.MAX_VALUE, amount); }

    @Override
    public boolean test(ItemStack stack) { return inner.test(stack); }

    @Override
    public Stream<ItemStack> getItems() { return Stream.of(inner.getItems()).map(ItemStack::copy); }

    @Override
    public boolean isSimple() { return false; }

    @Override
    public IngredientType<?> getType() { return INGREDIENT_TYPE; }

    @Override
    public boolean equals(Object other) {
        return other instanceof LongIngredient ingredient && actualAmount == ingredient.actualAmount &&
                inner.equals(ingredient.inner);
    }

    @Override
    public int hashCode() { return Objects.hash(inner, actualAmount); }
}
