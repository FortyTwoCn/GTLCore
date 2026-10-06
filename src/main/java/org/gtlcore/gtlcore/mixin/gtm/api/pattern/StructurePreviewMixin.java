package org.gtlcore.gtlcore.mixin.gtm.api.pattern;

import org.gtlcore.gtlcore.common.data.machines.GTLGCYMMachinePolicies;

import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.IBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;

import com.google.common.collect.HashBasedTable;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

/** Carry Core's explicit IO previews into native pattern preferences, without changing formation limits or user choices. */
@Mixin(AbstractStructureHelper.class)
public abstract class StructurePreviewMixin {
    @Shadow(remap = false) @Final
    protected HashBasedTable<MultiPredicate, BasePredicate, IntIntPair> minMaxPreferences;

    @Inject(method = "populate", at = @At("HEAD"), remap = false, require = 1)
    private void gtlcore$gcymPreview(Map<BlockPos, BlockInfo> result, IBlockPattern candidate,
            Long2ObjectMap<BlockInfo> userBlocks, Direction front, Direction up, boolean flipped, CallbackInfo ci) {
        if (!(candidate instanceof BlockPattern pattern)) return;
        boolean core = pattern.getPredicates().values().stream().filter(MultiPredicate::isController)
                .flatMap(predicate -> predicate.getCandidates().stream()).flatMap(java.util.Collection::stream)
                .map(info -> BuiltInRegistries.BLOCK.getKey(info.getBlockState().getBlock()))
                .anyMatch(GTLGCYMMachinePolicies::hasCorePreview);
        if (!core) return;
        for (var predicate : pattern.getPredicates().values()) {
            for (var base : predicate.expand()) {
                if (base.getPreviewCount() < 0 || minMaxPreferences.contains(predicate, base)) continue;
                int minimum = Math.max(base.getMinCount(), base.getPreviewCount());
                minMaxPreferences.put(predicate, base, IntIntPair.of(minimum, base.getMaxCount()));
            }
        }
    }
}
