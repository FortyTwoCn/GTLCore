package org.gtlcore.gtlcore.client.gui.widget;

import com.gregtechceu.gtceu.common.mui.GTGuiTextures;
import com.gregtechceu.gtceu.integration.ae2.gui.AEGuiHelper;
import brachy.modularui.integration.recipeviewer.entry.EntryList;
import brachy.modularui.integration.recipeviewer.entry.item.ItemStackList;
import brachy.modularui.integration.recipeviewer.entry.fluid.FluidStackList;
import brachy.modularui.integration.recipeviewer.handlers.IngredientProvider;
import brachy.modularui.screen.viewport.ModularGuiContext;
import brachy.modularui.theme.WidgetThemeEntry;
import brachy.modularui.value.sync.GenericSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.Widget;
import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import java.util.*;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Read-only, server-owned rotating native AE ingredient previews. Amounts stay long throughout synchronization. */
public final class PatternCycleWidget extends ParentWidget<PatternCycleWidget> {
    public static final int CYCLE_TICKS = 20;
    private static final int VISIBLE_STACKS = 6;
    private final Supplier<List<IPatternDetails>> patternSupplier;
    private @Nullable GenericSyncValue<RegistryFriendlyByteBuf, Preview> preview;

    public PatternCycleWidget(int x, int y, int width, int height, Supplier<List<IPatternDetails>> supplier) {
        this.patternSupplier = supplier; pos(x, y).size(width, height);
        int start = Math.max(0, (width - 131) / 2);
        for (int index = 0; index < VISIBLE_STACKS; index++) {
            final int slot = index;
            child(new StackCell(() -> displayed().inputs(), slot).pos(start + (index % 3) * 18, (index / 3) * 18));
            child(new StackCell(() -> displayed().outputs(), slot).pos(start + 77 + (index % 3) * 18, (index / 3) * 18));
        }
        child(new Widget<>().pos(start + 58, 13).size(15, 10).background(GTGuiTextures.PROGRESS_ARROW.main().getSubArea(0, 0, 1, 0.5f)));
    }

    public PatternCycleWidget sync(PanelSyncManager manager, String name, LongSupplier tick) {
        if (preview != null) throw new IllegalStateException("Pattern cycle preview already bound");
        name(name);
        preview = GenericSyncValue.<RegistryFriendlyByteBuf, Preview>builder(Preview.class)
                .getter(() -> Preview.select(patternSupplier.get(), tick.getAsLong()))
                .serializer(Preview::write).deserializer(Preview::read).copy(value -> value).build();
        manager.syncValue(name, preview);
        return this;
    }

    private Preview displayed() { return preview == null || preview.getValue() == null ? Preview.EMPTY : preview.getValue(); }

    public record Preview(List<GenericStack> inputs, List<GenericStack> outputs) {
        public static final Preview EMPTY = new Preview(List.of(), List.of());
        public Preview { inputs = checked(inputs); outputs = checked(outputs); }
        private static List<GenericStack> checked(List<GenericStack> stacks) {
            if (stacks.size() > VISIBLE_STACKS) throw new IllegalArgumentException("Too many native preview stacks");
            for (var stack : stacks) if (stack == null || stack.what() == null || stack.amount() <= 0)
                throw new IllegalArgumentException("Invalid native preview stack");
            return List.copyOf(stacks);
        }
        public static Preview select(List<IPatternDetails> patterns, long tick) {
            if (patterns.isEmpty()) return EMPTY;
            var pattern = patterns.get((int) Math.floorMod(Math.floorDiv(tick, CYCLE_TICKS), patterns.size()));
            var inputs = new ArrayList<GenericStack>();
            for (var input : pattern.getInputs()) {
                var possible = input.getPossibleInputs();
                if (possible.length > 0) {
                    inputs.add(new GenericStack(possible[0].what(), Math.multiplyExact(possible[0].amount(), input.getMultiplier())));
                    if (inputs.size() == VISIBLE_STACKS) break;
                }
            }
            return new Preview(inputs, pattern.getOutputs().stream().limit(VISIBLE_STACKS).toList());
        }
        public static void write(RegistryFriendlyByteBuf buffer, Preview value) {
            writeStacks(buffer, value.inputs); writeStacks(buffer, value.outputs);
        }
        private static void writeStacks(RegistryFriendlyByteBuf buffer, List<GenericStack> stacks) {
            buffer.writeVarInt(stacks.size());
            for (var stack : stacks) GenericStack.STREAM_CODEC.encode(buffer, stack);
        }
        public static Preview read(RegistryFriendlyByteBuf buffer) { return new Preview(readStacks(buffer), readStacks(buffer)); }
        private static List<GenericStack> readStacks(RegistryFriendlyByteBuf buffer) {
            int count = buffer.readVarInt();
            if (count < 0 || count > VISIBLE_STACKS) throw new IllegalArgumentException("Invalid native preview stack count");
            var stacks = new ArrayList<GenericStack>(count);
            for (int index = 0; index < count; index++) stacks.add(GenericStack.STREAM_CODEC.decode(buffer));
            return stacks;
        }
    }

    private static final class StackCell extends Widget<StackCell> implements IngredientProvider<Object> {
        private final Supplier<List<GenericStack>> stacks;
        private final int index;
        private StackCell(Supplier<List<GenericStack>> stacks, int index) {
            this.stacks = stacks; this.index = index; size(18);
            tooltipDynamic(tooltip -> {
                var value = stack();
                if (value != null) {
                    tooltip.addFromItem(GenericStack.wrapInItemStack(value));
                    tooltip.newLine().add(net.minecraft.network.chat.Component.literal(
                            AEGuiHelper.formatAmountFull(value.amount()) + (value.what() instanceof AEFluidKey ? " mB" : "")));
                }
            });
            tooltip().autoUpdate(true);
        }
        private @Nullable GenericStack stack() { var entries = stacks.get(); return index < entries.size() ? entries.get(index) : null; }
        @OnlyIn(Dist.CLIENT) @Override public void draw(ModularGuiContext context, WidgetThemeEntry<?> theme) {
            GTGuiTextures.SLOT.draw(context, 0, 0, 18, 18);
            var value = stack(); if (value == null) return;
            var graphics = context.getGraphics();
            if (value.what() instanceof AEItemKey item) graphics.renderItem(item.toStack(), 1, 1);
            else AEGuiHelper.drawFluid(graphics, value, 1, 1);
            var font = Minecraft.getInstance().font;
            String amount = org.gtlcore.gtlcore.utils.NumberUtils.formatLong(value.amount());
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 200); graphics.pose().scale(0.5f, 0.5f, 1);
            graphics.drawString(font, amount, 34 - font.width(amount), 34 - font.lineHeight, 0xFFFFFFFF, true);
            graphics.pose().popPose();
        }
        @OnlyIn(Dist.CLIENT) @Override public void drawForeground(ModularGuiContext context) {
            var value = stack();
            if (value != null && isHovering() && !context.getUISettings().drawTooltipExternally())
                tooltip().draw(context, GenericStack.wrapInItemStack(value));
        }
        @SuppressWarnings({"rawtypes", "unchecked"}) private static EntryList<Object> cast(EntryList<?> values) { return (EntryList) values; }
        @OnlyIn(Dist.CLIENT) @Override public EntryList<Object> getIngredients() {
            var value = stack();
            if (value != null && value.what() instanceof AEFluidKey fluid) return cast(FluidStackList.of(fluid.toStack(1000)));
            return cast(value != null && value.what() instanceof AEItemKey item ? ItemStackList.of(item.toStack()) : new ItemStackList());
        }
        @OnlyIn(Dist.CLIENT) @SuppressWarnings({"rawtypes", "unchecked"}) @Override public Class<Object> ingredientClass() {
            var value = stack(); return (Class) (value != null && value.what() instanceof AEFluidKey ? FluidStack.class : ItemStack.class);
        }
    }
}
