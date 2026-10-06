package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.api.pattern.DebugBlockPattern;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.mui.IItemUIHolder;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import brachy.modularui.api.drawable.Text;
import brachy.modularui.factory.PlayerInventoryGuiData;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.InteractionSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.value.sync.SyncHandlers;
import brachy.modularui.widgets.ButtonWidget;
import org.slf4j.LoggerFactory;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;

/** Native held-item UI; selections and rotation belong to the physical tool. */
public class StructureWriteBehavior implements IItemUIHolder {
    public static final StructureWriteBehavior INSTANCE = new StructureWriteBehavior();
    private static final String DATA = "structure_writer";
    protected StructureWriteBehavior() {}

    @Override public ModularPanel<?> buildUI(PlayerInventoryGuiData<?> data, PanelSyncManager sync, UISettings settings) {
        var panel = ModularPanel.defaultPanel("structureWriter", 176, 120).background(GTGuiTextures.BACKGROUND);
        var scale = SyncHandlers.<RegistryFriendlyByteBuf, Component>generic(Component.class)
                .getter(() -> {
                    var pos = getPos(data.getUsedItemStack());
                    return Component.translatable("structure_writer.structural_scale",
                            pos == null ? "0" : Long.toString(1L + pos[1].getX() - pos[0].getX()),
                            pos == null ? "0" : Long.toString(1L + pos[1].getY() - pos[0].getY()),
                            pos == null ? "0" : Long.toString(1L + pos[1].getZ() - pos[0].getZ()));
                }).serializer(ComponentSerialization.STREAM_CODEC).deserializer(ComponentSerialization.STREAM_CODEC)
                .copy(Component::copy).build();
        var order = SyncHandlers.<RegistryFriendlyByteBuf, Component>generic(Component.class)
                .getter(() -> {
                    var dirs = DebugBlockPattern.getDir(getDir(data.getUsedItemStack()));
                    return Component.translatable("structure_writer.export_order", dirs[0].name(), dirs[1].name(), dirs[2].name());
                }).serializer(ComponentSerialization.STREAM_CODEC).deserializer(ComponentSerialization.STREAM_CODEC)
                .copy(Component::copy).build();
        sync.syncValue("structureWriter_scale", scale);
        sync.syncValue("structureWriter_order", order);
        panel.child(Text.dynamic(() -> Objects.requireNonNullElse(scale.getValue(), Component.empty())).asWidget().pos(8, 10).width(160).height(24));
        panel.child(Text.dynamic(() -> Objects.requireNonNullElse(order.getValue(), Component.empty())).asWidget().pos(8, 35).width(160).height(26));
        action(panel, sync, data, "rotate_x", 9, 68, 77, item -> rotate(item, Direction.Axis.X));
        action(panel, sync, data, "rotate_y", 90, 68, 77, item -> rotate(item, Direction.Axis.Y));
        action(panel, sync, data, "export_log", 9, 91, 158, item -> {
            if (getPos(item) == null) return;
            try {
                LoggerFactory.getLogger("gtlcore.structure_writer").info(createExport(data.getPlayer().level(), item));
            } catch (IllegalArgumentException | IllegalStateException exception) {
                data.getPlayer().sendSystemMessage(Component.translatable("message.gtlcore.structure_writer.export_failed", exception.getMessage()));
            }
        });
        return panel;
    }
    private static void action(ModularPanel<?> panel, PanelSyncManager sync, PlayerInventoryGuiData<?> data,
                               String action, int x, int y, int width, Consumer<ItemStack> callback) {
        var handler = new InteractionSyncHandler().setOnMousePressed(mouse -> {
            if (mouse.isLeftMouseButton() && data.getPlayer() instanceof ServerPlayer) {
                var item = data.getUsedItemStack();
                callback.accept(item);
                data.setUsedItemStack(item);
            }
        });
        sync.syncValue("structureWriter_" + action, handler);
        panel.child(new ButtonWidget<>().name("structureWriter_" + action).background(GTGuiTextures.BUTTON).syncHandler(handler).overlay(Text.lang("structure_writer." + action)).pos(x, y).size(width, 20));
    }
    private static void rotate(ItemStack item, Direction.Axis axis) {
        if (getPos(item) != null) setDir(item, getDir(item).getClockWise(axis));
    }
    public static String createExport(Level level, ItemStack item) {
        var pos = getPos(item);
        if (pos == null) return "";
        var captured = new DebugBlockPattern(level, pos[0].getX(), pos[0].getY(), pos[0].getZ(),
                pos[1].getX(), pos[1].getY(), pos[1].getZ());
        var dirs = DebugBlockPattern.getDir(getDir(item));
        captured.changeDir(dirs[0], dirs[1], dirs[2]);
        return exportPattern(captured);
    }
    /** Emits the same slice/row/character directions used by the captured native pattern. */
    public static String exportPattern(DebugBlockPattern captured) {
        var dirs = captured.structureDir;
        var result = new StringBuilder(".pattern(definition -> MultiblockPatternBuilder.start(")
                .append("RelativeDirection.").append(dirs[2].name()).append(", RelativeDirection.").append(dirs[1].name())
                .append(", RelativeDirection.").append(dirs[0].name()).append(")\n");
        if (!captured.hasController()) {
            result.append("    // Select a controller too, or replace one block symbol with '~' and remove startOffset.\n")
                    .append("    .startOffset(OriginOffset.ZERO)\n");
        }
        for (var slice : captured.pattern) {
            result.append("    .slice(");
            for (int row = 0; row < slice.length; row++) {
                if (row != 0) result.append(", ");
                result.append('"').append(escape(slice[row], false)).append('"');
            }
            result.append(")\n");
        }
        if (captured.hasController()) result.append("    .where('~', Predicates.controller(definition))\n");
        captured.legend.entrySet().stream().sorted(Comparator.comparingInt(entry -> entry.getValue())).forEach(entry -> {
            if (entry.getValue() == ' ') return;
            result.append("    .where('").append(escape(entry.getValue().toString(), true))
                    .append("', Predicates.blocks(BuiltInRegistries.BLOCK.get(ResourceLocation.parse(\"")
                    .append(BuiltInRegistries.BLOCK.getKey(entry.getKey())).append("\"))))\n");
        });
        return result.append("    .build())\n").toString();
    }
    private static String escape(String value, boolean character) {
        var result = new StringBuilder();
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> result.append("\\\\");
                case '"' -> result.append(character ? "\"" : "\\\"");
                case '\'' -> result.append(character ? "\\'" : "'");
                case '\n' -> result.append("\\n");
                case '\r' -> result.append("\\r");
                case '\t' -> result.append("\\t");
                default -> result.append(c);
            }
        }
        return result.toString();
    }
    public static boolean isItemStructureWriter(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof ComponentItem item && item.getComponents().contains(INSTANCE);
    }
    private static CompoundTag settings(ItemStack item) {
        return item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(DATA);
    }
    private static void write(ItemStack item, CompoundTag settings) {
        var root = item.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (settings.isEmpty()) root.remove(DATA); else root.put(DATA, settings);
        if (root.isEmpty()) item.remove(DataComponents.CUSTOM_DATA); else item.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }
    public static Direction getDir(ItemStack stack) {
        var dir = Direction.byName(settings(stack).getString("dir"));
        return dir == null ? Direction.WEST : dir;
    }
    public static void setDir(ItemStack stack, Direction dir) {
        var settings = settings(stack); settings.putString("dir", dir.getName()); write(stack, settings);
    }
    public static BlockPos[] getPos(ItemStack stack) {
        var settings = settings(stack);
        for (var key : new String[]{"minX", "minY", "minZ", "maxX", "maxY", "maxZ"})
            if (!settings.contains(key, Tag.TAG_INT)) return null;
        var min = new BlockPos(settings.getInt("minX"), settings.getInt("minY"), settings.getInt("minZ"));
        var max = new BlockPos(settings.getInt("maxX"), settings.getInt("maxY"), settings.getInt("maxZ"));
        return min.getX() <= max.getX() && min.getY() <= max.getY() && min.getZ() <= max.getZ() ? new BlockPos[]{min, max} : null;
    }
    public static void addPos(ItemStack stack, BlockPos pos) {
        var settings = settings(stack); var bounds = getPos(stack);
        settings.putInt("minX", bounds == null ? pos.getX() : Math.min(bounds[0].getX(), pos.getX()));
        settings.putInt("minY", bounds == null ? pos.getY() : Math.min(bounds[0].getY(), pos.getY()));
        settings.putInt("minZ", bounds == null ? pos.getZ() : Math.min(bounds[0].getZ(), pos.getZ()));
        settings.putInt("maxX", bounds == null ? pos.getX() : Math.max(bounds[1].getX(), pos.getX()));
        settings.putInt("maxY", bounds == null ? pos.getY() : Math.max(bounds[1].getY(), pos.getY()));
        settings.putInt("maxZ", bounds == null ? pos.getZ() : Math.max(bounds[1].getZ(), pos.getZ()));
        write(stack, settings);
    }
    public static void removePos(ItemStack stack) {
        var settings = settings(stack);
        for (var key : new String[]{"minX", "minY", "minZ", "maxX", "maxY", "maxZ"}) settings.remove(key);
        write(stack, settings);
    }
    @Override public InteractionResult onItemUseFirst(ItemStack item, UseOnContext context) {
        var player = context.getPlayer();
        if (player != null && !context.getLevel().isClientSide) {
            if (player.isShiftKeyDown()) removePos(item); else addPos(item, context.getClickedPos());
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }
    @Override public InteractionResultHolder<ItemStack> use(ItemStack item, Level level, Player player, InteractionHand hand) {
        if (!player.isShiftKeyDown()) return IItemUIHolder.super.use(item, level, player, hand);
        if (!level.isClientSide) removePos(item);
        return InteractionResultHolder.sidedSuccess(item, level.isClientSide);
    }
}
