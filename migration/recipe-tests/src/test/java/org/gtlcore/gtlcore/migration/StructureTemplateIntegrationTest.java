package org.gtlcore.gtlcore.migration;

import com.gregtechceu.gtceu.api.multiblock.Predicates;
import com.gregtechceu.gtceu.api.multiblock.pattern.BlockPattern;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;

import net.minecraft.world.level.block.Blocks;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

/** Compiles and builds the actual production templates against GTCEu 8. */
class StructureTemplateIntegrationTest {
    private static JsonArray baseline() {
        try (var reader = new InputStreamReader(Objects.requireNonNull(
                StructureTemplateIntegrationTest.class.getResourceAsStream("/structure-templates.json")),
                StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject().getAsJsonArray("templates");
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Supplier<MultiblockPatternBuilder> template(JsonObject baseline) throws ReflectiveOperationException {
        return (Supplier<MultiblockPatternBuilder>) Class.forName(baseline.get("class").getAsString())
                .getField(baseline.get("field").getAsString()).get(null);
    }

    private static BlockPattern pattern(MultiblockPatternBuilder builder, String symbols) {
        for (char symbol : symbols.toCharArray()) builder.where(symbol, Predicates.blocks(Blocks.STONE));
        return (BlockPattern) builder.build();
    }

    @Test
    void everyTemplateRetainsItsBaselineShapeRepeatBoundsAndDirections() throws Exception {
        var gson = new GsonBuilder().disableHtmlEscaping().create();
        assertEquals(159, baseline().size());
        for (var element : baseline()) {
            var expected = element.getAsJsonObject();
            String id = expected.get("class").getAsString() + "." + expected.get("field").getAsString();
            var actual = pattern(template(expected).get(), expected.get("symbols").getAsString());
            assertArrayEquals(gson.fromJson(expected.get("dimensions"), int[].class), actual.getDimensions(), id);
            assertArrayEquals(gson.fromJson(expected.get("directions"), String[].class),
                    Arrays.stream(actual.getDirections()).map(Enum::name).toArray(String[]::new), id);
            var expectedRepeats = expected.getAsJsonArray("repeats");
            for (int i = 0; i < actual.getSlices().length; i++) {
                assertEquals(expectedRepeats.get(i).getAsJsonArray().get(0).getAsInt(),
                        actual.getSlices()[i].getMinRepeats(), id + " slice " + i);
                assertEquals(expectedRepeats.get(i).getAsJsonArray().get(1).getAsInt(),
                        actual.getSlices()[i].getMaxRepeats(), id + " slice " + i);
            }
            String[][] rows = Arrays.stream(actual.getSlices())
                    .map(slice -> Arrays.stream(slice.getPattern()).map(String::new).toArray(String[]::new))
                    .toArray(String[][]::new);
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(gson.toJson(rows).getBytes(StandardCharsets.UTF_8)));
            assertEquals(expected.get("shape_sha256").getAsString(), hash, id);
            if (expected.has("origin_offset")) {
                int[] offset = gson.fromJson(expected.get("origin_offset"), int[].class);
                var actualOffset = actual.getOffset().toBlockPos();
                assertArrayEquals(offset, new int[]{actualOffset.getX(), actualOffset.getY(), actualOffset.getZ()}, id);
            }
        }
    }

    @Test
    void eachConsumerGetsItsOwnPredicatesSlicesAndBuilder() throws Exception {
        for (var entry : baseline()) {
            assertIndependentBuilder(entry.getAsJsonObject());
        }
    }

    private static void assertIndependentBuilder(JsonObject expected) throws Exception {
        var factory = template(expected);
        var firstBuilder = factory.get();
        var secondBuilder = factory.get();
        assertNotSame(firstBuilder, secondBuilder);
        String symbols = expected.get("symbols").getAsString();
        char symbol = symbols.charAt(symbols.length() - 1);
        var first = pattern(firstBuilder, symbols);
        for (char c : symbols.toCharArray()) secondBuilder.where(c, Predicates.blocks(Blocks.GOLD_BLOCK));
        var second = (BlockPattern) secondBuilder.build();
        assertNotSame(first.getPredicates(), second.getPredicates());
        assertNotSame(first.getSlices()[0], second.getSlices()[0]);
        assertEquals(Blocks.STONE, first.getPredicates().get(symbol).getCandidates().getFirst().getFirst()
                .getBlockState().getBlock());
        assertEquals(Blocks.GOLD_BLOCK, second.getPredicates().get(symbol).getCandidates().getFirst().getFirst()
                .getBlockState().getBlock());
        second.getSlices()[0].setActualRepeats(100);
        assertNotEquals(100, first.getSlices()[0].getActualRepeats());
    }
}
