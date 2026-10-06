package org.gtlcore.gtlcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.common.data.GTBlocks;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.Block;

/** Core-used GT 1.4.4 content absent from the native API baseline, registered under its original IDs. */
public final class GTLRetainedGTBlocks {
    private GTLRetainedGTBlocks() {}
    public static final BlockEntry<Block> CASING_STAINLESS_EVAPORATION = GTBlocks.createCasingBlock(
            "stainless_evaporation_casing", GTCEu.id("block/casings/solid/machine_casing_stainless_evaporation"));
    public static void init() {}
}
