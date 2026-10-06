package org.gtlcore.gtlcore.api.pattern;

import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class DebugBlockPattern {

    public RelativeDirection[] structureDir;
    public String[][] pattern;
    public int[][] aisleRepetitions;
    public Map<Character, Set<String>> symbolMap;
    public Map<Block, Character> legend = new LinkedHashMap<>();

    public DebugBlockPattern() {
        symbolMap = new HashMap<>();
        structureDir = new RelativeDirection[] {
                RelativeDirection.BACK, RelativeDirection.UP, RelativeDirection.RIGHT
        };
    }

    public DebugBlockPattern(
                             Level world, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this();
        if (maxX < minX || maxY < minY || maxZ < minZ) throw new IllegalArgumentException("Invalid structure bounds");
        pattern = new String[Math.addExact(1, Math.subtractExact(maxX, minX))][Math.addExact(1, Math.subtractExact(maxY, minY))];
        boolean controllerFound = false;
        aisleRepetitions = new int[pattern.length][2];
        for (int[] aisleRepetition : aisleRepetitions) {
            aisleRepetition[0] = 1;
            aisleRepetition[1] = 1;
        }

        legend.put(Blocks.AIR, ' ');

        char c = 'A'; // '~' is reserved for the first selected controller.

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                StringBuilder builder = new StringBuilder();
                for (int z = minZ; z <= maxZ; z++) {
                    var pos = new BlockPos(x, y, z);
                    if (!world.hasChunkAt(pos)) throw new IllegalStateException("Structure selection contains an unloaded chunk at " + pos);
                    Block block = world.getBlockState(pos).getBlock();
                    if (!controllerFound && MetaMachine.getMachine(world, pos) instanceof MultiblockControllerMachine) {
                        builder.append('~');
                        symbolMap.computeIfAbsent('~', key -> new HashSet<>()).add("~");
                        controllerFound = true;
                        continue;
                    }
                    if (!legend.containsKey(block)) {
                        legend.put(block, c);
                        String name = String.valueOf(c);
                        symbolMap.computeIfAbsent(c, key -> new HashSet<>()).add(name); // any
                        do { c++; } while (c == '~' || c == '\\' || c == '\'' || c == '"' || Character.isISOControl(c));
                    }
                    builder.append(legend.get(block));
                }
                pattern[x - minX][y - minY] = builder.toString();
            }
        }
        var dirs = getDir(Direction.NORTH);
        changeDir(dirs[0], dirs[1], dirs[2]);
    }

    public static RelativeDirection[] getDir(Direction facing) {
        if (facing == Direction.WEST) {
            return new RelativeDirection[] {
                    RelativeDirection.LEFT, RelativeDirection.UP, RelativeDirection.BACK
            };
        } else if (facing == Direction.EAST) {
            return new RelativeDirection[] {
                    RelativeDirection.RIGHT, RelativeDirection.UP, RelativeDirection.FRONT
            };
        } else if (facing == Direction.NORTH) {
            return new RelativeDirection[] {
                    RelativeDirection.BACK, RelativeDirection.UP, RelativeDirection.RIGHT
            };
        } else if (facing == Direction.SOUTH) {
            return new RelativeDirection[] {
                    RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT
            };
        } else if (facing == Direction.DOWN) {
            return new RelativeDirection[] {
                    RelativeDirection.RIGHT, RelativeDirection.BACK, RelativeDirection.UP
            };
        } else {
            return new RelativeDirection[] {
                    RelativeDirection.LEFT, RelativeDirection.FRONT, RelativeDirection.UP
            };
        }
    }

    public void changeDir(RelativeDirection charDir, RelativeDirection stringDir,
                          RelativeDirection aisleDir) {
        RelativeDirection.validateFacingsArray(new RelativeDirection[] {charDir, stringDir, aisleDir});
        char[][][] newPattern = new char[sameAxis(structureDir[0], aisleDir) ? pattern[0][0].length() :
                sameAxis(structureDir[1], aisleDir) ? pattern[0].length :
                        pattern.length][sameAxis(structureDir[0], stringDir) ? pattern[0][0].length() :
                                sameAxis(structureDir[1], stringDir) ? pattern[0].length :
                                        pattern.length][sameAxis(structureDir[0], charDir) ? pattern[0][0].length() :
                                                sameAxis(structureDir[1], charDir) ? pattern[0].length :
                                                        pattern.length];
        for (int i = 0; i < pattern.length; i++) {
            for (int j = 0; j < pattern[0].length; j++) {
                for (int k = 0; k < pattern[0][0].length(); k++) {
                    char c = pattern[i][j].charAt(k);
                    int x = 0, y = 0, z = 0;
                    if (sameAxis(structureDir[2], aisleDir)) {
                        if (structureDir[2] == aisleDir) {
                            x = i;
                        } else {
                            x = pattern.length - i - 1;
                        }
                    } else if (sameAxis(structureDir[2], stringDir)) {
                        if (structureDir[2] == stringDir) {
                            y = i;
                        } else {
                            y = pattern.length - i - 1;
                        }
                    } else if (sameAxis(structureDir[2], charDir)) {
                        if (structureDir[2] == charDir) {
                            z = i;
                        } else {
                            z = pattern.length - i - 1;
                        }
                    }

                    if (sameAxis(structureDir[1], aisleDir)) {
                        if (structureDir[1] == aisleDir) {
                            x = j;
                        } else {
                            x = pattern[0].length - j - 1;
                        }
                    } else if (sameAxis(structureDir[1], stringDir)) {
                        if (structureDir[1] == stringDir) {
                            y = j;
                        } else {
                            y = pattern[0].length - j - 1;
                        }
                    } else if (sameAxis(structureDir[1], charDir)) {
                        if (structureDir[1] == charDir) {
                            z = j;
                        } else {
                            z = pattern[0].length - j - 1;
                        }
                    }

                    if (sameAxis(structureDir[0], aisleDir)) {
                        if (structureDir[0] == aisleDir) {
                            x = k;
                        } else {
                            x = pattern[0][0].length() - k - 1;
                        }
                    } else if (sameAxis(structureDir[0], stringDir)) {
                        if (structureDir[0] == stringDir) {
                            y = k;
                        } else {
                            y = pattern[0][0].length() - k - 1;
                        }
                    } else if (sameAxis(structureDir[0], charDir)) {
                        if (structureDir[0] == charDir) {
                            z = k;
                        } else {
                            z = pattern[0][0].length() - k - 1;
                        }
                    }
                    newPattern[x][y][z] = c;
                }
            }
        }

        pattern = new String[newPattern.length][newPattern[0].length];
        for (int i = 0; i < pattern.length; i++) {
            for (int j = 0; j < pattern[0].length; j++) {
                StringBuilder builder = new StringBuilder();
                for (char c : newPattern[i][j]) {
                    builder.append(c);
                }
                pattern[i][j] = builder.toString();
            }
        }

        aisleRepetitions = new int[pattern.length][2];
        for (int[] aisleRepetition : aisleRepetitions) {
            aisleRepetition[0] = 1;
            aisleRepetition[1] = 1;
        }

        structureDir = new RelativeDirection[] { charDir, stringDir, aisleDir };
    }

    private static boolean sameAxis(RelativeDirection a, RelativeDirection b) {
        return a.ordinal() / 2 == b.ordinal() / 2;
    }

    public boolean hasController() {
        for (var slice : pattern) for (var row : slice) if (row.indexOf('~') >= 0) return true;
        return false;
    }

    public DebugBlockPattern copy() {
        DebugBlockPattern newPattern = new DebugBlockPattern();
        System.arraycopy(this.structureDir, 0, newPattern.structureDir, 0, this.structureDir.length);

        newPattern.pattern = new String[pattern.length][pattern[0].length];
        for (int i = 0; i < pattern.length; i++) {
            System.arraycopy(pattern[i], 0, newPattern.pattern[i], 0, pattern[i].length);
        }

        newPattern.aisleRepetitions = new int[aisleRepetitions.length][2];
        for (int i = 0; i < aisleRepetitions.length; i++) {
            System.arraycopy(
                    aisleRepetitions[i], 0, newPattern.aisleRepetitions[i], 0, aisleRepetitions[i].length);
        }

        symbolMap.forEach((k, v) -> newPattern.symbolMap.put(k, new HashSet<>(v)));

        newPattern.legend.putAll(legend);
        return newPattern;
    }
}
