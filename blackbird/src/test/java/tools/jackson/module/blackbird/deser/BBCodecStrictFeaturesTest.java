package tools.jackson.module.blackbird.deser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Strict-feature parity with vanilla, both per-call and enabled at build:
 * FAIL_ON_UNKNOWN_PROPERTIES, FAIL_ON_MISSING_CREATOR_PROPERTIES, and
 * FAIL_ON_NULL_CREATOR_PROPERTIES. Per-call cases warm the codec with a
 * default read first, so the strict read runs through the cached generated
 * codec rather than deciding generation. Every case compares against vanilla.
 */
public class BBCodecStrictFeaturesTest extends BlackbirdTestBase
{
    public static class SimpleBean {
        public int i;
        public String s;
    }

    public record RefRecord(String name, int count) { }

    public record ReqRecord(@JsonProperty(required = true) String name, int count) { }

    private final ObjectMapper vanilla = newVanillaJSONMapper();

    private static String message(Exception e) {
        String m = e.getMessage();
        int nl = m.indexOf('\n');
        return (nl < 0) ? m : m.substring(0, nl);
    }

    /** Blackbird mapper with a capture modifier, so engagement is assertable. */
    private static ObjectMapper capturingMapper(Map<Class<?>, ValueDeserializer<?>> seen,
            DeserializationFeature... enable) {
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
        for (DeserializationFeature f : enable) {
            b = b.enable(f);
        }
        return b.build();
    }

    private static void assertEngaged(Map<Class<?>, ValueDeserializer<?>> seen, Class<?> cls) {
        ValueDeserializer<?> d = seen.get(cls);
        assertNotNull(d, "no deserializer captured for " + cls);
        assertEquals("BBReaderPlaceholder", d.getClass().getSimpleName(),
                cls.getSimpleName() + " did not engage a codec");
    }

    // FAIL_ON_UNKNOWN_PROPERTIES enabled at build time: the bean still
    // engages a codec, and the unknown property throws exactly like vanilla.

    @Test
    public void testBuildTimeFailOnUnknown() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper strict = capturingMapper(seen,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        ObjectMapper vstrict = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();

        SimpleBean ok = strict.readValue(a2q("{'i':1,'s':'x'}"), SimpleBean.class);
        assertEquals(1, ok.i);
        assertEngaged(seen, SimpleBean.class);

        String doc = a2q("{'i':1,'nope':true,'s':'x'}");
        UnrecognizedPropertyException ve = assertThrows(UnrecognizedPropertyException.class,
                () -> vstrict.readValue(doc, SimpleBean.class));
        UnrecognizedPropertyException me = assertThrows(UnrecognizedPropertyException.class,
                () -> strict.readValue(doc, SimpleBean.class));
        assertEquals(message(ve), message(me));
        assertEquals(ve.getPath().toString(), me.getPath().toString());
    }

    // FAIL_ON_MISSING_CREATOR_PROPERTIES

    @Test
    public void testBuildTimeFailOnMissingCreator() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper strict = capturingMapper(seen,
                DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        String doc = a2q("{'name':'x'}");
        MismatchedInputException ve = assertThrows(MismatchedInputException.class,
                () -> vanilla.reader(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                        .forType(RefRecord.class).readValue(doc));
        MismatchedInputException me = assertThrows(MismatchedInputException.class,
                () -> strict.readValue(doc, RefRecord.class));
        assertEquals(ve.getClass(), me.getClass());
        assertEngaged(seen, RefRecord.class);
    }

    @Test
    public void testPerCallFailOnMissingCreator() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        // Warm: the codec generates under default config and stays cached.
        assertEquals(new RefRecord("a", 1),
                mapper.readValue(a2q("{'name':'a','count':1}"), RefRecord.class));
        assertEngaged(seen, RefRecord.class);

        String doc = a2q("{'name':'x'}");
        MismatchedInputException ve = assertThrows(MismatchedInputException.class,
                () -> vanilla.reader(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                        .forType(RefRecord.class).readValue(doc));
        MismatchedInputException me = assertThrows(MismatchedInputException.class,
                () -> mapper.reader(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                        .forType(RefRecord.class).readValue(doc));
        assertEquals(ve.getClass(), me.getClass());
    }

    // FAIL_ON_NULL_CREATOR_PROPERTIES

    @Test
    public void testPerCallFailOnNullCreatorExplicitNull() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        assertEquals(new RefRecord("a", 1),
                mapper.readValue(a2q("{'name':'a','count':1}"), RefRecord.class));
        assertEngaged(seen, RefRecord.class);

        String doc = a2q("{'name':null,'count':3}");
        MismatchedInputException ve = assertThrows(MismatchedInputException.class,
                () -> vanilla.reader(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                        .forType(RefRecord.class).readValue(doc));
        MismatchedInputException me = assertThrows(MismatchedInputException.class,
                () -> mapper.reader(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                        .forType(RefRecord.class).readValue(doc));
        assertEquals(message(ve), message(me));
    }

    @Test
    public void testPerCallFailOnNullCreatorMissingComponent() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        assertEquals(new RefRecord("a", 1),
                mapper.readValue(a2q("{'name':'a','count':1}"), RefRecord.class));
        assertEngaged(seen, RefRecord.class);

        String doc = a2q("{'count':3}");
        MismatchedInputException ve = assertThrows(MismatchedInputException.class,
                () -> vanilla.reader(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                        .forType(RefRecord.class).readValue(doc));
        MismatchedInputException me = assertThrows(MismatchedInputException.class,
                () -> mapper.reader(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                        .forType(RefRecord.class).readValue(doc));
        assertEquals(message(ve), message(me));
    }

    @Test
    public void testFailOnNullCreatorOffMatchesVanilla() throws Exception {
        ObjectMapper mapper = newObjectMapper();
        String doc = a2q("{'name':null,'count':3}");
        assertEquals(vanilla.readValue(doc, RefRecord.class),
                mapper.readValue(doc, RefRecord.class));
    }

    @Test
    public void testBuildTimeFailOnNullCreator() throws Exception {
        Map<Class<?>, ValueDeserializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper strict = capturingMapper(seen,
                DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
        assertThrows(MismatchedInputException.class,
                () -> strict.readValue(a2q("{'name':null,'count':3}"), RefRecord.class));
        assertEngaged(seen, RefRecord.class);
    }

    // Required stays enforced regardless of features

    @Test
    public void testRequiredComponentStillEnforced() throws Exception {
        ObjectMapper mapper = newObjectMapper();
        assertThrows(MismatchedInputException.class,
                () -> mapper.readValue(a2q("{'count':3}"), ReqRecord.class));
    }
}
