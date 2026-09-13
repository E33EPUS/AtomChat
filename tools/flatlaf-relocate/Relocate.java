import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Relocates com.formdev.flatlaf -> com.atom.chat.shaded.flatlaf inside a jar.
 *
 * <p>Why this exists: FlatLaf lives under a global package name, so any other
 * mod that also bundles it can win the class loading race — that happened in the
 * field (a mod called Mcpatch shipped its own copy, our picker then ran on it
 * and Forge's class transformer blew up on a class its copy did not have).
 * Moving our copy under a private package makes the collision impossible.
 *
 * <p>Class references alone are not enough: FlatLaf stores UI delegate class
 * names as string constants and looks resources up by path, so string constants
 * and resource paths have to move with the classes.
 *
 * <p>Usage: Relocate &lt;flatlaf.jar&gt; &lt;output.jar&gt;
 * Requires asm, asm-commons and asm-tree on the classpath (see relocate-flatlaf.sh).
 */
public final class Relocate {

    static final String FROM_SLASH = "com/formdev/flatlaf";
    static final String TO_SLASH = "com/atom/chat/shaded/flatlaf";
    static final String FROM_DOT = "com.formdev.flatlaf";
    static final String TO_DOT = "com.atom.chat.shaded.flatlaf";

    /** Rewrites both the slashed (internal) and dotted forms wherever they appear. */
    static String rewrite(String s) {
        if (s == null || s.isEmpty()) {
            return s;
        }
        if (!s.contains(FROM_DOT) && !s.contains(FROM_SLASH)) {
            return s;
        }
        return s.replace(FROM_SLASH, TO_SLASH).replace(FROM_DOT, TO_DOT);
    }

    private static final class Remap extends Remapper {
        @Override
        public String map(String internalName) {
            if (internalName != null && internalName.startsWith(FROM_SLASH)) {
                return TO_SLASH + internalName.substring(FROM_SLASH.length());
            }
            return internalName;
        }

        @Override
        public Object mapValue(Object value) {
            if (value instanceof String s) {
                return rewrite(s);
            }
            return super.mapValue(value);
        }
    }

    /** Text resources may carry the package name too (services files, properties). */
    private static boolean isTextResource(String name) {
        return name.endsWith(".properties") || name.endsWith(".xml") || name.endsWith(".json")
                || name.startsWith("META-INF/services/") || name.endsWith(".txt");
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.err.println("usage: Relocate <flatlaf.jar> <output.jar>");
            System.exit(2);
        }
        Path in = Path.of(args[0]);
        Path out = Path.of(args[1]);

        int classes = 0;
        int resources = 0;
        int skipped = 0;
        List<String> renamed = new ArrayList<>();

        try (ZipInputStream zin = new ZipInputStream(Files.newInputStream(in));
             ZipOutputStream zout = new ZipOutputStream(Files.newOutputStream(out))) {

            // Forge's JarJar refuses to embed a jar with no explicit module name
            // ("Cannot embed local file dependency ... it has no explicit Java
            // module name"), so the manifest is written here rather than copied.
            ZipEntry manifest = new ZipEntry("META-INF/MANIFEST.MF");
            manifest.setTime(System.currentTimeMillis());
            zout.putNextEntry(manifest);
            zout.write(("Manifest-Version: 1.0\r\n"
                    + "Automatic-Module-Name: " + TO_DOT + "\r\n"
                    + "\r\n").getBytes(StandardCharsets.UTF_8));
            zout.closeEntry();

            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] data = readAll(zin);

                // Replaced above.
                if (name.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                    skipped++;
                    continue;
                }
                // Directory entries are optional in a jar, and the intermediate
                // ones ("com/formdev/") cannot be rewritten by a prefix swap.
                if (name.endsWith("/")) {
                    skipped++;
                    continue;
                }
                // A relocated module descriptor only causes trouble.
                if (name.equals("module-info.class") || name.endsWith("/module-info.class")) {
                    skipped++;
                    continue;
                }

                String newName = rewrite(name);
                if (!newName.equals(name)) {
                    renamed.add(name);
                }

                byte[] outData;
                if (name.endsWith(".class")) {
                    ClassReader reader = new ClassReader(data);
                    ClassWriter writer = new ClassWriter(0);
                    reader.accept(new ClassRemapper(writer, new Remap()), 0);
                    outData = writer.toByteArray();
                    classes++;
                } else if (isTextResource(name)) {
                    outData = rewrite(new String(data, StandardCharsets.UTF_8))
                            .getBytes(StandardCharsets.UTF_8);
                    resources++;
                } else {
                    outData = data;
                }

                ZipEntry copy = new ZipEntry(newName);
                copy.setTime(entry.getTime());
                zout.putNextEntry(copy);
                zout.write(outData);
                zout.closeEntry();
            }
        }

        System.out.println("[relocate] classes=" + classes + " textResources=" + resources
                + " skipped=" + skipped + " renamed=" + renamed.size());
        for (int i = 0; i < Math.min(5, renamed.size()); i++) {
            System.out.println("[relocate]   " + renamed.get(i) + (i == 4 ? "  ..." : ""));
        }

        // Byte-level audit: anything still mentioning the old package would be a
        // silent hole in the relocation.
        int leaks = 0;
        try (ZipInputStream check = new ZipInputStream(Files.newInputStream(out))) {
            ZipEntry e;
            while ((e = check.getNextEntry()) != null) {
                String asText = new String(readAll(check), StandardCharsets.ISO_8859_1);
                if (asText.contains(FROM_SLASH) || asText.contains(FROM_DOT)) {
                    leaks++;
                    System.out.println("[relocate] LEFTOVER in " + e.getName());
                }
            }
        }
        System.out.println("[relocate] entries still mentioning com.formdev.flatlaf: " + leaks);
        if (leaks > 0) {
            System.exit(1);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
