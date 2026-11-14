package tools.jackson.module.blackbird.deser;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
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
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.deser.std.StdScalarDeserializer;
import tools.jackson.databind.json.JsonMapper;

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

    public record Refs(Color e, Object o, Child c, List<String> list, Map<String, Integer> map) {}

    public record References(Optional<String> opt, AtomicReference<String> ref, JsonNode node) {}

    // [databind#6437]: absent scalar Optionals are empty, not null
    public record ScalarOptionals(OptionalInt oi, OptionalLong ol, OptionalDouble od, String s) {}

    // Keeps the inherited ALWAYS_NULL null access (getNullValue is null), but
    // its absent value is not null
    public static class DefaultingDeserializer extends StdScalarDeserializer<String> {
        public DefaultingDeserializer() { super(String.class); }

        @Override
        public String deserialize(JsonParser p, DeserializationContext ctxt) {
            return p.getString();
        }

        @Override
        public Object getAbsentValue(DeserializationContext ctxt) {
            return "absent";
        }
    }

    public record CustomAbsent(String s,
            @JsonDeserialize(using = DefaultingDeserializer.class) String t) {}

    public record NullHandling(@JsonSetter(nulls = Nulls.AS_EMPTY) List<String> empty, String s) {}

    public record NullsFail(@JsonSetter(nulls = Nulls.FAIL) String fail, String s) {}

    public record Required(@JsonProperty(required = true) String req, String s) {}

    private static final List<Class<?>> TYPES = List.of(Scalars.class, Refs.class,
            References.class, ScalarOptionals.class, CustomAbsent.class, NullHandling.class,
            NullsFail.class, Required.class);

    private static final List<String> DOCS = List.of("{}", "{\"s\":\"x\"}",
            "{\"s\":\"x\",\"i\":1,\"l\":2,\"b\":true,\"w\":3,\"d\":4.5}",
            "{\"e\":\"RED\",\"o\":1,\"c\":{\"name\":\"n\"},\"list\":[\"a\"],\"map\":{\"k\":1}}",
            "{\"opt\":\"v\",\"ref\":\"r\",\"node\":{\"a\":1}}",
            "{\"oi\":1,\"ol\":2,\"od\":3.5}",
            "{\"req\":\"r\"}");

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

    // The feature is checked per call: one mapper, so one generated reader,
    // reads the same document with and without it.
    @Test
    public void testPerCallFailOnMissing() {
        ObjectReader vanilla = JsonMapper.builder().build().readerFor(Scalars.class);
        JsonMapper bbMapper = mapperBuilder().build();
        assertGeneratedReader(bbMapper, Scalars.class);
        ObjectReader bb = bbMapper.readerFor(Scalars.class);
        DeserializationFeature f = DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES;
        String doc = "{\"s\":\"x\"}";
        String lenient = read(bb, doc);
        String failing = read(bb.with(f), doc);
        assertNotEquals(lenient, failing);
        assertEquals(read(vanilla, doc), lenient);
        assertEquals(read(vanilla.with(f), doc), failing);
        assertEquals(lenient, read(bb, doc));
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

    // toString, as AtomicReference has identity equality; the exception class on failure.
    private static String read(JsonMapper mapper, Class<?> type, String doc) {
        return read(mapper.readerFor(type), doc);
    }

    private static String read(ObjectReader reader, String doc) {
        try {
            return String.valueOf((Object) reader.readValue(doc));
        } catch (JacksonException e) {
            return e.getClass().getName();
        }
    }
}
