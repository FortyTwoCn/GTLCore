package org.gtlcore.gtlcore.client.preview;

import org.junit.jupiter.api.Test;

/** Pure Java checks; the client renderer and real inventory routes also require in-game validation. */
public final class MultiblockPreviewTest {

    @Test
    void controls() {
        PreviewControlsRegression.run();
    }

    @Test
    void performance() throws Exception {
        PreviewPerformanceRegression.run();
    }

    public static void main(String[] args) throws Exception {
        int assertions = PreviewControlsRegression.run() + PreviewPerformanceRegression.run();
        System.out.println("Multiblock preview regression assertions=" + assertions);
    }
}
