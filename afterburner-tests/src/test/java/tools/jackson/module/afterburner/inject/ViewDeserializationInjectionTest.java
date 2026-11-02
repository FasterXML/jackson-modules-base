package tools.jackson.module.afterburner.inject;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonView;

import tools.jackson.databind.deser.SettableBeanProperty;

import static org.junit.jupiter.api.Assertions.*;

// Verifies that JSON Views are honored by SuperSonic deserializers when properties
// use generated mutators: the in-tree Afterburner view tests run in a named module,
// where bytecode injection is disabled.
public class ViewDeserializationInjectionTest extends AfterburnerInjectionTestBase
{
    public static class ViewA { }
    public static class ViewB { }

    // Handled by `SuperSonicUnrolledDeserializer`
    public static class NarrowBean {
        @JsonView(ViewA.class)
        public int included;
        @JsonView(ViewB.class)
        public int excluded;

        private String _includedSetter;
        private String _excludedSetter;

        @JsonView(ViewA.class)
        public void setIncludedSetter(String v) { _includedSetter = v; }
        @JsonView(ViewB.class)
        public void setExcludedSetter(String v) { _excludedSetter = v; }
    }

    // Handled by `SuperSonicBeanDeserializer`
    public static class WideBean {
        @JsonView(ViewA.class)
        public int a;
        @JsonView(ViewB.class)
        public int b;
        @JsonView(ViewA.class)
        public long c;
        @JsonView(ViewB.class)
        public long d;
        @JsonView(ViewA.class)
        public boolean e;
        @JsonView(ViewB.class)
        public boolean f;
        @JsonView(ViewA.class)
        public String g;
        @JsonView(ViewB.class)
        public String h;
    }

    private final static String NARROW_JSON =
            "{\"included\":1,\"excluded\":2,\"includedSetter\":\"x\",\"excludedSetter\":\"y\"}";

    private final static String WIDE_JSON =
            "{\"a\":1,\"b\":2,\"c\":3,\"d\":4,\"e\":true,\"f\":true,\"g\":\"x\",\"h\":\"y\"}";

    private final Harness h = newHarness();

    @Test
    public void testNarrowRead() throws Exception
    {
        _verifyNarrow(h.mapper.readValue(NARROW_JSON, NarrowBean.class), false);
        _verifyNarrow(h.mapper.readerWithView(ViewA.class).forType(NarrowBean.class)
                .<NarrowBean>readValue(NARROW_JSON), true);
        _verifySetup(NarrowBean.class, "SuperSonicUnrolledDeserializer");
    }

    @Test
    public void testNarrowUpdate() throws Exception
    {
        NarrowBean bean = new NarrowBean();
        h.mapper.readerForUpdating(bean).readValue(NARROW_JSON);
        _verifyNarrow(bean, false);

        bean = new NarrowBean();
        h.mapper.readerWithView(ViewA.class).withValueToUpdate(bean).readValue(NARROW_JSON);
        _verifyNarrow(bean, true);
        _verifySetup(NarrowBean.class, "SuperSonicUnrolledDeserializer");
    }

    @Test
    public void testWideRead() throws Exception
    {
        _verifyWide(h.mapper.readValue(WIDE_JSON, WideBean.class), false);
        _verifyWide(h.mapper.readerWithView(ViewA.class).forType(WideBean.class)
                .<WideBean>readValue(WIDE_JSON), true);
        _verifySetup(WideBean.class, "SuperSonicBeanDeserializer");
    }

    @Test
    public void testWideUpdate() throws Exception
    {
        WideBean bean = new WideBean();
        h.mapper.readerForUpdating(bean).readValue(WIDE_JSON);
        _verifyWide(bean, false);

        bean = new WideBean();
        h.mapper.readerWithView(ViewA.class).withValueToUpdate(bean).readValue(WIDE_JSON);
        _verifyWide(bean, true);
        _verifySetup(WideBean.class, "SuperSonicBeanDeserializer");
    }

    private void _verifyNarrow(NarrowBean bean, boolean filtered)
    {
        assertEquals(1, bean.included);
        assertEquals("x", bean._includedSetter);
        assertEquals(filtered ? 0 : 2, bean.excluded);
        assertEquals(filtered ? null : "y", bean._excludedSetter);
    }

    private void _verifyWide(WideBean bean, boolean filtered)
    {
        assertEquals(1, bean.a);
        assertEquals(3L, bean.c);
        assertTrue(bean.e);
        assertEquals("x", bean.g);
        assertEquals(filtered ? 0 : 2, bean.b);
        assertEquals(filtered ? 0L : 4L, bean.d);
        assertEquals(!filtered, bean.f);
        assertEquals(filtered ? null : "y", bean.h);
    }

    // Tests would prove nothing about injection unless these hold
    private void _verifySetup(Class<?> beanClass, String expDeserializer)
    {
        assertTrue(afterburnerClassChainIncludes(h.deserFor(beanClass).getClass(), expDeserializer),
                "Wrong deserializer for "+beanClass.getSimpleName()+": "
                +h.deserFor(beanClass).getClass().getName());
        for (SettableBeanProperty prop : propsOf(h.deserFor(beanClass))) {
            assertTrue(isOptimizedProperty(prop),
                    "Property '"+prop.getName()+"' not optimized: "+prop.getClass().getName());
        }
    }
}
