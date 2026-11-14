package tools.jackson.module.blackbird.deser;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectReader;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.deser.std.StdValueInstantiator;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.util.AccessPattern;
import tools.jackson.databind.util.StdConverter;

import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Absent record components: the generated reader must give what stock gives,
 * whether it constructs directly or through the stock creator.
 */
public class BBCodecRecordAbsentTest extends BlackbirdTestBase
{
    enum Color { RED }

    public record Child(String name) {}

    public record Scalars(String s, int i, long l, boolean b, Integer w, double d) {}

    // Any subset of these components may be absent and still take the direct
    // constructor call. The constructor records whether the stock creator
    // (StdValueInstantiator) called it.
    public record Defaults(String s, int i, long l, boolean b, Child c) {
        static boolean viaStock;

        public Defaults {
            viaStock = StackWalker.getInstance().walk(frames -> frames.anyMatch(
                    f -> f.getClassName().equals(StdValueInstantiator.class.getName())));
        }
    }

    public record Refs(Color e, Object o, Child c, List<String> list, Map<String, Integer> map) {}

    public record References(Optional<String> opt, AtomicReference<String> ref, JsonNode node) {}

    public record NullHandling(@JsonSetter(nulls = Nulls.AS_EMPTY) List<String> empty, String s) {}

    public record NullsFail(@JsonSetter(nulls = Nulls.FAIL) String fail, String s) {}

    public record Required(@JsonProperty(required = true) String req, String s) {}

    public record PrimitiveOptionals(OptionalInt i, OptionalLong l, OptionalDouble d, String s) {}

    public record Converted(@JsonDeserialize(converter = Upper.class) String c, String s) {}

    // A non-null absent value behind an ALWAYS_NULL null access pattern, as
    // the OptionalInt deserializer of databind 3.3.0 reports
    // (databind#6437): only stock's absent value gives the right component.
    public record MisreportedAbsent(@JsonDeserialize(using = AbsentText.class) String t,
            String s) {}

    public record CustomPrimitive(@JsonDeserialize(using = NoAbsentInt.class) int p, String s) {}

    public record MorePrimitives(char ch, short sh, byte by, float fl, String s) {}

    public record ThrowingAbsent(@JsonDeserialize(using = AbsentFails.class) String f,
            String s) {}

    static class Upper extends StdConverter<String, String> {
        @Override
        public String convert(String value) {
            return value.toUpperCase();
        }
    }

    static class AbsentText extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            return p.getString();
        }

        @Override
        public AccessPattern getNullAccessPattern() {
            return AccessPattern.ALWAYS_NULL;
        }

        @Override
        public Object getAbsentValue(DeserializationContext ctxt) {
            return "absent";
        }
    }

    // Gives null for an absent int, which stock passes to the constructor.
    static class NoAbsentInt extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) {
            return p.getIntValue();
        }
    }

    static class AbsentFails extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            return p.getString();
        }

        @Override
        public Object getAbsentValue(DeserializationContext ctxt) {
            return ctxt.reportInputMismatch(String.class, "no absent value");
        }
    }

    // Counts the calls for an absent value, which stock makes once per read.
    public record Counted(@JsonDeserialize(using = CountingAbsent.class) String n, String s) {}

    static final AtomicInteger ABSENT_CALLS = new AtomicInteger();

    static class CountingAbsent extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            return p.getString();
        }

        @Override
        public Object getAbsentValue(DeserializationContext ctxt) {
            ABSENT_CALLS.incrementAndGet();
            return "absent";
        }
    }

    private static final List<Class<?>> TYPES = List.of(Scalars.class, Defaults.class, Refs.class,
            References.class, NullHandling.class, NullsFail.class, Required.class,
            PrimitiveOptionals.class, Converted.class, MisreportedAbsent.class,
            CustomPrimitive.class, ThrowingAbsent.class, MorePrimitives.class);

    private static final List<String> DOCS = List.of("{}", "{\"s\":\"x\"}",
            "{\"s\":\"x\",\"i\":1,\"l\":2,\"b\":true,\"w\":3,\"d\":4.5}",
            "{\"e\":\"RED\",\"o\":1,\"c\":{\"name\":\"n\"},\"list\":[\"a\"],\"map\":{\"k\":1}}",
            "{\"opt\":\"v\",\"ref\":\"r\",\"node\":{\"a\":1}}",
            "{\"req\":\"r\"}",
            "{\"i\":1,\"l\":2,\"d\":3.5,\"c\":\"c\",\"t\":\"t\",\"p\":4,\"f\":\"f\",\"ch\":\"x\",\"sh\":5,\"by\":6,\"fl\":7.5}",
            "{\"i\":1,\"c\":{\"name\":\"n\"}}",
            "{\"s\":\"x\",\"l\":2,\"b\":true}");

    // Each of these leaves out only Defaults components, so the generated
    // reader constructs directly.
    private static final List<String> DIRECT_DOCS = List.of("{}", "{\"s\":\"x\"}",
            "{\"i\":1,\"c\":{\"name\":\"n\"}}", "{\"s\":\"x\",\"l\":2,\"b\":true}",
            "{\"s\":\"x\",\"i\":1,\"l\":2,\"b\":true,\"c\":{\"name\":\"n\"}}");

    @Test
    public void testDefaults() {
        assertParity(JsonMapper.builder(), mapperBuilder());
    }

    @Test
    public void testFeatures() {
        for (DeserializationFeature f : List.of(
                DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES,
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                DeserializationFeature.USE_NULL_FOR_MISSING_REFERENCE_VALUES)) {
            assertParity(JsonMapper.builder().enable(f), mapperBuilder().enable(f));
        }
        assertParity(JsonMapper.builder().enable(JsonNodeFeature.MAP_ABSENT_TO_MISSING),
                mapperBuilder().enable(JsonNodeFeature.MAP_ABSENT_TO_MISSING));
    }

    @Test
    public void testDirectConstruction() {
        JsonMapper vanilla = JsonMapper.builder().build();
        JsonMapper bb = mapperBuilder().build();
        assertGeneratedReader(bb, Defaults.class);
        for (String doc : DIRECT_DOCS) {
            String expected = read(vanilla, Defaults.class, doc);
            assertTrue(Defaults.viaStock, "stock should construct through its creator");
            assertEquals(expected, read(bb, Defaults.class, doc), doc);
            assertFalse(Defaults.viaStock, "should construct directly: " + doc);
        }
    }

    // The feature is checked per call: one mapper, so one generated reader,
    // reads the same documents with and without it.
    @Test
    public void testPerCallFailOnMissing() {
        ObjectReader vanilla = JsonMapper.builder().build().readerFor(Defaults.class);
        JsonMapper bbMapper = mapperBuilder().build();
        assertGeneratedReader(bbMapper, Defaults.class);
        ObjectReader bb = bbMapper.readerFor(Defaults.class);
        DeserializationFeature f = DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES;
        for (String doc : DIRECT_DOCS) {
            assertEquals(read(vanilla, doc), read(bb, doc), doc);
            assertEquals(read(vanilla.with(f), doc), read(bb.with(f), doc), doc);
            assertEquals(read(vanilla, doc), read(bb, doc), doc);
        }
        // With every component present, the feature does not leave the direct path
        String full = DIRECT_DOCS.get(DIRECT_DOCS.size() - 1);
        read(bb.with(f), full);
        assertFalse(Defaults.viaStock);
        // and with one absent, it fails
        assertNotEquals(read(bb, "{}"), read(bb.with(f), "{}"));
    }

    // Stock asks once for an absent value. So does the generated reader, on
    // the direct path and when it sends a filled read to the stock creator
    // (FAIL_ON_NULL_CREATOR_PROPERTIES here, for the null of s).
    @Test
    public void testAbsentValueComputedOnce() {
        JsonMapper bb = mapperBuilder().build();
        assertGeneratedReader(bb, Counted.class);
        assertEquals(1, absentCalls(bb, "{\"s\":\"x\"}"));
        assertEquals(new Counted("absent", "x"), bb.readValue("{\"s\":\"x\"}", Counted.class));

        JsonMapper vanilla = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES).build();
        JsonMapper nullsFail = mapperBuilder()
                .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES).build();
        assertEquals(1, absentCalls(vanilla, "{}"));
        assertEquals(1, absentCalls(nullsFail, "{}"));
    }

    private static int absentCalls(JsonMapper mapper, String doc) {
        ABSENT_CALLS.set(0);
        read(mapper, Counted.class, doc);
        return ABSENT_CALLS.get();
    }

    private static void assertParity(JsonMapper.Builder stock, JsonMapper.Builder blackbird) {
        JsonMapper vanilla = stock.build();
        JsonMapper bb = blackbird.build();
        for (Class<?> type : TYPES) {
            assertGeneratedReader(bb, type);
            for (String doc : DOCS) {
                assertEquals(read(vanilla, type, doc), read(bb, type, doc),
                        type.getSimpleName() + " " + doc);
            }
        }
    }

    // toString, because AtomicReference has identity equality. On failure,
    // the exception class and message.
    private static String read(JsonMapper mapper, Class<?> type, String doc) {
        return read(mapper.readerFor(type), doc);
    }

    private static String read(ObjectReader reader, String doc) {
        try {
            return String.valueOf((Object) reader.readValue(doc));
        } catch (JacksonException e) {
            return e.getClass().getName() + ": " + e.getMessage();
        }
    }
}
