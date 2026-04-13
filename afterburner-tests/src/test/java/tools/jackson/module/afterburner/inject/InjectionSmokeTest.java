package tools.jackson.module.afterburner.inject;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.deser.SettableBeanProperty;

import static org.junit.jupiter.api.Assertions.*;

// High-level smoke test: serialize+deserialize a simple POJO with a mix of primitive
// and reference fields, and confirm that every property on both sides was replaced
// with an Afterburner-generated optimized variant. This transitively exercises
// MyClassLoader.defineClass, PropertyMutatorCollector / PropertyAccessorCollector,
// and the OptimizedSettableBeanProperty / OptimizedBeanPropertyWriter swap in
// ABDeserializerModifier / ABSerializerModifier.
public class InjectionSmokeTest extends AfterburnerInjectionTestBase
{
    public static class SimplePojo {
        public int intField;
        public long longField;
        public boolean boolField;
        public String stringField;

        public SimplePojo() { }

        public SimplePojo(int i, long l, boolean b, String s) {
            intField = i; longField = l; boolField = b; stringField = s;
        }
    }

    @Test
    public void testRoundTripWithFullInjection() throws Exception
    {
        Harness h = newHarness();
        String json = h.mapper.writeValueAsString(new SimplePojo(1, 2L, true, "x"));
        SimplePojo back = h.mapper.readValue(json, SimplePojo.class);

        assertEquals(1, back.intField);
        assertEquals(2L, back.longField);
        assertTrue(back.boolField);
        assertEquals("x", back.stringField);

        // Every property on the deserializer side should be Afterburner-optimized.
        SettableBeanProperty[] props = propsOf(h.deserFor(SimplePojo.class));
        assertEquals(4, props.length);
        for (SettableBeanProperty prop : props) {
            assertTrue(isOptimizedProperty(prop),
                    "deser property '" + prop.getName() + "' not optimized (is "
                            + prop.getClass().getName() + "); injection pipeline did not run");
        }

        // Every writer on the serializer side should be Afterburner-optimized.
        var writers = writersOf(h.serFor(SimplePojo.class));
        assertEquals(4, writers.size());
        for (var w : writers) {
            assertTrue(isOptimizedWriter(w),
                    "ser writer '" + w.getName() + "' not optimized (is "
                            + w.getClass().getName() + "); injection pipeline did not run");
        }
    }
}
