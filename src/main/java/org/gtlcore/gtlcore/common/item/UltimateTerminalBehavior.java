package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.api.gui.BlockMapSelectorWidget;
import org.gtlcore.gtlcore.api.pattern.AdvancedBlockPattern;
import org.gtlcore.gtlcore.common.block.BlockMap;
import org.gtlcore.gtlcore.integration.terminal.TerminalMaterials;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.textfield.TextFieldWidget;
import java.util.*;
import java.util.function.Consumer;

/** Settings belong to the physical terminal, including off-hand interactions. */
public class UltimateTerminalBehavior implements IItemUIHolder {
    private static final String[] MODES = {"NoHatchMode", "ReplaceMode", "IsFlipped", "AEMode", "DismantleMode"};
    private static final String[] LABELS = {"no_hatch_mode", "replace_mode", "mirror_mode", "ae_mode", "dismantle_mode"};

    private static CompoundTag settings(ItemStack item) {
        return item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
    }
    public static void updateSettings(ItemStack item, Consumer<CompoundTag> change) {
        var tag = settings(item); change.accept(tag); item.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }
    public static void setRepeatCount(ItemStack item, int count) {
        updateSettings(item, tag -> tag.putInt("RepeatCount", Math.clamp(count, 0, 648)));
    }
    public static void setMode(ItemStack item, String key, boolean enabled) {
        if (!Arrays.asList(MODES).contains(key)) throw new IllegalArgumentException("Unknown terminal mode: " + key);
        updateSettings(item, tag -> tag.putBoolean(key, enabled));
    }
    private static boolean getMode(ItemStack item, String key) {
        var tag = settings(item); return tag.contains(key) ? tag.getBoolean(key) : key.equals("NoHatchMode");
    }
    public static AutoBuildSetting getAutoBuildSetting(ItemStack item) {
        var tag = settings(item); var result = new AutoBuildSetting();
        result.setRepeatCount(tag.getInt("RepeatCount"));
        result.setNoHatchMode(getMode(item, "NoHatchMode")); result.setReplaceMode(getMode(item, "ReplaceMode"));
        result.setFlipped(getMode(item, "IsFlipped")); result.setAeMode(getMode(item, "AEMode"));
        result.setDismantleMode(getMode(item, "DismantleMode"));
        if (tag.contains("BoundAE", Tag.TAG_COMPOUND)) {
            var binding = tag.getCompound("BoundAE"); var id = ResourceLocation.tryParse(binding.getString("dim"));
            if (id != null) result.setBoundAE(GlobalPos.of(ResourceKey.create(Registries.DIMENSION, id),
                    new BlockPos(binding.getInt("X"), binding.getInt("Y"), binding.getInt("Z"))));
        }
        for (var entry : BlockMap.tierBlockMap.entrySet()) {
            Block selected = getSelectedTierBlock(item, entry.getKey());
            if (selected != null) result.addTierSelection(entry.getValue().get(), selected);
        }
        return result;
    }
    public static Block getSelectedTierBlock(ItemStack item, String type) {
        var source = BlockMap.tierBlockMap.get(type); if (source == null) return null;
        var blocks = source.get(); var tag = settings(item); Block selected = null;
        var selections = tag.getCompound("TierSelections");
        if (selections.contains(type, Tag.TAG_STRING)) {
            var id = ResourceLocation.tryParse(selections.getString(type));
            if (id != null) selected = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
        } else if (type.equals(tag.getString("blocks")) && blocks.length > 0) {
            selected = blocks[Math.clamp(tag.getInt("Tier"), 0, blocks.length - 1)];
        }
        return Arrays.asList(blocks).contains(selected) ? selected : null;
    }
    public static void setTierSelection(ItemStack item, String type, int tier) {
        var source = BlockMap.tierBlockMap.get(type);
        if (source == null || tier < -1 || tier >= source.get().length) throw new IllegalArgumentException("Invalid terminal tier selection");
        updateSettings(item, tag -> {
            var selections = tag.getCompound("TierSelections");
            if (tier == -1) selections.remove(type);
            else selections.putString(type, BuiltInRegistries.BLOCK.getKey(source.get()[tier]).toString());
            tag.put("TierSelections", selections);
            if (tier == -1 && type.equals(tag.getString("blocks"))) { tag.remove("blocks"); tag.remove("Tier"); }
            else if (tier >= 0) { tag.putString("blocks", type); tag.putInt("Tier", tier); }
        });
    }
    @Override public InteractionResult useOn(UseOnContext context) {
        var player = context.getPlayer(); if (player == null || !player.isShiftKeyDown()) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer)) return InteractionResult.SUCCESS;
        var level = context.getLevel(); var pos = context.getClickedPos(); var item = context.getItemInHand();
        var node = AdvancedBlockPattern.getGridNode(level.getBlockEntity(pos));
        if (node != null) {
            updateSettings(item, tag -> {
                var binding = new CompoundTag(); binding.putInt("X", pos.getX()); binding.putInt("Y", pos.getY()); binding.putInt("Z", pos.getZ());
                binding.putString("dim", level.dimension().location().toString()); tag.put("BoundAE", binding);
            });
            player.sendSystemMessage(Component.translatable("gtlcore.terminal.ae_bound", pos.toShortString()));
            if (getMode(item, "AEMode") && node.isActive()) {
                new TerminalMaterials(player, item, node.getGrid().getStorageService().getInventory()).refund();
            }
            return InteractionResult.SUCCESS;
        }
        if (!(MetaMachine.getMachine(level, pos) instanceof MultiblockControllerMachine controller)) return InteractionResult.PASS;
        var options = getAutoBuildSetting(item);
        var pattern = AdvancedBlockPattern.getAdvancedBlockPattern(controller.getSubstructurePattern(MultiblockControllerMachine.DEFAULT_STRUCTURE));
        if (options.isDismantleMode()) pattern.dismantleMultiblock(controller, player, options, item);
        else if (!controller.isFormed() || options.isReplaceMode()) pattern.autoBuild(player, controller, options, item);
        return InteractionResult.SUCCESS;
    }
    @Override public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager sync, UISettings settings) {
        var panel = ModularPanel.defaultPanel("ultimateTerminal", 408, 224).background(GTGuiTextures.BACKGROUND)
                .child(Text.lang("gui.gtlcore.ultimate_terminal_settings").asWidget().pos(8, 6));
        var repeats = SyncHandlers.string(() -> Integer.toString(getAutoBuildSetting(data.getUsedItemStack()).getRepeatCount()), text -> {
            try { var item = data.getUsedItemStack(); setRepeatCount(item, Integer.parseInt(text)); data.setUsedItemStack(item); }
            catch (NumberFormatException ignored) { /* Keep the last saved value while editing. */ }
        }).allowC2S();
        sync.syncValue("terminalRepeat", repeats);
        panel.child(Text.lang("gui.gtlcore.repeat_count").asWidget().pos(8, 29).width(130));
        panel.child(new TextFieldWidget().setNumbersLong(number -> Math.clamp(number, 0, 648)).value(repeats).size(48, 16).pos(142, 25));
        for (int i = 0; i < MODES.length; i++) {
            String key = MODES[i]; var value = SyncHandlers.bool(() -> getMode(data.getUsedItemStack(), key), enabled -> {
                var item = data.getUsedItemStack(); setMode(item, key, enabled); data.setUsedItemStack(item);
            }).allowC2S();
            sync.syncValue("terminal_" + key, value);
            panel.child(Text.lang("gui.gtlcore." + LABELS[i]).asWidget().pos(8, 50 + i * 22).width(130));
            panel.child(new ToggleButton().background(GTGuiTextures.BUTTON).value(value).overlay(false, Text.of(Component.literal("OFF"))).overlay(true, Text.of(Component.literal("ON")))
                    .size(48, 18).pos(142, 46 + i * 22));
        }
        panel.child(new BlockMapSelectorWidget(208, 25, 192, data, sync));
        var materials = SyncHandlers.<RegistryFriendlyByteBuf, Component>generic(Component.class)
                .getter(() -> TerminalMaterials.describe(data.getUsedItemStack(), data.getPlayer().registryAccess()))
                .serializer(ComponentSerialization.STREAM_CODEC).deserializer(ComponentSerialization.STREAM_CODEC)
                .copy(Component::copy).build();
        sync.syncValue("terminalMaterials", materials);
        panel.child(Text.dynamic(() -> Objects.requireNonNullElse(materials.getValue(), Component.empty()))
                .asWidget().pos(8, 164).width(392).height(36));
        panel.child(Text.lang("gtlcore.terminal.pending_hint").asWidget().pos(8, 202).width(392).height(18));
        return panel;
    }
    public static class AutoBuildSetting {
        private final Map<Block, Block> tierReplacements = new IdentityHashMap<>();
        private final Set<Block> blocks = Collections.newSetFromMap(new IdentityHashMap<>());
        private int repeatCount;
        private boolean noHatchMode = true, replaceMode, isFlipped, dismantleMode, aeMode;
        private GlobalPos boundAE;
        public int getRepeatCount() { return repeatCount; }
        public void setRepeatCount(int count) { repeatCount = Math.clamp(count, 0, 648); }
        public boolean isNoHatchMode() { return noHatchMode; }
        public void setNoHatchMode(boolean value) { noHatchMode = value; }
        public boolean isReplaceMode() { return replaceMode; }
        public void setReplaceMode(boolean value) { replaceMode = value; }
        public boolean isFlipped() { return isFlipped; }
        public void setFlipped(boolean value) { isFlipped = value; }
        public boolean isDismantleMode() { return dismantleMode; }
        public void setDismantleMode(boolean value) { dismantleMode = value; }
        public boolean isAeMode() { return aeMode; }
        public void setAeMode(boolean value) { aeMode = value; }
        public GlobalPos getBoundAE() { return boundAE; }
        public void setBoundAE(GlobalPos value) { boundAE = value; }
        public Set<Block> getBlocks() { return Collections.unmodifiableSet(blocks); }
        public void addTierSelection(Block[] tiers, Block selected) {
            if (!Arrays.asList(tiers).contains(selected)) throw new IllegalArgumentException("Selected block is not in the tier group");
            for (Block block : tiers) { tierReplacements.put(block, selected); blocks.add(block); }
        }
        public Block selectedReplacement(Block block) { return tierReplacements.get(block); }
        public List<ItemStack> apply(BlockInfo[] candidates) {
            if (candidates == null) return List.of();
            for (var candidate : candidates) {
                Block selected = selectedReplacement(candidate.getBlockState().getBlock());
                if (selected != null && candidates.length > 1) return List.of(new ItemStack(selected));
            }
            var result = new ArrayList<ItemStack>();
            for (var candidate : candidates) {
                if (candidate.getBlockState().getBlock() instanceof LiquidBlock) result.add(new ItemStack(candidate.getBlockState().getFluidState().getType().getBucket()));
                else if (candidate.getBlockState().getBlock() != Blocks.AIR) result.add(candidate.getItemStackForm().copy());
            }
            return result;
        }
        public boolean isPlaceHatch(BlockInfo[] candidates) {
            return !noHatchMode || candidates == null || candidates.length == 0 || !isHatch(candidates[0].getBlockState().getBlock());
        }
        public boolean shouldPreserveDuringDismantle(Block block) { return dismantleMode && noHatchMode && isHatch(block); }
        private static final Map<Block, Boolean> HATCHES = new IdentityHashMap<>();
        public static boolean isHatch(Block block) {
            return HATCHES.computeIfAbsent(block, candidate -> candidate instanceof MetaMachineBlock machine &&
                    machine.newBlockEntity(BlockPos.ZERO, machine.defaultBlockState()) instanceof MultiblockPartMachine);
        }
    }
}
