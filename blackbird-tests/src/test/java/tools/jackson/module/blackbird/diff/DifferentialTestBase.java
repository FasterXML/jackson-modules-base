package tools.jackson.module.blackbird.diff;

import java.util.function.UnaryOperator;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.internal.GeneratedBuilderBase;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;
import tools.jackson.module.blackbird.internal.GeneratedWriterBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Differential tests: every scenario runs through two mappers built from the
 * same configuration, one of them with Blackbird, and must give the same
 * outcome - the same value (class and stock serialization), or the same
 * exception (class and message, which includes the reference path).
 * Scenarios then assert which path Blackbird took: the generated codec, a
 * stock arm inside it for one property, or the stock (de)serializer for the
 * whole bean. The assertions look at the (de)serializer the mapper actually
 * uses, not at what a modifier saw.
 */
public abstract class DifferentialTestBase
{
    static {
        // Strict mode: a codec-generation failure fails the test instead of
        // demoting to the stock path (see CodegenFallbacks).
        System.setProperty("tools.jackson.module.blackbird.failOnCodegenError", "true");
    }

    // Describes values; never has Blackbird.
    private static final ObjectMapper DESCRIBER = JsonMapper.builder().build();

    /** Two mappers with one configuration; only the second has Blackbird. */
    protected record Mappers(JsonMapper stock, JsonMapper blackbird) {}

    protected static Mappers mappers() {
        return mappers(b -> b);
    }

    protected static Mappers mappers(UnaryOperator<JsonMapper.Builder> config) {
        return new Mappers(config.apply(JsonMapper.builder()).build(),
                config.apply(JsonMapper.builder()).addModule(new BlackbirdModule()).build());
    }

    /** The result of one operation: a value, or the exception it threw. */
    protected record Outcome(Object value, Throwable error) {
        String describe() {
            if (error != null) {
                return "threw " + error.getClass().getName() + ": " + error.getMessage();
            }
            if (value == null) {
                return "null";
            }
            if (value instanceof String s) {
                return "string " + s;
            }
            try {
                return value.getClass().getName() + " " + DESCRIBER.writeValueAsString(value);
            } catch (RuntimeException e) {
                return value.getClass().getName() + " " + value;
            }
        }

        @SuppressWarnings("unchecked")
        <T> T get() {
            assertNull(error, () -> "expected a value, got " + describe());
            return (T) value;
        }

        <T extends Throwable> T error(Class<T> type) {
            assertTrue(type.isInstance(error), () -> "expected " + type.getName() + ", got "
                    + describe());
            return type.cast(error);
        }
    }

    protected static Outcome read(ObjectMapper mapper, String json, Class<?> type) {
        try {
            return new Outcome(mapper.readValue(json, type), null);
        } catch (RuntimeException | StackOverflowError e) {
            return new Outcome(null, e);
        }
    }

    protected static Outcome write(ObjectMapper mapper, Object value) {
        try {
            return new Outcome(mapper.writeValueAsString(value), null);
        } catch (RuntimeException | StackOverflowError e) {
            return new Outcome(null, e);
        }
    }

    /** Reads with both mappers; returns the Blackbird outcome once it matches stock. */
    protected static Outcome assertSameRead(Mappers m, String json, Class<?> type) {
        Outcome stock = read(m.stock(), json, type);
        Outcome blackbird = read(m.blackbird(), json, type);
        assertEquals(stock.describe(), blackbird.describe(),
                "Blackbird and stock differ reading " + type.getSimpleName() + " from " + json);
        return blackbird;
    }

    /** Writes with both mappers; returns the Blackbird outcome once it matches stock. */
    protected static Outcome assertSameWrite(Mappers m, Object value) {
        Outcome stock = write(m.stock(), value);
        Outcome blackbird = write(m.blackbird(), value);
        assertEquals(stock.describe(), blackbird.describe(),
                "Blackbird and stock differ writing " + value.getClass().getSimpleName());
        return blackbird;
    }

    /*
    /**********************************************************************
    /* Which path Blackbird took
    /**********************************************************************
     */

    @SuppressWarnings("unchecked")
    protected static ValueDeserializer<Object> rootReader(ObjectMapper mapper, Class<?> type) {
        DeserializationContext ctxt = mapper._deserializationContext();
        // Contextual but not type-wrapped: under default typing a root value
        // is read through a TypeWrappedDeserializer around this one.
        return (ValueDeserializer<Object>) ctxt.findContextualValueDeserializer(
                ctxt.constructType(type), null);
    }

    protected static ValueSerializer<Object> rootWriter(ObjectMapper mapper, Class<?> type) {
        SerializationContext ctxt = mapper._serializationContext();
        return ctxt.handleRootContextualization(ctxt.findValueSerializer(type));
    }

    /** Asserts that a generated reader handles the type; returns its "name=KIND" layout. */
    protected static String assertGeneratedReader(ObjectMapper mapper, Class<?> type) {
        ValueDeserializer<Object> d = rootReader(mapper, type);
        if (d instanceof GeneratedReaderBase r) {
            return r.codecLayout();
        }
        if (d instanceof GeneratedBuilderBase b) {
            return b.codecLayout();
        }
        return fail(type.getSimpleName() + " should use a generated reader, uses " + d);
    }

    /** Asserts that the stock deserializer handles the whole type. */
    protected static void assertStockReader(ObjectMapper mapper, Class<?> type) {
        ValueDeserializer<Object> d = rootReader(mapper, type);
        assertFalse(d instanceof GeneratedReaderBase || d instanceof GeneratedBuilderBase,
                type.getSimpleName() + " should keep the stock deserializer, uses " + d);
    }

    /** Asserts that a generated writer handles the type; returns its "name=KIND" layout. */
    protected static String assertGeneratedWriter(ObjectMapper mapper, Class<?> type) {
        ValueSerializer<Object> s = rootWriter(mapper, type);
        if (s instanceof GeneratedWriterBase w) {
            return w.codecLayout();
        }
        return fail(type.getSimpleName() + " should use a generated writer, uses " + s);
    }

    /** Asserts that the stock serializer handles the whole type. */
    protected static void assertStockWriter(ObjectMapper mapper, Class<?> type) {
        ValueSerializer<Object> s = rootWriter(mapper, type);
        assertFalse(s instanceof GeneratedWriterBase,
                type.getSimpleName() + " should keep the stock serializer, uses " + s);
    }

    /** Asserts that the layout has the arm, e.g. "pet=STOCK". */
    protected static void assertArm(String layout, String arm) {
        assertTrue(("," + layout + ",").contains("," + arm + ","),
                "expected arm " + arm + " in layout " + layout);
    }
}
