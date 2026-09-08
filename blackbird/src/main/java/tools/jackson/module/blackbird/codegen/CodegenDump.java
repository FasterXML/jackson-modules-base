package tools.jackson.module.blackbird.codegen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Debug aid: when the {@code blackbird.debug.dumpDir} system property names a
 * directory, every generated class's bytes are written there as
 * {@code <bean-class-name>-<kind>.class} before the hidden class is defined,
 * for inspection with javap or a decompiler. A dump failure never affects
 * generation.
 */
final class CodegenDump
{
    private static final Path DIR = resolveDir();

    private CodegenDump() {}

    private static Path resolveDir() {
        String dir = System.getProperty("blackbird.debug.dumpDir");
        return (dir == null) ? null : Path.of(dir);
    }

    static void dump(Class<?> beanClass, String kind, byte[] bytes) {
        if (DIR == null) {
            return;
        }
        try {
            Files.createDirectories(DIR);
            Files.write(DIR.resolve(beanClass.getName() + "-" + kind + ".class"), bytes);
        } catch (IOException e) {
            Logger.getLogger("tools.jackson.module.blackbird").log(Level.FINE,
                    "Generated-class dump failed for " + beanClass.getName(), e);
        }
    }
}
