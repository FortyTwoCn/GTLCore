package org.gtlcore.gtlcore.api.gui.preview;

import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.block.property.GTBlockStateProperties;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.multiblock.MultiPredicate;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.ExpandablePattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.IBlockPattern;
import com.gregtechceu.gtceu.api.multiblock.util.AbstractStructureHelper;
import com.gregtechceu.gtceu.api.multiblock.util.BlockInfo;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MachineEntry;
import org.gtlcore.gtlcore.api.pattern.NativePatternPredicateLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.*;

/** A sparse visual snapshot of a native pattern. It never owns a live block entity. */
public final class NativePreviewGeometry {
    private final Map<BlockPos, BlockState> states;
    private final Map<BlockPos, MultiPredicate> predicates;
    private final BlockPos controllerPos;

    private NativePreviewGeometry(Map<BlockPos, BlockState> states, Map<BlockPos, MultiPredicate> predicates,
                                  BlockPos controllerPos) {
        this.states = Collections.unmodifiableMap(new LinkedHashMap<>(states));
        this.predicates = Collections.unmodifiableMap(new LinkedHashMap<>(predicates));
        this.controllerPos = controllerPos.immutable();
    }

    public Map<BlockPos, BlockState> states() { return states; }
    public Map<BlockPos, MultiPredicate> predicates() { return predicates; }
    public BlockPos controllerPos() { return controllerPos; }

    public static NativePreviewGeometry fromStates(Map<BlockPos, BlockState> states, BlockPos controllerPos) {
        var occupied = new LinkedHashMap<BlockPos, BlockState>();
        states.forEach((pos, state) -> { if (!state.isAir()) occupied.put(pos.immutable(), state); });
        return new NativePreviewGeometry(occupied, Map.of(), controllerPos);
    }

    /** Reorients the complete sparse visual around its controller using the native GT orientation basis. */
    public NativePreviewGeometry oriented(Direction front, Direction up, boolean flipped) {
        if (front.getAxis() == up.getAxis()) throw new IllegalArgumentException("Preview front and upwards axes coincide");
        var host = states.get(controllerPos);
        Direction sourceFront = Direction.NORTH, sourceUp = Direction.UP;
        if (host != null && host.getBlock() instanceof MetaMachineBlock machine) {
            var property = machine.getRotationState().property;
            if (property != null && host.hasProperty(property)) sourceFront = host.getValue(property);
            if (host.hasProperty(GTBlockStateProperties.UPWARDS_FACING)) sourceUp = host.getValue(GTBlockStateProperties.UPWARDS_FACING);
        }
        if (sourceFront.getAxis() == sourceUp.getAxis())
            sourceUp = sourceFront.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        var directions = new EnumMap<Direction, Direction>(Direction.class);
        for (var relative : RelativeDirection.values())
            directions.put(relative.getRelativeFacing(sourceFront, sourceUp), relative.getRelativeFacing(front, up, flipped));
        var transformed = new LinkedHashMap<BlockPos, BlockState>();
        var transformedPredicates = new LinkedHashMap<BlockPos, MultiPredicate>();
        for (var entry : states.entrySet()) {
            var delta = entry.getKey().subtract(controllerPos);
            var pos = BlockPos.ZERO.relative(directions.get(Direction.EAST), delta.getX())
                    .relative(directions.get(Direction.UP), delta.getY())
                    .relative(directions.get(Direction.SOUTH), delta.getZ());
            var state = entry.getValue();
            for (var property : state.getProperties()) state = orientProperty(state, property, directions);
            if (entry.getKey().equals(controllerPos) && state.hasProperty(GTBlockStateProperties.UPWARDS_FACING))
                state = state.setValue(GTBlockStateProperties.UPWARDS_FACING, up);
            transformed.put(pos, state);
            var predicate = predicates.get(entry.getKey());
            if (predicate != null) transformedPredicates.put(pos, predicate);
        }
        return new NativePreviewGeometry(transformed, transformedPredicates, BlockPos.ZERO);
    }

    @SuppressWarnings("unchecked")
    private static BlockState orientProperty(BlockState state, Property<?> property, Map<Direction, Direction> directions) {
        var value = state.getValue(property);
        if (value instanceof Direction direction) {
            var oriented = directions.get(direction);
            if (property.getPossibleValues().contains(oriented)) return state.setValue((Property<Direction>) property, oriented);
        } else if (value instanceof Direction.Axis axis) {
            var oriented = directions.get(Direction.get(Direction.AxisDirection.POSITIVE, axis)).getAxis();
            if (property.getPossibleValues().contains(oriented)) return state.setValue((Property<Direction.Axis>) property, oriented);
        }
        return state;
    }

    private NativePreviewGeometry withNativePredicates(MultiblockMachineDefinition definition) {
        var host = states.get(controllerPos);
        var rotation = definition.getRotationState();
        var front = rotation.property != null && host.hasProperty(rotation.property) ? host.getValue(rotation.property) : Direction.NORTH;
        var up = host.hasProperty(GTBlockStateProperties.UPWARDS_FACING) ? host.getValue(GTBlockStateProperties.UPWARDS_FACING) : Direction.UP;
        if (front.getAxis() == up.getAxis()) up = front.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        var pattern = definition.getStructurePatterns().get("main").get();
        var repeats = new Int2IntOpenHashMap();
        var dimensions = new ArrayList<Integer>();
        if (pattern instanceof BlockPattern blockPattern) {
            int minimum = 0, maximum = 0;
            var slices = blockPattern.getSlices();
            for (int i = 0; i < slices.length; i++) {
                repeats.put(i, slices[i].getMinRepeats()); minimum += slices[i].getMinRepeats(); maximum += slices[i].getMaxRepeats();
            }
            var sliceDirection = blockPattern.getDirections()[0].getRelativeFacing(front, up);
            int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
            for (var pos : states.keySet()) {
                int value = sliceDirection.getAxis().choose(pos.getX(), pos.getY(), pos.getZ());
                min = Math.min(min, value); max = Math.max(max, value);
            }
            int target = max - min + 1;
            if (target >= minimum && target <= maximum) {
                int extra = target - minimum;
                for (int i = 0; i < slices.length && extra > 0; i++) {
                    int added = Math.min(extra, slices[i].getMaxRepeats() - slices[i].getMinRepeats());
                    repeats.put(i, slices[i].getMinRepeats() + added); extra -= added;
                }
            }
        } else if (pattern instanceof ExpandablePattern expandable) {
            var bounds = expandable.getBoundsConstraints().apply();
            for (int i = 0; i < bounds.size(); i++) {
                var direction = RelativeDirection.VALUES[i].getRelativeFacing(front, up);
                int extent = 0;
                for (var pos : states.keySet()) {
                    var delta = pos.subtract(controllerPos);
                    extent = Math.max(extent, delta.getX() * direction.getStepX() + delta.getY() * direction.getStepY() + delta.getZ() * direction.getStepZ());
                }
                var bound = bounds.get(i); dimensions.add(Math.clamp(extent, bound.leftInt(), bound.rightInt()));
            }
        }
        var nativeShape = fromPattern(definition, pattern, repeats, dimensions, front, up, false);
        var mapped = new LinkedHashMap<BlockPos, MultiPredicate>();
        for (var pos : states.keySet()) {
            var predicate = nativeShape.predicates().get(pos.subtract(controllerPos).offset(nativeShape.controllerPos()));
            if (predicate != null) mapped.put(pos, predicate);
        }
        return new NativePreviewGeometry(states, mapped, controllerPos);
    }

    public static NativePreviewGeometry fromPattern(MultiblockMachineDefinition definition, IBlockPattern pattern,
                                                    Int2IntMap repeats, List<Integer> dimensions,
                                                    Direction front, Direction up, boolean flipped) {
        AbstractStructureHelper helper;
        if (pattern instanceof BlockPattern blockPattern) {
            var actual = new Int2IntOpenHashMap();
            for (int i = 0; i < blockPattern.getSlices().length; i++) {
                var slice = blockPattern.getSlices()[i];
                int count = repeats.containsKey(i) ? repeats.get(i) : slice.getMinRepeats();
                if (count < slice.getMinRepeats() || count > slice.getMaxRepeats()) {
                    throw new IllegalArgumentException("Preview repetition outside native slice bounds: " + i);
                }
                actual.put(i, count);
            }
            helper = AbstractStructureHelper.blockPattern(actual);
        } else if (pattern instanceof ExpandablePattern expandable) {
            var bounds = expandable.getBoundsConstraints().apply();
            if (!dimensions.isEmpty() && dimensions.size() != bounds.size()) {
                throw new IllegalArgumentException("Preview dimension count differs from native pattern");
            }
            var actual = new IntArrayList();
            for (int i = 0; i < bounds.size(); i++) {
                var bound = bounds.get(i);
                int value = dimensions.isEmpty() ? bound.leftInt() : dimensions.get(i);
                if (value < bound.leftInt() || value > bound.rightInt()) {
                    throw new IllegalArgumentException("Preview dimension outside native bounds: " + i);
                }
                actual.add(value);
            }
            helper = AbstractStructureHelper.expandable(actual);
        } else {
            throw new IllegalArgumentException("Unsupported native preview pattern: " + pattern.getClass().getName());
        }
        var populated = new NativePreviewBlockMap();
        helper.populate(populated, pattern, null, front, up, flipped);
        java.util.function.Function<BlockPos, MultiPredicate> predicateAt = pattern instanceof BlockPattern block
                ? NativePatternPredicateLookup.blockPattern(block, repeats, front, up, flipped)
                : pos -> helper.getPredicateFromPos(pattern, pos, front, up, flipped);
        var states = new LinkedHashMap<BlockPos, BlockState>();
        var predicates = new LinkedHashMap<BlockPos, MultiPredicate>();
        BlockPos controller = null;
        for (var entry : populated.entrySet()) {
            var pos = entry.getKey().immutable(); var state = entry.getValue().getBlockState();
            if (state.isAir()) continue;
            states.put(pos, state);
            var predicate = predicateAt.apply(pos);
            if (predicate != null) predicates.put(pos, predicate);
            if (state.is(definition.getBlock())) {
                if (controller != null) throw new IllegalArgumentException("Preview has more than one host controller");
                controller = pos;
            }
        }
        if (controller == null) throw new IllegalArgumentException("Native preview has no controller: " + definition.getId());
        return new NativePreviewGeometry(states, predicates, controller);
    }

    public static VisualBuilder visualBuilder() { return new VisualBuilder(); }

    /** Preserves explicit visual variants (hatch facing, transparent shells) independently of matching. */
    public static final class VisualBuilder {
        private final List<String[]> slices = new ArrayList<>();
        private final Map<Character, BlockState> palette = new HashMap<>();
        public VisualBuilder aisle(String... rows) { slices.add(rows.clone()); return this; }
        public VisualBuilder where(char key, Block block) { return where(key, block.defaultBlockState()); }
        public VisualBuilder where(char key, BlockState state) { palette.put(key, state); return this; }
        public VisualBuilder where(char key, MachineEntry<?> entry, Direction facing) { return where(key, entry.get(), facing); }
        public VisualBuilder where(char key, MachineDefinition definition, Direction facing) {
            var state = definition.getBlock().defaultBlockState(); var rotation = definition.getRotationState();
            if (rotation.property != null && state.hasProperty(rotation.property)) {
                if (!rotation.test(facing)) throw new IllegalArgumentException("Invalid visual hatch facing: " + definition.getId());
                state = state.setValue(rotation.property, facing);
            }
            if (state.hasProperty(GTBlockStateProperties.UPWARDS_FACING)) state = state.setValue(GTBlockStateProperties.UPWARDS_FACING,
                    facing.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP);
            return where(key, state);
        }
        public VisualBuilder shallowCopy() {
            var copy = new VisualBuilder(); slices.forEach(rows -> copy.slices.add(rows.clone())); copy.palette.putAll(palette); return copy;
        }
        public NativePreviewGeometry build(MultiblockMachineDefinition definition) {
            return build(definition, false);
        }
        /** Explicit preview aisles whose horizontal layout follows native pattern slices instead of visual X planes. */
        public NativePreviewGeometry buildWithTransposedHorizontalAxes(MultiblockMachineDefinition definition) {
            return build(definition, true);
        }
        private NativePreviewGeometry build(MultiblockMachineDefinition definition, boolean transposeHorizontal) {
            var states = new LinkedHashMap<BlockPos, BlockState>(); BlockPos controller = null;
            for (int x = 0; x < slices.size(); x++) {
                var rows = slices.get(x);
                for (int y = 0; y < rows.length; y++) for (int z = 0; z < rows[y].length(); z++) {
                    char key = rows[y].charAt(z); var state = palette.get(key);
                    if (state == null && key == ' ') state = Blocks.AIR.defaultBlockState();
                    if (state == null) throw new IllegalArgumentException("Unmapped preview key: " + key);
                    if (state.isAir()) continue;
                    var pos = transposeHorizontal ? new BlockPos(z, y, x) : new BlockPos(x, y, z);
                    states.put(pos, state);
                    if (state.getBlock() instanceof MetaMachineBlock machine && machine.getDefinition() == definition) {
                        if (controller != null) throw new IllegalArgumentException("Visual preview has more than one host controller");
                        controller = pos;
                    }
                }
            }
            if (controller == null) throw new IllegalArgumentException("Visual preview has no host controller: " + definition.getId());
            return new NativePreviewGeometry(states, Map.of(), controller).withNativePredicates(definition);
        }
    }
}
