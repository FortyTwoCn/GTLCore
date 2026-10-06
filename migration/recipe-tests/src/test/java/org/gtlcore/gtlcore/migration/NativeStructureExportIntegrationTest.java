package org.gtlcore.gtlcore.migration;

import org.gtlcore.gtlcore.api.pattern.DebugBlockPattern;
import org.gtlcore.gtlcore.common.item.StructureWriteBehavior;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class NativeStructureExportIntegrationTest {
    @TempDir Path directory;
    @Test void everyRotatedExportCompilesAgainstNativeGtIncludingEscapedSymbolsAndController() throws Exception {
        var captured = new DebugBlockPattern();
        captured.pattern = new String[][]{{"A\\\"", "A'A"}, {"A'A", "AA~"}};
        captured.aisleRepetitions = new int[][]{{1, 1}, {1, 1}};
        captured.legend.put(Blocks.STONE, 'A'); captured.legend.put(Blocks.GOLD_BLOCK, '\\');
        captured.legend.put(Blocks.IRON_BLOCK, '\''); captured.legend.put(Blocks.DIAMOND_BLOCK, '"');
        var sources = new ArrayList<Path>();
        for (var facing : Direction.values()) {
            var rotated = captured.copy(); var dirs = DebugBlockPattern.getDir(facing);
            rotated.changeDir(dirs[0], dirs[1], dirs[2]);
            assertEquals(captured.legend, rotated.legend, "Copy must retain exported block IDs");
            var export = StructureWriteBehavior.exportPattern(rotated);
            assertTrue(export.contains(".where('~', Predicates.controller(definition))"));
            var expression = export.substring(".pattern(definition -> ".length()).stripTrailing();
            expression = expression.substring(0, expression.length() - 1);
            var source = directory.resolve("Export" + facing.name() + ".java");
            Files.writeString(source, """
                    import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
                    import com.gregtechceu.gtceu.api.multiblock.*;
                    import com.gregtechceu.gtceu.api.multiblock.pattern.*;
                    import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
                    import net.minecraft.core.registries.BuiltInRegistries;
                    import net.minecraft.resources.ResourceLocation;
                    class Export%s {
                        static IBlockPattern build(MultiblockMachineDefinition definition) {
                            return %s;
                        }
                    }
                    """.formatted(facing.name(), expression));
            sources.add(source);
        }
        var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Native exporter verification requires the configured JDK");
        var diagnostics = new java.io.StringWriter();
        try (var manager = compiler.getStandardFileManager(null, null, null)) {
            assertTrue(compiler.getTask(diagnostics, manager, null,
                    List.of("-proc:none", "-classpath", System.getProperty("migration.nativeCompileClasspath"), "-d", directory.toString()),
                    null, manager.getJavaFileObjectsFromPaths(sources)).call(), diagnostics.toString());
        }
    }
}
