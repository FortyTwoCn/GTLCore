package org.gtlcore.gtlcore.common.data.machines.structure;

import com.gregtechceu.gtceu.api.multiblock.OriginOffset;
import com.gregtechceu.gtceu.api.multiblock.pattern.MultiblockPatternBuilder;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;

import java.util.function.Supplier;

import static com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection.*;

public class GCyMMachinesStructure {

    public static final Supplier<MultiblockPatternBuilder> LARGE_MACERATION_TOWER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXX", "XXXXX", "XXXXX", "XXXXX")
            .slice("XXXXX", "XGGGX", "XGGGX", "XAAAX")
            .slice("XXXXX", "XGGGX", "XGGGX", "XAAAX")
            .slice("XXXXX", "XGGGX", "XGGGX", "XAAAX")
            .slice("XXXXX", "XXXXX", "XXSXX", "XXXXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_CHEMICAL_BATH = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXX", "XXXXX", "XXXXX")
            .slice("XXXXX", "XTTTX", "X   X")
            .slice("XXXXX", "X   X", "X   X")
            .slice("XXXXX", "X   X", "X   X")
            .slice("XXXXX", "X   X", "X   X")
            .slice("XXXXX", "XTTTX", "X   X")
            .slice("XXXXX", "XXSXX", "XXXXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_CENTRIFUGE = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("#XXX#", "XXXXX", "#XXX#")
            .slice("XXXXX", "XAPAX", "XXXXX")
            .slice("XXXXX", "XPAPX", "XXXXX")
            .slice("XXXXX", "XAPAX", "XXXXX")
            .slice("#XXX#", "XXSXX", "#XXX#");

    public static final Supplier<MultiblockPatternBuilder> LARGE_MIXER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("#XXX#", "#XXX#", "#XXX#", "#XXX#", "#XXX#", "##F##")
            .slice("XXXXX", "XAPAX", "XAAAX", "XAPAX", "XAAAX", "##F##")
            .slice("XXXXX", "XPPPX", "XAPAX", "XPPPX", "XAGAX", "FFGFF")
            .slice("XXXXX", "XAPAX", "XAAAX", "XAPAX", "XAAAX", "##F##")
            .slice("#XXX#", "#XSX#", "#XXX#", "#XXX#", "#XXX#", "##F##");

    public static final Supplier<MultiblockPatternBuilder> LARGE_ELECTROLYZER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXX", "XXXXX", "XXXXX")
            .slice("XXXXX", "XCCCX", "XCCCX")
            .slice("XXXXX", "XCCCX", "XCCCX")
            .slice("XXXXX", "XXSXX", "XXXXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_ELECTROMAGNET = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXX", "XXXXX", "XXXXX")
            .slice("XCXCX", "XCXCX", "XCXCX")
            .slice("XCXCX", "XCXCX", "XCXCX")
            .slice("XXXXX", "XXSXX", "XXXXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_PACKER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXX", "XXX", "XXX")
            .slice("XXX", "XAX", "XXX")
            .slice("XXX", "XAX", "XXX")
            .slice("XXX", "XAX", "XXX")
            .slice("XXX", "XAX", "XXX")
            .slice("XXX", "XSX", "XXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_ASSEMBLER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXXXXXX", "XXXXXXXXX", "XXXXXXXXX")
            .slice("XXXXXXXXX", "XAAAXAAAX", "XGGGXXXXX")
            .slice("XXXXXXXXX", "XGGGXXSXX", "XGGGX###X");

    public static final Supplier<MultiblockPatternBuilder> LARGE_CIRCUIT_ASSEMBLER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXXXX", "XXXXXXX", "XXXXXXX")
            .slice("XXXXXXX", "XPPPPPX", "XGGGGGX")
            .slice("XXXXXXX", "XAAAAPX", "XGGGGGX")
            .slice("XXXXXXX", "XTTTTXX", "XXXXXXX")
            .slice("#####XX", "#####SX", "#####XX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_ARC_SMELTER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("#XXX#", "#XXX#", "#XXX#", "#XXX#")
            .slice("XXXXX", "XCACX", "XCACX", "XXXXX")
            .slice("XXXXX", "XAAAX", "XAAAX", "XXMXX")
            .slice("XXXXX", "XACAX", "XACAX", "XXXXX")
            .slice("#XXX#", "#XSX#", "#XXX#", "#XXX#");

    public static final Supplier<MultiblockPatternBuilder> LARGE_ENGRAVING_LASER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXX", "XXGXX", "XXGXX", "XXXXX")
            .slice("XXXXX", "XAAAX", "XAAAX", "XKKKX")
            .slice("XXXXX", "GAAAG", "GACAG", "XKXKX")
            .slice("XXXXX", "XAAAX", "XAAAX", "XKKKX")
            .slice("XXSXX", "XXGXX", "XXGXX", "XXXXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_SIFTING_FUNNEL = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("#X#X#", "#X#X#", "#XXX#", "XXXXX", "#XXX#")
            .slice("XXXXX", "XAXAX", "XKKKX", "XKKKX", "X###X")
            .slice("#XXX#", "#XAX#", "XKKKX", "XKKKX", "X###X")
            .slice("XXXXX", "XAXAX", "XKKKX", "XKKKX", "X###X")
            .slice("#X#X#", "#X#X#", "#XSX#", "XXXXX", "#XXX#");

    public static final Supplier<MultiblockPatternBuilder> BLAST_ALLOY_SMELTER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("#XXX#", "#CCC#", "#GGG#", "#CCC#", "#XXX#")
            .slice("XXXXX", "CAAAC", "GAAAG", "CAAAC", "XXXXX")
            .slice("XXXXX", "CAAAC", "GAAAG", "CAAAC", "XXMXX")
            .slice("XXXXX", "CAAAC", "GAAAG", "CAAAC", "XXXXX")
            .slice("#XSX#", "#CCC#", "#GGG#", "#CCC#", "#XXX#");

    public static final Supplier<MultiblockPatternBuilder> LARGE_AUTOCLAVE = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXX", "XXX", "XXX")
            .slice("XXX", "XTX", "XXX")
            .slice("XXX", "XTX", "XXX")
            .slice("XXX", "XTX", "XXX")
            .slice("XXX", "XSX", "XXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_MATERIAL_PRESS = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXXXX", "XXXXXXX", "XXXXXXX")
            .slice("XXXXXXX", "XAXGGGX", "XXXXXXX")
            .slice("XXXXXXX", "XSXCCCX", "XXXXXXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_BREWER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("#XXX#", "#XXX#", "#XXX#", "#XXX#", "#####")
            .slice("XXXXX", "XCCCX", "XAAAX", "XXAXX", "##X##")
            .slice("XXXXX", "XCPCX", "XAPAX", "XAPAX", "#XMX#")
            .slice("XXXXX", "XCCCX", "XAAAX", "XXAXX", "##X##")
            .slice("#XXX#", "#XSX#", "#XXX#", "#XXX#", "#####");

    public static final Supplier<MultiblockPatternBuilder> LARGE_CUTTER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXXXX", "XXXXXXX", "XXXXXXX", "##XXXXX")
            .slice("XXXXXXX", "XAXCCCX", "XXXAAAX", "##XXXXX")
            .slice("XXXXXXX", "XAXCCCX", "XXXAAAX", "##XXXXX")
            .slice("XXXXXXX", "XSXGGGX", "XXXGGGX", "##XXXXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_EXTRACTOR = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXX", "XXXXX", "XXXXX")
            .slice("XXXXX", "XCACX", "XXXXX")
            .slice("XXXXX", "XXSXX", "XXXXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_EXTRUDER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .startOffset(OriginOffset.of(-1, -1, -5))
            .slice("##XXX", "##XXX", "##XXX")
            .sliceRepeatable(2, 2, "##XXX", "##XPX", "##XGX")
            .slice("XXXXX", "XXXPX", "XXXGX")
            .slice("XXXXX", "XAXPX", "XXXGX")
            .slice("XXXXX", "XSXXX", "XXXXX");

    public static final Supplier<MultiblockPatternBuilder> LARGE_SOLIDIFIER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("#XXX#", "#XXX#", "#XXX#", "#XXX#")
            .slice("XXXXX", "XCACX", "XCACX", "XXXXX")
            .slice("XXXXX", "XAAAX", "XAAAX", "XXXXX")
            .slice("XXXXX", "XCACX", "XCACX", "XXXXX")
            .slice("#XXX#", "#XSX#", "#XXX#", "#XXX#");

    public static final Supplier<MultiblockPatternBuilder> LARGE_WIREMILL = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXX", "XXXXX", "XXX##")
            .slice("XXXXX", "X#CCX", "XXXXX")
            .slice("XXXXX", "XSXXX", "XXX##");

    public static final Supplier<MultiblockPatternBuilder> MEGA_BLAST_FURNACE = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("##XXXXXXXXX##", "##XXXXXXXXX##", "#############", "#############", "#############",
                    "#############", "#############", "#############", "#############", "#############",
                    "#############", "#############", "#############", "#############", "#############",
                    "#############", "#############")
            .slice("#XXXXXXXXXXX#", "#XXXXXXXXXXX#", "###F#####F###", "###F#####F###", "###FFFFFFF###",
                    "#############", "#############", "#############", "#############", "#############",
                    "####FFFFF####", "#############", "#############", "#############", "#############",
                    "#############", "#############")
            .slice("XXXXXXXXXXXXX", "XXXXVVVVVXXXX", "##F#######F##", "##F#######F##", "##FFFHHHFFF##",
                    "##F#######F##", "##F#######F##", "##F#######F##", "##F#######F##", "##F#######F##",
                    "##FFFHHHFFF##", "#############", "#############", "#############", "#############",
                    "#############", "###TTTTTTT###")
            .slice("XXXXXXXXXXXXX", "XXXXXXXXXXXXX", "#F####P####F#", "#F####P####F#", "#FFHHHPHHHFF#",
                    "######P######", "######P######", "######P######", "######P######", "######P######",
                    "##FHHHPHHHF##", "######P######", "######P######", "######P######", "######P######",
                    "######P######", "##TTTTPTTTT##")
            .slice("XXXXXXXXXXXXX", "XXVXXXXXXXVXX", "####BBPBB####", "####TITIT####", "#FFHHHHHHHFF#",
                    "####BITIB####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####BITIB####",
                    "#FFHHHHHHHFF#", "####BITIB####", "####CCCCC####", "####CCCCC####", "####CCCCC####",
                    "####BITIB####", "##TTTTPTTTT##")
            .slice("XXXXXXXXXXXXX", "XXVXXXXXXXVXX", "####BAAAB####", "####IAAAI####", "#FHHHAAAHHHF#",
                    "####IAAAI####", "####CAAAC####", "####CAAAC####", "####CAAAC####", "####IAAAI####",
                    "#FHHHAAAHHHF#", "####IAAAI####", "####CAAAC####", "####CAAAC####", "####CAAAC####",
                    "####IAAAI####", "##TTTTPTTTT##")
            .slice("XXXXXXXXXXXXX", "XXVXXXXXXXVXX", "###PPAAAPP###", "###PTAAATP###", "#FHPHAAAHPHF#",
                    "###PTAAATP###", "###PCAAACP###", "###PCAAACP###", "###PCAAACP###", "###PTAAATP###",
                    "#FHPHAAAHPHF#", "###PTAAATP###", "###PCAAACP###", "###PCAAACP###", "###PCAAACP###",
                    "###PTAAATP###", "##TPPPMPPPT##")
            .slice("XXXXXXXXXXXXX", "XXVXXXXXXXVXX", "####BAAAB####", "####IAAAI####", "#FHHHAAAHHHF#",
                    "####IAAAI####", "####CAAAC####", "####CAAAC####", "####CAAAC####", "####IAAAI####",
                    "#FHHHAAAHHHF#", "####IAAAI####", "####CAAAC####", "####CAAAC####", "####CAAAC####",
                    "####IAAAI####", "##TTTTPTTTT##")
            .slice("XXXXXXXXXXXXX", "XXVXXXXXXXVXX", "####BBPBB####", "####TITIT####", "#FFHHHHHHHFF#",
                    "####BITIB####", "####CCCCC####", "####CCCCC####", "####CCCCC####", "####BITIB####",
                    "#FFHHHHHHHFF#", "####BITIB####", "####CCCCC####", "####CCCCC####", "####CCCCC####",
                    "####BITIB####", "##TTTTPTTTT##")
            .slice("XXXXXXXXXXXXX", "XXXXXXXXXXXXX", "#F####P####F#", "#F####P####F#", "#FFHHHPHHHFF#",
                    "######P######", "######P######", "######P######", "######P######", "######P######",
                    "##FHHHPHHHF##", "######P######", "######P######", "######P######", "######P######",
                    "######P######", "##TTTTPTTTT##")
            .slice("XXXXXXXXXXXXX", "XXXXVVVVVXXXX", "##F#######F##", "##F#######F##", "##FFFHHHFFF##",
                    "##F#######F##", "##F#######F##", "##F#######F##", "##F#######F##", "##F#######F##",
                    "##FFFHHHFFF##", "#############", "#############", "#############", "#############",
                    "#############", "###TTTTTTT###")
            .slice("#XXXXXXXXXXX#", "#XXXXXXXXXXX#", "###F#####F###", "###F#####F###", "###FFFFFFF###",
                    "#############", "#############", "#############", "#############", "#############",
                    "####FFFFF####", "#############", "#############", "#############", "#############",
                    "#############", "#############")
            .slice("##XXXXXXXXX##", "##XXXXSXXXX##", "#############", "#############", "#############",
                    "#############", "#############", "#############", "#############", "#############",
                    "#############", "#############", "#############", "#############", "#############",
                    "#############", "#############");

    public static final Supplier<MultiblockPatternBuilder> MEGA_VACUUM_FREEZER = () -> MultiblockPatternBuilder.start(RelativeDirection.FRONT, RelativeDirection.UP, RelativeDirection.LEFT)
            .slice("XXXXXXX#KKK", "XXXXXXX#KVK", "XXXXXXX#KVK", "XXXXXXX#KVK", "XXXXXXX#KKK", "XXXXXXX####",
                    "XXXXXXX####")
            .slice("XXXXXXX#KVK", "XPPPPPPPPPV", "XPAPAPX#VPV", "XPPPPPPPPPV", "XPAPAPX#KVK", "XPPPPPX####",
                    "XXXXXXX####")
            .slice("XXXXXXX#KVK", "XPAPAPXAVPV", "XAAAAAX#VPV", "XPAAAPX#VPV", "XAAAAAX#KVK", "XPAPAPX####",
                    "XXXXXXX####")
            .slice("XXXXXXX#KVK", "XPAPAPPPPPV", "XAAAAAX#VPV", "XPAAAPPPPPV", "XAAAAAX#KVK", "XPAPAPX####",
                    "XXXXXXX####")
            .slice("XXXXXXX#KKK", "XPPPPPX#KVK", "XPA#APX#KVK", "XPAAAPX#KVK", "XPAAAPX#KKK", "XPPPPPX####",
                    "XXXXXXX####")
            .slice("#XXXXX#####", "#XXSXX#####", "#XGGGX#####", "#XGGGX#####", "#XGGGX#####", "#XXXXX#####",
                    "###########");

    public static final Supplier<MultiblockPatternBuilder> LARGE_DISTILLERY = () -> MultiblockPatternBuilder.start(UP, BACK, RIGHT)
            .slice("#YYY#", "YYYYY", "YYYYY", "YYYYY", "#YYY#")
            .slice("#YSY#", "YAAAY", "YAAAY", "YAAAY", "#YYY#")
            .sliceRepeatable(1, 12, "##X##", "#XAX#", "XAPAX", "#XAX#", "##X##")
            .slice("#####", "#ZZZ#", "#ZCZ#", "#ZZZ#", "#####");
}
