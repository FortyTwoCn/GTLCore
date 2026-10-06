package org.gtlcore.gtlcore.client.preview;

import org.gtlcore.gtlcore.mixin.ldlib.BlockInfoAccessor;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewGeometry;

import com.lowdragmc.lowdraglib.utils.data.BlockInfo;

import net.minecraft.core.BlockPos;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/** Geometry can be shared; mutable BlockInfo entity caches cannot. */
public final class SparsePreviewShape {

    public final long[] positions;
    public final BlockInfo[] blocks;
    public final BlockPos controller;
    public final int height;

    public SparsePreviewShape(NativePreviewGeometry shape, MultiblockMachineDefinition definition) {
        var positions = new LongArrayList();
        var infos = new ObjectArrayList<BlockInfo>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (var pos : shape.states().keySet()) {
            minX = Math.min(minX, pos.getX()); minY = Math.min(minY, pos.getY()); minZ = Math.min(minZ, pos.getZ()); maxY = Math.max(maxY, pos.getY());
        }
        var minimum = new BlockPos(minX, minY, minZ);
        for (var entry : shape.states().entrySet()) {
            var pos = entry.getKey().subtract(minimum); positions.add(pos.asLong());
            infos.add(BlockInfo.fromBlockState(entry.getValue()));
        }
        BlockPos controller = shape.controllerPos().subtract(minimum);
        int height = maxY - minY + 1;
        this.positions = positions.toLongArray();
        this.blocks = infos.toArray(BlockInfo[]::new);
        this.controller = controller;
        this.height = height;
    }

    public long estimatedBytes() {
        return 64L + positions.length * 64L;
    }

    public static BlockInfo copy(BlockInfo source) {
        var extra = (BlockInfoAccessor) source;
        var item = extra.gtlcore$getItemStack();
        var copy = new BlockInfo(source.getBlockState(), source.hasBlockEntity(),
                item == null ? null : item.copy(), extra.gtlcore$getPostCreate());
        if (extra.gtlcore$getTag() != null) copy.setTag(extra.gtlcore$getTag().copy());
        return copy;
    }
}
