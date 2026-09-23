package tools.jackson.module.blackbird.deser;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.exc.MismatchedInputException;
import tools.jackson.databind.exc.UnrecognizedPropertyException;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Behavior-parity regression tests against stock databind: quoted-scalar
 * coercion, unknown-property handling under per-call config and problem
 * handlers, record required and missing components, {@code @JsonUnwrapped}
 * with codec-eligible children, PROPERTY_NAME entry, and modifier
 * serializability. Every case compares against a vanilla mapper.
 */
public class BBCodecCompatibilityTest extends BlackbirdTestBase
{
    public static class ScalarBean {
        public int i;
        public long l;
        public boolean b;
        public String s;
    }

    public record ReqRecord(@JsonProperty(required = true) String name, int count) { }

    public record PlainRecord(String name, int count) { }

    public static class Child {
        public int value;
        public String tag;
    }

    public static class UnwrappedParent {
        public int a;
        @JsonUnwrapped
        public Child child;
    }

    public static class PrefixedParent {
        public int a;
        @JsonUnwrapped(prefix = "foo.")
        public Child child;
    }

    @JsonIgnoreProperties({ "skipMe" })
    public static class IgnoralBean {
        public int i;
    }

    private final ObjectMapper mapper = newObjectMapper();
    private final ObjectMapper vanilla = newVanillaJSONMapper();

    @Test
    public void testQuotedScalarCoercion() throws Exception {
        String doc = a2q("{'i':'123','l':'456','b':'true','s':'x'}");
        ScalarBean exp = vanilla.readValue(doc, ScalarBean.class);
        ScalarBean act = mapper.readValue(doc, ScalarBean.class);
        assertEquals(exp.i, act.i);
        assertEquals(exp.l, act.l);
        assertEquals(exp.b, act.b);
        assertEquals(exp.s, act.s);
    }

    @Test
    public void testFloatToIntCoercion() throws Exception {
        String doc = a2q("{'i':1.5,'l':2.5}");
        ScalarBean exp = vanilla.readValue(doc, ScalarBean.class);
        ScalarBean act = mapper.readValue(doc, ScalarBean.class);
        assertEquals(exp.i, act.i);
        assertEquals(exp.l, act.l);
    }

    @Test
    public void testScalarFromNumberToken() throws Exception {
        // String property fed a number token: stock coerces, so must we.
        String doc = a2q("{'s':42,'b':true,'i':7}");
        ScalarBean exp = vanilla.readValue(doc, ScalarBean.class);
        ScalarBean act = mapper.readValue(doc, ScalarBean.class);
        assertEquals(exp.s, act.s);
        assertEquals(exp.b, act.b);
        assertEquals(exp.i, act.i);
    }

    @Test
    public void testPerCallFailOnUnknownProperties() throws Exception {
        String doc = a2q("{'i':1,'nope':{'x':2},'s':'y'}");
        // Default (disabled): unknown skips, other properties land.
        ScalarBean ok = mapper.readValue(doc, ScalarBean.class);
        assertEquals(1, ok.i);
        assertEquals("y", ok.s);
        // Per-call enable must fail, exactly like vanilla.
        assertThrows(UnrecognizedPropertyException.class, () -> vanilla
                .readerFor(ScalarBean.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(doc));
        assertThrows(UnrecognizedPropertyException.class, () -> mapper
                .readerFor(ScalarBean.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(doc));
    }

    @Test
    public void testProblemHandlerSeesUnknownProperty() throws Exception {
        List<String> seen = new ArrayList<>();
        ObjectMapper m = mapperBuilder().addHandler(new DeserializationProblemHandler() {
            @Override
            public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
                    ValueDeserializer<?> deserializer, Object beanOrClass, String propertyName) {
                seen.add(propertyName);
                p.skipChildren();
                return true;
            }
        }).build();
        ScalarBean bean = m.readValue(a2q("{'i':1,'mystery':[1,2]}"), ScalarBean.class);
        assertEquals(1, bean.i);
        assertEquals(List.of("mystery"), seen);
    }

    @Test
    public void testIgnoredPropertiesKeepStockSemantics() throws Exception {
        String doc = a2q("{'i':1,'skipMe':{'x':2}}");
        IgnoralBean exp = vanilla.readerFor(IgnoralBean.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(doc);
        IgnoralBean act = mapper.readerFor(IgnoralBean.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(doc);
        assertEquals(exp.i, act.i);
        assertThrows(UnrecognizedPropertyException.class, () -> mapper
                .readerFor(IgnoralBean.class)
                .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .readValue(a2q("{'i':1,'other':2}")));
    }

    @Test
    public void testRequiredRecordComponentMissing() throws Exception {
        String doc = a2q("{'count':3}");
        MismatchedInputException expEx = assertThrows(MismatchedInputException.class,
                () -> vanilla.readValue(doc, ReqRecord.class));
        MismatchedInputException actEx = assertThrows(MismatchedInputException.class,
                () -> mapper.readValue(doc, ReqRecord.class));
        assertTrue(expEx.getMessage().contains("Missing required creator property 'name'"),
                expEx.getMessage());
        assertTrue(actEx.getMessage().contains("Missing required creator property 'name'"),
                actEx.getMessage());
        // Present case unaffected.
        assertEquals(vanilla.readValue(a2q("{'name':'x','count':3}"), ReqRecord.class),
                mapper.readValue(a2q("{'name':'x','count':3}"), ReqRecord.class));
    }

    @Test
    public void testPerCallFailOnMissingCreatorProperties() throws Exception {
        String doc = a2q("{'name':'x'}");
        // Default: missing component takes its default value.
        assertEquals(vanilla.readValue(doc, PlainRecord.class),
                mapper.readValue(doc, PlainRecord.class));
        assertThrows(MismatchedInputException.class, () -> vanilla
                .readerFor(PlainRecord.class)
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .readValue(doc));
        assertThrows(MismatchedInputException.class, () -> mapper
                .readerFor(PlainRecord.class)
                .with(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                .readValue(doc));
    }

    @Test
    public void testUnwrappedChildWithCodec() throws Exception {
        UnwrappedParent p = new UnwrappedParent();
        p.a = 1;
        p.child = new Child();
        p.child.value = 3;
        p.child.tag = "t";
        String expJson = vanilla.writeValueAsString(p);
        assertEquals(expJson, mapper.writeValueAsString(p));

        UnwrappedParent exp = vanilla.readValue(expJson, UnwrappedParent.class);
        UnwrappedParent act = mapper.readValue(expJson, UnwrappedParent.class);
        assertNotNull(act.child);
        assertEquals(exp.a, act.a);
        assertEquals(exp.child.value, act.child.value);
        assertEquals(exp.child.tag, act.child.tag);
    }

    @Test
    public void testPrefixedUnwrappedChildWithCodec() throws Exception {
        PrefixedParent p = new PrefixedParent();
        p.a = 1;
        p.child = new Child();
        p.child.value = 3;
        p.child.tag = "t";
        String expJson = vanilla.writeValueAsString(p);
        assertEquals(expJson, mapper.writeValueAsString(p));

        PrefixedParent exp = vanilla.readValue(expJson, PrefixedParent.class);
        PrefixedParent act = mapper.readValue(expJson, PrefixedParent.class);
        assertNotNull(act.child);
        assertEquals(exp.a, act.a);
        assertEquals(exp.child.value, act.child.value);
        assertEquals(exp.child.tag, act.child.tag);
    }

    @Test
    public void testPropertyNameEntry() throws Exception {
        String doc = a2q("{'i':7,'l':8,'b':true,'s':'y'}");
        ScalarBean exp;
        try (JsonParser p = vanilla.createParser(doc)) {
            p.nextToken();
            p.nextToken();
            exp = vanilla.readValue(p, ScalarBean.class);
        }
        ScalarBean act;
        try (JsonParser p = mapper.createParser(doc)) {
            p.nextToken();
            p.nextToken();
            act = mapper.readValue(p, ScalarBean.class);
        }
        assertEquals(exp.i, act.i);
        assertEquals(exp.l, act.l);
        assertEquals(exp.b, act.b);
        assertEquals(exp.s, act.s);
    }

    @Test
    public void testMapperUsableAfterJdkSerialization() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(newObjectMapper());
        }
        ObjectMapper revived;
        try (ObjectInputStream in = new ObjectInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            revived = (ObjectMapper) in.readObject();
        }
        // Building a deserializer runs the modifier's updateBuilder, which
        // uses the transient ThreadLocal that deserialization nulled out.
        ScalarBean bean = revived.readValue(a2q("{'i':5,'s':'z'}"), ScalarBean.class);
        assertEquals(5, bean.i);
        assertEquals("z", bean.s);
        assertEquals(newObjectMapper().writeValueAsString(bean),
                revived.writeValueAsString(bean));
    }
}
