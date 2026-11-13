package tools.jackson.module.blackbird.deser;

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

    private static final ObjectMapper MAPPER = newBlackbirdMapper();

    @Test
    public void testResolvedTypeVariableGenerates() throws Exception {
        // The resolved Payload links its child codec into the Object-typed local.
        assertTrue(assertGeneratedReader(MAPPER, new TypeReference<Envelope<Payload>>() {}.getType())
                .contains("data=CHILD"));
        assertGeneratedReader(MAPPER, new TypeReference<Envelope<List<String>>>() {}.getType());
        assertGeneratedReader(MAPPER, new TypeReference<Bounded<Integer>>() {}.getType());
        // Unresolved, the type variable binds as its bound.
        assertGeneratedReader(MAPPER, Envelope.class);
        assertGeneratedReader(MAPPER, Bounded.class);
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

    @Test
    public void testBindingOutsideBoundStaysStock() throws Exception {
        // A binding that breaks the declared bound gives a property type the
        // component cannot hold; the canonical constructor would reject it.
        JavaType type = MAPPER.getTypeFactory().constructParametricType(Bounded.class, String.class);
        assertStockReader(MAPPER, type);
        String doc = "{\"name\":\"b\",\"value\":\"x\"}";
        DatabindException bb = assertThrows(DatabindException.class,
                () -> MAPPER.readValue(doc, type));
        DatabindException vanilla = assertThrows(DatabindException.class,
                () -> newVanillaJSONMapper().readValue(doc, type));
        assertEquals(vanilla.getClass(), bb.getClass());
    }

    @Test
    public void testNonCanonicalCreatorStaysStock() throws Exception {
        assertStockReader(MAPPER, Narrowed.class);
        String doc = "{\"a\":\"x\",\"b\":\"y\"}";
        assertEquals(new Narrowed((Object) "ctor:x", (Object) "ctor:y"),
                MAPPER.readValue(doc, Narrowed.class));
        assertEquals(newVanillaJSONMapper().readValue(doc, Narrowed.class),
                MAPPER.readValue(doc, Narrowed.class));
    }

    @Test
    public void testAnnotationNarrowedComponentsGenerate() throws Exception {
        String layout = assertGeneratedReader(MAPPER, AnnotationNarrowed.class);
        assertTrue(layout.contains("payload=CHILD"), layout);

        String doc = "{\"items\":[\"a\",\"b\"],\"payload\":{\"s\":\"x\",\"n\":3}}";
        AnnotationNarrowed value = MAPPER.readValue(doc, AnnotationNarrowed.class);
        assertEquals(new AnnotationNarrowed(new LinkedList<>(List.of("a", "b")), new Payload("x", 3)),
                value);
        assertEquals(LinkedList.class, value.items().getClass());
        assertEquals(newVanillaJSONMapper().readValue(doc, AnnotationNarrowed.class), value);
    }
}
