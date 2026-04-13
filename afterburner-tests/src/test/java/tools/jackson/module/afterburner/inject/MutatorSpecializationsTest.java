package tools.jackson.module.afterburner.inject;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.deser.SettableBeanProperty;

import static org.junit.jupiter.api.Assertions.*;

// Verifies each of PropertyMutatorCollector's primitive-type specializations — int,
// long, boolean, and object/String — runs end-to-end for both public-field access
// and setter-method access. A regression in any individual code-generation path
// (e.g. a bad ByteBuddy template, a wrong descriptor, a missing bytecode branch)
// should surface here.
public class MutatorSpecializationsTest extends AfterburnerInjectionTestBase
{
    public static class IntFieldBean {
        public int value;
    }
    public static class LongFieldBean {
        public long value;
    }
    public static class BooleanFieldBean {
        public boolean value;
    }
    public static class StringFieldBean {
        public String value;
    }

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

    private final Harness h = newHarness();

    @Test
    public void testIntField() throws Exception {
        IntFieldBean b = h.mapper.readValue("{\"value\":42}", IntFieldBean.class);
        assertEquals(42, b.value);
        assertAllPropsOptimized(IntFieldBean.class);
    }

    @Test
    public void testLongField() throws Exception {
        LongFieldBean b = h.mapper.readValue("{\"value\":9999999999}", LongFieldBean.class);
        assertEquals(9999999999L, b.value);
        assertAllPropsOptimized(LongFieldBean.class);
    }

    @Test
    public void testBooleanField() throws Exception {
        BooleanFieldBean b = h.mapper.readValue("{\"value\":true}", BooleanFieldBean.class);
        assertTrue(b.value);
        assertAllPropsOptimized(BooleanFieldBean.class);
    }

    @Test
    public void testStringField() throws Exception {
        StringFieldBean b = h.mapper.readValue("{\"value\":\"hi\"}", StringFieldBean.class);
        assertEquals("hi", b.value);
        assertAllPropsOptimized(StringFieldBean.class);
    }

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

    private void assertAllPropsOptimized(Class<?> cls) {
        SettableBeanProperty[] props = propsOf(h.deserFor(cls));
        assertTrue(props.length > 0, "no properties for " + cls.getSimpleName());
        for (SettableBeanProperty p : props) {
            assertTrue(isOptimizedProperty(p),
                    cls.getSimpleName() + "." + p.getName() + " not optimized: "
                            + p.getClass().getName());
        }
    }
}
