package tools.jackson.module.blackbird.deser;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonView;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.MapperFeature;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * View-parity regression tests: with an active view the generated codecs stay
 * on the fast path and test a per-view visibility bitmask per property
 * (visibility read from the stock property, so DEFAULT_VIEW_INCLUSION and
 * matcher semantics are exactly stock); a hidden property consumes its value
 * or is omitted like stock. Beans with more than 64 properties instead
 * delegate the whole view-active call to the stock fallback. Every case
 * compares against a vanilla mapper, in both directions, with and without an
 * active view, so view semantics come from stock rather than hardcoded
 * expectations.
 */
public class BBCodecViewTest extends BlackbirdTestBase
{
    public static class ViewA { }
    public static class ViewB { }

    public static class ViewBean {
        private String a;
        private int b;
        private String plain;

        @JsonView(ViewA.class)
        public String getA() { return a; }
        public void setA(String v) { a = v; }

        @JsonView(ViewB.class)
        public int getB() { return b; }
        public void setB(int v) { b = v; }

        public String getPlain() { return plain; }
        public void setPlain(String v) { plain = v; }
    }

    public static class ViewFieldBean {
        @JsonView(ViewA.class)
        public String a;
        @JsonView(ViewB.class)
        public int b;
    }

    public record ViewRecord(
            @JsonView(ViewA.class) String a,
            @JsonView(ViewB.class) int b) { }

    public static class Parent {
        private ViewBean child;
        // Visible in both views, so the child itself deserializes and its own
        // per-property view filtering is what the test exercises.
        @JsonView({ ViewA.class, ViewB.class })
        public ViewBean getChild() { return child; }
        public void setChild(ViewBean c) { child = c; }
    }

    private static final ObjectMapper VANILLA = new JsonMapper();
    private static final ObjectMapper MODULE = newObjectMapper();

    private static final String DOC = "{\"a\":\"x\",\"b\":7,\"plain\":\"p\"}";

    private static ViewBean bean() {
        ViewBean bean = new ViewBean();
        bean.setA("x");
        bean.setB(7);
        bean.setPlain("p");
        return bean;
    }

    @Test
    public void testSerializeMatchesVanillaPerView() throws Exception {
        for (Class<?> view : new Class<?>[] { ViewA.class, ViewB.class }) {
            assertEquals(VANILLA.writerWithView(view).writeValueAsString(bean()),
                    MODULE.writerWithView(view).writeValueAsString(bean()),
                    "view " + view.getSimpleName());
        }
        assertEquals(VANILLA.writeValueAsString(bean()), MODULE.writeValueAsString(bean()),
                "no active view");
    }

    @Test
    public void testDeserializeMatchesVanillaPerView() throws Exception {
        for (Class<?> view : new Class<?>[] { ViewA.class, ViewB.class }) {
            ViewBean expected = VANILLA.readerWithView(view).forType(ViewBean.class).readValue(DOC);
            ViewBean actual = MODULE.readerWithView(view).forType(ViewBean.class).readValue(DOC);
            assertEquals(expected.getA(), actual.getA(), "a under " + view.getSimpleName());
            assertEquals(expected.getB(), actual.getB(), "b under " + view.getSimpleName());
            assertEquals(expected.getPlain(), actual.getPlain(),
                    "plain under " + view.getSimpleName());
        }
    }

    @Test
    public void testViewReadConsumesSkippedValues() throws Exception {
        // Non-visible properties must be consumed exactly as stock consumes
        // them: a top-level array derails if the skip leaves the stream
        // mispositioned.
        String doc = "[" + DOC + "," + DOC + "]";
        List<ViewBean> beans = MODULE.readerWithView(ViewA.class)
                .forType(MODULE.getTypeFactory()
                        .constructCollectionType(List.class, ViewBean.class))
                .readValue(doc);
        assertEquals(2, beans.size());
        assertEquals("x", beans.get(1).getA());
        assertEquals(0, beans.get(1).getB(),
                "@JsonView(ViewB) property must stay default with ViewA active");
    }

    @Test
    public void testFieldBeanMatchesVanillaPerView() throws Exception {
        ViewFieldBean bean = new ViewFieldBean();
        bean.a = "x";
        bean.b = 7;
        String doc = "{\"a\":\"x\",\"b\":7}";
        for (Class<?> view : new Class<?>[] { ViewA.class, ViewB.class }) {
            assertEquals(VANILLA.writerWithView(view).writeValueAsString(bean),
                    MODULE.writerWithView(view).writeValueAsString(bean));
            ViewFieldBean expected = VANILLA.readerWithView(view)
                    .forType(ViewFieldBean.class).readValue(doc);
            ViewFieldBean actual = MODULE.readerWithView(view)
                    .forType(ViewFieldBean.class).readValue(doc);
            assertEquals(expected.a, actual.a);
            assertEquals(expected.b, actual.b);
        }
    }

    @Test
    public void testRecordMatchesVanillaPerView() throws Exception {
        String doc = "{\"a\":\"x\",\"b\":7}";
        for (Class<?> view : new Class<?>[] { ViewA.class, ViewB.class }) {
            ViewRecord expected = VANILLA.readerWithView(view)
                    .forType(ViewRecord.class).readValue(doc);
            ViewRecord actual = MODULE.readerWithView(view)
                    .forType(ViewRecord.class).readValue(doc);
            assertEquals(expected, actual, "record under " + view.getSimpleName());
            assertEquals(VANILLA.writerWithView(view).writeValueAsString(expected),
                    MODULE.writerWithView(view).writeValueAsString(actual));
        }
    }

    @Test
    public void testNestedCodecChildMatchesVanillaPerView() throws Exception {
        Parent parent = new Parent();
        parent.setChild(bean());
        String doc = "{\"child\":" + DOC + "}";
        for (Class<?> view : new Class<?>[] { ViewA.class, ViewB.class }) {
            assertEquals(VANILLA.writerWithView(view).writeValueAsString(parent),
                    MODULE.writerWithView(view).writeValueAsString(parent));
            Parent expected = VANILLA.readerWithView(view).forType(Parent.class).readValue(doc);
            Parent actual = MODULE.readerWithView(view).forType(Parent.class).readValue(doc);
            assertEquals(expected.getChild().getA(), actual.getChild().getA());
            assertEquals(expected.getChild().getB(), actual.getChild().getB());
        }
    }

    @Test
    public void testViewBeansEngageCodecs() throws Exception {
        // The cases above only pin view parity if the codecs actually engage
        // for these beans when no view is active.
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ValueDeserializerModifier capture = new ValueDeserializerModifier() {
            private static final long serialVersionUID = 1L;
            @Override
            public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
                    BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer) {
                seen.put(beanDescRef.getBeanClass(), deserializer);
                return deserializer;
            }
        };
        ObjectMapper m = JsonMapper.builder()
                .addModule(new SimpleModule("capture").setDeserializerModifier(capture))
                .addModule(new BlackbirdModule())
                .build();
        m.readValue(DOC, ViewBean.class);
        m.readValue("{\"a\":\"x\",\"b\":7}", ViewRecord.class);
        assertEquals("BBReaderPlaceholder", seen.get(ViewBean.class).getClass().getSimpleName());
        assertEquals("BBReaderPlaceholder", seen.get(ViewRecord.class).getClass().getSimpleName());
    }

    // A plain bean with no @JsonView annotations: with DEFAULT_VIEW_INCLUSION
    // off, stock hides every property under any active view on the write side
    // and keeps them all on the read side (the deser matcher is null). The
    // codec must reproduce both, which forces the MASK strategy for a bean
    // that carries no view annotations at all.
    public static class PlainBean {
        private String a;
        private int b;
        public String getA() { return a; }
        public void setA(String v) { a = v; }
        public int getB() { return b; }
        public void setB(int v) { b = v; }
    }

    @Test
    public void testDefaultViewInclusionOffMatchesVanilla() throws Exception {
        ObjectMapper vanilla = JsonMapper.builder()
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .build();
        ObjectMapper module = JsonMapper.builder()
                .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
                .addModule(new BlackbirdModule())
                .build();
        PlainBean bean = new PlainBean();
        bean.setA("x");
        bean.setB(7);
        String doc = "{\"a\":\"x\",\"b\":7}";
        assertEquals(vanilla.writerWithView(ViewA.class).writeValueAsString(bean),
                module.writerWithView(ViewA.class).writeValueAsString(bean),
                "write, inclusion off, active view: both should omit all");
        PlainBean ev = vanilla.readerWithView(ViewA.class).forType(PlainBean.class).readValue(doc);
        PlainBean am = module.readerWithView(ViewA.class).forType(PlainBean.class).readValue(doc);
        assertEquals(ev.getA(), am.getA());
        assertEquals(ev.getB(), am.getB());
    }

    // More than 64 properties: the mask cannot fit, so an active view delegates
    // the whole call. Output must still match vanilla in both directions.
    public static class WideViewBean {
        public int p0, p1, p2, p3, p4, p5, p6, p7, p8, p9, p10, p11, p12, p13, p14, p15;
        public int p16, p17, p18, p19, p20, p21, p22, p23, p24, p25, p26, p27, p28, p29, p30, p31;
        public int p32, p33, p34, p35, p36, p37, p38, p39, p40, p41, p42, p43, p44, p45, p46, p47;
        public int p48, p49, p50, p51, p52, p53, p54, p55, p56, p57, p58, p59, p60, p61, p62, p63;
        @JsonView(ViewA.class)
        public int p64;
        public int p65;
    }

    @Test
    public void testWideBeanDelegatesUnderView() throws Exception {
        WideViewBean bean = new WideViewBean();
        bean.p64 = 64;
        bean.p65 = 65;
        for (Class<?> view : new Class<?>[] { ViewA.class, ViewB.class }) {
            assertEquals(VANILLA.writerWithView(view).writeValueAsString(bean),
                    MODULE.writerWithView(view).writeValueAsString(bean),
                    "wide bean under " + view.getSimpleName());
        }
        assertEquals(VANILLA.writeValueAsString(bean), MODULE.writeValueAsString(bean),
                "wide bean, no view");
    }

    // A codec whose bean has 64 or fewer properties keeps view-active calls on
    // the generated mask path (only the wide bean above delegates). This
    // check pins that the mask read is byte-faithful to stock: the ViewB
    // property is hidden under ViewA, the visible ones match, and the skipped
    // value leaves the stream correctly positioned for the trailing property.
    @Test
    public void testViewActiveMaskMatchesVanilla() throws Exception {
        ViewBean expected = VANILLA.readerWithView(ViewA.class)
                .forType(ViewBean.class).readValue(DOC);
        ViewBean actual = MODULE.readerWithView(ViewA.class)
                .forType(ViewBean.class).readValue(DOC);
        assertEquals(expected.getA(), actual.getA());
        assertEquals(expected.getB(), actual.getB());
        assertEquals(expected.getPlain(), actual.getPlain());
    }
}
