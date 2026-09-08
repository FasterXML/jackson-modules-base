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
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * View-parity regression tests: an active view makes the generated codecs
 * delegate the whole call to the stock fallback, whose own view machinery
 * (filtered writers, visibleInView checks) then applies. Every case compares
 * against a vanilla mapper, in both directions, with and without an active
 * view, so DEFAULT_VIEW_INCLUSION semantics come from stock rather than
 * hardcoded expectations.
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
        assertEquals("BBCodecPlaceholder", seen.get(ViewBean.class).getClass().getSimpleName());
        assertEquals("BBCodecPlaceholder", seen.get(ViewRecord.class).getClass().getSimpleName());
    }
}
