package org.gtlcore.gtlcore.api.pattern;

import org.gtlcore.gtlcore.common.item.UltimateTerminalBehavior.AutoBuildSetting;
import org.gtlcore.gtlcore.integration.ae2.WirelessTerminalGridResolver;
import org.gtlcore.gtlcore.integration.terminal.TerminalMaterials;
import org.gtlcore.gtlcore.api.gui.preview.NativePreviewBlockMap;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.PredicateContext;
import com.gregtechceu.gtceu.api.multiblock.pattern.*;
import com.gregtechceu.gtceu.api.multiblock.predicates.BasePredicate;
import com.gregtechceu.gtceu.api.multiblock.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.fluids.FluidType;
import appeng.api.networking.*;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;
import it.unimi.dsi.fastutil.ints.*;
import org.jetbrains.annotations.Nullable;
import java.util.*;

/** Uses native structure helpers and predicates; it never reflects into the removed Forge pattern engine. */
public final class AdvancedBlockPattern {
    public static final String MATCHED_REPETITIONS_CONTEXT = "gtlcore:matched_repetitions";
    public static final String MATCHED_MIN_Z_CONTEXT = "gtlcore:matched_min_z";
    private final IBlockPattern pattern;
    private AdvancedBlockPattern(IBlockPattern pattern) { this.pattern = Objects.requireNonNull(pattern); }
    public static AdvancedBlockPattern getAdvancedBlockPattern(IBlockPattern pattern) { return new AdvancedBlockPattern(pattern); }

    public static Int2IntMap configuredRepetitions(BlockPattern pattern, int repeatCount) {
        var result = new Int2IntOpenHashMap();
        for (int i = 0; i < pattern.getSlices().length; i++) {
            var slice = pattern.getSlices()[i]; result.put(i, Math.clamp(repeatCount, slice.getMinRepeats(), slice.getMaxRepeats()));
        }
        return result;
    }
    private static List<BasePredicate> bases(MultiPredicate predicate) {
        var result = new ArrayList<>(predicate.predicates());
        for (var child : predicate.children()) result.addAll(bases(child)); return result;
    }
    private AbstractStructureHelper helper(AutoBuildSetting settings) {
        if (pattern instanceof BlockPattern block) return AbstractStructureHelper.blockPattern(configuredRepetitions(block, settings.getRepeatCount()));
        if (pattern instanceof ExpandablePattern expandable) {
            var bounds = new IntArrayList();
            for (var constraint : Objects.requireNonNull(expandable.getBoundsConstraints(), "Missing expandable pattern bounds").apply())
                bounds.add(Math.clamp(settings.getRepeatCount(), constraint.leftInt(), constraint.rightInt()));
            return AbstractStructureHelper.expandable(bounds);
        }
        throw new IllegalArgumentException("Unsupported native pattern implementation: " + pattern.getClass().getName());
    }
    private void preferences(AbstractStructureHelper helper, MultiPredicate predicate, AutoBuildSetting settings) {
        boolean hasCasing = bases(predicate).stream().flatMap(base -> base.getCandidates().stream())
                .anyMatch(info -> info.nonAir() && !AutoBuildSetting.isHatch(info.getBlockState().getBlock()));
        preferences(helper, predicate, settings, hasCasing);
    }
    private void preferences(AbstractStructureHelper helper, MultiPredicate predicate, AutoBuildSetting settings, boolean hasCasing) {
        for (var base : predicate.predicates()) {
            var candidates = base.getCandidates();
            if (settings.isNoHatchMode() && !candidates.isEmpty() && candidates.stream().allMatch(info -> AutoBuildSetting.isHatch(info.getBlockState().getBlock()))) {
                if (hasCasing) helper.getMinMaxPreferences().put(predicate, base, IntIntPair.of(0, 0));
                else helper.getBlockPreferences().put(predicate, base, BlockInfo.EMPTY);
                continue;
            }
            for (var candidate : candidates) {
                var selected = settings.selectedReplacement(candidate.getBlockState().getBlock());
                if (selected == null) continue;
                var replacement = candidates.stream().filter(info -> info.getBlockState().getBlock() == selected).findFirst();
                if (replacement.isPresent()) helper.getBlockPreferences().put(predicate, base, replacement.get());
            }
        }
        for (var child : predicate.children()) preferences(helper, child, settings, hasCasing);
    }
    public Map<BlockPos, BlockInfo> plan(BlockPos center, Direction front, Direction up, AutoBuildSetting settings) {
        return plan(center, front, up, settings, null);
    }
    private Map<BlockPos, BlockInfo> plan(BlockPos center, Direction front, Direction up, AutoBuildSetting settings, @Nullable Level level) {
        var helper = helper(settings); var schema = new NativePreviewBlockMap();
        if (pattern instanceof BlockPattern block) {
            for (var predicate : block.getPredicates().values()) preferences(helper, predicate, settings);
        } else {
            helper.populate(schema, pattern, null, front, up, settings.isFlipped());
            for (var pos : schema.keySet()) preferences(helper, helper.getPredicateFromPos(pattern, pos, front, up, settings.isFlipped()), settings);
            schema.clear();
        }
        BlockPos offset = center;
        if (pattern instanceof BlockPattern block) {
            var origin = center.mutable(); block.getOffset().apply(origin, front, up, settings.isFlipped());
            var repeats = configuredRepetitions(block, settings.getRepeatCount());
            int[] dimensions = {repeats.values().intStream().sum(), block.getDimensions()[1], block.getDimensions()[2]};
            // Native helpers normalize rotated structures into a positive bounding box. Undo that normalization.
            for (int axis = 0; axis < 3; axis++) {
                var direction = block.getDirections()[axis].getRelativeFacing(front, up, settings.isFlipped());
                if (direction.getAxisDirection() == Direction.AxisDirection.NEGATIVE) origin.move(direction, dimensions[axis] - 1);
            }
            offset = origin.immutable();
            if (level != null) {
                var positions = layout(block, center, front, up, settings.isFlipped(), repeats);
                for (var entry : positions.entrySet()) {
                    var pos = entry.getKey(); if (!level.hasChunkAt(pos)) continue;
                    var current = level.getBlockState(pos);
                    if (current.isAir() || settings.isReplaceMode() && settings.getBlocks().contains(current.getBlock())) continue;
                    if (!matches(level, pos, entry.getValue())) continue;
                    var canonical = bases(entry.getValue()).stream().flatMap(base -> base.getCandidates().stream())
                            .filter(info -> info.getBlockState().getBlock() == current.getBlock()).findFirst();
                    canonical.ifPresent(info -> schema.put(pos.subtract(origin), info));
                }
            }
        } else {
            var origin = center.mutable(); pattern.getOffset().apply(origin, front, up, settings.isFlipped()); offset = origin.immutable();
        }
        helper.populate(schema, pattern, null, front, up, settings.isFlipped());
        var result = new LinkedHashMap<BlockPos, BlockInfo>();
        for (var entry : schema.entrySet()) if (!entry.getValue().isAir()) result.put(entry.getKey().offset(offset), entry.getValue());
        return result;
    }

    public void autoBuild(Player player, MultiblockControllerMachine controller, AutoBuildSetting settings, ItemStack terminal) {
        if (!(player.level() instanceof ServerLevel level) || controller.getLevel() != level) return;
        var network = findStorage(level, player, settings.getBoundAE(), settings.isAeMode());
        var materials = new TerminalMaterials(player, terminal, network);
        var blocks = plan(controller.getBlockPos(), controller.getFrontFacing(), controller.getUpwardsFacing(), settings, level);
        var predicates = pattern instanceof BlockPattern block ? layout(block, controller.getBlockPos(), controller.getFrontFacing(),
                controller.getUpwardsFacing(), settings.isFlipped(), configuredRepetitions(block, settings.getRepeatCount())) : Map.<BlockPos, MultiPredicate>of();
        try {
            for (var entry : blocks.entrySet()) {
                var pos = entry.getKey(); var proposed = entry.getValue();
                if (pos.equals(controller.getBlockPos()) || !level.hasChunkAt(pos) || !level.mayInteract(player, pos)) continue;
                var current = level.getBlockState(pos);
                if (!current.isAir() && (!settings.isReplaceMode() || !settings.getBlocks().contains(current.getBlock()))) continue;
                if (current.getBlock() == proposed.getBlockState().getBlock() && !current.isAir()) continue;
                var candidates = new ArrayList<BlockInfo>(); candidates.add(proposed);
                var predicate = predicates.get(pos);
                if (predicate != null) for (var base : bases(predicate)) {
                    if (base.getCandidates().stream().noneMatch(info -> info.getBlockState().getBlock() == proposed.getBlockState().getBlock())) continue;
                    for (var candidate : base.getCandidates()) {
                        var selected = settings.selectedReplacement(candidate.getBlockState().getBlock());
                        if (selected == null || selected == candidate.getBlockState().getBlock()) candidates.add(candidate);
                    }
                }
                TerminalMaterials.Reservation reservation = null; BlockInfo chosen = null;
                for (var candidate : candidates) {
                    var candidateBlock = candidate.getBlockState().getBlock();
                    if (candidate.isAir() || settings.isNoHatchMode() && AutoBuildSetting.isHatch(candidateBlock)) continue;
                    boolean liquid = candidateBlock instanceof LiquidBlock;
                    var cost = liquid ? new ItemStack(candidate.getBlockState().getFluidState().getType().getBucket()) : candidate.getItemStackForm();
                    if (!player.mayUseItemAt(pos, Direction.UP, cost)) continue;
                    var key = liquid ? AEFluidKey.of(candidate.getBlockState().getFluidState().getType()) : AEItemKey.of(cost);
                    if (key == null) continue;
                    reservation = materials.reserve(key, liquid ? FluidType.BUCKET_VOLUME : 1);
                    if (reservation != null) { chosen = candidate; break; }
                }
                if (reservation == null) continue;
                var block = chosen.getBlockState().getBlock(); boolean fluid = block instanceof LiquidBlock;
                var snapshot = BlockSnapshot.create(level.dimension(), level, pos);
                var drops = current.isAir() ? List.<ItemStack>of() : Block.getDrops(current, level, pos, level.getBlockEntity(pos), player, terminal);
                boolean placed = false;
                try {
                    if (!current.isAir()) level.removeBlock(pos, false);
                    if (fluid) {
                        placed = level.setBlock(pos, chosen.getBlockState(), 3);
                    } else if (reservation.item().getItem() instanceof BlockItem item) {
                        var placement = reservation.item();
                        var context = new BlockPlaceContext(level, player, InteractionHand.MAIN_HAND, placement,
                                new BlockHitResult(pos.getCenter(), Direction.UP, pos, false));
                        placed = item.place(context).consumesAction() && level.getBlockState(pos).getBlock() == block;
                    }
                    if (placed) {
                        reservation.commit();
                        // Apply the native helper's orientation to the already placed block, retaining its block entity.
                        if (block instanceof com.gregtechceu.gtceu.api.block.MetaMachineBlock machine) {
                            var state = level.getBlockState(pos); var facing = machine.getRotationState().property;
                            if (state.hasProperty(facing) && proposed.getBlockState().hasProperty(facing))
                                level.setBlock(pos, state.setValue(facing, proposed.getBlockState().getValue(facing)), 3);
                        }
                        for (var drop : drops) if (!drop.isEmpty()) materials.add(AEItemKey.of(drop), drop.getCount());
                    }
                } finally {
                    if (!placed) snapshot.restore();
                }
            }
        } finally {
            materials.refund();
            refreshController(controller);
        }
    }

    /** The world-space positions use exactly the same offset and directions as BlockPattern.checkSlice. */
    public static Map<BlockPos, MultiPredicate> layout(BlockPattern block, BlockPos center, Direction front, Direction up,
                                                      boolean flipped, Int2IntMap repeats) {
        var origin = center.mutable(); block.getOffset().apply(origin, front, up, flipped);
        Direction sliceDir = block.getDirections()[0].getRelativeFacing(front, up, flipped);
        Direction stringDir = block.getDirections()[1].getRelativeFacing(front, up, flipped);
        Direction charDir = block.getDirections()[2].getRelativeFacing(front, up, flipped);
        var result = new LinkedHashMap<BlockPos, MultiPredicate>(); int offset = 0;
        for (int sliceIndex = 0; sliceIndex < block.getSlices().length; sliceIndex++) {
            var slice = block.getSlices()[sliceIndex];
            for (int repetition = 0; repetition < repeats.get(sliceIndex); repetition++, offset++) {
                for (int row = 0; row < block.getDimensions()[1]; row++) for (int column = 0; column < block.getDimensions()[2]; column++) {
                    var predicate = block.getPredicates().get(slice.charAt(row, column));
                    if (predicate.isAny() || predicate.isAir()) continue;
                    result.put(origin.relative(sliceDir, offset).relative(stringDir, row).relative(charDir, column), predicate);
                }
            }
        }
        return result;
    }
    private static boolean matches(Level level, BlockPos pos, MultiPredicate predicate) {
        var context = new PredicateContext(null); context.updateLevel(level); context.updatePos(pos);
        predicate.resetLogic(); return NativePredicateMatch.test(predicate, context);
    }
    private static int scoreSlice(Level level, BlockPattern block, BlockPos center, Direction front, Direction up,
                                  boolean flipped, int sliceIndex, int offset) {
        var origin = center.mutable(); block.getOffset().apply(origin, front, up, flipped);
        var directions = block.getDirections(); var slice = block.getSlices()[sliceIndex]; int score = 0;
        for (int row = 0; row < block.getDimensions()[1]; row++) for (int column = 0; column < block.getDimensions()[2]; column++) {
            var predicate = block.getPredicates().get(slice.charAt(row, column)); if (predicate.isAny() || predicate.isAir()) continue;
            var pos = origin.relative(directions[0].getRelativeFacing(front, up, flipped), offset)
                    .relative(directions[1].getRelativeFacing(front, up, flipped), row)
                    .relative(directions[2].getRelativeFacing(front, up, flipped), column);
            if (level.hasChunkAt(pos) && !level.getBlockState(pos).isAir() && matches(level, pos, predicate)) score++;
        }
        return score;
    }
    private static Int2IntMap partialRepetitions(Level level, BlockPattern block, BlockPos center, Direction front, Direction up, boolean flipped) {
        var repeats = new Int2IntOpenHashMap(); int offset = 0;
        for (int index = 0; index < block.getSlices().length; index++) {
            var slice = block.getSlices()[index]; int count = 0;
            if (slice.getMinRepeats() == slice.getMaxRepeats()) count = slice.getMinRepeats();
            else while (count < slice.getMaxRepeats()) {
                int here = scoreSlice(level, block, center, front, up, flipped, index, offset + count);
                int next = index + 1 < block.getSlices().length ? scoreSlice(level, block, center, front, up, flipped, index + 1, offset + count) : 0;
                if (count >= slice.getMinRepeats() && (next > here || here == 0)) break; count++;
            }
            repeats.put(index, count); offset += count;
        }
        return repeats;
    }
    private static long score(Level level, Map<BlockPos, MultiPredicate> layout) {
        return layout.entrySet().stream().filter(entry -> level.hasChunkAt(entry.getKey()) && !level.getBlockState(entry.getKey()).isAir() && matches(level, entry.getKey(), entry.getValue())).count();
    }

    public void dismantleMultiblock(MultiblockControllerMachine controller, Player player, AutoBuildSetting settings, ItemStack terminal) {
        if (!(player.level() instanceof ServerLevel level) || controller.getLevel() != level) return;
        var materials = new TerminalMaterials(player, terminal, findStorage(level, player, settings.getBoundAE(), settings.isAeMode()));
        var center = controller.getBlockPos(); var front = controller.getFrontFacing(); var up = controller.getUpwardsFacing();
        Map<BlockPos, MultiPredicate> positions;
        // A cached controller check does not restore repetition counts on another pattern instance.
        // Recheck this layout explicitly before reading native slice counts.
        var state = new PatternState(); state.setController(controller, center);
        boolean matched = pattern.checkPatternAt(level, state, center, front, up, false);
        boolean flippedMatch = false;
        if (!matched && controller.allowFlip()) { matched = pattern.checkPatternAt(level, state, center, front, up, true); flippedMatch = matched; }
        if (pattern instanceof BlockPattern block) {
            if (matched) {
                var repeats = new Int2IntOpenHashMap();
                for (int i = 0; i < block.getSlices().length; i++) repeats.put(i, block.getRepetitionCount(i));
                positions = layout(block, center, front, up, flippedMatch, repeats);
            } else {
                positions = layout(block, center, front, up, false, partialRepetitions(level, block, center, front, up, false));
                if (controller.allowFlip()) {
                    var flipped = layout(block, center, front, up, true, partialRepetitions(level, block, center, front, up, true));
                    if (score(level, flipped) > score(level, positions)) positions = flipped;
                }
            }
            // A shorter, complete repeat layout may still have a partially built next layer.
            // Keep that matching fragment in the dismantle selection instead of abandoning it.
            var partial = layout(block, center, front, up, false, partialRepetitions(level, block, center, front, up, false));
            if (score(level, partial) > score(level, positions)) positions = partial;
            if (controller.allowFlip()) {
                var partialFlipped = layout(block, center, front, up, true, partialRepetitions(level, block, center, front, up, true));
                if (score(level, partialFlipped) > score(level, positions)) positions = partialFlipped;
            }
        } else {
            positions = new LinkedHashMap<>(); var helper = helper(settings); var generated = new LinkedHashMap<BlockPos, BlockInfo>();
            helper.populate(generated, pattern, null, front, up, settings.isFlipped());
            var origin = center.mutable(); pattern.getOffset().apply(origin, front, up, settings.isFlipped());
            for (var pos : generated.keySet()) positions.put(pos.offset(origin), helper.getPredicateFromPos(pattern, pos, front, up, settings.isFlipped()));
        }
        try {
            for (var entry : positions.entrySet()) {
                var pos = entry.getKey();
                if (pos.equals(center) || !level.hasChunkAt(pos) || !level.mayInteract(player, pos)) continue;
                var current = level.getBlockState(pos);
                if (current.isAir() || settings.shouldPreserveDuringDismantle(current.getBlock()) || !matches(level, pos, entry.getValue())) continue;
                if (current.getBlock() instanceof LiquidBlock liquid) {
                    if (!current.getFluidState().isSource()) continue;
                    // Level.removeBlock deliberately leaves the previous fluid in place.
                    if (level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3)) materials.add(AEFluidKey.of(current.getFluidState().getType()), FluidType.BUCKET_VOLUME);
                    continue;
                }
                var drops = Block.getDrops(current, level, pos, level.getBlockEntity(pos), player, terminal);
                if (!level.removeBlock(pos, false)) continue;
                for (var drop : drops) if (!drop.isEmpty()) materials.add(AEItemKey.of(drop), drop.getCount());
            }
        } finally {
            materials.refund(); refreshController(controller);
        }
    }
    private static void refreshController(MultiblockControllerMachine controller) {
        controller.invalidateStructure();
        var state = controller.getDefaultPatternState(); state.getCache().clear();
        state.setState(PatternState.CheckState.UNINITIALIZED); state.setShouldUpdate(true);
        controller.checkAndFormStructure();
    }
    public static @Nullable IGridNode getGridNode(@Nullable BlockEntity entity) {
        if (entity instanceof AENetworkedBlockEntity network) return network.getMainNode().getNode();
        if (entity instanceof IGridConnectedMachine machine) return machine.getMainNode().getNode();
        if (entity instanceof IInWorldGridNodeHost host) {
            for (var direction : Direction.values()) { var node = host.getGridNode(direction); if (node != null) return node; }
        }
        return null;
    }
    private static @Nullable MEStorage findStorage(Level level, Player player, @Nullable GlobalPos binding, boolean enabled) {
        if (!enabled) return null;
        if (binding != null && binding.dimension().equals(level.dimension()) && level.hasChunkAt(binding.pos())) {
            var node = getGridNode(level.getBlockEntity(binding.pos())); if (node != null && node.isActive()) return node.getGrid().getStorageService().getInventory();
        }
        var grid = WirelessTerminalGridResolver.find(player, level);
        return grid == null ? null : grid.getStorageService().getInventory();
    }
}
