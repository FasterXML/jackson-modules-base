package tools.jackson.module.blackbird.jdk;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.tools.ToolProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdModule;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs a small program against the packaged Blackbird jar in child JVMs: on
 * the JDK that runs the build (25 or later), and on each Java 17-24 home
 * listed (comma-separated) in the {@value #OLD_JDKS_PROPERTY} system property.
 * The jar is multi-release: its base classes are class file 61 and only the
 * two code generators are release 25 classes under META-INF/versions/25. On
 * Java 17-24, module discovery must skip Blackbird with one warning and still
 * register every other module, and an explicit registration must fail with a
 * clear message, also when discovery runs before or after it; on 25+, both
 * must engage the generated codecs. A second module provider, compiled at
 * release 17, is on the path of every run. The program is a single source
 * file that the child's launcher compiles (JEP 330), so it runs on every JDK
 * no matter what release this module compiles at.
 */
public class JdkCompatibilityTest
{
    static final String OLD_JDKS_PROPERTY = "blackbird.test.oldJdks";

    private static final String INACTIVE_WARNING =
            "found through module discovery, is inactive";

    private static final String PROBE = """
            import java.util.ArrayList;
            import java.util.Collections;
            import java.util.List;

            import tools.jackson.databind.JacksonModule;
            import tools.jackson.databind.ObjectMapper;
            import tools.jackson.databind.json.JsonMapper;

            // Names Blackbird only reflectively, like an application that
            // gets it through module discovery.
            public class BlackbirdProbe {
                public static class Bean {
                    public int x;
                    public String s;
                }

                public static void main(String[] args) throws Exception {
                    System.out.println("runtime " + Runtime.version().feature()
                            + " " + Runtime.version());
                    if (args[0].equals("discover")) {
                        for (int i = 0; i < 2; i++) {
                            ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
                            boolean found = false;
                            for (JacksonModule m : mapper.registeredModules()) {
                                found |= m.getClass().getName()
                                        .startsWith("tools.jackson.module.blackbird.");
                            }
                            Bean bean = mapper.readValue("{\\"x\\":1,\\"s\\":\\"a\\"}", Bean.class);
                            System.out.println("discovered " + found + " modules " + names(mapper)
                                    + " roundtrip " + mapper.writeValueAsString(bean));
                        }
                        return;
                    }
                    JacksonModule module = (JacksonModule) Class.forName(
                            "tools.jackson.module.blackbird.BlackbirdModule")
                            .getDeclaredConstructor().newInstance();
                    JsonMapper.Builder builder = JsonMapper.builder();
                    switch (args[0]) {
                        case "explicit" -> builder.addModule(module);
                        case "explicit-discover" -> builder.addModule(module).findAndAddModules();
                        case "discover-explicit" -> builder.findAndAddModules().addModule(module);
                        default -> throw new IllegalArgumentException(args[0]);
                    }
                    try {
                        System.out.println("registered modules " + names(builder.build()));
                    } catch (IllegalStateException e) {
                        System.out.println("rejected " + e.getMessage());
                    }
                }

                private static List<String> names(ObjectMapper mapper) {
                    List<String> names = new ArrayList<>();
                    for (JacksonModule m : mapper.registeredModules()) {
                        names.add(m.getModuleName());
                    }
                    Collections.sort(names);
                    return names;
                }
            }
            """;

    // The module provider of another library, for the class path
    // (META-INF/services) and the module path (module-info).
    private static final String OTHER_MODULE_NAME = "OtherProbeModule";

    private static final String OTHER_MODULE_INFO = """
            module blackbird.probe.other {
                requires tools.jackson.databind;
                provides tools.jackson.databind.JacksonModule with probe.other.OtherProbeModule;
            }
            """;

    private static final String OTHER_MODULE = """
            package probe.other;

            import tools.jackson.core.Version;
            import tools.jackson.databind.JacksonModule;

            public final class OtherProbeModule extends JacksonModule {
                @Override
                public String getModuleName() {
                    return "OtherProbeModule";
                }

                @Override
                public Version version() {
                    return Version.unknownVersion();
                }

                @Override
                public void setupModule(SetupContext context) {
                }
            }
            """;

    @TempDir
    static Path probeDir;

    static Stream<Path> javaHomes() {
        List<Path> homes = new ArrayList<>();
        homes.add(Path.of(System.getProperty("java.home")));
        homes.addAll(oldJdkHomes());
        return homes.stream();
    }

    private static List<Path> oldJdkHomes() {
        String prop = System.getProperty(OLD_JDKS_PROPERTY, "").trim();
        if (prop.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(prop.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Path::of)
                .collect(Collectors.toList());
    }

    @Test
    public void oldJdksAreConfigured() {
        assumeTrue(!oldJdkHomes().isEmpty(), "No Java 17-24 home configured: set -D"
                + OLD_JDKS_PROPERTY + "=<home>[,<home>] to run the old-runtime cases");
        for (Path home : oldJdkHomes()) {
            assertTrue(Files.isExecutable(home.resolve("bin/java")), "not a JDK home: " + home);
        }
    }

    @ParameterizedTest
    @MethodSource("javaHomes")
    public void discoveryOnClassPath(Path javaHome) throws Exception {
        assertDiscovery(run(javaHome, false, "discover"));
    }

    @ParameterizedTest
    @MethodSource("javaHomes")
    public void discoveryOnModulePath(Path javaHome) throws Exception {
        assertDiscovery(run(javaHome, true, "discover"));
    }

    @ParameterizedTest
    @MethodSource("javaHomes")
    public void explicitRegistration(Path javaHome) throws Exception {
        assertExplicit(run(javaHome, false, "explicit"), List.of("BlackbirdModule"));
    }

    // Databind keeps the module added last for each registration id, so the
    // discovery provider must not replace an explicit registration.
    @ParameterizedTest
    @MethodSource("javaHomes")
    public void explicitRegistrationThenDiscovery(Path javaHome) throws Exception {
        assertExplicit(run(javaHome, false, "explicit-discover"),
                List.of("BlackbirdModule", OTHER_MODULE_NAME));
    }

    @ParameterizedTest
    @MethodSource("javaHomes")
    public void discoveryThenExplicitRegistration(Path javaHome) throws Exception {
        assertExplicit(run(javaHome, false, "discover-explicit"),
                List.of("BlackbirdModule", OTHER_MODULE_NAME));
    }

    // The packaged jar is what users get; target/classes (used by a reactor
    // build that stops before the package phase) is not multi-release.
    @Test
    public void jarIsMultiReleaseWithJava17Base() throws Exception {
        Path jar = blackbirdJar();
        try (JarFile jf = new JarFile(jar.toFile())) {
            Attributes main = jf.getManifest().getMainAttributes();
            assertEquals("true", main.getValue("Multi-Release"));
            assertEquals("osgi.ee;filter:=\"(&(osgi.ee=JavaSE)(version=17))\"",
                    main.getValue("Require-Capability"));
            List<String> versioned = new ArrayList<>();
            for (JarEntry e : jf.stream().toList()) {
                if (!e.getName().endsWith(".class")) {
                    continue;
                }
                int major = classMajor(jf, e);
                if (e.getName().startsWith("META-INF/versions/25/")) {
                    assertEquals(69, major, e.getName());
                    versioned.add(e.getName());
                } else {
                    assertEquals(61, major, e.getName());
                }
            }
            assertTrue(versioned.contains("META-INF/versions/25/tools/jackson/module/blackbird/codegen/BeanReaderGenerator.class"),
                    "versioned entries: " + versioned);
            assertTrue(versioned.contains("META-INF/versions/25/tools/jackson/module/blackbird/codegen/BeanWriterGenerator.class"),
                    "versioned entries: " + versioned);
        }
    }

    private static void assertDiscovery(Result r) {
        assertEquals(0, r.exitCode, r.toString());
        String discovered = "discovered true modules [BlackbirdModule, " + OTHER_MODULE_NAME
                + "] roundtrip {\"s\":\"a\",\"x\":1}";
        assertEquals(List.of(r.stdout.get(0), discovered, discovered), r.stdout, r.toString());
        long warnings = r.stderr.stream().filter(l -> l.contains(INACTIVE_WARNING)).count();
        boolean engaged = r.stderr.stream().anyMatch(l -> l.contains("BBReader_BlackbirdProbe"))
                && r.stderr.stream().anyMatch(l -> l.contains("BBWriter_BlackbirdProbe"));
        if (r.feature >= 25) {
            assertEquals(0, warnings, r.toString());
            assertTrue(engaged, "expected generated codecs: " + r);
        } else {
            assertEquals(1, warnings, "expected exactly one warning: " + r);
            assertFalse(engaged, r.toString());
        }
    }

    private static void assertExplicit(Result r, List<String> modules) {
        assertEquals(0, r.exitCode, r.toString());
        assertEquals(2, r.stdout.size(), r.toString());
        if (r.feature >= 25) {
            assertEquals("registered modules " + modules, r.stdout.get(1), r.toString());
        } else {
            assertEquals("rejected Cannot register BlackbirdModule: it requires Java 25 or"
                    + " later, and this runtime is Java " + r.version, r.stdout.get(1)
                            .replaceFirst("\\. Remove the registration.*$", ""),
                    r.toString());
        }
    }

    private static Result run(Path javaHome, boolean modulePath, String mode)
            throws IOException, InterruptedException, URISyntaxException {
        Path probe = probeDir.resolve("BlackbirdProbe.java");
        if (!Files.exists(probe)) {
            Files.writeString(probe, PROBE);
        }
        String path = Stream.of(blackbirdJar(), otherModuleJar(), codeSource(ObjectMapper.class),
                        codeSource(JsonParser.class), codeSource(JsonProperty.class))
                .map(Path::toString)
                .collect(Collectors.joining(java.io.File.pathSeparator));
        List<String> cmd = new ArrayList<>();
        cmd.add(javaHome.resolve("bin/java").toString());
        cmd.add("-Dblackbird.debug.codegen=true");
        if (modulePath) {
            cmd.addAll(List.of("--module-path", path, "--add-modules", "ALL-MODULE-PATH"));
        } else {
            cmd.addAll(List.of("--class-path", path));
        }
        cmd.add(probe.toString());
        cmd.add(mode);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        Map<String, String> env = pb.environment();
        env.remove("JAVA_TOOL_OPTIONS");
        env.remove("JDK_JAVA_OPTIONS");
        Path out = probeDir.resolve(mode + "-" + modulePath + "-out.txt");
        Path err = probeDir.resolve(mode + "-" + modulePath + "-err.txt");
        pb.redirectOutput(out.toFile());
        pb.redirectError(err.toFile());
        Process process = pb.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("probe timed out: " + cmd);
        }
        Result r = new Result(cmd, process.exitValue(),
                Files.readAllLines(out, StandardCharsets.UTF_8),
                Files.readAllLines(err, StandardCharsets.UTF_8));
        assertFalse(r.stdout.isEmpty(), r.toString());
        return r;
    }

    private static Path otherModuleJar() throws IOException, URISyntaxException {
        Path jar = probeDir.resolve("other-probe-module.jar");
        if (Files.exists(jar)) {
            return jar;
        }
        Path src = probeDir.resolve("other-src");
        Path classes = probeDir.resolve("other-classes");
        Files.createDirectories(src.resolve("probe/other"));
        Files.writeString(src.resolve("module-info.java"), OTHER_MODULE_INFO);
        Files.writeString(src.resolve("probe/other/OtherProbeModule.java"), OTHER_MODULE);
        String modulePath = Stream.of(codeSource(ObjectMapper.class),
                        codeSource(JsonParser.class), codeSource(JsonProperty.class))
                .map(Path::toString)
                .collect(Collectors.joining(java.io.File.pathSeparator));
        ByteArrayOutputStream diagnostics = new ByteArrayOutputStream();
        int rc = ToolProvider.getSystemJavaCompiler().run(null, diagnostics, diagnostics,
                "--release", "17", "--module-path", modulePath, "-d", classes.toString(),
                src.resolve("module-info.java").toString(),
                src.resolve("probe/other/OtherProbeModule.java").toString());
        assertEquals(0, rc, diagnostics.toString(StandardCharsets.UTF_8));
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            for (String name : List.of("module-info.class", "probe/other/OtherProbeModule.class")) {
                out.putNextEntry(new JarEntry(name));
                out.write(Files.readAllBytes(classes.resolve(name)));
                out.closeEntry();
            }
            out.putNextEntry(new JarEntry("META-INF/services/" + JacksonModule.class.getName()));
            out.write("probe.other.OtherProbeModule\n".getBytes(StandardCharsets.UTF_8));
            out.closeEntry();
        }
        return jar;
    }

    private static Path blackbirdJar() throws URISyntaxException {
        Path location = codeSource(BlackbirdModule.class);
        assumeTrue(Files.isRegularFile(location), "Needs the packaged Blackbird jar (mvn verify);"
                + " this run reads " + location + ", which is not multi-release");
        return location;
    }

    private static Path codeSource(Class<?> cls) throws URISyntaxException {
        return Path.of(cls.getProtectionDomain().getCodeSource().getLocation().toURI());
    }

    private static int classMajor(JarFile jf, JarEntry e) throws IOException {
        try (InputStream in = jf.getInputStream(e)) {
            byte[] head = in.readNBytes(8);
            return ((head[6] & 0xFF) << 8) | (head[7] & 0xFF);
        }
    }

    private static final class Result {
        final List<String> cmd;
        final int exitCode;
        final List<String> stdout;
        final List<String> stderr;
        final int feature;
        final String version;

        Result(List<String> cmd, int exitCode, List<String> stdout, List<String> stderr) {
            this.cmd = cmd;
            this.exitCode = exitCode;
            this.stdout = stdout;
            this.stderr = stderr;
            String first = stdout.isEmpty() ? "" : stdout.get(0);
            // "runtime <feature> <Runtime.version()>"
            String[] parts = first.split(" ");
            this.feature = (parts.length == 3) ? Integer.parseInt(parts[1]) : -1;
            this.version = (parts.length == 3) ? parts[2] : "?";
        }

        @Override
        public String toString() {
            return "command " + cmd + "\nexit " + exitCode + "\nstdout:\n  "
                    + String.join("\n  ", stdout) + "\nstderr:\n  " + String.join("\n  ", stderr);
        }
    }
}
