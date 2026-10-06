package org.gtlcore.gtlcore.mixin.gtm;

import org.gtlcore.gtlcore.integration.jade.NativeRecipeLogicTooltip;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.integration.jade.provider.RecipeLogicProvider;
import net.minecraft.nbt.CompoundTag;
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

@Mixin(RecipeLogicProvider.class)
public abstract class RecipeLogicProviderMixin {
    @Inject(method = "write(Lcom/gregtechceu/gtceu/api/machine/trait/recipe/RecipeLogic;)Lnet/minecraft/nbt/CompoundTag;",
            at = @At("RETURN"), remap = false, require = 1)
    private void gtlcore$writeStatus(RecipeLogic logic, CallbackInfoReturnable<CompoundTag> cir) {
        NativeRecipeLogicTooltip.writeStatus(cir.getReturnValue(), logic,
                logic.getMachine().getLevel().registryAccess());
    }

    @Inject(method = "addTooltip(Lnet/minecraft/nbt/CompoundTag;Lsnownee/jade/api/ITooltip;Lnet/minecraft/world/entity/player/Player;Lsnownee/jade/api/BlockAccessor;Lnet/minecraft/world/level/block/entity/BlockEntity;Lsnownee/jade/api/config/IPluginConfig;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private void gtlcore$nativeStatusTooltip(CompoundTag data, ITooltip tooltip, Player player,
                                           BlockAccessor accessor, BlockEntity entity, IPluginConfig config,
                                           CallbackInfo ci) {
        NativeRecipeLogicTooltip.rows(data, NativeRecipeLogicTooltip.steamRate(entity),
                accessor.getLevel().registryAccess()).forEach(tooltip::add);
        ci.cancel();
    }
}
