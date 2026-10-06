package org.gtlcore.gtlcore.migration;

import com.gtladd.gtladditions.api.pattern.PatternPredicatePatchKt;
import com.gtladd.gtladditions.api.pattern.PatternPredicateSelector;
import com.gtladd.gtladditions.api.registry.NativeQueuedMachines;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import com.gregtechceu.gtceu.common.data.GTRecipeTypes;
import net.minecraft.world.level.block.Blocks;
import kotlin.jvm.functions.Function0;
import static com.gregtechceu.gtceu.api.multiblock.Predicates.*;

/** Independent IDs exercise the actual queued native registration and immutable-definition boundary. */
public final class NativeQueuedMachineFixtures {
    public static final MachineEntry<MultiblockMachineDefinition> MACHINE = NativeMachineFixtures.REGISTRATE
            .multiblock("native_queued_machine", WorkableElectricMultiblockMachine::new)
            .recipeType(GTRecipeTypes.CHEMICAL_RECIPES).appearanceBlock(() -> Blocks.IRON_BLOCK)
            .pattern(definition -> {
                var shared = blocks(Blocks.IRON_BLOCK).setGlobalMinMax(1, 3);
                return MultiblockPatternBuilder.start().slice("STA", "UT ")
                        .where('S', controller(definition)).where('T', shared).where('U', shared)
                        .where('A', air()).where(' ', any()).build();
            }).register();

    public static void init() {
        NativeQueuedMachines.replaceFactory(MACHINE, QueuedController::new);
        NativeQueuedMachines.defaultRecipeLogic(MACHINE, QueuedLogic::new);
        PatternPredicatePatchKt.patchPatternPredicates(MACHINE, "gold_hatch",
                PatternPredicateSelector.Companion.containsBlock(Blocks.IRON_BLOCK).notController(),
                (Function0<MultiPredicate>) () -> blocks(Blocks.GOLD_BLOCK).setMaxGlobalLimited(1), true);
        PatternPredicatePatchKt.replacePatternPredicates(MACHINE, "allow_internal_contents",
                PatternPredicateSelector.Companion.pureAir(), (Function0<MultiPredicate>) () -> any(), true);
    }

    public static final class QueuedController extends WorkableElectricMultiblockMachine {
        public QueuedController(BlockEntityCreationInfo info) { super(info); }
    }
    public static final class QueuedLogic extends RecipeLogic {}
}
