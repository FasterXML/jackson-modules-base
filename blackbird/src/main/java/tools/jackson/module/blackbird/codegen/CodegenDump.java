package tools.jackson.module.blackbird.codegen;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Debug aid: when the {@code blackbird.debug.dumpDir} system property names a
 * directory, every generated class's bytes are written there as
 * {@code <bean-class-name>-<kind>.class} before the hidden class is defined,
 * for inspection with javap or a decompiler. The shared write helpers are
 * written as {@code <class-name>.class}. A dump failure never affects
 * generation.
 */
final class CodegenDump
{
    private CodegenDump() {}

    // Read per class (generation is rare and costly anyway), so that tests
    // can turn dumping on and off.
    static void dump(Class<?> beanClass, String kind, byte[] bytes) {
        dump(beanClass.getName() + "-" + kind, bytes);
    }

    static void dump(String fileStem, byte[] bytes) {
        String dir = System.getProperty(CodegenDebug.DUMP_DIR_PROPERTY);
        if (dir == null) {
            return;
        }
        try {
            Path path = Path.of(dir);
            Files.createDirectories(path);
            Files.write(path.resolve(fileStem + ".class"), bytes);
        } catch (IOException e) {
            System.getLogger("tools.jackson.module.blackbird").log(Level.DEBUG,
                    "Generated-class dump failed for " + fileStem, e);
        }
    }
}
