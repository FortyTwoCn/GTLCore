package org.gtlcore.gtlcore.api.pattern;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.ITieredMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.error.PatternStringError;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static com.gregtechceu.gtceu.api.multiblock.Predicates.blocks;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.builder;

public final class GTLPredicates {
    private GTLPredicates() {}

    public static MultiPredicate tierCasings(Int2ObjectMap<Supplier<?>> map, String tierType) {
        Objects.requireNonNull(tierType);
        Map<Block, Integer> tiers = map.int2ObjectEntrySet().stream().collect(Collectors.toMap(
                entry -> (Block) entry.getValue().get(), Int2ObjectMap.Entry::getIntKey,
                (previous, next) -> { throw new IllegalArgumentException("One casing block has two tiers"); },
                LinkedHashMap::new));
        List<BlockInfo> candidates = tiers.keySet().stream().map(BlockInfo::fromBlock).toList();
        return builder("GTLTierCasings:" + tierType)
                .predicate(context -> {
                    Integer tier = tiers.get(context.state().getBlock());
                    return tier != null && PatternMatchData.of(context).matchTier(tierType, tier);
                })
                .errorFunction(context -> PatternStringError.translatable("gtceu.multiblock.pattern.error.tier"))
                .candidates(candidates)
                .toMultiPredicate()
                .addTooltips(Component.translatable("gtceu.multiblock.pattern.error.tier"));
    }

    public static MultiPredicate countBlock(String name, Block... blocks) {
        Objects.requireNonNull(name);
        List<Block> accepted = List.of(blocks);
        return builder("GTLCountBlock:" + name)
                .predicate(context -> {
                    if (!accepted.contains(context.state().getBlock())) return false;
                    PatternMatchData.of(context).countBlock(name, context.pos());
                    return true;
                })
                .candidates(accepted.stream().map(BlockInfo::fromBlock))
                .toMultiPredicate();
    }

    public static MultiPredicate RotorBlock() {
        return builder("GTLReinforcedRotor")
                .predicate(context -> {
                    if (!PartAbility.ROTOR_HOLDER.isApplicable(context.state().getBlock())) return false;
                    MetaMachine machine = MetaMachine.getMachine(context.level(), context.pos());
                    if (!(machine instanceof ITieredMachine tiered)) return false;
                    if (!context.level().getBlockState(context.pos().relative(machine.getFrontFacing())).isAir()) {
                        return false;
                    }
                    return PatternMatchData.of(context).matchTier("ReinforcedRotor", tiered.getTier());
                })
                .candidatesSupplier(() -> PartAbility.ROTOR_HOLDER.getAllBlocks().stream()
                        .map(BlockInfo::fromBlock).toList())
                .toMultiPredicate();
    }

    /** The blocks in the first ability group that do not occur in the second. */
    public static MultiPredicate diffAbilities(Collection<PartAbility> group1, Collection<PartAbility> group2) {
        ObjectSet<Block> difference = unionBlocks(group1);
        difference.removeAll(unionBlocks(group2));
        return blocks(difference.toArray(Block[]::new));
    }

    private static ObjectSet<Block> unionBlocks(Collection<PartAbility> group) {
        Objects.requireNonNull(group);
        ObjectSet<Block> out = new ObjectOpenHashSet<>();
        for (PartAbility ability : group) out.addAll(ability.getAllBlocks());
        return out;
    }
}
