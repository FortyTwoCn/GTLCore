import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.zip.*;

/** Finds actual callers without loading client or mod classes. */
public class FindNativeCalls {
    public static void main(String[] args) throws Exception {
        for (String path : Files.readAllLines(Path.of(args[0]))) {
            if (!path.endsWith(".jar")) continue;
            try (var jar = new ZipFile(path)) {
                for (var entry : jar.stream().filter(e -> e.getName().endsWith(".class") && e.getName().startsWith(args[1])).toList()) {
                    var node = new ClassNode();
                    new ClassReader(jar.getInputStream(entry)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    for (var method : node.methods) for (var instruction : method.instructions)
                        if (instruction instanceof MethodInsnNode call && call.name.equals(args[2]))
                            System.out.println(node.name + "#" + method.name + method.desc + " -> " + call.owner + ";" + call.name + call.desc);
                }
            }
        }
    }
}
