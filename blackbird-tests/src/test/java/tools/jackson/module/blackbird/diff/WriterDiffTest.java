package tools.jackson.module.blackbird.diff;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonApplyView;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

import tools.jackson.core.JsonGenerator;
import tools.jackson.core.StreamWriteConstraints;
import tools.jackson.core.exc.StreamConstraintsException;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.module.SimpleModule;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generated writers: values with type serializers and non-default null
 * serializers stay with the stock property writer, a value that is the bean
 * itself gets stock self-reference handling, applied views apply, and
 * failures carry the stock exception, message and reference path.
 */
public class WriterDiffTest extends DifferentialTestBase
{
    public static class Plain {
        public String name = "p";
        public int count = 1;
    }

    /*
    /**********************************************************************
    /* Type serializers, null serializers, applied views
    /**********************************************************************
     */

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "@t")
    @JsonSubTypes({ @JsonSubTypes.Type(value = TypedRec.class, name = "rec") })
    public record TypedRec(String v) {}

    public static class HoldsTypedRec {
        public String name = "h";
        public TypedRec rec = new TypedRec("x");
    }

    @Test
    public void childWithTypeInfoWritesTypeId() {
        Mappers m = mappers();
        String json = assertSameWrite(m, new HoldsTypedRec()).get();
        assertTrue(json.contains("\"@t\":\"rec\""), json);
        String layout = assertGeneratedWriter(m.blackbird(), HoldsTypedRec.class);
        assertArm(layout, "rec=STOCK");
        assertArm(layout, "name=STRING");
    }

    public record PlainRec(String v) {}

    public static class HoldsRecord {
        public PlainRec rec = new PlainRec("x");
        public String name = "h";
    }

    @Test
    public void defaultTypingWritesTypeIds() {
        Mappers m = mappers(b -> b.activateDefaultTypingAsProperty(
                BasicPolymorphicTypeValidator.builder().allowIfBaseType(Object.class).build(),
                DefaultTyping.NON_FINAL_AND_RECORDS, "@class"));
        String json = assertSameWrite(m, new HoldsRecord()).get();
        assertTrue(json.contains("\"@class\""), json);
        assertArm(assertGeneratedWriter(m.blackbird(), HoldsRecord.class), "rec=STOCK");
    }

    public static class EmptyForNull extends ValueSerializer<Object> {
        @Override
        public void serialize(Object value, JsonGenerator g, SerializationContext ctxt) {
            g.writeString("");
        }
    }

    public static class NullsUsing {
        @JsonSerialize(nullsUsing = EmptyForNull.class)
        public String a;
        public String b;
    }

    @Test
    public void propertyNullSerializer() {
        Mappers m = mappers();
        assertEquals("{\"a\":\"\",\"b\":null}", assertSameWrite(m, new NullsUsing()).get());
        String layout = assertGeneratedWriter(m.blackbird(), NullsUsing.class);
        assertArm(layout, "a=STOCK");
        assertArm(layout, "b=STRING");
    }

    // Final value types get statically assigned serializers, so without the
    // null-serializer gate a generated child arm would write them.
    public static final class FinalKid {
        public String name = "k";
    }

    public static class HoldsChild {
        public FinalKid kid;
        public String s;
        public int n = 3;
    }

    @Test
    public void defaultNullSerializer() {
        Mappers m = mappers(b -> b.addModule(new SimpleModule("nulls")
                .setDefaultNullValueSerializer(new EmptyForNull())));
        assertEquals("{\"kid\":\"\",\"n\":3,\"s\":\"\"}",
                assertSameWrite(m, new HoldsChild()).get());
        String layout = assertGeneratedWriter(m.blackbird(), HoldsChild.class);
        assertArm(layout, "kid=STOCK");
        assertArm(layout, "s=STOCK");
        // Primitives are never null: they keep the generated write.
        assertArm(layout, "n=INT");
    }

    public static class ViewA { }
    public static class ViewB { }

    public static final class Viewed {
        @JsonView(ViewA.class)
        public String a = "1";
        @JsonView(ViewB.class)
        public String b = "2";
    }

    public static class AppliesView {
        @JsonView(ViewA.class)
        @JsonApplyView(ViewB.class)
        public Viewed inner = new Viewed();
        @JsonView(ViewA.class)
        public Viewed plain = new Viewed();
    }

    @Test
    public void appliedViewApplies() {
        Mappers m = mappers();
        Outcome stock = new Outcome(m.stock().writerWithView(ViewA.class)
                .writeValueAsString(new AppliesView()), null);
        Outcome blackbird = new Outcome(m.blackbird().writerWithView(ViewA.class)
                .writeValueAsString(new AppliesView()), null);
        assertEquals(stock.describe(), blackbird.describe());
        assertEquals("{\"inner\":{\"b\":\"2\"},\"plain\":{\"a\":\"1\"}}", blackbird.get());
        String layout = assertGeneratedWriter(m.blackbird(), AppliesView.class);
        assertArm(layout, "inner=STOCK");
        assertArm(layout, "plain=CHILD");
    }

    /*
    /**********************************************************************
    /* Failures and the reference path
    /**********************************************************************
     */

    public static final class ThrowingGetter {
        public String a = "x";

        public String getB() {
            throw new IllegalStateException("getter boom");
        }
    }

    public static final class Inner {
        public ThrowingGetter tg = new ThrowingGetter();
    }

    public static class Outer {
        public Inner inner = new Inner();
    }

    @Test
    public void getterFailureCarriesReferencePath() {
        Mappers m = mappers();
        DatabindException e = assertSameWrite(m, new ThrowingGetter())
                .error(DatabindException.class);
        assertTrue(e.getMessage().contains("getter boom"), e.getMessage());
        assertTrue(e.getMessage().contains("ThrowingGetter[\"b\"]"), e.getMessage());
        DatabindException nested = assertSameWrite(m, new Outer())
                .error(DatabindException.class);
        assertTrue(nested.getMessage().contains("Outer[\"inner\"]->"), nested.getMessage());
        assertGeneratedWriter(m.blackbird(), ThrowingGetter.class);
        assertArm(assertGeneratedWriter(m.blackbird(), Outer.class), "inner=CHILD");
    }

    @Test
    public void getterFailureWithoutWrapping() {
        Mappers m = mappers(b -> b.disable(SerializationFeature.WRAP_EXCEPTIONS));
        assertSameWrite(m, new ThrowingGetter()).error(IllegalStateException.class);
        assertSameWrite(m, new Outer()).error(IllegalStateException.class);
    }

    public static class CycA {
        public String name = "a";
        public CycB b;
    }

    public static class CycB {
        public String name = "b";
        public CycA a;
    }

    private static CycA cycle() {
        CycA a = new CycA();
        CycB b = new CycB();
        a.b = b;
        b.a = a;
        return a;
    }

    @Test
    public void cycleHitsNestingLimitWithStockPath() {
        Mappers m = mappers();
        StreamConstraintsException e = assertSameWrite(m, cycle())
                .error(StreamConstraintsException.class);
        assertTrue(e.getMessage().contains("CycA[\"b\"]->"), e.getMessage());
        assertTrue(e.getMessage().contains("CycB[\"a\"]->"), e.getMessage());
        assertArm(assertGeneratedWriter(m.blackbird(), CycA.class), "name=STRING");
    }

    @Test
    public void infiniteRecursion() {
        // A class first initialized near the overflow would fail to
        // initialize for good, so the exception paths run once up front.
        assertSameWrite(mappers(), new ThrowingGetter());
        assertSameWrite(mappers(), cycle());
        // No nesting limit: the stack overflows first.
        JsonFactory unlimited = JsonFactory.builder()
                .streamWriteConstraints(StreamWriteConstraints.builder()
                        .maxNestingDepth(Integer.MAX_VALUE).build())
                .build();
        Mappers m = new Mappers(JsonMapper.builder(unlimited).build(),
                JsonMapper.builder(unlimited)
                        .addModule(new tools.jackson.module.blackbird.BlackbirdModule()).build());
        Outcome stock = write(m.stock(), cycle());
        Outcome blackbird = write(m.blackbird(), cycle());
        DatabindException se = stock.error(DatabindException.class);
        DatabindException be = blackbird.error(DatabindException.class);
        assertTrue(se.getMessage().startsWith("Infinite recursion (StackOverflowError)"),
                se.getMessage());
        // The overflow depth differs between the paths, and databind keeps a
        // bounded number of path entries, so the chains are cut at different
        // points of the same alternation.
        assertEquals(head(se.getMessage()), head(be.getMessage()));
        assertEquals(cycleEntries(), Set.copyOf(chain(se.getMessage())));
        assertEquals(cycleEntries(), Set.copyOf(chain(be.getMessage())));
    }

    private static final String CHAIN_START = "(through reference chain: ";

    private static String head(String message) {
        return message.substring(0, message.indexOf(CHAIN_START));
    }

    private static List<String> chain(String message) {
        String chain = message.substring(message.indexOf(CHAIN_START) + CHAIN_START.length(),
                message.lastIndexOf(')'));
        return Arrays.stream(chain.split("->")).filter(e -> !e.equals("...")).toList();
    }

    private static Set<String> cycleEntries() {
        return Set.of(CycA.class.getName() + "[\"b\"]", CycB.class.getName() + "[\"a\"]");
    }

    /*
    /**********************************************************************
    /* Self-references
    /**********************************************************************
     */

    public static class Selfie {
        public String name = "s";

        public Selfie getSelf() {
            return this;
        }
    }

    @Test
    public void directSelfReferenceFails() {
        Mappers m = mappers();
        InvalidDefinitionException e = assertSameWrite(m, new Selfie())
                .error(InvalidDefinitionException.class);
        assertTrue(e.getMessage().startsWith("Direct self-reference leading to cycle"),
                e.getMessage());
        assertGeneratedWriter(m.blackbird(), Selfie.class);
    }

    @Test
    public void directSelfReferenceAsNull() {
        Mappers m = mappers(b -> b.disable(SerializationFeature.FAIL_ON_SELF_REFERENCES)
                .enable(SerializationFeature.WRITE_SELF_REFERENCES_AS_NULL));
        assertEquals("{\"name\":\"s\",\"self\":null}", assertSameWrite(m, new Selfie()).get());
    }
}
