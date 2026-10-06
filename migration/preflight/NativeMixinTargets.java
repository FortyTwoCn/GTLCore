import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Read-only bytecode preflight. This does not replace running the strict Mixin transformer. */
public final class NativeMixinTargets {
    private final List<Path> paths = new ArrayList<>();
    private final Map<Path, ZipFile> archives = new HashMap<>();
    private final Map<String, ClassNode> classes = new HashMap<>();
    private int checked;

    private ClassNode read(String name) throws Exception {
        if (classes.containsKey(name)) return classes.get(name);
        byte[] bytes = null;
        for (Path path : paths) {
            if (Files.isDirectory(path)) {
                Path file = path.resolve(name + ".class");
                if (Files.isRegularFile(file)) { bytes = Files.readAllBytes(file); break; }
            } else if (Files.isRegularFile(path)) {
                ZipFile zip = archives.get(path);
                if (zip == null) { zip = new ZipFile(path.toFile()); archives.put(path, zip); }
                ZipEntry entry = zip.getEntry(name + ".class");
                if (entry != null) { bytes = zip.getInputStream(entry).readAllBytes(); break; }
            }
        }
        ClassNode node = null;
        if (bytes != null) {
            node = new ClassNode();
            new ClassReader(bytes).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
        classes.put(name, node);
        return node;
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> visible, List<AnnotationNode> invisible) {
        List<AnnotationNode> result = new ArrayList<>();
        if (visible != null) result.addAll(visible);
        if (invisible != null) result.addAll(invisible);
        return result;
    }
    private static AnnotationNode annotation(List<AnnotationNode> nodes, String suffix) {
        return nodes.stream().filter(a -> a.desc.endsWith("/" + suffix + ";")).findFirst().orElse(null);
    }
    private static Object value(AnnotationNode node, String key) {
        if (node == null || node.values == null) return null;
        for (int i = 0; i < node.values.size(); i += 2)
            if (key.equals(node.values.get(i))) return node.values.get(i + 1);
        return null;
    }
    private static List<?> values(Object value) {
        return value == null ? List.of() : value instanceof List<?> list ? list : List.of(value);
    }
    private void report(ClassNode mixin, String target, String member, String kind, String detail) {
        System.out.println(kind + "\t" + mixin.name + "\t" + target + "\t" + member + "\t" + detail);
    }
    private boolean field(ClassNode target, String name, String desc) throws Exception {
        if (target == null) return false;
        if (target.fields.stream().anyMatch(f -> f.name.equals(name) && f.desc.equals(desc))) return true;
        return field(read(target.superName), name, desc);
    }
    private List<MethodNode> methods(ClassNode target, String selector, boolean inherited) throws Exception {
        if (target == null) return List.of();
        int index = selector.indexOf('(');
        String name = index < 0 ? selector : selector.substring(0, index);
        String desc = index < 0 ? null : selector.substring(index);
        List<MethodNode> result = new ArrayList<>();
        for (var method : target.methods)
            if (method.name.equals(name) && (desc == null || method.desc.equals(desc))) result.add(method);
        if (inherited && result.isEmpty()) result.addAll(methods(read(target.superName), selector, true));
        return result;
    }
    private void audit(ClassNode mixin) throws Exception {
        var mark = annotation(annotations(mixin.visibleAnnotations, mixin.invisibleAnnotations), "Mixin");
        if (mark == null) return;
        List<String> targets = new ArrayList<>();
        for (Object type : values(value(mark, "value"))) targets.add(((Type) type).getInternalName());
        for (Object name : values(value(mark, "targets"))) targets.add(name.toString().replace('.', '/'));
        for (String targetName : targets) {
            ClassNode target = read(targetName);
            if (target == null) { report(mixin, targetName, "", "MISSING_TARGET", "Optional dependency or changed class"); continue; }
            checked++;
            for (FieldNode f : mixin.fields) {
                var shadow = annotation(annotations(f.visibleAnnotations, f.invisibleAnnotations), "Shadow");
                if (shadow != null && !field(target, f.name, f.desc))
                    report(mixin, targetName, f.name, "SHADOW_FIELD", f.desc);
            }
            for (MethodNode handler : mixin.methods) {
                var anns = annotations(handler.visibleAnnotations, handler.invisibleAnnotations);
                for (var ann : anns) {
                    if (ann.desc.endsWith("/Accessor;")) {
                        String name = (String) value(ann, "value");
                        Type[] args = Type.getArgumentTypes(handler.desc);
                        String desc = args.length == 0 ? Type.getReturnType(handler.desc).getDescriptor() : args[0].getDescriptor();
                        if (name != null && !field(target, name, desc)) report(mixin, targetName, handler.name, "ACCESSOR_FIELD", name + ":" + desc);
                    }
                    if (ann.desc.endsWith("/Invoker;")) {
                        String name = (String) value(ann, "value");
                        if (name != null && !name.equals("<init>") && methods(target, name + handler.desc, true).isEmpty())
                            report(mixin, targetName, handler.name, "INVOKER_METHOD", name + handler.desc);
                    }
                    if (ann.desc.endsWith("/Shadow;") || ann.desc.endsWith("/Overwrite;")) {
                        String name = handler.name.startsWith("shadow$") ? handler.name.substring(7) : handler.name;
                        if (methods(target, name + handler.desc, true).isEmpty())
                            report(mixin, targetName, handler.name, "SHADOW_METHOD", handler.desc);
                    }
                    Object selectors = value(ann, "method");
                    if (selectors == null) continue;
                    for (var selector : values(selectors)) {
                        List<MethodNode> selected = methods(target, selector.toString(), false);
                        if (selected.isEmpty()) {
                            report(mixin, targetName, handler.name, "SELECTOR", selector.toString());
                            continue;
                        }
                        if (ann.desc.endsWith("/Inject;")) {
                            Type[] args = Type.getArgumentTypes(handler.desc);
                            int ci = -1;
                            for (int i = 0; i < args.length; i++)
                                if (args[i].getDescriptor().contains("/callback/CallbackInfo")) { ci = i; break; }
                            if (ci > 0) {
                                boolean match = false;
                                for (var m : selected) {
                                    var targetArgs = Type.getArgumentTypes(m.desc);
                                    if (ci != targetArgs.length) continue;
                                    boolean compatible = true;
                                    for (int i = 0; i < ci; i++)
                                        if (!args[i].equals(targetArgs[i]) && !args[i].equals(Type.getType(Object.class))) compatible = false;
                                    match |= compatible;
                                }
                                if (!match) report(mixin, targetName, handler.name, "INJECT_ARGS",
                                        handler.desc + " expected " + selected.stream().map(m -> m.desc).toList());
                            }
                        }
                        for (Object atObject : values(value(ann, "at"))) {
                            if (!(atObject instanceof AnnotationNode at)) continue;
                            String invocation = (String) value(at, "target");
                            if (!"INVOKE".equals(value(at, "value")) || invocation == null || !invocation.startsWith("L")) continue;
                            boolean found = false;
                            for (var method : selected)
                                for (var instruction : method.instructions)
                                    if (instruction instanceof MethodInsnNode call && invocation.equals("L" + call.owner + ";" + call.name + call.desc)) found = true;
                            if (!found) report(mixin, targetName, handler.name, "INVOKE", selector + " -> " + invocation);
                        }
                    }
                }
            }
        }
    }
    public static void main(String[] args) throws Exception {
        var audit = new NativeMixinTargets();
        for (String path : Files.readAllLines(Path.of(args[0]))) if (!path.isBlank()) audit.paths.add(Path.of(path));
        for (int i = 1; i < args.length; i++) {
            Path root = Path.of(args[i]);
            audit.paths.addFirst(root);
            try (var files = Files.walk(root)) {
                for (var file : files.filter(f -> f.toString().endsWith(".class")).toList()) {
                    var node = new ClassNode();
                    new ClassReader(Files.readAllBytes(file)).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    audit.audit(node);
                }
            }
        }
        for (var archive : audit.archives.values()) archive.close();
        System.err.println("Preflight inspected " + audit.checked + " available Mixin targets; runtime injection remains required.");
    }
}
