package org.gtlcore.gtlcore.data;

import org.gtlcore.gtlcore.common.data.GTLBedrockFluids;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import net.minecraft.core.RegistrySetBuilder;
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Set;

public final class GTLWorldgenData {
    private GTLWorldgenData() {}

    public static void gatherData(GatherDataEvent event) {
        event.getGenerator().addProvider(event.includeServer(), new DatapackBuiltinEntriesProvider(
                event.getGenerator().getPackOutput(), event.getLookupProvider(),
                new RegistrySetBuilder().add(GTRegistries.Keys.BEDROCK_FLUID, GTLBedrockFluids::bootstrap),
                Set.of("gtceu")));
    }
}
