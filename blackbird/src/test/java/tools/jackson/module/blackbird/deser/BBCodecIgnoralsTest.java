package tools.jackson.module.blackbird.deser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.exc.IgnoredPropertyException;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Ignoral parity: beans with {@code @JsonIgnoreProperties},
 * {@code @JsonIncludeProperties}, or {@code @JsonIgnore} now engage codecs,
 * and the unknown arm consults the ignoral sets in the stock loop's exact
 * order. Every case compares against vanilla.
 */
public class BBCodecIgnoralsTest extends BlackbirdTestBase
{
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LenientBean {
        public int i;
        public String s;
    }

    @JsonIgnoreProperties({ "secret" })
    public static class IgnoredSetBean {
        public int i;
        public String s;
    }

    @JsonIncludeProperties({ "i" })
    public static class IncludedSetBean {
        public int i;
        public String s;
    }

    public static class JsonIgnoreBean {
        public int i;
        @JsonIgnore
        public String hidden;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LenientRecord(String name, int count) { }

    private final ObjectMapper vanilla = newVanillaJSONMapper();

    private static String message(Exception e) {
        String m = e.getMessage();
        int nl = m.indexOf('\n');
        return (nl < 0) ? m : m.substring(0, nl);
    }

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
        return tools.jackson.databind.json.JsonMapper.builder()
                .addModule(capture)
                .addModule(new BlackbirdModule())
                .build();
    }

    private static void assertEngaged(Map<Class<?>, ValueDeserializer<?>> seen, Class<?> cls) {
        ValueDeserializer<?> d = seen.get(cls);
        assertNotNull(d, "no deserializer captured for " + cls);
        assertEquals("BBReaderPlaceholder", d.getClass().getSimpleName(),
                cls.getSimpleName() + " did not engage a codec");
    }

    @Test
    public void testIgnoreUnknownSkips() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'i':1,'nope':{'deep':[1,2]},'s':'x','more':true}");
        LenientBean v = vanilla.readValue(doc, LenientBean.class);
        LenientBean m = mapper.readValue(doc, LenientBean.class);
        assertEngaged(seen, LenientBean.class);
        assertEquals(v.i, m.i);
        assertEquals(v.s, m.s);
    }

    @Test
    public void testIgnoreUnknownBeatsStrictPerCall() throws Exception {
        // Stock skips silently under ignoreUnknown even when
        // FAIL_ON_UNKNOWN_PROPERTIES is enabled per call.
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'i':1,'nope':true}");
        LenientBean v = vanilla.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .forType(LenientBean.class).readValue(doc);
        LenientBean m = mapper.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .forType(LenientBean.class).readValue(doc);
        assertEngaged(seen, LenientBean.class);
        assertEquals(v.i, m.i);
    }

    @Test
    public void testIgnoredSetSkipsAndFailOnIgnored() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'i':1,'secret':'boo','s':'x'}");
        IgnoredSetBean v = vanilla.readValue(doc, IgnoredSetBean.class);
        IgnoredSetBean m = mapper.readValue(doc, IgnoredSetBean.class);
        assertEngaged(seen, IgnoredSetBean.class);
        assertEquals(v.i, m.i);
        assertEquals(v.s, m.s);

        IgnoredPropertyException ve = assertThrows(IgnoredPropertyException.class,
                () -> vanilla.reader(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                        .forType(IgnoredSetBean.class).readValue(doc));
        IgnoredPropertyException me = assertThrows(IgnoredPropertyException.class,
                () -> mapper.reader(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                        .forType(IgnoredSetBean.class).readValue(doc));
        assertEquals(message(ve), message(me));
    }

    @Test
    public void testIncludedSetIgnoresOthers() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'i':1,'s':'x'}");
        IncludedSetBean v = vanilla.readValue(doc, IncludedSetBean.class);
        IncludedSetBean m = mapper.readValue(doc, IncludedSetBean.class);
        assertEngaged(seen, IncludedSetBean.class);
        assertEquals(v.i, m.i);
        assertEquals(v.s, m.s);
        assertNull(m.s);
    }

    @Test
    public void testJsonIgnoredNameInDocument() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'i':1,'hidden':'boo'}");
        JsonIgnoreBean v = vanilla.readValue(doc, JsonIgnoreBean.class);
        JsonIgnoreBean m = mapper.readValue(doc, JsonIgnoreBean.class);
        assertEngaged(seen, JsonIgnoreBean.class);
        assertEquals(v.i, m.i);
        assertNull(m.hidden);
    }

    @Test
    public void testUnknownStillReportedWithIgnoredSet() throws Exception {
        // An unknown name outside the ignored set still runs the stock
        // unknown handling (throws under per-call FAIL_ON_UNKNOWN).
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'i':1,'other':true}");
        Exception ve = assertThrows(Exception.class,
                () -> vanilla.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .forType(IgnoredSetBean.class).readValue(doc));
        Exception me = assertThrows(Exception.class,
                () -> mapper.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                        .forType(IgnoredSetBean.class).readValue(doc));
        assertEngaged(seen, IgnoredSetBean.class);
        assertEquals(ve.getClass(), me.getClass());
        assertEquals(message(ve), message(me));
    }

    @Test
    public void testIgnoreUnknownRecord() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'name':'a','junk':[1,{'x':2}],'count':3}");
        assertEquals(vanilla.readValue(doc, LenientRecord.class),
                mapper.readValue(doc, LenientRecord.class));
        assertEngaged(seen, LenientRecord.class);
    }
}
