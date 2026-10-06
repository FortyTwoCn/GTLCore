package org.gtlcore.gtlcore.migration;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import org.gtlcore.gtlcore.common.data.GTLIngredientTypes;

/** A loader entrypoint for persistence regression tests, not a substitute for GTLCore. */
@Mod("gtlcore_migration_test")
public class MigrationTestHarness {
    public MigrationTestHarness(IEventBus modBus) { GTLIngredientTypes.register(modBus); }
}
