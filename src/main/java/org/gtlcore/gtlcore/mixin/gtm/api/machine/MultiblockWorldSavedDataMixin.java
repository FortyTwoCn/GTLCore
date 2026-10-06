package org.gtlcore.gtlcore.mixin.gtm.api.machine;

import org.gtlcore.gtlcore.api.pattern.INewMultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.multiblock.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import java.util.Set;

/** Native GT owns dispatch, multi-substructure indexing and removal; expose a stable read-only snapshot to Core callers. */
@Mixin(value = MultiblockWorldSavedData.class, remap = false)
public abstract class MultiblockWorldSavedDataMixin implements INewMultiblockWorldSavedData {
    @Override
    public Set<PatternState> getControllersInChunk(ChunkPos chunkPos) {
        return Set.of(((MultiblockWorldSavedData) (Object) this).getPatternsInChunk(chunkPos));
    }
}
