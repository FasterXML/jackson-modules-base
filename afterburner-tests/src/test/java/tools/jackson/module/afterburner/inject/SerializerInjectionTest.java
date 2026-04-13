package tools.jackson.module.afterburner.inject;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ser.BeanPropertyWriter;

import static org.junit.jupiter.api.Assertions.*;

// Verifies Afterburner's serializer-side bytecode injection runs end-to-end:
// PropertyAccessorCollector replaces each BeanPropertyWriter with an
// OptimizedBeanPropertyWriter subclass whose generated accessor reads the field
// directly instead of going through reflection. This is the only test in this
// module that touches the serializer side; deser-side injection is covered by
// MutatorSpecializationsTest.
public class SerializerInjectionTest extends AfterburnerInjectionTestBase
{
    public static class SerPojo {
        public int intField;
        public long longField;
        public boolean boolField;
        public String stringField;

        public SerPojo() { }

        public SerPojo(int i, long l, boolean b, String s) {
            intField = i; longField = l; boolField = b; stringField = s;
        }
    }

    @Test
    public void testWriterInjectionRuns() throws Exception
    {
        Harness h = newHarness();

        // Emit JSON through the optimized serializer and sanity-check the output.
        String json = h.mapper.writeValueAsString(new SerPojo(1, 2L, true, "x"));
        assertTrue(json.contains("\"intField\":1"));
        assertTrue(json.contains("\"longField\":2"));
        assertTrue(json.contains("\"boolField\":true"));
        assertTrue(json.contains("\"stringField\":\"x\""));

        // Every writer on the serializer side should be Afterburner-optimized.
        List<BeanPropertyWriter> writers = writersOf(h.serFor(SerPojo.class));
        assertEquals(4, writers.size());
        for (BeanPropertyWriter w : writers) {
            assertTrue(isOptimizedWriter(w),
                    "ser writer '" + w.getName() + "' not optimized (is "
                            + w.getClass().getName() + "); PropertyAccessorCollector"
                            + " did not run on this POJO");
        }
    }
}
