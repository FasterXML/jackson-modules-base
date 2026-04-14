package tools.jackson.module.blackbird.inject;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ser.BeanPropertyWriter;

import static org.junit.jupiter.api.Assertions.*;

// Verifies Blackbird's serializer-side optimization runs end-to-end for all
// accessor-method specializations, and documents the parallel limitation with
// the deserializer side: direct public-field writers are not optimized either.
// BBSerializerModifier.createProperty skips properties whose backing JDK
// member isn't an AnnotatedMethod (see BBSerializerModifier.java lines
// 103-109), so public fields stay as plain BeanPropertyWriter instances.
public class SerializerInjectionTest extends BlackbirdInjectionTestBase
{
    public static class GetterSerPojo {
        private int intProp;
        private long longProp;
        private boolean boolProp;
        private String stringProp;
        private java.util.List<String> objectProp;

        public GetterSerPojo() { }

        public GetterSerPojo(int i, long l, boolean b, String s, java.util.List<String> o) {
            intProp = i; longProp = l; boolProp = b; stringProp = s; objectProp = o;
        }

        public int getIntProp() { return intProp; }
        public long getLongProp() { return longProp; }
        public boolean isBoolProp() { return boolProp; }
        public String getStringProp() { return stringProp; }
        public java.util.List<String> getObjectProp() { return objectProp; }
    }

    public static class FieldSerPojo {
        public int value;

        public FieldSerPojo() { }
        public FieldSerPojo(int v) { this.value = v; }
    }

    @Test
    public void testAllGetterBasedWritersOptimized() throws Exception
    {
        Harness h = newHarness();

        String json = h.mapper.writeValueAsString(
                new GetterSerPojo(1, 2L, true, "x", java.util.Arrays.asList("a", "b")));
        assertTrue(json.contains("\"intProp\":1"), json);
        assertTrue(json.contains("\"longProp\":2"), json);
        assertTrue(json.contains("\"boolProp\":true"), json);
        assertTrue(json.contains("\"stringProp\":\"x\""), json);
        assertTrue(json.contains("\"objectProp\":[\"a\",\"b\"]"), json);

        List<BeanPropertyWriter> writers = writersOf(h.serFor(GetterSerPojo.class));
        assertEquals(5, writers.size(), "expected 5 writers, got " + writers);
        for (BeanPropertyWriter w : writers) {
            assertTrue(isOptimizedWriter(w),
                    "ser writer '" + w.getName() + "' not optimized (is "
                            + w.getClass().getName() + "); BBSerializerModifier"
                            + " did not replace it with a Blackbird-generated writer");
        }
    }

    @Test
    public void testFieldBackedWriterNotOptimized() throws Exception
    {
        Harness h = newHarness();

        // Serialization still works end-to-end, just not via a Blackbird-generated accessor.
        String json = h.mapper.writeValueAsString(new FieldSerPojo(42));
        assertTrue(json.contains("\"value\":42"), json);

        List<BeanPropertyWriter> writers = writersOf(h.serFor(FieldSerPojo.class));
        assertEquals(1, writers.size());
        BeanPropertyWriter w = writers.get(0);
        assertFalse(isOptimizedWriter(w),
                "Blackbird unexpectedly optimized a field-backed writer '" + w.getName()
                        + "' (is " + w.getClass().getName() + "). If BBSerializerModifier"
                        + " has grown field-access support, update this test to assert"
                        + " the positive case instead.");
    }
}
