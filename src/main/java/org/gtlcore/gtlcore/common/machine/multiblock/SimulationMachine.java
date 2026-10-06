package org.gtlcore.gtlcore.common.machine.multiblock;

import org.gtlcore.gtlcore.common.machine.multiblock.electric.StorageMachine;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.item.MetaMachineItem;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.pattern.IBlockPattern;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A one-item blueprint holder. Its virtual controller uses GT's native schema and preview renderer. */
public class SimulationMachine extends StorageMachine {
    public SimulationMachine(BlockEntityCreationInfo info, Object... args) { super(info, 1); }

    @Override
    protected boolean filter(@NotNull ItemStack stack) {
        return stack.getItem() instanceof MetaMachineItem item && item.getDefinition() instanceof MultiblockMachineDefinition;
    }

    @Override
    public void checkAndFormStructure() {
        // A simulator presents another controller's structure and must never form that structure itself.
    }

    @Override
    public boolean isFormed() { return false; }

    private @Nullable MultiblockMachineDefinition blueprint() {
        var stack = getMachineStorageItem();
        return !stack.isEmpty() && stack.getItem() instanceof MetaMachineItem item &&
                item.getDefinition() instanceof MultiblockMachineDefinition definition ? definition : null;
    }

    @Override
    public IBlockPattern getDefaultStructurePattern() {
        var definition = blueprint();
        if (definition != null && definition.getStructurePatterns().containsKey(DEFAULT_STRUCTURE))
            return definition.getStructurePatterns().get(DEFAULT_STRUCTURE).get();
        return super.getDefaultStructurePattern();
    }

    public MultiblockControllerMachine getRealController() {
        var definition = blueprint();
        if (definition == null) return this;
        var blockState = definition.getBlock().withPropertiesOf(getBlockState());
        var entity = definition.getBlockEntityType().create(getBlockPos(), blockState);
        if (!(entity instanceof MultiblockControllerMachine controller)) return this;
        controller.setFrontFacing(getFrontFacing());
        controller.setUpwardsFacing(getUpwardsFacing());
        controller.setFlipped(isFlipped());
        // Rotate before assigning a level so a virtual object cannot publish server structure mappings.
        if (getLevel() != null) controller.setLevel(getLevel());
        return controller;
    }

    @Override
    public InteractionResult onUse(ExtendedUseOnContext context) {
        var player = context.getPlayer();
        if (player != null && player.isShiftKeyDown() && player.getItemInHand(context.getHand()).isEmpty()) {
            if (isRemote()) {
                var controller = getRealController();
                if (controller != this) return controller.onUse(context);
            }
            return InteractionResult.sidedSuccess(isRemote());
        }
        return super.onUse(context);
    }
}
