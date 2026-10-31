package tools.jackson.module.blackbird.deser;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;
import tools.jackson.module.blackbird.internal.GeneratedBuilderBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the stock deserializers make on rare paths, which the generated
 * loops must make too: null handling of a null child value, finishBuild's
 * checks on what a builder loop produced, widening of a primitive record
 * component's stock value, and forward Object Id references in record
 * components. Every case compares against a vanilla mapper.
 */
public class BBCodecStockChecksTest extends BlackbirdTestBase
{
    // A builder-built child whose build method returns null.
    @JsonDeserialize(builder = NullChild.Builder.class)
    public static class NullChild {
        public static class Builder {
            public int v;
            public Builder withV(int v) { this.v = v; return this; }
            public NullChild build() { return null; }
        }
    }

    public static class FailParent {
        @JsonSetter(nulls = Nulls.FAIL)
        public NullChild child;
        public int x;
    }

    public static class SkipParent {
        @JsonSetter(nulls = Nulls.SKIP)
        public NullChild child = new NullChild();
        public int x;
    }

    public record FailRecord(@JsonSetter(nulls = Nulls.FAIL) NullChild child, int x) { }

    // A builder whose with-method may return the built value.
    @JsonDeserialize(builder = Wt.Builder.class)
    public static class Wt {
        final int v;

        Wt(int v) { this.v = v; }

        public static class Builder {
            public Object withV(int v) { return new Wt(v * 10); }
            public Wt build() { return new Wt(-1); }
        }
    }

    // A builder property with an alias; a negative value fails in the
    // with-method.
    @JsonDeserialize(builder = Aliased.Builder.class)
    public static class Aliased {
        final int v;

        Aliased(int v) { this.v = v; }

        public static class Builder {
            int v;

            @JsonAlias("alt")
            public Builder withV(int v) {
                if (v < 0) {
                    throw new IllegalArgumentException("negative");
                }
                this.v = v;
                return this;
            }

            public Aliased build() { return new Aliased(v); }
        }
    }

    // A polymorphic builder subtype with an alias: a type id after the
    // first property makes the codec run on the buffered property, then on
    // the live stream.
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY,
            property = "type")
    @JsonSubTypes(@JsonSubTypes.Type(value = AliasedSub.class, name = "sub"))
    public interface AliasedBase { }

    @JsonDeserialize(builder = AliasedSub.Builder.class)
    public static class AliasedSub implements AliasedBase {
        final String a;
        final int z;

        AliasedSub(String a, int z) { this.a = a; this.z = z; }

        public static class Builder {
            String a;
            int z;

            @JsonAlias("aa")
            public Builder withA(String a) { this.a = a; return this; }

            public Builder withZ(int z) {
                if (z < 0) {
                    throw new IllegalArgumentException("negative");
                }
                this.z = z;
                return this;
            }

            public AliasedSub build() { return new AliasedSub(a, z); }
        }
    }

    // A builder whose construction a problem handler replaces with null.
    @JsonDeserialize(builder = Boom.Builder.class)
    public static class Boom {
        public static class Builder {
            public Builder() { throw new IllegalStateException("boom"); }
            public Builder withV(int v) { return this; }
            public Boom build() { return new Boom(); }
        }
    }

    // Returns an Integer whatever the target type.
    public static class IntDeser extends ValueDeserializer<Object> {
        @Override
        public Object deserialize(JsonParser p, DeserializationContext ctxt) {
            return p.getIntValue();
        }
    }

    public record LongRecord(@JsonDeserialize(using = IntDeser.class) long v, String s) { }

    public record DoubleRecord(@JsonDeserialize(using = IntDeser.class) double v, String s) { }

    public record BoolRecord(@JsonDeserialize(using = IntDeser.class) boolean v, String s) { }

    @JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
    public static class Item {
        public int id;
        public String name;
    }

    public record RefHolder(AtomicReference<Item> ref, String tag) { }

    // The reference resolves inside the record (items comes after ref),
    // before construction; a negative tag makes the constructor throw.
    public record RefPair(AtomicReference<Item> ref, List<Item> items, int tag) {
        public RefPair {
            if (tag < 0) {
                throw new IllegalArgumentException("negative tag");
            }
        }
    }

    public static class RefDoc {
        public RefHolder holder;
        public List<Item> items;
    }

    private static final ObjectMapper VANILLA = newVanillaJSONMapper();
    private static final ObjectMapper MODULE = newObjectMapper();

    private static <T> void assertSameOutcome(ObjectMapper vanilla, ObjectMapper module,
            String doc, Class<T> type, Function<T, Object> view) throws Exception {
        Object expected;
        try {
            expected = view.apply(vanilla.readValue(doc, type));
        } catch (Exception e) {
            Exception actual = assertThrows(Exception.class, () -> module.readValue(doc, type),
                    "stock threw " + e);
            assertEquals(e.getClass(), actual.getClass(), actual.toString());
            return;
        }
        assertEquals(expected, view.apply(module.readValue(doc, type)));
    }

    @Test
    public void testNullChildValueGetsNullHandling() throws Exception {
        String doc = "{\"child\":{\"v\":1},\"x\":2}";
        assertSameOutcome(VANILLA, MODULE, doc, FailParent.class, b -> b.child + "/" + b.x);
        assertSameOutcome(VANILLA, MODULE, doc, SkipParent.class,
                b -> (b.child != null) + "/" + b.x);
        assertSameOutcome(VANILLA, MODULE, doc, FailRecord.class, r -> r.toString());
        assertGeneratedReader(MODULE, FailParent.class);
        assertGeneratedReader(MODULE, SkipParent.class);
        assertGeneratedReader(MODULE, FailRecord.class);
        // Sanity: stock does fail, and does skip.
        assertThrows(Exception.class, () -> VANILLA.readValue(doc, FailParent.class));
        assertNotNull(VANILLA.readValue(doc, SkipParent.class).child);
    }

    @Test
    public void testBuilderWithMethodReturningTarget() throws Exception {
        assertSameOutcome(VANILLA, MODULE, "{\"v\":3}", Wt.class, w -> w.v);
        assertEquals(30, MODULE.readValue("{\"v\":3}", Wt.class).v);
        assertGeneratedReader(MODULE, Wt.class);
    }

    @Test
    public void testBuilderReplacedByNullIsReported() throws Exception {
        DeserializationProblemHandler toNull = new DeserializationProblemHandler() {
            @Override
            public Object handleInstantiationProblem(DeserializationContext ctxt,
                    Class<?> instClass, Object argument, Throwable t) {
                return null;
            }
        };
        ObjectMapper vanilla = JsonMapper.builder().addHandler(toNull).build();
        ObjectMapper module = mapperBuilder().addHandler(toNull).build();
        assertSameOutcome(vanilla, module, "{}", Boom.class, b -> b);
        assertThrows(Exception.class, () -> vanilla.readValue("{}", Boom.class));
        assertGeneratedReader(module, Boom.class);
    }

    @Test
    public void testPrimitiveComponentStockValueWidens() throws Exception {
        assertSameOutcome(VANILLA, MODULE, "{\"v\":5,\"s\":\"x\"}", LongRecord.class,
                r -> r.toString());
        assertEquals(new LongRecord(5L, "x"),
                MODULE.readValue("{\"v\":5,\"s\":\"x\"}", LongRecord.class));
        assertSameOutcome(VANILLA, MODULE, "{\"v\":5,\"s\":\"x\"}", DoubleRecord.class,
                r -> r.toString());
        // Integer cannot become boolean: both fail in the creator call.
        assertSameOutcome(VANILLA, MODULE, "{\"v\":5,\"s\":\"x\"}", BoolRecord.class,
                r -> r.toString());
        assertGeneratedReader(MODULE, LongRecord.class);
        assertGeneratedReader(MODULE, BoolRecord.class);
    }

    @Test
    public void testForwardReferenceInRecordComponent() throws Exception {
        ObjectMapper vanilla = JsonMapper.builder()
                .disable(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS).build();
        ObjectMapper module = mapperBuilder()
                .disable(DeserializationFeature.FAIL_ON_UNRESOLVED_OBJECT_IDS).build();
        // Dangling: the component stays null.
        assertSameOutcome(vanilla, module, "{\"ref\":7,\"tag\":\"t\"}", RefHolder.class,
                h -> h.ref() + "/" + h.tag());
        assertGeneratedReader(module, RefHolder.class);
        // Resolved later: stock reports that a record has no fallback setter.
        String doc = "{\"holder\":{\"ref\":1,\"tag\":\"t\"},\"items\":[{\"id\":1,\"name\":\"a\"}]}";
        assertSameOutcome(VANILLA, MODULE, doc, RefDoc.class, d -> d.holder + "/" + d.items);
        assertThrows(Exception.class, () -> VANILLA.readValue(doc, RefDoc.class));
    }

    // A problem handler replaces the failed construction with null after the
    // forward reference has resolved: with no record to apply it to, stock
    // drops the resolved value ([databind#6325]).
    @Test
    public void testForwardReferenceResolvedBeforeNullRecord() throws Exception {
        DeserializationProblemHandler toNull = new DeserializationProblemHandler() {
            @Override
            public Object handleInstantiationProblem(DeserializationContext ctxt,
                    Class<?> instClass, Object argument, Throwable t) {
                return null;
            }
        };
        ObjectMapper vanilla = JsonMapper.builder().addHandler(toNull).build();
        ObjectMapper module = mapperBuilder().addHandler(toNull).build();
        String doc = "{\"ref\":1,\"items\":[{\"id\":1,\"name\":\"a\"}],\"tag\":-1}";
        assertGeneratedReader(module, RefPair.class);
        assertSameOutcome(vanilla, module, doc, RefPair.class, r -> r);
        // With an unknown property too, stock reports that first.
        String unknown = doc.replace("{\"ref\"", "{\"extra\":true,\"ref\"");
        ObjectMapper strictVanilla = vanilla.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        ObjectMapper strictModule = module.rebuild()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        assertThrows(UnrecognizedPropertyException.class,
                () -> strictVanilla.readValue(unknown, RefPair.class));
        assertSameOutcome(strictVanilla, strictModule, unknown, RefPair.class, r -> r);
        // Without the failure both build the record with the reference set.
        String ok = doc.replace("-1", "2");
        assertSameOutcome(vanilla, module, ok, RefPair.class,
                r -> r.ref().get().name + "/" + r.items().get(0).name + "/" + r.tag());
    }

    // A failing builder property is reported under the name in the input,
    // as stock's general builder loop does: here the alias.
    @Test
    public void testBuilderFailureReportsInputName() throws Exception {
        assertGeneratedReader(MODULE, Aliased.class);
        for (String doc : new String[] { "{\"alt\":\"x\"}", "{\"alt\":-1}", "{\"v\":-1}" }) {
            Exception expected = assertThrows(Exception.class,
                    () -> VANILLA.readValue(doc, Aliased.class));
            Exception actual = assertThrows(Exception.class,
                    () -> MODULE.readValue(doc, Aliased.class));
            assertEquals(expected.getClass(), actual.getClass(), doc);
            assertEquals(expected.getMessage(), actual.getMessage(), doc);
        }
        Exception e = assertThrows(Exception.class,
                () -> MODULE.readValue("{\"alt\":-1}", Aliased.class));
        assertTrue(e.getMessage().contains("[\"alt\"]"), e.getMessage());
    }

    // The same after a type id that follows a buffered property: the
    // failing property is read from the live stream, past the buffer.
    @Test
    public void testBuilderFailureNameAfterBufferedTypeId() throws Exception {
        String doc = "{\"aa\":\"x\",\"type\":\"sub\",\"z\":-1}";
        Exception expected = assertThrows(Exception.class,
                () -> VANILLA.readValue(doc, AliasedBase.class));
        Exception actual = assertThrows(Exception.class,
                () -> MODULE.readValue(doc, AliasedBase.class));
        // The subtype reader sits behind the type deserializer: the
        // generated codec's failure helper shows that it reported this.
        assertTrue(Arrays.stream(actual.getStackTrace()).anyMatch(
                f -> f.getClassName().equals(GeneratedBuilderBase.class.getName())),
                "failure should come from the generated builder codec");
        assertEquals(expected.getClass(), actual.getClass());
        assertEquals(expected.getMessage(), actual.getMessage());
        assertTrue(actual.getMessage().contains("[\"z\"]"), actual.getMessage());
        assertSameOutcome(VANILLA, MODULE, doc.replace("-1", "2"), AliasedBase.class,
                b -> ((AliasedSub) b).a + "/" + ((AliasedSub) b).z);
    }
}
