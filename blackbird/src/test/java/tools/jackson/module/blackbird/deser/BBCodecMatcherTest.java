package tools.jackson.module.blackbird.deser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Name-matching parity: case-insensitive mappers use the core CI matcher
 * instead of demoting, and property aliases map to the same generated arm the
 * way stock BeanPropertyMap appends them. Every case compares against vanilla.
 */
public class BBCodecMatcherTest extends BlackbirdTestBase
{
    public static class AliasBean {
        @JsonAlias({ "identifier", "the_id" })
        public int id;
        public String name;
    }

    public record AliasRecord(@JsonAlias("nm") @JsonProperty(required = true) String name,
            int count) { }

    public static class PlainBean {
        public int count;
        public String label;
    }

    @JsonFormat(with = JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
    public static class FormatCIBean {
        public int count;
        public String label;
    }

    private final ObjectMapper vanilla = newVanillaJSONMapper();

    private static ObjectMapper capturingMapper(Map<Class<?>, ValueDeserializer<?>> seen,
            boolean caseInsensitive) {
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
        JsonMapper.Builder b =
                JsonMapper.builder()
                        .addModule(capture)
                        .addModule(new BlackbirdModule());
        if (caseInsensitive) {
            b = b.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
        }
        return b.build();
    }

    private static void assertEngaged(Map<Class<?>, ValueDeserializer<?>> seen, Class<?> cls) {
        ValueDeserializer<?> d = seen.get(cls);
        assertNotNull(d, "no deserializer captured for " + cls);
        assertEquals("BBReaderPlaceholder", d.getClass().getSimpleName(),
                cls.getSimpleName() + " did not engage a codec");
    }

    @Test
    public void testCaseInsensitiveMapper() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen, true);
        ObjectMapper vci = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .build();
        String doc = a2q("{'COUNT':7,'Label':'x'}");
        PlainBean v = vci.readValue(doc, PlainBean.class);
        PlainBean m = mapper.readValue(doc, PlainBean.class);
        assertEngaged(seen, PlainBean.class);
        assertEquals(v.count, m.count);
        assertEquals(v.label, m.label);
        assertEquals(7, m.count);
    }

    @Test
    public void testCaseInsensitiveUnknownStillUnknown() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen, true);
        String doc = a2q("{'COUNT':7,'zzz':1}");
        PlainBean m = mapper.readValue(doc, PlainBean.class);
        assertEquals(7, m.count);
        assertEngaged(seen, PlainBean.class);
    }

    @Test
    public void testAliasNamesHitTheSameArm() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen, false);
        for (String key : new String[] { "id", "identifier", "the_id" }) {
            String doc = a2q("{'" + key + "':42,'name':'x'}");
            AliasBean v = vanilla.readValue(doc, AliasBean.class);
            AliasBean m = mapper.readValue(doc, AliasBean.class);
            assertEquals(v.id, m.id, "via " + key);
            assertEquals(42, m.id, "via " + key);
            assertEquals("x", m.name);
        }
        assertEngaged(seen, AliasBean.class);
    }

    @Test
    public void testAliasSatisfiesRequiredRecordComponent() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen, false);
        String doc = a2q("{'nm':'a','count':3}");
        assertEquals(vanilla.readValue(doc, AliasRecord.class),
                mapper.readValue(doc, AliasRecord.class));
        assertEngaged(seen, AliasRecord.class);

        assertThrows(MismatchedInputException.class,
                () -> mapper.readValue(a2q("{'count':3}"), AliasRecord.class));
    }

    @Test
    public void testAliasUnderCaseInsensitiveMapper() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen, true);
        ObjectMapper vci = JsonMapper.builder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .build();
        String doc = a2q("{'IDENTIFIER':42,'NAME':'x'}");
        AliasBean v = vci.readValue(doc, AliasBean.class);
        AliasBean m = mapper.readValue(doc, AliasBean.class);
        assertEngaged(seen, AliasBean.class);
        assertEquals(v.id, m.id);
        assertEquals(v.name, m.name);
    }

    @Test
    public void testClassLevelFormatCaseInsensitivity() throws Exception {
        // Class-level @JsonFormat CI arrives through createContextual; the
        // placeholder falls back to the stock contextual instance, so behavior
        // must match vanilla whichever path serves it.
        ObjectMapper mapper = newObjectMapper();
        String doc = a2q("{'CoUnT':5,'LABEL':'y'}");
        FormatCIBean v = vanilla.readValue(doc, FormatCIBean.class);
        FormatCIBean m = mapper.readValue(doc, FormatCIBean.class);
        assertEquals(v.count, m.count);
        assertEquals(v.label, m.label);
    }
}
