package tools.jackson.module.blackbird.deser;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.exc.IgnoredPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Any-setter parity: beans with {@code @JsonAnySetter} engage codecs, and the
 * unknown arm feeds the stock any-setter in handleUnknownVanilla's exact
 * order (explicit ignorals first, then the any-setter, which also consumes
 * names that ignoreUnknown would otherwise skip). Every case compares against
 * vanilla. Records with an any-setter stay on the stock path: their values
 * buffer before construction, which the typed-locals loop does not model.
 */
public class BBCodecAnySetterTest extends BlackbirdTestBase
{
    public static class AnyBean {
        public int i;
        public String s;
        public Map<String, Object> extra = new HashMap<>();

        @JsonAnySetter
        public void set(String name, Object value) {
            extra.put(name, value);
        }
    }

    @JsonIgnoreProperties(value = { "secret" }, ignoreUnknown = true)
    public static class AnyWithIgnoralsBean {
        public int i;
        public Map<String, Object> extra = new HashMap<>();

        @JsonAnySetter
        public void set(String name, Object value) {
            extra.put(name, value);
        }
    }

    public static class AnyFieldBean {
        public int i;

        @JsonAnySetter
        public Map<String, Object> extra = new HashMap<>();
    }

    public record AnyRecord(String name) {
        @JsonAnySetter
        public void set(String n, Object v) { }
    }

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

    private static void assertEngaged(Map<Class<?>, ValueDeserializer<?>> seen, Class<?> cls,
            boolean engaged) {
        ValueDeserializer<?> d = seen.get(cls);
        assertNotNull(d, "no deserializer captured for " + cls);
        assertEquals(engaged, "BBReaderPlaceholder".equals(d.getClass().getSimpleName()),
                cls.getSimpleName() + " engagement");
    }

    @Test
    public void testAnySetterCollectsUnknowns() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'i':1,'x':'a','s':'known','deep':{'k':[1,2]},'n':null}");
        AnyBean v = vanilla.readValue(doc, AnyBean.class);
        AnyBean m = mapper.readValue(doc, AnyBean.class);
        assertEngaged(seen, AnyBean.class, true);
        assertEquals(v.i, m.i);
        assertEquals(v.s, m.s);
        assertEquals(v.extra, m.extra);
    }

    @Test
    public void testAnySetterBeatsStrictUnknowns() throws Exception {
        // With an any-setter, nothing is "unknown": per-call
        // FAIL_ON_UNKNOWN_PROPERTIES never fires, exactly like stock.
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'i':1,'x':'a'}");
        AnyBean v = vanilla.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .forType(AnyBean.class).readValue(doc);
        AnyBean m = mapper.reader(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .forType(AnyBean.class).readValue(doc);
        assertEngaged(seen, AnyBean.class, true);
        assertEquals(v.extra, m.extra);
    }

    @Test
    public void testIgnoredNameNeverReachesAnySetter() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'i':1,'secret':'boo','x':'a'}");
        AnyWithIgnoralsBean v = vanilla.readValue(doc, AnyWithIgnoralsBean.class);
        AnyWithIgnoralsBean m = mapper.readValue(doc, AnyWithIgnoralsBean.class);
        assertEngaged(seen, AnyWithIgnoralsBean.class, true);
        assertEquals(v.i, m.i);
        assertEquals(v.extra, m.extra);
        assertFalse(m.extra.containsKey("secret"));

        IgnoredPropertyException ve = assertThrows(IgnoredPropertyException.class,
                () -> vanilla.reader(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                        .forType(AnyWithIgnoralsBean.class).readValue(doc));
        IgnoredPropertyException me = assertThrows(IgnoredPropertyException.class,
                () -> mapper.reader(DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
                        .forType(AnyWithIgnoralsBean.class).readValue(doc));
        assertEquals(ve.getPropertyName(), me.getPropertyName());
    }

    @Test
    public void testAnySetterField() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'i':2,'a':1,'b':'two'}");
        AnyFieldBean v = vanilla.readValue(doc, AnyFieldBean.class);
        AnyFieldBean m = mapper.readValue(doc, AnyFieldBean.class);
        assertEngaged(seen, AnyFieldBean.class, true);
        assertEquals(v.i, m.i);
        assertEquals(v.extra, m.extra);
    }

    @Test
    public void testRecordWithAnySetterStaysStock() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        String doc = a2q("{'name':'a','other':1}");
        AnyRecord v = vanilla.readValue(doc, AnyRecord.class);
        AnyRecord m = mapper.readValue(doc, AnyRecord.class);
        assertEngaged(seen, AnyRecord.class, false);
        assertEquals(v, m);
    }
}
