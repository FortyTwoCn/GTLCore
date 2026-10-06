package org.gtlcore.gtlcore.api.machine.multiblock;

import org.gtlcore.gtlcore.api.machine.trait.AECraft.*;
import org.gtlcore.gtlcore.api.machine.trait.IMolecularAssemblerMachine;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import org.gtlcore.gtlcore.common.machine.trait.MolecularAssemblerRecipesLogic;

import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;

import net.minecraft.core.BlockPos;

import com.google.common.primitives.Ints;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/** Native workable lifecycle with an AE craft queue instead of conventional recipe inputs. */
public abstract class MolecularAssemblerMultiblockMachineBase extends NoEnergyMultiblockMachine
        implements ParallelMachine, IMolecularAssemblerMachine {
    @Nullable
    protected IMolecularAssemblerHandler molecularAssemblerHandler;
    @Getter protected int tickDuration = 40;
    @Getter protected int maxParallel;
    @Getter protected int patternSize;

    public MolecularAssemblerMultiblockMachineBase(BlockEntityCreationInfo info) {
        this(info, new MolecularAssemblerRecipesLogic());
    }

    public MolecularAssemblerMultiblockMachineBase(BlockEntityCreationInfo info, RecipeLogic logic) {
        super(info, logic);
    }

    @Override
    public @Nullable IMolecularAssemblerHandler getMAHandler() {
        return molecularAssemblerHandler;
    }

    @Override
    public void formStructure(String name) {
        super.formStructure(name);
        if (DEFAULT_STRUCTURE.equals(name) && isFormed() && !isRemote()) {
            updateCraftParts();
            RecipeResult.of(this, null);
            RecipeResult.ofWorking(this, null);
        }
    }

    private void updateCraftParts() {
        Set<BlockPos> patternInventoryPos = new ObjectArraySet<>();
        int speedTier = 0;
        int totalSlots = 0;
        long totalParallel = 0;
        IMECraftIOPart io = null;
        for (var part : getParts()) {
            if (part instanceof IMECraftPatternContainer patterns) {
                patternInventoryPos.add(patterns.getBlockPos());
                totalSlots = Ints.saturatedCast((long) totalSlots + patterns.getSlots());
            } else if (part instanceof IMECraftParallelCore parallel) {
                totalParallel += parallel.getParallel();
            } else if (part instanceof IMECraftSpeedCore speed) {
                speedTier = Ints.saturatedCast((long) speedTier + speed.getSpeedTier());
            } else if (part instanceof IMECraftIOPart craftIO) {
                if (io != null) throw new IllegalStateException("Multiple molecular assembler IO parts");
                io = craftIO;
            }
        }
        if (io == null) throw new IllegalStateException("Missing molecular assembler IO part");
        patternSize = totalSlots;
        tickDuration = speedTier <= 39 ? 40 - speedTier : 1;
        maxParallel = Ints.saturatedCast(totalParallel == 0 ? 1 : totalParallel);
        io.init(patternInventoryPos);
        var handler = io.getNotifiableMAHandlerTrait();
        molecularAssemblerHandler = handler;
        traitSubscriptions.add(handler.addChangedListener(recipeLogic::updateTickSubscription));
        recipeLogic.updateTickSubscription();
    }

    @Override
    public void onUnload() {
        molecularAssemblerHandler = null;
        super.onUnload();
    }

    @Override
    public void onPartUnload() {
        molecularAssemblerHandler = null;
        super.onPartUnload();
    }

    @Override
    public void invalidateStructure(String name) {
        if (DEFAULT_STRUCTURE.equals(name)) {
            molecularAssemblerHandler = null;
            patternSize = 0;
            tickDuration = 40;
            maxParallel = 0;
        }
        super.invalidateStructure(name);
    }

    @Override
    public boolean isRecipeLogicAvailable() {
        return super.isRecipeLogicAvailable() && molecularAssemblerHandler != null;
    }
}
