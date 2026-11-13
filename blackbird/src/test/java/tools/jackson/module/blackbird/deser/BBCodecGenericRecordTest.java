package tools.jackson.module.blackbird.deser;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonDeserialize;

import tools.jackson.module.blackbird.BlackbirdTestBase;

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

    // A non-canonical creator of the canonical arity whose parameter types
    // fit the components but differ from them: not the canonical constructor.
    public record Narrowed(Object a, Object b) {
        @JsonCreator
        public Narrowed(@JsonProperty("a") String a, @JsonProperty("b") String b) {
            this((Object) ("ctor:" + a), (Object) ("ctor:" + b));
        }
    }

    // Non-generic components narrowed by annotation: the property type is a
    // subtype of the component type, as for a resolved type variable.
    public record AnnotationNarrowed(
            @JsonDeserialize(as = LinkedList.class) List<String> items,
            @JsonDeserialize(as = Payload.class) Object payload) {}

    private static final Type ENVELOPE_PAYLOAD = new TypeReference<Envelope<Payload>>() {}.getType();
    private static final Type ENVELOPE_LIST = new TypeReference<Envelope<List<String>>>() {}.getType();
    private static final Type BOUNDED_INT = new TypeReference<Bounded<Integer>>() {}.getType();

    private static final ObjectMapper MAPPER = newBlackbirdMapper();
    private static final ObjectMapper VANILLA = newVanillaJSONMapper();

    /** Reads the document with Blackbird, asserting plain databind reads the same value. */
    private static Object read(String doc, Type type) throws Exception {
        JavaType t = MAPPER.constructType(type);
        Object value = MAPPER.readValue(doc, t);
        assertEquals(VANILLA.readValue(doc, t), value);
        return value;
    }

    @Test
    public void testResolvedTypeVariable() throws Exception {
        // The resolved Payload links its child codec into the Object-typed local.
        assertTrue(assertGeneratedReader(MAPPER, ENVELOPE_PAYLOAD).contains("data=CHILD"));
        String doc = "{\"topic\":\"t\",\"ts\":5,\"data\":{\"s\":\"x\",\"n\":3}}";
        assertEquals(new Envelope<>("t", 5L, new Payload("x", 3)), read(doc, ENVELOPE_PAYLOAD));
        read("{\"topic\":\"t\"}", ENVELOPE_PAYLOAD);

        assertGeneratedReader(MAPPER, ENVELOPE_LIST);
        assertEquals(new Envelope<>("t", 1L, List.of("a", "b")),
                read("{\"topic\":\"t\",\"ts\":1,\"data\":[\"a\",\"b\"]}", ENVELOPE_LIST));

        assertGeneratedReader(MAPPER, BOUNDED_INT);
        assertEquals(new Bounded<>("b", 7), read("{\"name\":\"b\",\"value\":7}", BOUNDED_INT));
    }

    @Test
    public void testUnresolvedTypeVariable() throws Exception {
        // The type variable binds as its bound; the data binds as untyped.
        assertGeneratedReader(MAPPER, Envelope.class);
        assertGeneratedReader(MAPPER, Bounded.class);
        read("{\"topic\":\"t\",\"ts\":5,\"data\":{\"s\":\"x\",\"n\":3}}", Envelope.class);
    }

    @Test
    public void testBindingOutsideBoundStaysStock() throws Exception {
        // A binding that breaks the declared bound gives a property type the
        // component cannot hold; the canonical constructor would reject it.
        JavaType type = MAPPER.getTypeFactory().constructParametricType(Bounded.class, String.class);
        assertStockReader(MAPPER, type);
        String doc = "{\"name\":\"b\",\"value\":\"x\"}";
        DatabindException expected = assertThrows(DatabindException.class, () -> VANILLA.readValue(doc, type));
        DatabindException actual = assertThrows(DatabindException.class, () -> MAPPER.readValue(doc, type));
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected.getMessage(), actual.getMessage());
    }

    @Test
    public void testNonCanonicalCreatorStaysStock() throws Exception {
        assertStockReader(MAPPER, Narrowed.class);
        assertEquals(new Narrowed((Object) "ctor:x", (Object) "ctor:y"),
                read("{\"a\":\"x\",\"b\":\"y\"}", Narrowed.class));
    }

    @Test
    public void testAnnotationNarrowedComponents() throws Exception {
        String layout = assertGeneratedReader(MAPPER, AnnotationNarrowed.class);
        assertTrue(layout.contains("payload=CHILD"), layout);
        AnnotationNarrowed value = (AnnotationNarrowed) read(
                "{\"items\":[\"a\",\"b\"],\"payload\":{\"s\":\"x\",\"n\":3}}", AnnotationNarrowed.class);
        assertEquals(new AnnotationNarrowed(new LinkedList<>(List.of("a", "b")), new Payload("x", 3)), value);
        assertEquals(LinkedList.class, value.items().getClass());
    }
}
