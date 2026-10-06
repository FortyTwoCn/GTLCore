package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.machine.multiblock.MolecularAssemblerMultiblockMachine;
import org.gtlcore.gtlcore.api.machine.trait.AECraft.*;
import org.gtlcore.gtlcore.common.data.GTLRecipeTypes;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MECraftHandler;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.NotifiableMAHandlerTrait;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MECraftPatternContainerPartMachine;
import org.gtlcore.gtlcore.common.machine.multiblock.part.ae.MEMolecularAssemblerIOPartMachine;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.api.data.RotationState;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;

import java.util.List;
import java.util.Set;

import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;
import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

/** Real native parts expose production queue handlers; these fixtures do not create an AE network. */
public final class NativeMolecularFixtures {
    public static final MachineEntry<MachineDefinition> PATTERNS = NativeMachineFixtures.REGISTRATE
            .machine("native_molecular_patterns", MECraftPatternContainerPartMachine::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> PARALLEL = NativeMachineFixtures.REGISTRATE
            .machine("native_molecular_parallel", ParallelPart::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> SPEED = NativeMachineFixtures.REGISTRATE
            .machine("native_molecular_speed", SpeedPart::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> IO = NativeMachineFixtures.REGISTRATE
            .machine("native_molecular_io", OutputPart::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MachineDefinition> PRODUCTION_IO = NativeMachineFixtures.REGISTRATE
            .machine("native_production_molecular_io", MEMolecularAssemblerIOPartMachine::new).rotationState(RotationState.ALL).register();
    public static final MachineEntry<MultiblockMachineDefinition> ASSEMBLER = NativeMachineFixtures.REGISTRATE
            .multiblock("native_molecular_assembler", MolecularAssemblerMultiblockMachine::new)
            .rotationState(RotationState.ALL).recipeType(GTLRecipeTypes.MOLECULAR_ASSEMBLER)
            .appearanceBlock(() -> Blocks.IRON_BLOCK).allowFlip(false)
            .pattern(definition -> MultiblockPatternBuilder.start(FRONT, UP, LEFT).slice("PSR", "IAX")
                    .where('S', controller(definition)).where('P', blocks(PATTERNS.getBlock()))
                    .where('I', blocks(IO.getBlock(), PRODUCTION_IO.getBlock())).where('R', blocks(PARALLEL.getBlock()))
                    .where('X', blocks(SPEED.getBlock())).where('A', blocks(Blocks.IRON_BLOCK)).build())
            .register();

    public static void init() {}

    public static final class ParallelPart extends MultiblockPartMachine implements IMECraftParallelCore {
        public ParallelPart(BlockEntityCreationInfo info) { super(info); }
        @Override public int getParallel() { return 8; }
    }
    public static final class SpeedPart extends MultiblockPartMachine implements IMECraftSpeedCore {
        public SpeedPart(BlockEntityCreationInfo info) { super(info); }
        @Override public int getSpeedTier() { return 37; }
    }
    public static final class OutputPart extends MultiblockPartMachine implements IMECraftIOPart, IMolecularAssemblerOutputMachine {
        private final Object2LongMap<GenericStack> queue = new Object2LongLinkedOpenHashMap<>();
        private final Object2LongMap<AEKey> buffer = new Object2LongLinkedOpenHashMap<>();
        @SaveField private final MolecularAssemblerOutputStorage craftStorage = new MolecularAssemblerOutputStorage(this);
        public final MECraftHandler handler;
        public Set<BlockPos> proxies = Set.of();
        public int notifications;
        public OutputPart(BlockEntityCreationInfo info) { super(info); handler = attachTrait(new MECraftHandler()); }
        @Override public Object2LongMap<GenericStack> getOutputItems() { return queue; }
        @Override public Object2LongMap<AEKey> getBuffer() { return buffer; }
        @Override public void notifyOutputBufferChanged() { notifications++; }
        @Override public NotifiableMAHandlerTrait getNotifiableMAHandlerTrait() { return handler; }
        @Override public void init(Set<BlockPos> proxies) { this.proxies = Set.copyOf(proxies); }
        @Override public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
        @Override public boolean pushPattern(IPatternDetails pattern, KeyCounter[] inputs) { return false; }
        @Override public boolean isBusy() { return false; }
    }
}
