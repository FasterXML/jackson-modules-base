package tools.jackson.module.blackbird.deser;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.annotation.JacksonInject;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonMerge;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.InjectableValues;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.std.StdDeserializer;
import tools.jackson.module.blackbird.BlackbirdTestBase;

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

    @JsonFilter("f")
    public static class FilterBean {
        public int value;
    }

    // A builder with a creator constructor: the builder codec demotes
    @JsonDeserialize(builder = CreatorBuilt.Builder.class)
    public static class CreatorBuilt {
        final String name;
        final int n;

        CreatorBuilt(String name, int n) {
            this.name = name;
            this.n = n;
        }

        public static class Builder {
            private final String name;
            private int n;

            @JsonCreator
            public Builder(@JsonProperty("name") String name) {
                this.name = name;
            }

            public Builder withN(int n) {
                this.n = n;
                return this;
            }

            public CreatorBuilt build() {
                return new CreatorBuilt(name, n);
            }
        }
    }

    // Counts how often databind contextualizes it
    public static class CountingDeser extends StdDeserializer<String> {
        static final AtomicInteger CONTEXTUALIZED = new AtomicInteger();

        public CountingDeser() {
            super(String.class);
        }

        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext ctxt,
                BeanProperty property) {
            CONTEXTUALIZED.incrementAndGet();
            return this;
        }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            return p.getValueAsString();
        }
    }

    // Demoted (object id); the per-property ignoral contextualizes it into
    // a new instance
    @JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class)
    public static class CountedIdBean {
        @JsonDeserialize(using = CountingDeser.class)
        public String s;
    }

    public static class CountedHolder {
        @JsonIgnoreProperties("x")
        public CountedIdBean bean;
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

    // A demoted bean gets the stock (de)serializer itself from
    // contextualization, not the placeholder that stands in for it
    @Test
    public void testDemotedBeanUsesStockClasses() throws Exception {
        ObjectMapper mapper = newObjectMapper();
        assertSame(rootWriter(vanilla, FilterBean.class).getClass(),
                rootWriter(mapper, FilterBean.class).getClass());
        assertSame(rootReader(vanilla, IdBean.class).getClass(),
                rootReader(mapper, IdBean.class).getClass());
        assertSame(rootReader(vanilla, InjectRecord.class).getClass(),
                rootReader(mapper, InjectRecord.class).getClass());
        assertSame(rootReader(vanilla, CreatorBuilt.class).getClass(),
                rootReader(mapper, CreatorBuilt.class).getClass());

        CreatorBuilt built = mapper.readValue(a2q("{'name':'a','n':3}"), CreatorBuilt.class);
        assertEquals("a", built.name);
        assertEquals(3, built.n);
    }

    // Resolution already contextualized the demoted bean's properties, so a
    // later contextualization into a new instance must not redo it
    @Test
    public void testDemotedBeanPropertiesContextualizedAsStock() throws Exception {
        final String doc = a2q("{'bean':{'@id':1,'s':'v'}}");
        CountingDeser.CONTEXTUALIZED.set(0);
        assertEquals("v", newVanillaJSONMapper().readValue(doc, CountedHolder.class).bean.s);
        int stock = CountingDeser.CONTEXTUALIZED.get();

        CountingDeser.CONTEXTUALIZED.set(0);
        assertEquals("v", newObjectMapper().readValue(doc, CountedHolder.class).bean.s);
        assertEquals(stock, CountingDeser.CONTEXTUALIZED.get());
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
        assertTrue(hasGeneratedCodec(seen.get(AnySetterBean.class)),
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
        assertTrue(hasGeneratedCodec(seen.get(InjectBean.class)),
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
        assertTrue(hasGeneratedCodec(seen.get(InjectOverrideBean.class)),
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
