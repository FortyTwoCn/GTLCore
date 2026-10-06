package org.gtlcore.gtlcore.common.data.machines.structure;

import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;

import java.util.function.Supplier;

public class MultiBlockMachineBStructure {

    public static final Supplier<MultiblockPatternBuilder> LARGE_FRAGMENT_WORLD_COLLECTION_MACHINE = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("AAA", "AXA", "XXX", "XXX", "XXX", "AXA", "AAA")
            .slice("AOA", "XXX", "XXX", "XXX", "XXX", "XXX", "AIA")
            .slice("AAA", "AXA", "XXX", "XSX", "XXX", "AXA", "AAA");

    public static final Supplier<MultiblockPatternBuilder> PRIMITIVE_VOID_ORE = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXX", "XXX", "XXX")
            .slice("XXX", "XAX", "XXX")
            .slice("XXX", "XSX", "XXX");

    public static final Supplier<MultiblockPatternBuilder> DESULFURIZER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("CCCCCIIIIIII", "CCCCCILILILI", "CCCCCILILILI", "            ", "            ", "            ")
            .slice("CCCCCIIIIIII", "CGCCCXXXXXXI", "CCCCCILILILI", "  CCCILILILI", "  CCCIIIIIII", "            ")
            .slice("CCCCCIIIIIII", "CGGGGILILILI", "CCCCCILILILI", "  CXXXXXXXXI", "  CCCIIIIIII", "            ")
            .slice("CCDDDIIIIIII", "CGDPDXXXXXXI", "CCDDDILILILI", "  DDDILILILI", "  DDDIIIIIII", "  DDD       ")
            .slice("CCCCCIIIIIII", "CCDPDILILILI", "CCDPDILILILI", "  DPD       ", "  DPD       ", "  DDD       ")
            .slice("  DDD       ", "  D~D       ", "  DDD       ", "  DDD       ", "  DDD       ", "  DDD       ");

    public static final Supplier<MultiblockPatternBuilder> WOOD_DISTILLATION = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("ABBBA IIIII IIIII ABBBA", "AAAAA IIIII IIIII AAAAA", "CAAAC CIIIC CIIIC CAAAC", "C   C C   C C   C CCCCC", "CCCCC C   C C   C CAAAC", "CAAAC C   C CCCCC C   C", "C   C CCCCC CJJJC CAAAC", "CAAAC CJJJC CJJJC C   C", "C   C CJJJC CJJJC CAAAC", "CAAAC CJJJC CBBBC C   C", "C   C CBBBC C   C CBBBC", "CAAAC CCCCC CCCCC CDDDC", "C   C C   C CDDDC CDDDC", "CBBBC CCCCC CDDDC CDDDC", "CDDDC CDDDC CDDDC CCCCC", "CDDDC CDDDC CCCCC      ", "CCCCC CDDDC            ", "      CDDDC            ", "      CCCCC            ", "                       ")
            .slice("BBBBB IKKKI IKKKI BBBBB", "AAAAA IKKKI IKKKI AAAAA", "ADDDA IDJDI IDJDI ADDDA", " DDD   DJD   DJD  CDDDC", "CDDDC  DJD   DJDC A   A", "A   A  DJD  CDJDC  EEE ", " EEE  CDJDC J   J A   A", "A   A J   J J   J  EEE ", " EEE  J   J J   J A   A", "A   A J   J B   B  EEE ", " EEE  B   B  DDD  B   B", "A   A CDDDC CDDDC D   D", " EEE   DDD  D   D D   D", "B   B CEEEC D   D D   D", "D   D D   D D   D CDDDC", "D   D D   D CDDDC  DDD ", "CDDDC D   D  DDD       ", " DDD  D   D            ", "      CDDDC            ", "       DDD             ")
            .slice("BBBBB IKKKI IKKKI BBBBB", "AAGAA IKGKI IKGKI AAGAA", "ADGDA IJGJI IJGJI ADGDA", " DGD   JGJ   JGJ  CDGDC", "CDGDC  JGJ   JGJC A G A", "A G A  JGJ  CJGJC  EGE ", " EGE  CJGJC J   J A G A", "A G A J   J J   J  EGE ", " EGE  J   J J   J A G A", "A G A J   J B   B  EGE ", " EGE  B   B  DGD  B   B", "A G A CDGDC CDGDC D   D", " EEE   DGD  D   GGD   D", "B   B CEEEC D   D D   D", "D   GGG   D D   D CD DC", "D   D D   D CD DC  DDD ", "CD DC D   D  DDD       ", " DDD  D   D            ", "      CD DC            ", "       DDD             ")
            .slice("BBBBB IKKKI IKKKI BBBBB", "AAAAA IKGKI IKGKI AAAAA", "ADDDA IDJDI IDJDI ADDDA", " DDD   DJD   DGD  CDDDC", "CDDDC  DJD   DJDC A   A", "A   A  DJD  CDJDC  EEE ", " EEE  CDJDC J   J A   A", "A   A J   J J   J  EEE ", " EEE  J   J J   J A   A", "A   A J   J B   B  EEE ", " EEE  B   B  DDD  B   B", "A   A CDDDC CDDDC D   D", " EEE   DDD  D   D D   D", "B   B CEEEC D   D D   D", "D   D D   D D   D CDDDC", "D   D D   D CDDDC  DDD ", "CDDDC D   D  DDD       ", " DDD  D   D            ", "      CDDDC            ", "       DDD             ")
            .slice("ABBBA IKKKI IKKKI ABBBA", "AAAAA IIGII IIGII AAAAA", "CAAAC CIIIC CIIIC CAAAC", "C   C C   C C   C CCCCC", "CCCCC C   C C   C CAAAC", "CAAAC C   C CCCCC C   C", "C   C CCCCC CJJJC CAAAC", "CAAAC CJJJC CJJJC C   C", "C   C CJJJC CJJJC CAAAC", "CAAAC CJJJC CBBBC C   C", "C   C CBBBC C   C CBBBC", "CAAAC CCCCC CCCCC CDDDC", "C   C C   C CDDDC CDDDC", "CBBBC CCCCC CDDDC CDDDC", "CDDDC CDDDC CDDDC CCCCC", "CDDDC CDDDC CCCCC      ", "CCCCC CDDDC            ", "      CDDDC            ", "      CCCCC            ", "                       ")
            .slice("                       ", "        G     G        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .slice("                       ", "        G     G        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .slice("                       ", "        G     G        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .slice("C   C  IIIIIIIII       ", "CCCCC  IGIIIIIGI       ", "CDDDC  IIIIIIIII       ", "CDDDC  IIIIIIIII       ", "CDDDC  IIIIIIIII       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .slice("       IKKKKKKKI       ", "CDDDC  I       I       ", "D   D  I       I       ", "D   D  I       I       ", "D   D  IIIIIIIII       ", " DDD     I   I         ", "         I   I         ", "         I   I         ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .slice("       IKKKKKKKI       ", "CDDDC  I       I       ", "D   D  I       I       ", "D   D  I       I       ", "D   D  IIEIIIEII       ", " DDD    IEI IEI        ", "        IEI IEI        ", "        IHI IHI        ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .slice("       IKKKKKKKI       ", "CDDDC  I       I       ", "D   D  I       I       ", "D   D  I       I       ", "D   D  IIIIIIIII       ", " DDD     I   I         ", "         I   I         ", "         I   I         ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .slice("       IILLLLLII       ", "CDDDC  IILLMLLII       ", "D   D  IILLLLLII       ", "D   D  IIIIIIIII       ", "D   D  IIIIIIIII       ", " DDD                   ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .slice("                       ", "CDDDC                  ", "D   D                  ", "D   D                  ", "D   D                  ", " DDD                   ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ")
            .slice("C   C                  ", "CCCCC                  ", "CDNDC                  ", "CDNDC                  ", "CDDDC                  ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ", "                       ");

    public static final Supplier<MultiblockPatternBuilder> DIGESTION_TANK = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("#OOOOO#", "#YMMMY#", "##YYY##", "#######")
            .slice("OXXXXXO", "YMAAAMY", "#YAAAY#", "#YYYYY#")
            .slice("OXKKKXO", "MAAAAAM", "YAAAAAY", "#YAAAY#")
            .slice("OXKKKXO", "MAAAAAM", "YAAAAAY", "#YAAAY#")
            .slice("OXKKKXO", "MAAAAAM", "YAAAAAY", "#YAAAY#")
            .slice("OXXXXXO", "YMAAAMY", "#YAAAY#", "#YYYYY#")
            .slice("#OOSOO#", "#YMMMY#", "##YYY##", "#######");

    public static final Supplier<MultiblockPatternBuilder> DISSOLVING_TANK = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("X###X", "OOOOO", "XGGGX", "XGGGX", "#XXX#")
            .slice("#####", "OKKKO", "GAAAG", "GAAAG", "XXXXX")
            .slice("#####", "OKKKO", "GAAAG", "GAAAG", "XXXXX")
            .slice("#####", "OKKKO", "GAAAG", "GAAAG", "XXXXX")
            .slice("X###X", "OOSOO", "XGGGX", "XGGGX", "#XXX#");

    public static final Supplier<MultiblockPatternBuilder> GRAVITATION_SHOCKBURST = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("aaaaaaaaa", "         ", "         ", "         ", "         ", "         ", "aaaaaaaaa")
            .slice("aaaaaaaaa", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", "aaaaaaaaa")
            .slice("aaaaaaaaa", " b ccc b ", " bcccccb ", " bcccccb ", " bcccccb ", " b ccc b ", "aaaaaaaaa")
            .slice("aaaaaaaaa", " bcccccb ", " bcccccb ", " bcc ccb ", " bcccccb ", " bcccccb ", "aaaaaaaaa")
            .slice("aaaaaaaaa", " bcccccb ", " bcc ccb ", " bc   cb ", " bcc ccb ", " bcccccb ", "aaaaaaaaa")
            .slice("aaaaaaaaa", " bcccccb ", " bcccccb ", " bcc ccb ", " bcccccb ", " bcccccb ", "aaaaaaaaa")
            .slice("aaaaaaaaa", " b ccc b ", " bcccccb ", " bcccccb ", " bcccccb ", " b ccc b ", "aaaaaaaaa")
            .slice("aaaaaaaaa", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", " abbbbba ", "aaaaaaaaa")
            .slice("aaaa~aaaa", "         ", "         ", "         ", "         ", "         ", "aaaaaaaaa");
}
