package tools.jackson.module.blackbird.deser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.InjectableValues;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Documented demotions: beans whose features the generator does not model
 * (object ids, per-property merge, injectables on records) stay on the stock
 * deserializer and behave exactly like vanilla. These pins keep the demotions
 * deliberate; if the generator grows support, the engagement assertion flips
 * - as it did for any-setter beans and injectable POJOs, whose engagement
 * pins live here too.
 */
public class BBCodecDemotionTest extends BlackbirdTestBase
{
    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class)
    public static class IdBean {
        public int value;
        public IdBean next;
    }

    public static class MergeTarget {
        public int a = 1;
        public int b = 2;
    }

    public static class MergeBean {
        @JsonMerge
        public MergeTarget child = new MergeTarget();
    }

    public static class AnySetterBean {
        public int known;
        public Map<String, Object> rest = new LinkedHashMap<>();

        @JsonAnySetter
        public void any(String name, Object value) {
            rest.put(name, value);
        }
    }

    public static class InjectBean {
        public int i;
        @JacksonInject("who")
        public String who;
    }

    public static class InjectOverrideBean {
        public int i;
        @JacksonInject("who")
        public String who;
        @JacksonInject("count")
        public int count;
    }

    public record InjectRecord(String name, @JacksonInject("who") String who) { }

    private final ObjectMapper vanilla = newVanillaJSONMapper();

    private static ObjectMapper capturingMapper(Map<Class<?>, ValueDeserializer<?>> seen) {
        SimpleModule capture = new SimpleModule("capture") {
            private static final long serialVersionUID = 1L;
            @Override
            public void setupModule(SetupContext ctxt) {
                super.setupModule(ctxt);
                ctxt.addDeserializerModifier(new ValueDeserializerModifier() {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public ValueDeserializer<?> modifyDeserializer(DeserializationConfig cfg,
                            BeanDescription.Supplier ref, ValueDeserializer<?> d) {
                        seen.put(ref.getBeanClass(), d);
                        return d;
                    }
                });
            }
        };
        return JsonMapper.builder()
                .addModule(capture)
                .addModule(new BlackbirdModule())
                .build();
    }

    // Stock comes in two shapes: the modifier demoted (raw stock captured),
    // or the factory gated at resolve time (placeholder captured, codec never
    // generated). Both mean every call runs the stock deserializer.
    private static void assertStock(Map<Class<?>, ValueDeserializer<?>> seen, Class<?> cls)
            throws Exception {
        ValueDeserializer<?> d = seen.get(cls);
        assertNotNull(d, "no deserializer captured for " + cls);
        if (d instanceof BBReaderPlaceholder placeholder) {
            java.lang.reflect.Field f = BBReaderPlaceholder.class.getDeclaredField("_codec");
            f.setAccessible(true);
            assertNull(f.get(placeholder),
                    cls.getSimpleName() + " unexpectedly generated a codec; if support"
                            + " was added deliberately, flip this pin to assert engagement");
        }
    }

    @Test
    public void testObjectIdBeanStaysStock() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'@id':1,'value':5,'next':1}");
        IdBean v = vanilla.readValue(doc, IdBean.class);
        IdBean m = mapper.readValue(doc, IdBean.class);
        assertStock(seen, IdBean.class);
        assertEquals(v.value, m.value);
        assertSame(m, m.next);
    }

    @Test
    public void testMergePropertyBeanStaysStock() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'child':{'a':9}}");
        MergeBean v = vanilla.readValue(doc, MergeBean.class);
        MergeBean m = mapper.readValue(doc, MergeBean.class);
        assertStock(seen, MergeBean.class);
        assertEquals(v.child.a, m.child.a);
        assertEquals(v.child.b, m.child.b);
        assertEquals(2, m.child.b);
    }

    @Test
    public void testAnySetterBeanEngages() throws Exception {
        // Widened from a demotion pin: the unknown arm feeds the stock
        // any-setter, so these beans accelerate (BBCodecAnySetterTest has the
        // full parity matrix; records with an any-setter still demote).
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'known':1,'x':'a','y':2}");
        AnySetterBean v = vanilla.readValue(doc, AnySetterBean.class);
        AnySetterBean m = mapper.readValue(doc, AnySetterBean.class);
        assertEquals("BBReaderPlaceholder",
                seen.get(AnySetterBean.class).getClass().getSimpleName(),
                "any-setter bean did not engage a codec");
        assertEquals(v.known, m.known);
        assertEquals(v.rest, m.rest);
        assertEquals(2, m.rest.size());
    }

    @Test
    public void testInjectableBeanEngages() throws Exception {
        // Widened from a demotion pin: injected values apply through the base
        // helper right after construction, the stock placement.
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper base = capturingMapper(seen);
        InjectableValues inject =
                new InjectableValues.Std().addValue("who", "injected");
        String doc = a2q("{'i':7}");
        InjectBean v = vanilla.reader(inject).forType(InjectBean.class).readValue(doc);
        InjectBean m = base.reader(inject).forType(InjectBean.class).readValue(doc);
        assertEquals("BBReaderPlaceholder",
                seen.get(InjectBean.class).getClass().getSimpleName(),
                "injectable bean did not engage a codec");
        assertEquals(v.i, m.i);
        assertEquals(v.who, m.who);
        assertEquals("injected", m.who);
    }

    @Test
    public void testDocumentOverridesInjectedValue() throws Exception {
        // Injection runs before the property loop, so a document value wins -
        // exactly like stock.
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper base = capturingMapper(seen);
        InjectableValues inject = new InjectableValues.Std()
                .addValue("who", "injected").addValue("count", 41);
        String doc = a2q("{'i':7,'who':'from-doc'}");
        InjectOverrideBean v = vanilla.reader(inject)
                .forType(InjectOverrideBean.class).readValue(doc);
        InjectOverrideBean m = base.reader(inject)
                .forType(InjectOverrideBean.class).readValue(doc);
        assertEquals("BBReaderPlaceholder",
                seen.get(InjectOverrideBean.class).getClass().getSimpleName(),
                "injectable bean did not engage a codec");
        assertEquals(v.who, m.who);
        assertEquals("from-doc", m.who);
        assertEquals(v.count, m.count);
        assertEquals(41, m.count);
    }

    @Test
    public void testInjectableRecordStaysStock() throws Exception {
        // Record codecs have no instance until the end of the document, so
        // injection cannot take its stock place; records with injectables
        // demote.
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper base = capturingMapper(seen);
        InjectableValues inject =
                new InjectableValues.Std().addValue("who", "injected");
        String doc = a2q("{'name':'a'}");
        InjectRecord v = vanilla.reader(inject).forType(InjectRecord.class).readValue(doc);
        InjectRecord m = base.reader(inject).forType(InjectRecord.class).readValue(doc);
        assertStock(seen, InjectRecord.class);
        assertEquals(v, m);
        assertEquals("injected", m.who());
    }
}
