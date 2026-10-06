package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.data.machines.GTResearchMachines;
import com.gregtechceu.gtceu.common.data.machines.GTMultiMachines;
import com.gregtechceu.gtceu.common.machine.trait.hpca.HPCAComputationProviderTrait;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.TranslatableContents;
import io.netty.buffer.Unpooled;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class NativeHpcaIntegrationTest {
    private static MetaMachine part(boolean advanced) {
        var entry = advanced ? GTResearchMachines.HPCA_ADVANCED_COMPUTATION_COMPONENT : GTResearchMachines.HPCA_COMPUTATION_COMPONENT;
        return (MetaMachine) entry.getBlockEntityType().create(BlockPos.ZERO, entry.getBlock().defaultBlockState());
    }
    private static List<Component> tooltip(MachineEntry<?> entry) {
        var result = new ArrayList<Component>(); result.add(entry.getBlock().getName());
        entry.get().getTooltipBuilder().accept(entry.asStack(), result); return result;
    }
    private static TranslatableContents translation(List<Component> lines, String key) {
        var matches = lines.stream().map(Component::getContents).filter(TranslatableContents.class::isInstance)
                .map(TranslatableContents.class::cast).filter(t -> t.getKey().equals(key)).toList();
        assertEquals(1, matches.size(), key); return matches.getFirst();
    }
    @Test void nativeProductionComputationTraitsProvideCoreCapacityWithUnchangedCoolingAndEnergy() {
        var regular = part(false).getTrait(HPCAComputationProviderTrait.class); var advanced = part(true).getTrait(HPCAComputationProviderTrait.class);
        assertEquals(8, regular.getCWUPerTick()); assertEquals(32, advanced.getCWUPerTick());
        assertEquals(2, regular.getCoolingPerTick()); assertEquals(4, advanced.getCoolingPerTick());
        assertEquals(GTValues.VA[GTValues.EV], regular.upkeepEUt()); assertEquals(GTValues.VA[GTValues.IV], advanced.upkeepEUt());
        assertEquals(GTValues.VA[GTValues.LuV], regular.maxEUt()); assertEquals(GTValues.VA[GTValues.ZPM], advanced.maxEUt());
    }
    @Test void nativeDamagePersistenceAndClientPacketDisableComputationUntilRepair() {
        var first = part(true); var second = part(true); var original = first.getTrait(HPCAComputationProviderTrait.class);
        var registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY); original.setDamaged(true);
        assertEquals(0, original.getCWUPerTick()); assertEquals(0, original.getCoolingPerTick());
        second.loadAdditional(first.saveWithoutMetadata(registry), registry);
        var copy = second.getTrait(HPCAComputationProviderTrait.class); assertTrue(copy.isDamaged()); assertEquals(0, copy.getCWUPerTick());
        original.setDamaged(false); var packet = new RegistryFriendlyByteBuf(Unpooled.buffer(), registry);
        try { first.getSyncDataHolder().writeClientPacket(registry, packet); second.getSyncDataHolder().readClientPacket(registry, packet); }
        finally { packet.release(); }
        assertFalse(copy.isDamaged()); assertEquals(32, copy.getCWUPerTick()); assertEquals(4, copy.getCoolingPerTick());
    }
    @Test void productionHpcaDescriptionsMatchTheNativeTraitAndKeepInheritedDamageWarning() {
        for (boolean advanced : new boolean[] { false, true }) {
            var entry = advanced ? GTResearchMachines.HPCA_ADVANCED_COMPUTATION_COMPONENT : GTResearchMachines.HPCA_COMPUTATION_COMPONENT;
            var lines = tooltip(entry); var trait = part(advanced).getTrait(HPCAComputationProviderTrait.class);
            assertEquals(trait.getCWUPerTick(), translation(lines, "gtceu.machine.hpca.component_type.computation_cwut").getArgs()[0]);
            translation(lines, "gtceu.machine.hpca.component_type.damaged"); translation(lines, "gtlcore.registry.modify");
        }
    }
    @Test void nativeSteamStorageAndMultiblockDescriptionsRetainCoreChangesWithoutDuplicateLines() {
        for (var entry : List.of(GTMachines.STEAM_IMPORT_BUS, GTMachines.STEAM_EXPORT_BUS)) {
            var lines = tooltip(entry); assertEquals(16, translation(lines, "gtceu.universal.tooltip.item_storage_capacity").getArgs()[0]);
            translation(lines, "gtlcore.registry.modify");
        }
        var ebf = tooltip(GTMultiMachines.ELECTRIC_BLAST_FURNACE); translation(ebf, "gtceu.machine.electric_blast_furnace.tooltip.a"); translation(ebf, "gtceu.machine.perfect_oc");
        translation(tooltip(GTMultiMachines.LARGE_CHEMICAL_REACTOR), "gtceu.machine.perfect_oc");
        var research = tooltip(GTResearchMachines.RESEARCH_STATION);
        for (int i = 0; i < 3; i++) translation(research, "gtceu.machine.research_station.tooltip." + i);
        for (var entry : List.of(GTMultiMachines.STEAM_OVEN, GTMultiMachines.LARGE_BOILER_BRONZE, GTMultiMachines.LARGE_BOILER_STEEL,
                GTMultiMachines.LARGE_BOILER_TITANIUM, GTMultiMachines.LARGE_BOILER_TUNGSTENSTEEL)) translation(tooltip(entry), "gtlcore.registry.modify");
    }
}
