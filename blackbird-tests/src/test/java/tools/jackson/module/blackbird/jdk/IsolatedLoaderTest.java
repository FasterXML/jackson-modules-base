package tools.jackson.module.blackbird.jdk;

import java.lang.classfile.ClassFile;
import java.lang.classfile.Label;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;
import java.lang.reflect.Field;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.codegen.CodegenFallbacks;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;
import tools.jackson.module.blackbird.internal.GeneratedWriterBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ClassFile API computes stack map frames with a class hierarchy
 * resolver. Its default resolver reads class files through the system class
 * loader, which does not see Jackson when an application server, a plugin
 * host or OSGi loads Jackson and Blackbird in a loader of their own. This
 * test runs a child JVM whose class path has only the test classes, and
 * loads Jackson and Blackbird there in a loader whose parent is the platform
 * loader.
 */
public class IsolatedLoaderTest
{
    public static class Child {
        public String name;
        public int rank;
    }

    public static class Parent {
        public String id;
        public Child child;
        public List<Child> children;
        public long count;
        public boolean flag;
    }

    public record Rec(String name, int count, Child child) {}

    @TempDir
    Path dir;

    /** Runs inside the isolated loader. */
    public static class Probe {
        public static List<String> run() throws Exception {
            System.setProperty(CodegenFallbacks.FAIL_ON_ERROR_PROPERTY, "true");
            List<String> out = new ArrayList<>();
            out.add("isolated " + (JsonMapper.class.getClassLoader() == Probe.class.getClassLoader()));
            Field field = Class.forName("tools.jackson.module.blackbird.codegen.BeanReaderGenerator")
                    .getDeclaredField("CLASS_FILE");
            field.setAccessible(true);
            out.add("default resolver " + mergeFrame(ClassFile.of())
                    + ", generators " + mergeFrame((ClassFile) field.get(null)));

            ObjectMapper mapper = JsonMapper.builder().addModule(new BlackbirdModule()).build();
            Parent p = mapper.readValue("{\"id\":\"p\",\"child\":{\"name\":\"c\",\"rank\":2},"
                    + "\"children\":[{\"name\":\"d\",\"rank\":3}],\"count\":4,\"flag\":true}",
                    Parent.class);
            out.add(mapper.writeValueAsString(p));
            Rec r = mapper.readValue("{\"name\":\"r\",\"count\":5,\"child\":{\"name\":\"e\"}}",
                    Rec.class);
            out.add(mapper.writeValueAsString(r));
            DeserializationContext dc = mapper._deserializationContext();
            SerializationContext sc = mapper._serializationContext();
            for (Class<?> type : List.of(Parent.class, Child.class, Rec.class)) {
                boolean reader = dc.findRootValueDeserializer(dc.constructType(type))
                        instanceof GeneratedReaderBase;
                boolean writer = sc.handleRootContextualization(sc.findValueSerializer(type))
                        instanceof GeneratedWriterBase;
                out.add(type.getSimpleName() + " reader=" + reader + " writer=" + writer);
            }
            return out;
        }

        // Two branches leave a GeneratedReaderBase and a BeanDeserializer on
        // the stack. The frame where they join needs their common superclass,
        // so the resolver must find both classes.
        private static String mergeFrame(ClassFile classFile) {
            ClassDesc reader = ClassDesc.of(GeneratedReaderBase.class.getName());
            ClassDesc stock = ClassDesc.of(BeanDeserializer.class.getName());
            try {
                classFile.build(ClassDesc.of("Merge"), clb -> clb.withMethod("pick",
                        MethodTypeDesc.of(ConstantDescs.CD_Object, ConstantDescs.CD_boolean,
                                reader, stock),
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
                        mb -> mb.withCode(cob -> {
                            Label second = cob.newLabel();
                            Label join = cob.newLabel();
                            cob.iload(0).ifeq(second)
                                    .aload(1).goto_(join)
                                    .labelBinding(second).aload(2)
                                    .labelBinding(join).areturn();
                        })));
                return "resolves";
            } catch (IllegalArgumentException e) {
                return "fails";
            }
        }
    }

    /** The main class of the child JVM: it sees only the test classes. */
    public static class Launcher {
        public static void main(String[] args) throws Exception {
            URL[] urls = new URL[args.length];
            for (int i = 0; i < args.length; i++) {
                urls[i] = Path.of(args[i]).toUri().toURL();
            }
            try (URLClassLoader loader = new URLClassLoader("isolated", urls,
                    ClassLoader.getPlatformClassLoader())) {
                Class<?> probe = Class.forName(Launcher.class.getName()
                        .replace("$Launcher", "$Probe"), true, loader);
                for (Object line : (List<?>) probe.getMethod("run").invoke(null)) {
                    System.out.println(line);
                }
            }
        }
    }

    private static String location(Class<?> type) throws URISyntaxException {
        return Path.of(type.getProtectionDomain().getCodeSource().getLocation().toURI())
                .toString();
    }

    @Test
    public void codecsGenerateWhenTheSystemLoaderCannotSeeJackson() throws Exception {
        String testClasses = location(IsolatedLoaderTest.class);
        List<String> cmd = List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "--class-path", testClasses,
                Launcher.class.getName(),
                location(BlackbirdModule.class),
                location(ObjectMapper.class),
                location(JsonParser.class),
                location(JsonProperty.class),
                testClasses);
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.environment().remove("JAVA_TOOL_OPTIONS");
        pb.environment().remove("JDK_JAVA_OPTIONS");
        pb.redirectOutput(dir.resolve("out.txt").toFile());
        pb.redirectError(dir.resolve("err.txt").toFile());
        Process process = pb.start();
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("child JVM timed out: " + cmd);
        }
        String err = Files.readString(dir.resolve("err.txt"));
        assertEquals(0, process.exitValue(), err);
        assertEquals(List.of(
                "isolated true",
                "default resolver fails, generators resolves",
                "{\"child\":{\"name\":\"c\",\"rank\":2},"
                        + "\"children\":[{\"name\":\"d\",\"rank\":3}],\"count\":4,\"flag\":true,"
                        + "\"id\":\"p\"}",
                "{\"name\":\"r\",\"count\":5,\"child\":{\"name\":\"e\",\"rank\":0}}",
                "Parent reader=true writer=true",
                "Child reader=true writer=true",
                "Rec reader=true writer=true"),
                Files.readAllLines(dir.resolve("out.txt")), err);
    }
}
