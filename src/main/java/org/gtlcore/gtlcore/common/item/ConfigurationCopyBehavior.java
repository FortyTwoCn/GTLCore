package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.IAutoConfiguratioGravityPart;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.IAutoConfigurationMaintenanceHatch;
import org.gtlcore.gtlcore.common.machine.multiblock.part.maintenance.IGravityPartMachine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.item.component.ICustomDescriptionId;
import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.SimpleTieredMachine;
import com.gregtechceu.gtceu.common.machine.trait.AutoOutputTrait;
import com.gregtechceu.gtceu.common.machine.trait.ProgrammableCircuitSlotTrait;
import org.gtlcore.gtlcore.mixin.gtm.machine.QuantumChestMachineAccessor;
import org.gtlcore.gtlcore.mixin.gtm.machine.QuantumTankMachineAccessor;
import com.gregtechceu.gtceu.common.item.behavior.IntCircuitBehaviour;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import com.gregtechceu.gtceu.common.machine.storage.QuantumChestMachine;
import com.gregtechceu.gtceu.common.machine.storage.QuantumTankMachine;

import net.neoforged.neoforge.fluids.FluidStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import java.util.Objects;


public class ConfigurationCopyBehavior implements IInteractionItem, ICustomDescriptionId {

    public static final ConfigurationCopyBehavior INSTANCE = new ConfigurationCopyBehavior();

    private static final String QUANTUM_CHEST_CONFIGURATION = "QuantumChestConfiguration";
    private static final String QUANTUM_TANK_CONFIGURATION = "QuantumTankConfiguration";
    private static final String FLUID_OUTPUT_HATCH_CONFIGURATION = "FluidOutputHatchConfiguration";
    private static final String COPIED_CONFIGURATION_TYPE = "CopiedConfigurationType";
    private static final String COPIED_NAME_SUFFIX = "item.gtlcore.cfg_copy.copied_suffix";

    private static final String[] LEGACY_CONFIGURATION_KEYS = {
            "Configuration",
            "hasAutoOutputItem",
            "hasAutoOutputFluid",
            "outputFacingItems",
            "outputFacingFluids",
            "allowInputFromOutputSideItems",
            "allowInputFromOutputSideFluids",
            "circuit",
            "isDistinct",
            "isWorkingEnabled",
            "durationMultiplier",
            "currentGravity"
    };

    @Override
    public Component getItemName(ItemStack stack) {
        CopiedConfiguration copiedConfiguration = getCopiedConfiguration(getBehaviorsTag(stack));
        if (copiedConfiguration == null) {
            return null;
        }
        return Component.translatable(stack.getItem().getDescriptionId())
                .append(Component.translatable(COPIED_NAME_SUFFIX,
                        Component.translatable(copiedConfiguration.translationKey)));
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        var metaMachine = MetaMachine.getMachine(context.getLevel(), context.getClickedPos());
        if (context.getPlayer() == null || metaMachine == null) return InteractionResult.PASS;
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        stack = context.getItemInHand();
        CompoundTag tags = getBehaviorsTag(stack);
        if (metaMachine != null) {
            if (metaMachine instanceof SimpleTieredMachine simpleTieredMachine) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.SIMPLE_MACHINE);
                    getSTMCfg(tags, simpleTieredMachine);
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else {
                    if (tags.getBoolean("Configuration")) {
                        setSTMCfg(tags, simpleTieredMachine);
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                    } else {
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                    }
                }
            } else if (metaMachine instanceof QuantumChestMachine quantumChestMachine) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.QUANTUM_CHEST);
                    tags.put(QUANTUM_CHEST_CONFIGURATION, getQuantumChestCfg(quantumChestMachine));
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else if (tags.get(QUANTUM_CHEST_CONFIGURATION) instanceof CompoundTag configuration) {
                    setQuantumChestCfg(configuration, quantumChestMachine);
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                } else {
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                }
            } else if (metaMachine instanceof QuantumTankMachine quantumTankMachine) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.QUANTUM_TANK);
                    tags.put(QUANTUM_TANK_CONFIGURATION, getQuantumTankCfg(quantumTankMachine));
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else if (tags.get(QUANTUM_TANK_CONFIGURATION) instanceof CompoundTag configuration) {
                    setQuantumTankCfg(configuration, quantumTankMachine);
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                } else {
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                }
            } else if (metaMachine instanceof FluidHatchPartMachine fluidHatchPartMachine && fluidHatchPartMachine.tank.getHandlerIO() == IO.OUT) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.FLUID_OUTPUT_HATCH);
                    tags.put(FLUID_OUTPUT_HATCH_CONFIGURATION, getFluidOutputHatchCfg(fluidHatchPartMachine));
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else if (tags.get(FLUID_OUTPUT_HATCH_CONFIGURATION) instanceof CompoundTag configuration) {
                    setFluidOutputHatchCfg(configuration, fluidHatchPartMachine);
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                } else {
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                }
            } else if (metaMachine instanceof ItemBusPartMachine itemBusPartMachine) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.ITEM_BUS);
                    getBusCfg(tags, itemBusPartMachine);
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else {
                    if (tags.getBoolean("Configuration")) {
                        setBusCfg(tags, itemBusPartMachine);
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                    } else {
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                    }
                }
            } else if (metaMachine instanceof IAutoConfiguratioGravityPart acgp) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.GRAVITY_CONFIGURATION_HATCH);
                    tags.putBoolean("Configuration", true);
                    tags.putFloat("durationMultiplier", acgp.getDurationMultiplier());
                    tags.putInt("currentGravity", acgp.getCurrentGravity());
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else {
                    if (tags.getBoolean("Configuration")) {
                        acgp.setDurationMultiplier(tags.getFloat("durationMultiplier"));
                        acgp.setCurrentGravity(tags.getInt("currentGravity"));
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                    } else {
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                    }
                }
            } else if (metaMachine instanceof IAutoConfigurationMaintenanceHatch acmh) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.CONFIGURATION_MAINTENANCE_HATCH);
                    tags.putBoolean("Configuration", true);
                    tags.putFloat("durationMultiplier", acmh.getDurationMultiplier());
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else {
                    if (tags.getBoolean("Configuration")) {
                        acmh.setDurationMultiplier(tags.getFloat("durationMultiplier"));
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                    } else {
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                    }
                }
            } else if (metaMachine instanceof IGravityPartMachine gpm) {
                if (Objects.requireNonNull(context.getPlayer()).isShiftKeyDown()) {
                    prepareCopy(tags, CopiedConfiguration.GRAVITY_HATCH);
                    tags.putBoolean("Configuration", true);
                    tags.putInt("currentGravity", gpm.getCurrentGravity());
                    context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_copied"), true);
                } else {
                    if (tags.getBoolean("Configuration")) {
                        gpm.setCurrentGravity(tags.getInt("currentGravity"));
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_pasted"), true);
                    } else {
                        context.getPlayer().displayClientMessage(Component.translatable("message.gtlcore.machine_data_not_found"), true);
                    }
                }
            }
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tags));
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    private void prepareCopy(CompoundTag tags, CopiedConfiguration copiedConfiguration) {
        tags.remove(QUANTUM_CHEST_CONFIGURATION);
        tags.remove(QUANTUM_TANK_CONFIGURATION);
        tags.remove(FLUID_OUTPUT_HATCH_CONFIGURATION);
        for (String key : LEGACY_CONFIGURATION_KEYS) {
            tags.remove(key);
        }
        tags.putString(COPIED_CONFIGURATION_TYPE, copiedConfiguration.id);
    }

    private CopiedConfiguration getCopiedConfiguration(CompoundTag tags) {
        CopiedConfiguration copiedConfiguration = CopiedConfiguration.fromId(tags.getString(COPIED_CONFIGURATION_TYPE));
        if (copiedConfiguration != null) {
            return copiedConfiguration;
        }
        if (tags.contains(QUANTUM_CHEST_CONFIGURATION)) {
            return CopiedConfiguration.QUANTUM_CHEST;
        }
        if (tags.contains(QUANTUM_TANK_CONFIGURATION)) {
            return CopiedConfiguration.QUANTUM_TANK;
        }
        if (tags.contains(FLUID_OUTPUT_HATCH_CONFIGURATION)) {
            return CopiedConfiguration.FLUID_OUTPUT_HATCH;
        }
        if (!tags.getBoolean("Configuration")) {
            return null;
        }
        if (tags.contains("durationMultiplier") && tags.contains("currentGravity")) {
            return CopiedConfiguration.GRAVITY_CONFIGURATION_HATCH;
        }
        if (tags.contains("durationMultiplier")) {
            return CopiedConfiguration.CONFIGURATION_MAINTENANCE_HATCH;
        }
        if (tags.contains("currentGravity")) {
            return CopiedConfiguration.GRAVITY_HATCH;
        }
        if (tags.contains("isDistinct")) {
            return CopiedConfiguration.ITEM_BUS;
        }
        return tags.contains("hasAutoOutputItem") ? CopiedConfiguration.SIMPLE_MACHINE : null;
    }

    private static CompoundTag getBehaviorsTag(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }

    private CompoundTag getQuantumChestCfg(QuantumChestMachine machine) {
        var tag = new CompoundTag(); copyOutput(tag, machine.autoOutput);
        tag.putBoolean("isVoiding", machine.isVoiding());
        tag.putBoolean("isLocked", machine.isLocked());
        if (machine.isLocked()) tag.put("lockedItem", machine.getLockedItem().copyWithCount(1).save(machine.getLevel().registryAccess()));
        return tag;
    }

    private void setQuantumChestCfg(CompoundTag tag, QuantumChestMachine machine) {
        pasteOutput(tag, machine.autoOutput); machine.setVoiding(tag.getBoolean("isVoiding"));
        var item = tag.getBoolean("isLocked") ? ItemStack.parse(machine.getLevel().registryAccess(), tag.getCompound("lockedItem")).orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        ((QuantumChestMachineAccessor) machine).gtlcore$getLockedInventory().setStackInSlot(0, item.copyWithCount(1));
    }

    private CompoundTag getQuantumTankCfg(QuantumTankMachine machine) {
        var tag = new CompoundTag(); copyOutput(tag, machine.autoOutput);
        tag.putBoolean("isVoiding", machine.isVoiding()); tag.putBoolean("isLocked", machine.isLocked());
        if (machine.isLocked()) tag.put("lockedFluid", machine.getLockedFluid().copyWithAmount(1).save(machine.getLevel().registryAccess()));
        return tag;
    }

    private void setQuantumTankCfg(CompoundTag tag, QuantumTankMachine machine) {
        pasteOutput(tag, machine.autoOutput); machine.setVoiding(tag.getBoolean("isVoiding"));
        var access = (QuantumTankMachineAccessor) machine;
        access.gtlcore$setLocked(false);
        if (tag.getBoolean("isLocked")) access.gtlcore$setLockedFluid(FluidStack.parseOptional(machine.getLevel().registryAccess(), tag.getCompound("lockedFluid")));
    }

    private CompoundTag getFluidOutputHatchCfg(FluidHatchPartMachine machine) {
        var tag = new CompoundTag();
        tag.putBoolean("isWorkingEnabled", machine.isWorkingEnabled()); copyCircuit(tag, machine);
        tag.putBoolean("isLocked", machine.tank.isLocked());
        if (machine.tank.isLocked()) tag.put("lockedFluid", machine.tank.getLockedFluid().getFluid().copyWithAmount(1).save(machine.getLevel().registryAccess()));
        return tag;
    }

    private void setFluidOutputHatchCfg(CompoundTag tag, FluidHatchPartMachine machine) {
        machine.setWorkingEnabled(tag.getBoolean("isWorkingEnabled")); pasteCircuit(tag, machine);
        machine.tank.setLocked(false);
        if (tag.getBoolean("isLocked")) machine.tank.setLocked(true, FluidStack.parseOptional(machine.getLevel().registryAccess(), tag.getCompound("lockedFluid")));
    }

    private static void copyOutput(CompoundTag tag, AutoOutputTrait output) {
        if (output == null) return;
        tag.putBoolean("autoOutputItems", output.isAutoOutputItems());
        tag.putBoolean("autoOutputFluids", output.isAutoOutputFluids());
        if (output.getItemOutputDirection() != null) tag.putString("outputFacingItems", output.getItemOutputDirection().getName());
        if (output.getFluidOutputDirection() != null) tag.putString("outputFacingFluids", output.getFluidOutputDirection().getName());
        tag.putBoolean("allowInputFromOutputSideItems", output.allowsItemInputFromOutputSide());
        tag.putBoolean("allowInputFromOutputSideFluids", output.allowsFluidInputFromOutputSide());
    }

    private static void pasteOutput(CompoundTag tag, AutoOutputTrait output) {
        if (output == null) return;
        output.setAllowAutoOutputItems(tag.getBoolean("autoOutputItems"));
        output.setAllowAutoOutputFluids(tag.getBoolean("autoOutputFluids"));
        var itemDirection = Direction.byName(tag.getString("outputFacingItems"));
        var fluidDirection = Direction.byName(tag.getString("outputFacingFluids"));
        if (itemDirection != null) output.setItemOutputDirection(itemDirection);
        if (fluidDirection != null) output.setFluidOutputDirection(fluidDirection);
        output.setAllowItemInputFromOutputSide(tag.getBoolean("allowInputFromOutputSideItems"));
        output.setAllowFluidInputFromOutputSide(tag.getBoolean("allowInputFromOutputSideFluids"));
    }

    private static void copyCircuit(CompoundTag tag, MetaMachine machine) {
        var circuit = machine.getTrait(ProgrammableCircuitSlotTrait.class);
        if (circuit != null && circuit.isEnabled()) tag.putInt("circuit", circuit.getCurrentCircuit());
    }

    private static void pasteCircuit(CompoundTag tag, MetaMachine machine) {
        var circuit = machine.getTrait(ProgrammableCircuitSlotTrait.class);
        if (circuit == null || !circuit.isEnabled() || !tag.contains("circuit")) return;
        int value = Math.clamp(tag.getInt("circuit"), 0, 32);
        circuit.storage.setStackInSlot(0, value == 0 ? ItemStack.EMPTY : IntCircuitBehaviour.stack(value));
    }

    private void getBusCfg(CompoundTag tag, ItemBusPartMachine machine) {
        tag.putBoolean("Configuration", true); tag.putBoolean("isDistinct", machine.isDistinct());
        tag.putBoolean("isWorkingEnabled", machine.isWorkingEnabled()); copyCircuit(tag, machine);
    }

    private void setBusCfg(CompoundTag tag, ItemBusPartMachine machine) {
        machine.setDistinct(tag.getBoolean("isDistinct")); machine.setWorkingEnabled(tag.getBoolean("isWorkingEnabled")); pasteCircuit(tag, machine);
    }

    private void getSTMCfg(CompoundTag tag, SimpleTieredMachine machine) {
        tag.putBoolean("Configuration", true); copyOutput(tag, machine.getTrait(AutoOutputTrait.class)); copyCircuit(tag, machine);
    }

    private void setSTMCfg(CompoundTag tag, SimpleTieredMachine machine) {
        pasteOutput(tag, machine.getTrait(AutoOutputTrait.class)); pasteCircuit(tag, machine);
    }

    private enum CopiedConfiguration {

        SIMPLE_MACHINE("simple_machine", "item.gtlcore.cfg_copy.type.simple_machine"),
        ITEM_BUS("item_bus", "item.gtlcore.cfg_copy.type.item_bus"),
        QUANTUM_CHEST("quantum_chest", "item.gtlcore.cfg_copy.type.quantum_chest"),
        QUANTUM_TANK("quantum_tank", "item.gtlcore.cfg_copy.type.quantum_tank"),
        FLUID_OUTPUT_HATCH("fluid_output_hatch", "item.gtlcore.cfg_copy.type.fluid_output_hatch"),
        GRAVITY_CONFIGURATION_HATCH("gravity_configuration_hatch", "item.gtlcore.cfg_copy.type.gravity_configuration_hatch"),
        CONFIGURATION_MAINTENANCE_HATCH("configuration_maintenance_hatch", "item.gtlcore.cfg_copy.type.configuration_maintenance_hatch"),
        GRAVITY_HATCH("gravity_hatch", "item.gtlcore.cfg_copy.type.gravity_hatch");

        private final String id;
        private final String translationKey;

        CopiedConfiguration(String id, String translationKey) {
            this.id = id;
            this.translationKey = translationKey;
        }

        private static CopiedConfiguration fromId(String id) {
            for (CopiedConfiguration copiedConfiguration : values()) {
                if (copiedConfiguration.id.equals(id)) {
                    return copiedConfiguration;
                }
            }
            return null;
        }
    }
}
