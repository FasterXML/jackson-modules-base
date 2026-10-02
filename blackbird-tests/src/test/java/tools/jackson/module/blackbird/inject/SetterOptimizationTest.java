package tools.jackson.module.blackbird.inject;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// End-to-end verification that Blackbird's codec generation engages for setter
// POJOs loaded from the unnamed module (classpath). The beans cover the
// generator's scalar kinds (int, long, boolean, String) plus a non-scalar
// setter that rides the codec's stock-property arm.
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
        assertCodecEngaged(IntSetterBean.class);
    }

    @Test
    public void testLongSetter() throws Exception {
        LongSetterBean b = h.mapper.readValue("{\"value\":9999999999}", LongSetterBean.class);
        assertEquals(9999999999L, b.getValue());
        assertCodecEngaged(LongSetterBean.class);
    }

    @Test
    public void testBooleanSetter() throws Exception {
        BooleanSetterBean b = h.mapper.readValue("{\"value\":true}", BooleanSetterBean.class);
        assertTrue(b.isValue());
        assertCodecEngaged(BooleanSetterBean.class);
    }

    @Test
    public void testStringSetter() throws Exception {
        StringSetterBean b = h.mapper.readValue("{\"value\":\"hi\"}", StringSetterBean.class);
        assertEquals("hi", b.getValue());
        assertCodecEngaged(StringSetterBean.class);
    }

    @Test
    public void testObjectSetter() throws Exception {
        ObjectSetterBean b = h.mapper.readValue("{\"value\":[\"a\",\"b\"]}", ObjectSetterBean.class);
        assertEquals(2, b.getValue().size());
        assertEquals("a", b.getValue().get(0));
        assertCodecEngaged(ObjectSetterBean.class);
    }

    private void assertCodecEngaged(Class<?> cls) {
        assertTrue(isBlackbirdReader(h.deserFor(cls)),
                cls.getSimpleName() + " did not engage a Blackbird codec: "
                        + h.deserFor(cls).getClass().getName());
    }
}
