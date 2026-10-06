package org.gtlcore.gtlcore.api.pattern;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

/** Per-check chunk guard, shared across native slice probes and backtracking replays. */
public final class PatternCheckScope {
    public final LongOpenHashSet checkedChunks = new LongOpenHashSet();
    public boolean unloaded;
}
