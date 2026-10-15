package tools.jackson.module.blackbird.diff;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.InvalidNullException;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.ValueInstantiationException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Record codecs: absent components get the stock absent value, the
 * canonical constructor's failures are reported as stock reports them, and
 * unknown names are reported against the record once it exists. Every
 * scenario's record keeps the generated codec.
 */
public class RecordsDiffTest extends DifferentialTestBase
{
    /*
    /**********************************************************************
    /* Absent components
    /**********************************************************************
     */

    public record OptRec(String a, Optional<String> b) {}

    @Test
    public void absentOptionalIsEmpty() {
        Mappers m = mappers();
        OptRec r = assertSameRead(m, "{\"a\":\"1\"}", OptRec.class).get();
        assertEquals(new OptRec("1", Optional.empty()), r);
        assertGeneratedReader(m.blackbird(), OptRec.class);
    }

    @Test
    public void absentOptionalPassesFailOnNullCreatorProperties() {
        Mappers m = mappers(b -> b.enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES));
        OptRec r = assertSameRead(m, "{\"a\":\"1\"}", OptRec.class).get();
        assertEquals(new OptRec("1", Optional.empty()), r);
        // An absent String has no non-null absent value: the null fails.
        assertSameRead(m, "{\"b\":\"x\"}", OptRec.class).error(MismatchedInputException.class);
        assertGeneratedReader(m.blackbird(), OptRec.class);
    }

    public record AsEmptyRec(String a, @JsonSetter(nulls = Nulls.AS_EMPTY) String b) {}

    @Test
    public void absentAsEmptyComponentIsEmpty() {
        Mappers m = mappers();
        AsEmptyRec r = assertSameRead(m, "{\"a\":\"1\"}", AsEmptyRec.class).get();
        assertEquals(new AsEmptyRec("1", ""), r);
        assertGeneratedReader(m.blackbird(), AsEmptyRec.class);
    }

    public record FailNullsRec(String a, @JsonSetter(nulls = Nulls.FAIL) List<String> b) {}

    @Test
    public void absentFailNullsComponentFails() {
        Mappers m = mappers();
        assertSameRead(m, "{\"a\":\"1\"}", FailNullsRec.class).error(InvalidNullException.class);
        assertGeneratedReader(m.blackbird(), FailNullsRec.class);
    }

    public record RequiredRec(@JsonProperty(required = true) String a, int b) {}

    @Test
    public void missingRequiredAndFailOnMissing() {
        Mappers m = mappers();
        assertSameRead(m, "{\"b\":1}", RequiredRec.class).error(MismatchedInputException.class);
        Mappers strict = mappers(b ->
                b.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES));
        assertSameRead(strict, "{\"a\":\"x\"}", RequiredRec.class)
                .error(MismatchedInputException.class);
        assertGeneratedReader(m.blackbird(), RequiredRec.class);
    }

    public record Prims(int i, long l, boolean z, double d, String s) {}

    @Test
    public void absentPrimitivesAreDefaults() {
        Mappers m = mappers();
        assertEquals(new Prims(0, 0L, false, 0.0, null),
                assertSameRead(m, "{}", Prims.class).get());
        Mappers strict = mappers(b ->
                b.enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES));
        assertSameRead(strict, "{\"s\":\"x\"}", Prims.class);
        assertSameRead(strict, "{\"i\":null}", Prims.class);
        assertGeneratedReader(m.blackbird(), Prims.class);
    }

    public static class NullInt extends ValueDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser p, DeserializationContext ctxt) {
            p.skipChildren();
            return null;
        }
    }

    public record NullPrimitiveRec(@JsonDeserialize(using = NullInt.class) int n, String s) {}

    @Test
    public void nullFromCustomDeserializerForPrimitiveComponent() {
        Mappers m = mappers();
        assertSameRead(m, "{\"n\":5,\"s\":\"x\"}", NullPrimitiveRec.class);
        assertSameRead(m, "{\"n\":5,\"n\":6,\"s\":\"x\"}", NullPrimitiveRec.class);
        assertGeneratedReader(m.blackbird(), NullPrimitiveRec.class);
    }

    /*
    /**********************************************************************
    /* Constructor failures
    /**********************************************************************
     */

    public record Validated(int n, String s) {
        public Validated {
            if (n < 0) {
                throw new IllegalArgumentException("n must be >= 0");
            }
        }
    }

    @Test
    public void constructorFailureIsValueInstantiationException() {
        Mappers m = mappers();
        ValueInstantiationException e = assertSameRead(m, "{\"n\":-1,\"s\":\"x\"}",
                Validated.class).error(ValueInstantiationException.class);
        assertTrue(e.getMessage().contains("n must be >= 0"), e.getMessage());
        // Also when a component is absent (the stock creator constructs).
        assertSameRead(m, "{\"n\":-1}", Validated.class)
                .error(ValueInstantiationException.class);
        assertGeneratedReader(m.blackbird(), Validated.class);
    }

    @Test
    public void constructorFailureWithoutWrapping() {
        Mappers m = mappers(b -> b.disable(DeserializationFeature.WRAP_EXCEPTIONS));
        assertSameRead(m, "{\"n\":-1,\"s\":\"x\"}", Validated.class);
        assertSameRead(m, "{\"n\":-1}", Validated.class);
    }

    static final class ReplacingHandler extends DeserializationProblemHandler {
        @Override
        public Object handleInstantiationProblem(DeserializationContext ctxt, Class<?> instClass,
                Object argument, Throwable t) {
            return (instClass == Validated.class) ? new Validated(0, "replaced") : NOT_HANDLED;
        }
    }

    @Test
    public void problemHandlerReplacesFailedConstruction() {
        Mappers m = mappers(b -> b.addHandler(new ReplacingHandler()));
        Validated v = assertSameRead(m, "{\"n\":-1,\"s\":\"x\"}", Validated.class).get();
        assertEquals(new Validated(0, "replaced"), v);
        assertGeneratedReader(m.blackbird(), Validated.class);
    }

    /*
    /**********************************************************************
    /* JSON names that are not JVM names
    /**********************************************************************
     */

    public record Dotted(@JsonProperty("user.name") String userName,
            @JsonProperty("a;b/c[d]") int n) {}

    @Test
    public void namesThatAreNotJvmNames() {
        Mappers m = mappers();
        Dotted d = assertSameRead(m, "{\"user.name\":\"a\",\"a;b/c[d]\":1}", Dotted.class)
                .get();
        assertEquals(new Dotted("a", 1), d);
        assertGeneratedReader(m.blackbird(), Dotted.class);
    }

    /*
    /**********************************************************************
    /* Unknown names are reported against the record built
    /**********************************************************************
     */

    @Test
    public void unknownNameReportedAfterConstruction() {
        Mappers m = mappers(b -> b.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
        // The constructor fails first, as in stock.
        assertSameRead(m, "{\"zzz\":1,\"n\":-1,\"s\":\"x\"}", Validated.class)
                .error(ValueInstantiationException.class);
        // A valid record: the unknown name fails, against the instance.
        assertSameRead(m, "{\"zzz\":1,\"n\":1,\"s\":\"x\"}", Validated.class)
                .error(tools.jackson.databind.exc.UnrecognizedPropertyException.class);
        assertGeneratedReader(m.blackbird(), Validated.class);
    }

    static final class RecordingHandler extends DeserializationProblemHandler {
        final List<Object> seen = new ArrayList<>();

        @Override
        public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
                ValueDeserializer<?> deserializer, Object beanOrClass, String propertyName) {
            seen.add(beanOrClass);
            p.skipChildren();
            return true;
        }
    }

    @Test
    public void problemHandlerSeesRecordInstance() {
        RecordingHandler stock = new RecordingHandler();
        RecordingHandler blackbird = new RecordingHandler();
        Mappers m = new Mappers(
                tools.jackson.databind.json.JsonMapper.builder().addHandler(stock).build(),
                tools.jackson.databind.json.JsonMapper.builder().addHandler(blackbird)
                        .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build());
        Validated v = assertSameRead(m, "{\"zzz\":1,\"n\":1,\"s\":\"x\"}", Validated.class).get();
        assertEquals(List.of(new Validated(1, "x")), stock.seen);
        assertEquals(stock.seen, blackbird.seen);
        assertEquals(new Validated(1, "x"), v);
        assertGeneratedReader(m.blackbird(), Validated.class);
    }

    /*
    /**********************************************************************
    /* Nulls.AS_EMPTY for a record-valued property
    /**********************************************************************
     */

    public record Inner(String a, int b) {}

    public record Names(String a, String b) {}

    public static class AsEmptyHolder {
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public Names names;
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        public Inner inner;
        public String name;
    }

    // The empty value of a record comes from its creator with null
    // arguments, which a primitive component cannot take: stock fails then.
    @Test
    public void asEmptyRecordProperty() {
        Mappers m = mappers();
        AsEmptyHolder h = assertSameRead(m, "{\"names\":null,\"name\":\"n\"}",
                AsEmptyHolder.class).get();
        assertEquals(new Names(null, null), h.names);
        assertSameRead(m, "{\"inner\":null}", AsEmptyHolder.class)
                .error(ValueInstantiationException.class);
        assertGeneratedReader(m.blackbird(), AsEmptyHolder.class);
        assertGeneratedReader(m.blackbird(), Names.class);
        assertGeneratedReader(m.blackbird(), Inner.class);
    }

    /*
    /**********************************************************************
    /* Names as stock reads them through the creator
    /**********************************************************************
     */

    public record CiRec(@JsonAlias("nm") String name, int count) {}

    @Test
    public void caseInsensitiveNamesAndAliases() {
        Mappers m = mappers(b -> b.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES));
        assertEquals(new CiRec("a", 1),
                assertSameRead(m, "{\"NAME\":\"a\",\"Count\":1}", CiRec.class).get());
        assertEquals(new CiRec("b", 2),
                assertSameRead(m, "{\"NM\":\"b\",\"count\":2}", CiRec.class).get());
        assertGeneratedReader(m.blackbird(), CiRec.class);
    }

    public record AnyCreatorRec(String name,
            @com.fasterxml.jackson.annotation.JsonAnySetter java.util.Map<String, Object> rest) {}

    @Test
    public void anySetterCreatorParameterKeepsStockBean() {
        Mappers m = mappers();
        AnyCreatorRec r = assertSameRead(m, "{\"name\":\"n\",\"x\":1}", AnyCreatorRec.class)
                .get();
        assertEquals(java.util.Map.of("x", 1), r.rest());
        assertStockReader(m.blackbird(), AnyCreatorRec.class);
        assertGeneratedReader(m.blackbird(), Inner.class);
    }
}
