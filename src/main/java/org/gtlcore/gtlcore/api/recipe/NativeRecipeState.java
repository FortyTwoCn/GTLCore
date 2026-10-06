package org.gtlcore.gtlcore.api.recipe;

import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/** Namespaced, copy-isolated metadata for quantities and state absent from GT's native fields. */
public final class NativeRecipeState {
    private static final String KEY = "gtlcore:recipe_state";
    private NativeRecipeState() {}
    private static CompoundTag read(GTRecipe recipe) { return recipe.data.getCompound(KEY); }
    private static CompoundTag write(GTRecipe recipe) {
        recipe.data = recipe.data.copy();
        CompoundTag state = recipe.data.getCompound(KEY);
        recipe.data.put(KEY, state);
        return state;
    }
    public static long realParallels(GTRecipe recipe) {
        var state = read(recipe);
        if (state.contains("real_parallels", Tag.TAG_ANY_NUMERIC)) return Math.max(1, state.getLong("real_parallels"));
        long count = Math.max(1, recipe.parallels);
        count = saturatedMultiply(count, Math.max(1, recipe.subtickParallels));
        return saturatedMultiply(count, Math.max(1, recipe.batchParallels));
    }
    public static void realParallels(GTRecipe recipe, long value) {
        if (value < 1) throw new IllegalArgumentException("Recipe parallels must be positive");
        nativeParallels(recipe, value);
        recipe.parallels = (int) Math.min(Integer.MAX_VALUE, Math.max(1, value / Math.max(1, recipe.subtickParallels) / Math.max(1, recipe.batchParallels)));
    }
    /** Native modifier copies already contain their individual regular/subtick/batch fields. */
    public static void nativeParallels(GTRecipe recipe, long value) {
        if (value < 1) throw new IllegalArgumentException("Recipe parallels must be positive");
        write(recipe).putLong("real_parallels", value);
    }
    public static int batchSize(GTRecipe recipe) {
        var state = read(recipe);
        return state.contains("batch_size", Tag.TAG_ANY_NUMERIC) ? Math.max(1, state.getInt("batch_size")) :
                Math.max(1, recipe.batchParallels);
    }
    public static void batchSize(GTRecipe recipe, int value) {
        if (value < 1) throw new IllegalArgumentException("Recipe batch size must be positive");
        write(recipe).putInt("batch_size", value);
    }
    public static boolean flag(GTRecipe recipe, String name) { return read(recipe).getBoolean(name); }
    public static void flag(GTRecipe recipe, String name, boolean value) { write(recipe).putBoolean(name, value); }
    public static @Nullable Boolean tickOverride(GTRecipe recipe) {
        var state = read(recipe);
        return state.contains("has_tick", Tag.TAG_BYTE) ? state.getBoolean("has_tick") : null;
    }
    public static void clearTickOverride(GTRecipe recipe) { write(recipe).remove("has_tick"); }
    /** Encode a detached snapshot, including the native overclock level omitted by its default codec. */
    public static GTRecipe snapshot(GTRecipe recipe) {
        GTRecipe copy = recipe.copy();
        write(copy).putInt("oc_level", Math.max(0, recipe.ocLevel));
        NativeContentState.snapshot(copy);
        return copy;
    }
    public static GTRecipe restore(GTRecipe recipe) {
        if (read(recipe).contains("oc_level", Tag.TAG_ANY_NUMERIC)) recipe.ocLevel = Math.max(0, read(recipe).getInt("oc_level"));
        NativeContentState.restore(recipe);
        return recipe;
    }
    private static long saturatedMultiply(long first, long second) {
        return first > Long.MAX_VALUE / second ? Long.MAX_VALUE : first * second;
    }
}
