package org.gtlcore.gtlcore.api.recipe.ingredient;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.crafting.DataComponentFluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredient;
import net.neoforged.neoforge.fluids.crafting.FluidIngredientType;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

/** Actual long quantity inside NeoForge's immutable, int-sized fluid recipe wrapper. */
public final class LongFluidIngredient extends FluidIngredient {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("gtlcore", "long_fluid");
    public static final MapCodec<LongFluidIngredient> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            FluidIngredient.CODEC_NON_EMPTY.fieldOf("ingredient").forGetter(LongFluidIngredient::getInner),
            Codec.LONG.validate(value -> value > 0 ? DataResult.success(value) :
                    DataResult.error(() -> "Recipe fluid quantity must be positive"))
                    .fieldOf("actualAmount").forGetter(LongFluidIngredient::getActualAmount)
    ).apply(instance, LongFluidIngredient::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, LongFluidIngredient> STREAM_CODEC = StreamCodec.composite(
            FluidIngredient.STREAM_CODEC, LongFluidIngredient::getInner,
            ByteBufCodecs.VAR_LONG, LongFluidIngredient::getActualAmount,
            LongFluidIngredient::new);
    public static final FluidIngredientType<LongFluidIngredient> INGREDIENT_TYPE =
            new FluidIngredientType<>(CODEC, STREAM_CODEC);

    private final FluidIngredient inner;
    private final long actualAmount;

    private LongFluidIngredient(FluidIngredient inner, long actualAmount) {
        this.inner = Objects.requireNonNull(inner, "inner");
        if (actualAmount <= 0) throw new IllegalArgumentException("Recipe fluid quantity must be positive");
        this.actualAmount = actualAmount;
    }

    public FluidIngredient getInner() { return inner; }
    public long getActualAmount() { return actualAmount; }

    public static SizedFluidIngredient create(FluidIngredient inner, long amount) {
        if (inner instanceof LongFluidIngredient nested) inner = nested.inner;
        return new SizedFluidIngredient(new LongFluidIngredient(inner, amount),
                (int) Math.min(Integer.MAX_VALUE, amount));
    }

    public static SizedFluidIngredient create(Fluid fluid, long amount) {
        return create(FluidIngredient.single(fluid), amount);
    }

    public static SizedFluidIngredient create(TagKey<Fluid> tag, long amount) {
        return create(FluidIngredient.tag(tag), amount);
    }

    public static SizedFluidIngredient create(FluidStack stack, long amount) {
        return create(stack.getComponentsPatch().isEmpty() ? FluidIngredient.single(stack) :
                DataComponentFluidIngredient.of(true, stack), amount);
    }

    public static @Nullable LongFluidIngredient get(SizedFluidIngredient ingredient) {
        return ingredient.ingredient() instanceof LongFluidIngredient value ? value : null;
    }

    public static long amount(SizedFluidIngredient ingredient) {
        var value = get(ingredient);
        return value == null ? ingredient.amount() : value.actualAmount;
    }

    public static FluidIngredient matchingIngredient(SizedFluidIngredient ingredient) {
        var value = get(ingredient);
        return value == null ? ingredient.ingredient() : value.inner;
    }

    public static SizedFluidIngredient withAmount(SizedFluidIngredient ingredient, long amount) {
        return get(ingredient) != null || amount > Integer.MAX_VALUE ?
                create(matchingIngredient(ingredient), amount) :
                new SizedFluidIngredient(ingredient.ingredient(), Math.toIntExact(amount));
    }

    public static SizedFluidIngredient copy(SizedFluidIngredient ingredient) {
        return withAmount(ingredient, amount(ingredient));
    }

    @Override public boolean test(FluidStack stack) { return inner.test(stack); }
    @Override protected Stream<FluidStack> generateStacks() { return Stream.of(inner.getStacks()).map(FluidStack::copy); }
    @Override public boolean isSimple() { return false; }
    @Override public FluidIngredientType<?> getType() { return INGREDIENT_TYPE; }

    @Override
    public boolean equals(Object other) {
        return other instanceof LongFluidIngredient ingredient && actualAmount == ingredient.actualAmount &&
                inner.equals(ingredient.inner);
    }

    @Override public int hashCode() { return Objects.hash(inner, actualAmount); }
}
