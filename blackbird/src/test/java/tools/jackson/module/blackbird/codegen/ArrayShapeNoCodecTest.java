package tools.jackson.module.blackbird.codegen;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An array-shaped bean contextualizes to the stock as-array (de)serializer,
 * which never calls a codec, so none is generated for it. Generation is
 * observed through the class dump.
 */
public class ArrayShapeNoCodecTest extends BlackbirdTestBase
{
    @JsonFormat(shape = JsonFormat.Shape.ARRAY)
    @JsonPropertyOrder({ "a", "b" })
    public static class AsArray {
        public int a;
        public String b;
    }

    public static class AsObject {
        public int a;
        public String b;
    }

    @TempDir
    Path dumpDir;

    @Test
    public void arrayShapeGeneratesNoCodec() throws Exception {
        String old = System.setProperty(CodegenDebug.DUMP_DIR_PROPERTY, dumpDir.toString());
        try {
            ObjectMapper mapper = newObjectMapper();
            AsArray v = mapper.readValue("[1,\"x\"]", AsArray.class);
            assertEquals(1, v.a);
            assertEquals("x", v.b);
            assertEquals("[1,\"x\"]", mapper.writeValueAsString(v));
            mapper.readValue("{\"a\":1,\"b\":\"x\"}", AsObject.class);
            mapper.writeValueAsString(new AsObject());
        } finally {
            if (old == null) {
                System.clearProperty(CodegenDebug.DUMP_DIR_PROPERTY);
            } else {
                System.setProperty(CodegenDebug.DUMP_DIR_PROPERTY, old);
            }
        }
        assertFalse(Files.exists(dump(AsArray.class, "reader")));
        assertFalse(Files.exists(dump(AsArray.class, "writer")));
        assertTrue(Files.exists(dump(AsObject.class, "reader")));
        assertTrue(Files.exists(dump(AsObject.class, "writer")));
    }

    private Path dump(Class<?> type, String kind) {
        return dumpDir.resolve(type.getName() + "-" + kind + ".class");
    }
}
