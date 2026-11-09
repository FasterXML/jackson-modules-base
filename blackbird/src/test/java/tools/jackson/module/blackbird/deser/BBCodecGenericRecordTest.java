package tools.jackson.module.blackbird.deser;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;

import tools.jackson.module.blackbird.BlackbirdTestBase;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Records with a component declared through a type variable: the component
 * erases to its bound, the property carries the resolved type.
 */
public class BBCodecGenericRecordTest extends BlackbirdTestBase
{
    public record Envelope<T>(String topic, long ts, T data) {}

    public record Bounded<N extends Number>(String name, N value) {}

    public record Payload(String s, int n) {}

    private static final ObjectMapper MAPPER = newBlackbirdMapper();

    private static ValueDeserializer<Object> genericReader(ObjectMapper mapper,
            TypeReference<?> type) {
        DeserializationContext ctxt = mapper._deserializationContext();
        return ctxt.findRootValueDeserializer(ctxt.constructType(type.getType()));
    }

    @Test
    public void testResolvedTypeVariableGenerates() throws Exception {
        assertInstanceOf(GeneratedReaderBase.class,
                genericReader(MAPPER, new TypeReference<Envelope<Payload>>() {}));
        assertInstanceOf(GeneratedReaderBase.class,
                genericReader(MAPPER, new TypeReference<Bounded<Integer>>() {}));
    }

    @Test
    public void testGenericRecordValues() throws Exception {
        ObjectMapper vanilla = newVanillaJSONMapper();

        TypeReference<Envelope<Payload>> payload = new TypeReference<Envelope<Payload>>() {};
        String doc = "{\"topic\":\"t\",\"ts\":5,\"data\":{\"s\":\"x\",\"n\":3}}";
        assertEquals(new Envelope<>("t", 5L, new Payload("x", 3)), MAPPER.readValue(doc, payload));
        assertEquals(vanilla.readValue(doc, payload), MAPPER.readValue(doc, payload));
        String missing = "{\"topic\":\"t\"}";
        assertEquals(vanilla.readValue(missing, payload), MAPPER.readValue(missing, payload));

        TypeReference<Envelope<List<String>>> list = new TypeReference<Envelope<List<String>>>() {};
        String listDoc = "{\"topic\":\"t\",\"ts\":1,\"data\":[\"a\",\"b\"]}";
        assertEquals(new Envelope<>("t", 1L, List.of("a", "b")), MAPPER.readValue(listDoc, list));

        TypeReference<Bounded<Integer>> bounded = new TypeReference<Bounded<Integer>>() {};
        assertEquals(new Bounded<>("b", 7),
                MAPPER.readValue("{\"name\":\"b\",\"value\":7}", bounded));

        // Unresolved: the component stays Object and binds as untyped.
        @SuppressWarnings("rawtypes")
        Envelope raw = MAPPER.readValue(doc, Envelope.class);
        assertEquals(vanilla.readValue(doc, Envelope.class), raw);
    }
}
