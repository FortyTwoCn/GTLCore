package org.gtlcore.gtlcore.common.recipe.condition;

import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.data.GTLRecipeConditions;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.IGravityPartMachine;
import org.gtlcore.gtlcore.config.ConfigHolder;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;
import com.gregtechceu.gtceu.api.recipe.condition.RecipeConditionType;
import net.minecraft.network.chat.Component;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Objects;

/** Native condition codec includes the altitude threshold in JSON, item NBT and recipe packets. */
public class GravityCondition extends RecipeCondition<GravityCondition> {
    public static final MapCodec<GravityCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> RecipeCondition.isReverse(instance)
            .and(instance.group(Codec.BOOL.optionalFieldOf("gravity", false).forGetter(value -> value.zero),
                    Codec.INT.optionalFieldOf("height_threshold", 313).forGetter(value -> value.heightThreshold)))
            .apply(instance, GravityCondition::new));
    public static final GravityCondition INSTANCE = new GravityCondition();
    private final boolean zero;
    private final int heightThreshold;
    public GravityCondition() { this(false, false, 313); }
    public GravityCondition(boolean zero) { this(false, zero, 313); }
    public GravityCondition(boolean reverse, boolean zero) { this(reverse, zero, 313); }
    public GravityCondition(boolean zero, int threshold) { this(false, zero, threshold); }
    public GravityCondition(boolean reverse, boolean zero, int threshold) { super(reverse); this.zero = zero; heightThreshold = threshold; }
    public boolean isZeroGravity() { return zero; }
    public int getHeightThreshold() { return heightThreshold; }
    @Override public RecipeConditionType<GravityCondition> getType() { return GTLRecipeConditions.GRAVITY.value(); }
    @Override public Component getTooltips() { return Component.translatable("gtlcore.condition." + (zero ? "zero_" : "") + "gravity"); }
    @Override public boolean testCondition(GTRecipe recipe, RecipeLogic logic) {
        var machine = logic.getMachine();
        if (machine instanceof MultiblockControllerMachine controller) {
            for (var part : controller.getParts()) {
                if (part instanceof IGravityPartMachine gravity) {
                    if (gravity.getCurrentGravity() == (zero ? 0 : 100)) return true;
                    if (machine instanceof IRecipeLogicMachine holder) RecipeResult.of(holder, RecipeResult.fail(Component.translatable(
                            "gtceu.recipe.fail.gravity" + (zero ? ".low" : ".power"))));
                }
            }
        }
        var level = Objects.requireNonNull(machine.getLevel(), "Machine level cannot be null");
        return testEnvironment(zero, heightThreshold, ConfigHolder.INSTANCE.enableSkyBlokeMode,
                level.dimension().location().toString(), machine.getBlockPos().getY());
    }
    public static boolean testEnvironment(boolean zero, int threshold, boolean skyblock, String dimension, int altitude) {
        if (zero && dimension.contains("_orbit")) return true;
        return skyblock && zero && (dimension.equals("minecraft:overworld") && altitude >= threshold ||
                (dimension.equals("kubejs:void") || dimension.equals("kubejs:flat")) && altitude >= 505);
    }
    @Override public GravityCondition createTemplate() { return new GravityCondition(); }
}
