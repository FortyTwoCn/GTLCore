package org.gtlcore.gtlcore.utils.datastructure;

import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import org.jetbrains.annotations.NotNull;

/**
 * 模块渲染信息：记录在某个参考状态下，主机和子机的朝向关系及位置偏移
 * <ol>
 * <li>记录一个"参考状态"：主机朝向 (hostFront, hostUp) 和子机朝向 (moduleFront, moduleUp)</li>
 * <li>使用**世界坐标偏移** worldOffset，而不是相对坐标系</li>
 * <li>提供工具方法：给定新的主机朝向，计算子机的新朝向和新位置</li>
 * <li>使用GT的坐标变换方法来处理结构方块的位置</li>
 * </ol>
 *
 * @param worldOffset      子机控制器相对于主机控制器的世界坐标偏移（在参考状态下）
 * @param hostFront        参考状态下主机的前朝向
 * @param hostUp           参考状态下主机的上朝向
 * @param moduleFront      参考状态下子机的前朝向
 * @param moduleUp         参考状态下子机的上朝向
 * @param moduleDefinition 子机多方块定义
 */
@SuppressWarnings({ "DuplicateBranchesInSwitch", "IfStatementWithIdenticalBranches", "unused" })
@OnlyIn(Dist.CLIENT)
public record ModuleRenderInfo(
                               @NotNull BlockPos worldOffset,
                               @NotNull Direction hostFront,
                               @NotNull Direction hostUp,
                               @NotNull Direction moduleFront,
                               @NotNull Direction moduleUp,
                               @NotNull MultiblockMachineDefinition moduleDefinition) {

    public ModuleRenderInfo(@NotNull BlockPos worldOffset,
                            @NotNull Direction moduleFront,
                            @NotNull Direction moduleUp,
                            @NotNull MultiblockMachineDefinition moduleDefinition) {
        this(worldOffset, Direction.NORTH, Direction.UP, moduleFront, moduleUp, moduleDefinition);
    }

    public ModuleRenderInfo(@NotNull BlockPos worldOffset,
                            @NotNull Direction moduleFront,
                            @NotNull MultiblockMachineDefinition moduleDefinition) {
        this(worldOffset, Direction.NORTH, Direction.UP, moduleFront, Direction.UP, moduleDefinition);
    }

    /**
     * @param newHostFront 新的主机前朝向
     * @param newHostUp    新的主机上朝向
     * @return 子机相对于主机的新世界坐标偏移
     */
    public BlockPos calculateModuleOffset(@NotNull Direction newHostFront, @NotNull Direction newHostUp) {
        if (newHostFront == hostFront && newHostUp == hostUp) {
            return worldOffset;
        }

        BlockPos offset = reverseHorizontalRotation(worldOffset, hostFront);
        Rotation hostUpRotation = calculateUpRotation(hostFront, hostUp);
        offset = reverseRotateByFrontAxis(offset, hostFront, hostUpRotation);

        offset = applyHorizontalRotation(offset, newHostFront);
        Rotation newUpRotation = calculateUpRotation(newHostFront, newHostUp);
        offset = rotateByFrontAxis(offset, newHostFront, newUpRotation);

        return offset;
    }

    /**
     * @return 新主机朝向下，子机的前朝向
     */
    public Direction calculateModuleFront(@NotNull Direction newHostFront, @NotNull Direction newHostUp) {
        return convertDirection(newHostFront, newHostUp, moduleFront);
    }

    /**
     * @return 新主机朝向下，子机的上朝向
     */
    public Direction calculateModuleUp(@NotNull Direction newHostFront, @NotNull Direction newHostUp) {
        return convertDirection(newHostFront, newHostUp, moduleUp);
    }

    private Direction convertDirection(@NotNull Direction newHostFront, @NotNull Direction newHostUp, Direction moduleFront) {
        if (newHostFront == hostFront && newHostUp == hostUp) {
            return moduleFront;
        }

        BlockPos frontVec = new BlockPos(moduleFront.getStepX(), moduleFront.getStepY(), moduleFront.getStepZ());
        frontVec = reverseHorizontalRotation(frontVec, hostFront);

        Rotation hostUpRotation = calculateUpRotation(hostFront, hostUp);
        frontVec = reverseRotateByFrontAxis(frontVec, hostFront, hostUpRotation);

        frontVec = applyHorizontalRotation(frontVec, newHostFront);

        Rotation newUpRotation = calculateUpRotation(newHostFront, newHostUp);
        frontVec = rotateByFrontAxis(frontVec, newHostFront, newUpRotation);

        return vectorToDirection(frontVec);
    }

    // ============================================
    // Inner Utils
    // ============================================

    private static BlockPos applyHorizontalRotation(BlockPos offset, Direction front) {
        return switch (front) {
            case NORTH, UP, DOWN -> offset.rotate(Rotation.NONE);
            case SOUTH -> offset.rotate(Rotation.CLOCKWISE_180);
            case EAST -> offset.rotate(Rotation.COUNTERCLOCKWISE_90);
            case WEST -> offset.rotate(Rotation.CLOCKWISE_90);
        };
    }

    private static BlockPos reverseHorizontalRotation(BlockPos offset, Direction front) {
        return switch (front) {
            case NORTH, UP, DOWN -> offset.rotate(Rotation.NONE);
            case SOUTH -> offset.rotate(Rotation.CLOCKWISE_180);
            case EAST -> offset.rotate(Rotation.CLOCKWISE_90);
            case WEST -> offset.rotate(Rotation.COUNTERCLOCKWISE_90);
        };
    }

    private static Rotation calculateUpRotation(Direction front, Direction up) {
        if (front.getAxis() == Direction.Axis.Y) {
            return switch (up) {
                case NORTH -> Rotation.NONE;
                case EAST -> Rotation.CLOCKWISE_90;
                case SOUTH -> Rotation.CLOCKWISE_180;
                case WEST -> Rotation.COUNTERCLOCKWISE_90;
                default -> Rotation.NONE;
            };
        }
        return Rotation.NONE;
    }

    private static BlockPos rotateByFrontAxis(BlockPos pos, Direction front, Rotation rotation) {
        if (front.getAxis() == Direction.Axis.X) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(-pos.getX(), -front.getAxisDirection().getStep() * pos.getZ(),
                        front.getAxisDirection().getStep() * -pos.getY());
                case CLOCKWISE_180 -> new BlockPos(-pos.getX(), -pos.getY(), pos.getZ());
                case COUNTERCLOCKWISE_90 -> new BlockPos(-pos.getX(), front.getAxisDirection().getStep() * pos.getZ(),
                        front.getAxisDirection().getStep() * pos.getY());
                default -> new BlockPos(-pos.getX(), pos.getY(), -pos.getZ());
            };
        } else if (front.getAxis() == Direction.Axis.Y) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(pos.getY(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        -front.getAxisDirection().getStep() * pos.getX());
                case CLOCKWISE_180 -> new BlockPos(front.getAxisDirection().getStep() * pos.getX(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        pos.getY());
                case COUNTERCLOCKWISE_90 -> new BlockPos(-pos.getY(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        front.getAxisDirection().getStep() * pos.getX());
                default -> new BlockPos(-front.getAxisDirection().getStep() * pos.getX(),
                        -front.getAxisDirection().getStep() * pos.getZ(),
                        -pos.getY());
            };
        } else if (front.getAxis() == Direction.Axis.Z) {
            return switch (rotation) {
                case CLOCKWISE_90 -> new BlockPos(front.getAxisDirection().getStep() * pos.getY(),
                        -front.getAxisDirection().getStep() * pos.getX(), pos.getZ());
                case CLOCKWISE_180 -> new BlockPos(-pos.getX(), -pos.getY(), pos.getZ());
                case COUNTERCLOCKWISE_90 -> new BlockPos(-front.getAxisDirection().getStep() * pos.getY(),
                        front.getAxisDirection().getStep() * pos.getX(), pos.getZ());
                default -> pos;
            };
        }
        return pos;
    }

    private static BlockPos reverseRotateByFrontAxis(BlockPos pos, Direction front, Rotation rotation) {
        Rotation reverseRotation = switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            case CLOCKWISE_180 -> Rotation.CLOCKWISE_180;
            default -> Rotation.NONE;
        };
        return rotateByFrontAxis(pos, front, reverseRotation);
    }

    private static Direction vectorToDirection(BlockPos vec) {
        if (vec.getX() > 0) return Direction.EAST;
        if (vec.getX() < 0) return Direction.WEST;
        if (vec.getY() > 0) return Direction.UP;
        if (vec.getY() < 0) return Direction.DOWN;
        if (vec.getZ() > 0) return Direction.SOUTH;
        if (vec.getZ() < 0) return Direction.NORTH;
        return Direction.NORTH;
    }

    // ============================================
    // Part Rotation
    // ============================================

    /**
     * @param originalFace   方块在ShapeInfo中的原始朝向（相对于NORTH）
     * @param newModuleFront 子机控制器的新朝向
     * @return 方块的新朝向
     */
    public static Direction calculateBlockFacing(Direction originalFace, Direction newModuleFront) {
        if (originalFace.getAxis() == Direction.Axis.Y) {
            return originalFace;
        }

        return applyBlockRotation(originalFace, newModuleFront);
    }

    private static Direction applyBlockRotation(Direction face, Direction targetFront) {
        if (targetFront == Direction.NORTH || targetFront.getAxis() == Direction.Axis.Y) {
            return face;
        }

        return BLOCK_ROTATION_MAP[targetFront.ordinal()][face.ordinal()];
    }

    public static BlockPos applyGTTransform(BlockPos localOffset, Direction moduleFront, Direction moduleUp) {
        BlockPos offsetRotated = switch (moduleFront) {
            case NORTH, UP, DOWN -> localOffset.rotate(Rotation.NONE);
            case SOUTH -> localOffset.rotate(Rotation.CLOCKWISE_180);
            case EAST -> localOffset.rotate(Rotation.COUNTERCLOCKWISE_90);
            case WEST -> localOffset.rotate(Rotation.CLOCKWISE_90);
        };

        Rotation rotation = switch (moduleUp) {
            case NORTH -> Rotation.NONE;
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return rotateByFrontAxis(offsetRotated, moduleFront, rotation);
    }

    private static final Direction[][] BLOCK_ROTATION_MAP = buildBlockRotationMap();

    private static Direction[][] buildBlockRotationMap() {
        Direction[][] map = new Direction[6][6];

        for (Direction targetFront : Direction.values()) {
            for (Direction originalFace : Direction.values()) {
                if (originalFace.getAxis() == Direction.Axis.Y) {
                    map[targetFront.ordinal()][originalFace.ordinal()] = originalFace;
                } else if (targetFront.getAxis() == Direction.Axis.Y || targetFront == Direction.NORTH) {
                    map[targetFront.ordinal()][originalFace.ordinal()] = originalFace;
                } else {
                    Direction newFace = switch (targetFront) {
                        case SOUTH -> originalFace.getOpposite();
                        case EAST -> originalFace.getCounterClockWise();
                        case WEST -> originalFace.getClockWise();
                        default -> originalFace;
                    };
                    map[targetFront.ordinal()][originalFace.ordinal()] = newFace;
                }
            }
        }

        return map;
    }
}
