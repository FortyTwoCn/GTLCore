package org.gtlcore.gtlcore.integration.ftbu;

import org.gtlcore.gtlcore.config.ConfigHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import dev.ftb.mods.ftbultimine.api.shape.RegisterShapeEvent;
import dev.ftb.mods.ftbultimine.api.shape.Shape;
import dev.ftb.mods.ftbultimine.api.shape.ShapeContext;
import dev.ftb.mods.ftbultimine.utils.EntityDistanceComparator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.antlr.v4.runtime.misc.OrderedHashSet;

import java.util.*;

public class AreaShape implements Shape {

    public static void register() {
        RegisterShapeEvent.REGISTER.register(registry -> registry.register(new AreaShape()));
    }

    private static final class Neighbor {

        private final ArrayList<BlockPos> neighbors = new ArrayList<>();
        private int range;

        private Neighbor(int r) {
            update(r);
        }

        private void update(int r) {
            range = r;
            neighbors.clear();
            final var length = (range << 1) + 1;
            neighbors.ensureCapacity(length * length * length - 1);
            for (int x = -range; x <= range; ++x) {
                for (int y = -range; y <= range; ++y) {
                    for (int z = -range; z <= range; ++z) {
                        if (x != 0 || y != 0 || z != 0) {
                            neighbors.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
    }

    private static final AreaShape.Neighbor NEIGHBOR_POSITIONS = new AreaShape.Neighbor(0);

    @Override
    public ResourceLocation getName() {
        return ResourceLocation.fromNamespaceAndPath("gtlcore", "area");
    }

    @Override
    public List<BlockPos> getBlocks(ShapeContext context) {
        if (NEIGHBOR_POSITIONS.range != ConfigHolder.INSTANCE.ftbUltimineRange) {
            NEIGHBOR_POSITIONS.update(ConfigHolder.INSTANCE.ftbUltimineRange);
        }

        Set<BlockPos> known = new OrderedHashSet<>();
        walk(context, known);

        List<BlockPos> list = new ArrayList<>(known);
        list.sort(new EntityDistanceComparator(context.origPos()));

        if (list.size() > context.maxBlocks()) {
            list.subList(context.maxBlocks(), list.size()).clear();
        }

        return list;
    }

    private static void walk(ShapeContext context, Set<BlockPos> known) {
        LongOpenHashSet traversed = new LongOpenHashSet();
        Deque<BlockPos> openSet = new ArrayDeque<>();
        openSet.add(context.origPos());
        traversed.add(context.origPos().asLong());

        while (!openSet.isEmpty()) {
            BlockPos ptr = openSet.pop();

            if (context.check(ptr) && known.add(ptr)) {
                if (known.size() >= context.maxBlocks()) {
                    return;
                }

                for (BlockPos side : NEIGHBOR_POSITIONS.neighbors) {
                    BlockPos offset = ptr.offset(side);

                    if (traversed.add(offset.asLong())) {
                        openSet.add(offset);
                    }
                }
            }
        }
    }

    @Override
    public boolean isIndeterminateShape() {
        return true;
    }
}
