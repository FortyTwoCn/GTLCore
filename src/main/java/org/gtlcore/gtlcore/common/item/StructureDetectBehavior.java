package org.gtlcore.gtlcore.common.item;

import org.gtlcore.gtlcore.mixin.gtm.api.pattern.BasicSliceGroupAccessor;
import org.gtlcore.gtlcore.mixin.gtm.api.pattern.BasicSliceStrategyAccessor;
import org.gtlcore.gtlcore.network.GTLNetworkHandler;
import org.gtlcore.gtlcore.network.packet.SStructureDetectHighlight;

import com.gregtechceu.gtceu.api.item.component.IInteractionItem;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.PartAbility;
import com.gregtechceu.gtceu.api.multiblock.error.*;
import com.gregtechceu.gtceu.api.multiblock.pattern.*;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.common.item.behavior.TooltipBehavior;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/** The standalone detection item checks native structures without replacing a machine's live PatternState. */
public class StructureDetectBehavior extends TooltipBehavior implements IInteractionItem {
    private static final ReentrantLock LOCK = new ReentrantLock();
    private static final String MIRRORED = "isFlipped";
    public static final StructureDetectBehavior INSTANCE = new StructureDetectBehavior(lines -> {
        lines.add(Component.translatable("item.gtlcore.structure_detect.tooltip.0"));
        lines.add(Component.translatable("item.gtlcore.structure_detect.tooltip.1"));
    });

    public StructureDetectBehavior(Consumer<List<Component>> tooltips) { super(tooltips); }

    public static boolean isMirrored(ItemStack stack) {
        return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getBoolean(MIRRORED);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player) || !(context.getLevel() instanceof ServerLevel level))
            return InteractionResult.PASS;
        var pos = context.getClickedPos();
        if (MetaMachine.getMachine(level, pos) instanceof MultiblockControllerMachine controller) {
            if (controller.isFormed() && controller.getDefinition().getStructurePatterns().keySet().stream()
                    .allMatch(name -> controller.getPatternState(name).isFormed())) {
                player.sendSystemMessage(Component.translatable("message.gtlcore.structure_formed").withStyle(ChatFormatting.GREEN));
            } else {
                boolean mirrored = isMirrored(stack);
                level.getServer().execute(() -> {
                    if (controller.isRemoved() || level.getBlockEntity(pos) != controller) return;
                    if (!LOCK.tryLock()) {
                        player.sendSystemMessage(Component.translatable("message.gtlcore.structure_detection_running"));
                        return;
                    }
                    try {
                        for (var error : detect(controller, mirrored)) showError(player, error, mirrored);
                    } finally { LOCK.unlock(); }
                });
            }
            return InteractionResult.SUCCESS;
        }
        boolean mirrored = !isMirrored(stack);
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putBoolean(MIRRORED, mirrored));
        player.displayClientMessage(Component.translatable(mirrored ? "message.gtlcore.detection_mode_mirrored" :
                "message.gtlcore.detection_mode_normal"), true);
        return InteractionResult.SUCCESS;
    }

    /** Fresh contexts for all substructures; preserve accepted repetitions in the shared native pattern. */
    public List<DetectionError> detect(MultiblockControllerMachine controller, boolean mirrored) {
        if (!(controller.getLevel() instanceof ServerLevel level) || !level.getServer().isSameThread())
            throw new IllegalStateException("Structure detection must run on the server thread");
        LOCK.lock();
        try {
            var errors = new ArrayList<DetectionError>();
            for (var entry : controller.getDefinition().getStructurePatterns().entrySet()) {
                var pattern = controller.getSubstructurePattern(entry.getKey());
                if (pattern == null) pattern = entry.getValue().get();
                var state = new PatternState();
                state.setController(controller, controller.getBlockPos());
                int[] repeats = pattern instanceof BlockPattern block ?
                        Arrays.stream(block.getSlices()).mapToInt(PatternSlice::getActualRepeats).toArray() : new int[0];
                List<?> groups = pattern instanceof BlockPattern block && block.getSliceStrategy() instanceof BasicSliceStrategy strategy ?
                        ((BasicSliceStrategyAccessor) strategy).gtlcore$getGroups() : List.of();
                int[] groupRepeats = new int[groups.size()];
                if (pattern instanceof BlockPattern block && block.getSliceStrategy() instanceof BasicSliceStrategy strategy)
                    for (int i = 0; i < groups.size(); i++) groupRepeats[i] = strategy.getMultiSliceRepeats(i);
                try {
                    boolean valid = pattern.checkPatternAt(level, state, controller.getBlockPos(), controller.getFrontFacing(),
                            controller.getUpwardsFacing(), mirrored);
                    if (!valid) {
                        for (var error : state.getErrors()) {
                            var errorPos = error instanceof SinglePredicateError ? state.getContext().pos() : error.getPos();
                            errors.add(new DetectionError(entry.getKey(), error, errorPos));
                        }
                        if (!state.hasErrors()) errors.add(new DetectionError(entry.getKey(),
                                PatternStringError.translatable("gtceu.multiblock.invalid_structure"), controller.getBlockPos()));
                    }
                } finally {
                    if (pattern instanceof BlockPattern block) {
                        for (int i = 0; i < repeats.length; i++) block.getSlices()[i].setActualRepeats(repeats[i]);
                        for (int i = 0; i < groups.size(); i++) ((BasicSliceGroupAccessor) groups.get(i)).gtlcore$setActual(groupRepeats[i]);
                    }
                }
            }
            return List.copyOf(errors);
        } finally { LOCK.unlock(); }
    }

    public List<Component> describe(DetectionError detection, boolean mirrored) {
        var error = detection.error();
        if (error instanceof PatternStringError message) return List.of(message.getComponent());
        var pos = detection.pos();
        var location = Component.translatable("item.gtlcore.structure_detect.error.2", pos.getX(), pos.getY(), pos.getZ(),
                Component.translatable(mirrored ? "item.gtlcore.structure_detect.error.3" : "item.gtlcore.structure_detect.error.4")
                        .withStyle(mirrored ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
        var lines = new ArrayList<Component>();
        lines.add(Component.translatable(error instanceof SinglePredicateError ? "item.gtlcore.structure_detect.error.1" :
                "item.gtlcore.structure_detect.error.0", location));
        candidateNames(error).forEach(name -> lines.add(Component.literal(" - ").append(name)));
        if (error instanceof SinglePredicateError limit) {
            int expected = switch (limit.type) {
                case MIN_COUNT -> limit.predMinCount;
                case MAX_COUNT -> limit.predMaxCount;
                case MIN_LAYER_COUNT -> limit.predMinLayerCount;
                case MAX_LAYER_COUNT -> limit.predMaxLayerCount;
            };
            lines.add(Component.translatable("gtceu.multiblock.pattern.error.limited." + limit.type.getSerializedName(), expected, limit.actualCount));
        } else if (error instanceof MismatchError<?> mismatch) {
            lines.add(mismatch.lang());
        }
        return List.copyOf(lines);
    }

    private void showError(ServerPlayer player, DetectionError detection, boolean mirrored) {
        describe(detection, mirrored).forEach(player::sendSystemMessage);
        if (!(detection.error() instanceof PatternStringError))
            GTLNetworkHandler.INSTANCE.sendTo(new SStructureDetectHighlight(detection.pos(), player.level().dimension(),
                    System.currentTimeMillis() + 15000), player);
    }

    private List<Component> candidateNames(PatternError error) {
        if (error instanceof BlockMatchingError blocks) return blocks.getBlocks().stream().map(block -> (Component) block.getName()).toList();
        if (error instanceof PartAbilityError part) {
            var ability = PartAbility.VALUES.get(part.getPartAbilityName());
            return ability == null ? List.of() : ability.getAllBlocks().stream().map(block -> (Component) block.getName()).toList();
        }
        if (error instanceof SinglePredicateError limit) return limit.candidates.stream().map(this::candidateName).filter(java.util.Objects::nonNull).toList();
        if (error instanceof SimplePatternError simple) return simple.getCandidates().stream().filter(list -> !list.isEmpty())
                .map(list -> candidateName(list.getFirst())).filter(java.util.Objects::nonNull).toList();
        return List.of();
    }

    private Component candidateName(BlockInfo candidate) {
        var fluid = candidate.getBlockState().getFluidState();
        if (!fluid.isEmpty()) return fluid.getType().getFluidType().getDescription();
        var item = candidate.getItemStackForm();
        return item.isEmpty() ? null : item.getHoverName();
    }

    public record DetectionError(String structure, PatternError error, BlockPos pos) {
        public DetectionError { pos = pos.immutable(); }
    }
}
