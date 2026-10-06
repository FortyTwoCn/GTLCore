package org.gtlcore.gtlcore.common.machine.multiblock.part;

import org.gtlcore.gtlcore.api.capability.BindCapability;
import org.gtlcore.gtlcore.api.recipe.RecipeResult;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.IRecipeLogicMachine;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.machine.multiblock.part.OpticalDataHatchMachine;
import com.gregtechceu.gtceu.utils.ExtendedUseOnContext;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.CustomData;
import org.jetbrains.annotations.Nullable;
import java.util.Collection;
import java.util.Objects;

/** Same-level wireless research routing through the native optical data hatch contract. */
public class WirelessOpticalDataHatchMachine extends OpticalDataHatchMachine implements BindCapability {
    @SaveField @SyncToClient
    private @Nullable BlockPos transmitterPos;
    @SaveField @SyncToClient
    private @Nullable BlockPos receiverPos;

    public WirelessOpticalDataHatchMachine(BlockEntityCreationInfo info, boolean transmitter) {
        super(info, transmitter);
    }

    public @Nullable BlockPos getTransmitterPos() { return transmitterPos; }
    public @Nullable BlockPos getReceiverPos() { return receiverPos; }
    public void setTransmitterPos(@Nullable BlockPos pos) {
        if (Objects.equals(transmitterPos, pos)) return;
        transmitterPos = pos == null ? null : pos.immutable();
        setChanged(); getSyncDataHolder().markClientSyncFieldDirty("transmitterPos");
    }
    public void setReceiverPos(@Nullable BlockPos pos) {
        if (Objects.equals(receiverPos, pos)) return;
        receiverPos = pos == null ? null : pos.immutable();
        setChanged(); getSyncDataHolder().markClientSyncFieldDirty("receiverPos");
    }

    @Override
    public boolean isRecipeAvailable(GTRecipe recipe, Collection<IDataAccessHatch> seen) {
        if (isTransmitter()) return super.isRecipeAvailable(recipe, seen);
        seen.add(this);
        if (!isFormed()) return false;
        var level = getLevel();
        if (transmitterPos == null) {
            reportFailure(RecipeResult.fail(Component.translatable("gtceu.machine.wireless_data_receiver_hatch.unbind")));
            return false;
        }
        if (level != null && level.hasChunkAt(transmitterPos) &&
                MetaMachine.getMachine(level, transmitterPos) instanceof WirelessOpticalDataHatchMachine transmitter &&
                transmitter.isTransmitter() && !seen.contains(transmitter) && transmitter.isRecipeAvailable(recipe, seen)) return true;
        reportFailure(RecipeResult.FAIL_NO_FIND_RESEARCHED);
        return false;
    }

    private void reportFailure(RecipeResult failure) {
        if (!getControllers().isEmpty() && getControllers().first() instanceof IRecipeLogicMachine controller) {
            RecipeResult.of(controller, failure);
        }
    }

    private static CompoundTag positionTag(BlockPos pos) {
        var tag = new CompoundTag(); tag.putInt("x", pos.getX()); tag.putInt("y", pos.getY()); tag.putInt("z", pos.getZ());
        return tag;
    }
    private static @Nullable BlockPos readPosition(CompoundTag tag, String key) {
        if (!tag.contains(key, Tag.TAG_COMPOUND)) return null;
        var pos = tag.getCompound(key);
        if (!pos.contains("x", Tag.TAG_INT) || !pos.contains("y", Tag.TAG_INT) || !pos.contains("z", Tag.TAG_INT)) return null;
        return new BlockPos(pos.getInt("x"), pos.getInt("y"), pos.getInt("z"));
    }

    @Override
    public InteractionResult onUse(ExtendedUseOnContext context) {
        var stack = context.getItemInHand();
        if (!stack.is(GTItems.TOOL_DATA_STICK.asItem())) return super.onUse(context);
        var level = getLevel();
        if (level == null) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        String ownKey = isTransmitter() ? "transmitterPos" : "receiverPos";
        String remoteKey = isTransmitter() ? "receiverPos" : "transmitterPos";
        String ownDimension = isTransmitter() ? "transmitterDimension" : "receiverDimension";
        String remoteDimension = isTransmitter() ? "receiverDimension" : "transmitterDimension";
        String dimension = level.dimension().location().toString();
        var remotePos = readPosition(data, remoteKey);
        boolean bound = false;
        if (remotePos != null && dimension.equals(data.getString(remoteDimension)) && level.hasChunkAt(remotePos) &&
                MetaMachine.getMachine(level, remotePos) instanceof WirelessOpticalDataHatchMachine remote &&
                remote.isTransmitter() != isTransmitter() && !remote.isRemoved()) {
            if (isTransmitter()) {
                setReceiverPos(remotePos); remote.setTransmitterPos(getBlockPos());
            } else {
                setTransmitterPos(remotePos); remote.setReceiverPos(getBlockPos());
            }
            bound = true;
        }
        final boolean bindingCompleted = bound;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            if (bindingCompleted) {
                tag.remove("transmitterPos"); tag.remove("receiverPos");
                tag.remove("transmitterDimension"); tag.remove("receiverDimension");
            } else {
                tag.put(ownKey, positionTag(getBlockPos())); tag.putString(ownDimension, dimension);
            }
        });
        if (context.getPlayer() != null) context.getPlayer().sendSystemMessage(Component.translatable(bound ?
                "gtceu.machine.wireless_data_hatch.bind" : isTransmitter() ?
                        "gtceu.machine.wireless_data_transmitter_hatch.to_bind" : "gtceu.machine.wireless_data_receiver_hatch.to_bind"));
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean bind() { return isTransmitter() ? receiverPos != null : transmitterPos != null; }
    @Override
    public String pos() {
        var pos = isTransmitter() ? receiverPos : transmitterPos;
        return pos == null ? "" : pos.toShortString();
    }
}
