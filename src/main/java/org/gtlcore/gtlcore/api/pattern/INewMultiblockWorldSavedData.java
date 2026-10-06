package org.gtlcore.gtlcore.api.pattern;

import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;

import net.minecraft.world.level.ChunkPos;

import java.util.Set;

@FunctionalInterface
public interface INewMultiblockWorldSavedData {

    Set<PatternState> getControllersInChunk(ChunkPos chunkPos);
}
