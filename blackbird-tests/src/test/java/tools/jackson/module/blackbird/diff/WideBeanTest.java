package tools.jackson.module.blackbird.diff;

import java.lang.classfile.Attributes;
import java.lang.classfile.ClassFile;
import java.lang.classfile.MethodModel;
import java.lang.classfile.attribute.CodeAttribute;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.module.blackbird.codegen.ClassFileEmitters;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;
import tools.jackson.module.blackbird.internal.GeneratedWriterBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * HotSpot does not JIT-compile a method over 8000 bytes, so a bean whose
 * generated method would be larger keeps the stock (de)serializer. The test
 * builds beans of growing width, dumps every generated class, and checks
 * that each generated method stays within
 * {@link ClassFileEmitters#MAX_METHOD_BYTES}, that wide beans do generate up
 * to near that limit, and that wider ones are demoted.
 */
public class WideBeanTest extends DifferentialTestBase
{
    @TempDir
    static Path dumpDir;

    private static int beans;

    // A public class with n public String fields f0..f(n-1), defined next to
    // this test (databind reads and writes public fields).
    private static Class<?> wideBean(int n) throws IllegalAccessException {
        String name = WideBeanTest.class.getPackageName() + ".Wide" + n + "_" + (beans++);
        byte[] bytes = ClassFile.of().build(ClassDesc.of(name), clb -> {
            clb.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER);
            for (int i = 0; i < n; i++) {
                clb.withField("f" + i, ConstantDescs.CD_String, ClassFile.ACC_PUBLIC);
            }
            clb.withMethod(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void, ClassFile.ACC_PUBLIC,
                    mb -> mb.withCode(cob -> cob.aload(0)
                            .invokespecial(ConstantDescs.CD_Object, ConstantDescs.INIT_NAME,
                                    ConstantDescs.MTD_void)
                            .return_()));
        });
        return MethodHandles.lookup().defineClass(bytes);
    }

    private static String document(int n) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < n; i++) {
            sb.append(i == 0 ? "" : ",").append("\"f").append(i).append("\":\"v").append(i)
                    .append('"');
        }
        return sb.append('}').toString();
    }

    private static int largestMethod(Path classFile) throws Exception {
        int largest = 0;
        for (MethodModel m : ClassFile.of().parse(Files.readAllBytes(classFile)).methods()) {
            CodeAttribute code = m.findAttribute(Attributes.code()).orElse(null);
            if (code != null) {
                largest = Math.max(largest, code.codeLength());
            }
        }
        return largest;
    }

    @Test
    public void generatedMethodsStayWithinTheLimit() throws Exception {
        System.setProperty("blackbird.debug.dumpDir", dumpDir.toString());
        List<String> readers = new ArrayList<>();
        int widestReader = 0;
        int largestReader = 0;
        int largestWriter = 0;
        try {
            for (int n = 20; n <= 260; n += 20) {
                Class<?> type = wideBean(n);
                Mappers m = mappers();
                String json = document(n);
                Object value = assertSameRead(m, json, type).get();
                assertSameWrite(m, value);

                ValueDeserializer<Object> reader = rootReader(m.blackbird(), type);
                Path readerDump = dumpDir.resolve(type.getName() + "-reader.class");
                if (reader instanceof GeneratedReaderBase) {
                    int size = largestMethod(readerDump);
                    assertTrue(size <= ClassFileEmitters.MAX_METHOD_BYTES, n + ": " + size);
                    widestReader = n;
                    largestReader = Math.max(largestReader, size);
                    readers.add(n + "=" + size);
                } else {
                    assertFalse(Files.exists(readerDump), "a demoted reader is not dumped");
                }
                ValueSerializer<Object> writer = rootWriter(m.blackbird(), type);
                if (writer instanceof GeneratedWriterBase) {
                    int size = largestMethod(dumpDir.resolve(type.getName() + "-writer.class"));
                    assertTrue(size <= ClassFileEmitters.MAX_METHOD_BYTES, n + ": " + size);
                    largestWriter = Math.max(largestWriter, size);
                }
            }
        } finally {
            System.clearProperty("blackbird.debug.dumpDir");
        }
        // Wide beans do generate, up to near the limit, and the widest ones
        // keep the stock reader.
        assertTrue(widestReader >= 60, "readers: " + readers);
        assertTrue(widestReader < 260, "readers: " + readers);
        assertTrue(largestReader > ClassFileEmitters.MAX_METHOD_BYTES - 1000,
                "readers: " + readers);
        assertTrue(largestWriter > 0);
    }

    @Test
    public void wideBeanKeepsStockReader() throws Exception {
        Class<?> type = wideBean(260);
        Mappers m = mappers();
        assertSameRead(m, document(260), type);
        assertStockReader(m.blackbird(), type);
        Class<?> narrow = wideBean(20);
        assertSameRead(m, document(20), narrow);
        assertGeneratedReader(m.blackbird(), narrow);
    }
}
