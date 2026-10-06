package org.gtlcore.gtlcore.common.data.machines;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.machines.GCYMMachines;

/** Original Core names reference GT's native queued entries, so each gtceu ID is registered once. */
public final class GCyMMachines {
    private GCyMMachines() {}
    public static void init() {}

    public static final MachineEntry<MachineDefinition>[] PARALLEL_HATCH = GCYMMachines.PARALLEL_HATCH;
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_MACERATION_TOWER = registerAlias("large_maceration_tower", GCYMMachines.LARGE_MACERATION_TOWER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_CHEMICAL_BATH = registerAlias("large_chemical_bath", GCYMMachines.LARGE_CHEMICAL_BATH);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_CENTRIFUGE = registerAlias("large_centrifuge", GCYMMachines.LARGE_CENTRIFUGE);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_MIXER = registerAlias("large_mixer", GCYMMachines.LARGE_MIXER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_ELECTROLYZER = registerAlias("large_electrolyzer", GCYMMachines.LARGE_ELECTROLYZER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_ELECTROMAGNET = registerAlias("large_electromagnet", GCYMMachines.LARGE_ELECTROMAGNET);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_PACKER = registerAlias("large_packer", GCYMMachines.LARGE_PACKER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_ASSEMBLER = registerAlias("large_assembler", GCYMMachines.LARGE_ASSEMBLER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_CIRCUIT_ASSEMBLER = registerAlias("large_circuit_assembler", GCYMMachines.LARGE_CIRCUIT_ASSEMBLER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_ARC_SMELTER = registerAlias("large_arc_smelter", GCYMMachines.LARGE_ARC_SMELTER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_ENGRAVING_LASER = registerAlias("large_engraving_laser", GCYMMachines.LARGE_ENGRAVING_LASER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_SIFTING_FUNNEL = registerAlias("large_sifting_funnel", GCYMMachines.LARGE_SIFTING_FUNNEL);
    public static final MachineEntry<MultiblockMachineDefinition> BLAST_ALLOY_SMELTER = registerAlias("alloy_blast_smelter", GCYMMachines.BLAST_ALLOY_SMELTER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_AUTOCLAVE = registerAlias("large_autoclave", GCYMMachines.LARGE_AUTOCLAVE);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_MATERIAL_PRESS = registerAlias("large_material_press", GCYMMachines.LARGE_MATERIAL_PRESS);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_BREWER = registerAlias("large_brewer", GCYMMachines.LARGE_BREWER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_CUTTER = registerAlias("large_cutter", GCYMMachines.LARGE_CUTTER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_DISTILLERY = registerAlias("large_distillery", GCYMMachines.LARGE_DISTILLERY);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_EXTRACTOR = registerAlias("large_extractor", GCYMMachines.LARGE_EXTRACTOR);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_EXTRUDER = registerAlias("large_extruder", GCYMMachines.LARGE_EXTRUDER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_SOLIDIFIER = registerAlias("large_solidifier", GCYMMachines.LARGE_SOLIDIFIER);
    public static final MachineEntry<MultiblockMachineDefinition> LARGE_WIREMILL = registerAlias("large_wiremill", GCYMMachines.LARGE_WIREMILL);
    public static final MachineEntry<MultiblockMachineDefinition> MEGA_BLAST_FURNACE = GTLGCYMMachinePolicies.registerMegaBlastFurnace();
    public static final MachineEntry<MultiblockMachineDefinition> MEGA_VACUUM_FREEZER = registerAlias("mega_vacuum_freezer", GCYMMachines.MEGA_VACUUM_FREEZER);

    private static <D extends MachineDefinition> MachineEntry<D> registerAlias(String id, MachineEntry<D> entry) {
        if (!entry.getId().equals(GTCEu.id(id))) throw new IllegalStateException("GCYM alias has changed ID: " + id);
        return entry;
    }
}
