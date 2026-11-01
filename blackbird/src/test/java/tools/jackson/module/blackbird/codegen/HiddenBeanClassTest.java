package tools.jackson.module.blackbird.codegen;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A hidden bean class (as bytecode-generating frameworks define) has a
 * name like {@code Foo/0x1234}; generated codecs are named after the bean,
 * and '/' must not end up in their binary name, nor in the names of their
 * dumped class files.
 */
public class HiddenBeanClassTest extends BlackbirdTestBase
{
    // public class HiddenBean { public int x; public String name; }
    private static Class<?> defineHiddenBean() throws Exception {
        ClassDesc self = ClassDesc.of(HiddenBeanClassTest.class.getPackageName(), "HiddenBean");
        byte[] bytes = ClassFile.of().build(self, clb -> clb
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_SUPER)
                .withField("x", ConstantDescs.CD_int, ClassFile.ACC_PUBLIC)
                .withField("name", ConstantDescs.CD_String, ClassFile.ACC_PUBLIC)
                .withMethodBody(ConstantDescs.INIT_NAME, ConstantDescs.MTD_void,
                        ClassFile.ACC_PUBLIC, cob -> cob.aload(0)
                                .invokespecial(ConstantDescs.CD_Object,
                                        ConstantDescs.INIT_NAME, ConstantDescs.MTD_void)
                                .return_()));
        return MethodHandles.lookup().defineHiddenClass(bytes, true).lookupClass();
    }

    @Test
    public void testHiddenBeanClass() throws Exception {
        Class<?> type = defineHiddenBean();
        assertTrue(type.isHidden());
        assertTrue(type.getName().contains("/"), type.getName());

        ObjectMapper mapper = newObjectMapper();
        assertGeneratedReader(mapper, type);
        assertGeneratedWriter(mapper, type);

        Object bean = mapper.readValue(a2q("{'x':3,'name':'a'}"), type);
        assertEquals(3, type.getField("x").getInt(bean));
        assertEquals("a", type.getField("name").get(bean));
        assertEquals(newVanillaJSONMapper().writeValueAsString(bean),
                mapper.writeValueAsString(bean));
    }

    @Test
    public void testHiddenBeanClassDump(@TempDir Path dir) throws Exception {
        String old = System.getProperty(CodegenDebug.DUMP_DIR_PROPERTY);
        System.setProperty(CodegenDebug.DUMP_DIR_PROPERTY, dir.toString());
        try {
            Class<?> type = defineHiddenBean();
            ObjectMapper mapper = newObjectMapper();
            assertGeneratedReader(mapper, type);
            assertGeneratedWriter(mapper, type);
        } finally {
            if (old == null) {
                System.clearProperty(CodegenDebug.DUMP_DIR_PROPERTY);
            } else {
                System.setProperty(CodegenDebug.DUMP_DIR_PROPERTY, old);
            }
        }
        try (Stream<Path> files = Files.list(dir)) {
            String names = files.map(f -> f.getFileName().toString()).sorted()
                    .reduce("", (a, b) -> a + " " + b);
            assertTrue(names.matches(".*HiddenBean\\$0x[0-9a-f]+-reader\\.class.*"), names);
            assertTrue(names.matches(".*HiddenBean\\$0x[0-9a-f]+-writer\\.class.*"), names);
        }
    }
}
