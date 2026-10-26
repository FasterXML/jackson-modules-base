package tools.jackson.module.blackbird.codegen;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A failing class dump (here an invalid dump directory, which fails with an
 * unchecked InvalidPathException rather than an IOException) must never
 * affect generation, and in particular must not leave the shared write
 * helpers undefined for the writers generated after it.
 */
public class CodegenDumpTest extends BlackbirdTestBase
{
    public static class DumpBean {
        public int a = 1;
        public String b = "x";
    }

    public static class LaterBean {
        public int c = 2;
    }

    private static final String BAD_DIR = "bad\0dir";

    @Test
    public void testDumpFailureIsIgnored() {
        String old = System.getProperty(CodegenDebug.DUMP_DIR_PROPERTY);
        System.setProperty(CodegenDebug.DUMP_DIR_PROPERTY, BAD_DIR);
        try {
            CodegenDump.dump("Stem", new byte[0]);
            ObjectMapper mapper = newObjectMapper();
            assertEquals("{\"a\":1,\"b\":\"x\"}", mapper.writeValueAsString(new DumpBean()));
            assertGeneratedWriter(mapper, DumpBean.class);
        } finally {
            if (old == null) {
                System.clearProperty(CodegenDebug.DUMP_DIR_PROPERTY);
            } else {
                System.setProperty(CodegenDebug.DUMP_DIR_PROPERTY, old);
            }
        }
        ObjectMapper mapper = newObjectMapper();
        assertEquals("{\"c\":2}", mapper.writeValueAsString(new LaterBean()));
        assertGeneratedWriter(mapper, LaterBean.class);
    }
}
