package tools.jackson.module.blackbird.inject;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.deser.SettableBeanProperty;

import static org.junit.jupiter.api.Assertions.*;

// End-to-end verification that Blackbird's setter optimization runs on POJOs
// loaded from the unnamed module (classpath). The specializations tested here
// correspond to BBDeserializerModifier's four primitive branches (int, long,
// boolean, object/String) plus the object/non-String branch.
//
// Note: Blackbird does NOT optimize direct public-field access — only setter
// methods. See FieldAccessNotOptimizedTest for the documented negative case.
public class SetterOptimizationTest extends BlackbirdInjectionTestBase
{
    public static class IntSetterBean {
        private int value;
        public int getValue() { return value; }
        public void setValue(int v) { this.value = v; }
    }
    public static class LongSetterBean {
        private long value;
        public long getValue() { return value; }
        public void setValue(long v) { this.value = v; }
    }
    public static class BooleanSetterBean {
        private boolean value;
        public boolean isValue() { return value; }
        public void setValue(boolean v) { this.value = v; }
    }
    public static class StringSetterBean {
        private String value;
        public String getValue() { return value; }
        public void setValue(String v) { this.value = v; }
    }
    public static class ObjectSetterBean {
        private java.util.List<String> value;
        public java.util.List<String> getValue() { return value; }
        public void setValue(java.util.List<String> v) { this.value = v; }
    }

    private final Harness h = newHarness();

    @Test
    public void testIntSetter() throws Exception {
        IntSetterBean b = h.mapper.readValue("{\"value\":42}", IntSetterBean.class);
        assertEquals(42, b.getValue());
        assertAllPropsOptimized(IntSetterBean.class);
    }

    @Test
    public void testLongSetter() throws Exception {
        LongSetterBean b = h.mapper.readValue("{\"value\":9999999999}", LongSetterBean.class);
        assertEquals(9999999999L, b.getValue());
        assertAllPropsOptimized(LongSetterBean.class);
    }

    @Test
    public void testBooleanSetter() throws Exception {
        BooleanSetterBean b = h.mapper.readValue("{\"value\":true}", BooleanSetterBean.class);
        assertTrue(b.isValue());
        assertAllPropsOptimized(BooleanSetterBean.class);
    }

    @Test
    public void testStringSetter() throws Exception {
        StringSetterBean b = h.mapper.readValue("{\"value\":\"hi\"}", StringSetterBean.class);
        assertEquals("hi", b.getValue());
        assertAllPropsOptimized(StringSetterBean.class);
    }

    @Test
    public void testObjectSetter() throws Exception {
        ObjectSetterBean b = h.mapper.readValue("{\"value\":[\"a\",\"b\"]}", ObjectSetterBean.class);
        assertEquals(2, b.getValue().size());
        assertEquals("a", b.getValue().get(0));
        assertAllPropsOptimized(ObjectSetterBean.class);
    }

    private void assertAllPropsOptimized(Class<?> cls) {
        SettableBeanProperty[] props = propsOf(h.deserFor(cls));
        assertTrue(props.length > 0, "no properties for " + cls.getSimpleName());
        for (SettableBeanProperty p : props) {
            assertTrue(isOptimizedProperty(p),
                    cls.getSimpleName() + "." + p.getName() + " not optimized: "
                            + p.getClass().getName()
                            + " — BBDeserializerModifier did not install a SettableXProperty");
        }
    }
}
