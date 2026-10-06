package org.gtlcore.gtlcore.mixin.ldlib;

import com.lowdragmc.lowdraglib.client.bakedpipeline.Quad;
import com.lowdragmc.lowdraglib.client.bakedpipeline.Submap;
import com.lowdragmc.lowdraglib.client.model.ModelFactory;
import com.lowdragmc.lowdraglib.client.model.custommodel.*;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.*;

import java.util.*;
import java.util.concurrent.*;

import javax.annotation.Nonnull;

/**
 * @author EasterFG on 2025/3/2
 */
@Mixin(CustomBakedModel.class)
public abstract class CustomBakedModelFixed extends BakedModelWrapper<BakedModel> {
    @Unique
    private final ConcurrentMap<GTLQuadKey, List<BakedQuad>> gtlcore$quadCache = new ConcurrentHashMap<>();

    private record GTLQuadKey(Connections connections, BlockState state, Direction side,
                              RenderType layer, List<BakedQuad> base) {}

    protected CustomBakedModelFixed(BakedModel parent) {
        super(parent);
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    @Nonnull
    public List<BakedQuad> getCustomQuads(BlockAndTintGetter level, BlockPos pos, @Nonnull BlockState state,
                                         @Nullable Direction side, RandomSource rand, ModelData modelData,
                                         @Nullable RenderType renderType) {
        var connections = Connections.checkConnections(level, pos, state, side);
        var base = List.copyOf(originalModel.getQuads(state, side, rand, modelData, renderType));
        var key = new GTLQuadKey(connections, state, side, renderType, base);
        // Some dynamic parents allocate new quads every frame; bound their cache too.
        if (gtlcore$quadCache.size() >= 512) gtlcore$quadCache.clear();
        return gtlcore$quadCache.computeIfAbsent(key,
                ignored -> List.copyOf(buildCustomQuads(connections, base, 0.0f)));
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public static List<BakedQuad> reBakeCustomQuads(List<BakedQuad> quads, BlockAndTintGetter level, BlockPos pos, @Nonnull BlockState state, @Nullable Direction side, float offset) {
        return buildCustomQuads(Connections.checkConnections(level, pos, state, side), quads, offset);
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    public static List<BakedQuad> buildCustomQuads(Connections connections, List<BakedQuad> base, float offset) {
        List<BakedQuad> result = new LinkedList<>();
        for (BakedQuad bakedQuad : base) {
            var section = LDLMetadataSection.getMetadata(bakedQuad.getSprite());
            TextureAtlasSprite connection = section.connection == null ? null : ModelFactory.getBlockSprite(section.connection);
            if (connection == null) {
                result.add(makeQuad(bakedQuad, section, offset).rebake());
                continue;
            }

            Quad quad = makeQuad(bakedQuad, section, offset).derotate();
            Quad[] quads = quad.subdivide(4);

            int[] ctm = connections.getSubmapIndices();

            for (int j = 0; j < quads.length; j++) {
                Quad q = quads[j];
                if (q != null) {
                    int ctmid = q.getUvs().normalize().getQuadrant();
                    quads[j] = q.grow().transformUVs(ctm[ctmid] > 15 ? bakedQuad.getSprite() : connection, Submap.uvs[ctm[ctmid]]);
                }
            }
            result.addAll(Arrays.stream(quads).filter(Objects::nonNull).map(Quad::rebake).toList());
        }
        return result;
    }

    /**
     * @author .
     * @reason .
     */
    @Overwrite(remap = false)
    protected static Quad makeQuad(BakedQuad bq, LDLMetadataSection section, float offset) {
        Quad q = Quad.from(bq, offset);
        if (section.emissive) {
            q = q.setLight(15, 15);
        }
        return q;
    }
}
