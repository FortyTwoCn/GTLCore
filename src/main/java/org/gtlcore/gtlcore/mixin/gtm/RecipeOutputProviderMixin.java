package org.gtlcore.gtlcore.mixin.gtm;

import org.gtlcore.gtlcore.integration.jade.NativeRecipeOutputRows;
import org.gtlcore.gtlcore.utils.NumberUtils;
import com.gregtechceu.gtceu.api.capability.recipe.RecipeCapability;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.integration.jade.GTElementHelper;
import com.gregtechceu.gtceu.integration.jade.provider.RecipeOutputProvider;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.api.ui.IElementHelper;
import java.util.List;

@Mixin(RecipeOutputProvider.class)
public abstract class RecipeOutputProviderMixin {
    @WrapOperation(method = "write(Lcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At(value = "INVOKE", target = "Lcom/gregtechceu/gtceu/api/recipe/GTRecipe;getOutputContents(Lcom/gregtechceu/gtceu/api/capability/recipe/RecipeCapability;)Ljava/util/List;"), remap = false)
    private List<Content> gtlcore$nativeSizedOutputs(GTRecipe recipe, RecipeCapability<?> capability, Operation<List<Content>> original) {
        return original.call(recipe, capability).stream().filter(content -> !NativeRecipeOutputRows.isLong(capability, content)).toList();
    }

    @Inject(method = "write(Lcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;)Lnet/minecraft/nbt/CompoundTag;", at = @At("RETURN"), remap = false)
    private void gtlcore$appendLongOutputs(RecipeLogic logic, CallbackInfoReturnable<CompoundTag> cir) {
        if (logic.isWorking()) NativeRecipeOutputRows.append(logic.getLastUnrolledRecipe(), logic.getMachine().getLevel().registryAccess(), cir.getReturnValue());
    }

    @Inject(method = "addTooltip(Lnet/minecraft/nbt/CompoundTag;Lsnownee/jade/api/ITooltip;Lnet/minecraft/world/entity/player/Player;Lsnownee/jade/api/BlockAccessor;Lnet/minecraft/world/level/block/entity/BlockEntity;Lsnownee/jade/api/config/IPluginConfig;)V", at = @At("TAIL"), remap = false)
    private void gtlcore$longOutputTooltips(CompoundTag data, ITooltip tooltip, Player player, BlockAccessor accessor,
                                          BlockEntity entity, IPluginConfig config, CallbackInfo ci) {
        if (!data.getBoolean("Working")) return;
        var registries = accessor.getLevel().registryAccess();
        var items = NativeRecipeOutputRows.readItems(data, registries); var fluids = NativeRecipeOutputRows.readFluids(data, registries);
        if (items.isEmpty() && fluids.isEmpty()) return;
        if (!data.contains("OutputItems") && !data.contains("OutputFluids")) tooltip.add(Component.translatable("gtceu.top.recipe_output"));
        var helper = IElementHelper.get();
        for (var row : items) {
            tooltip.add(helper.smallItem(row.stack()));
            tooltip.append(Component.literal(" ").append(NumberUtils.formatLong(row.amount())).append("× ")
                    .append(row.stack().getHoverName()).withStyle(ChatFormatting.WHITE));
        }
        for (var row : fluids) {
            var stack = row.stack();
            tooltip.add(GTElementHelper.smallFluid(JadeFluidObject.of(stack.getFluid(), row.amount(), stack.getComponentsPatch())));
            tooltip.append(Component.literal(" ").append(NumberUtils.formatLong(row.amount())).append(" mB ")
                    .append(stack.getHoverName()).withStyle(ChatFormatting.WHITE));
        }
    }
}
