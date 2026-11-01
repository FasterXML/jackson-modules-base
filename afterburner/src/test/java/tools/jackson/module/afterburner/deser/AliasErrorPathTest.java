package tools.jackson.module.afterburner.deser;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonMerge;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.*;
import tools.jackson.databind.deser.DeserializationProblemHandler;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Path of an exception for a failing property must name the property as found
 * in input (which may be an alias), and only once.
 */
public class AliasErrorPathTest extends AfterburnerTestBase
{
    // Up to 6 properties: SuperSonicUnrolledDeserializer
    public static class SmallBean {
        @JsonAlias("a1") public int a;
        @JsonAlias("b1") public int b;
    }

    // More than 6 properties: SuperSonicBeanDeserializer
    public static class BigBean {
        @JsonAlias("a1") public int a;
        @JsonAlias("b1") public int b;
        public int c, d, e, f;
        @JsonAlias("g1") public int g;
    }

    public static class PlainBigBean {
        public int a, b, c, d, e, f, g;
    }

    public static class MergeHolder {
        @JsonMerge public BigBean big = new BigBean();
    }

    private static final List<Class<?>> BEANS = List.of(SmallBean.class, BigBean.class);

    // Afterburner's optimized deserializers need "vanilla" processing
    private final ObjectMapper MAPPER = _mapperBuilder().build();

    @Test
    public void aliasAndCanonicalName() throws Exception {
        for (Class<?> type : BEANS) {
            ObjectReader r = MAPPER.readerFor(type);
            _verifyPath(r, "{'a1':'bad'}", "a1");
            _verifyPath(r, "{'a':1,'b1':'bad'}", "b1");
            _verifyPath(r, "{'a':'bad'}", "a");
            // Not scalar: name must be found at these tokens too
            _verifyPath(r, "{'a1':{}}", "a1");
            _verifyPath(r, "{'a1':[]}", "a1");
        }
    }

    // Past the rolled out first properties of SuperSonicBeanDeserializer
    @Test
    public void laterProperties() throws Exception {
        ObjectReader r = MAPPER.readerFor(BigBean.class);
        _verifyPath(r, "{'a':1,'b':2,'c':3,'d':4,'e':5,'f':6,'g1':'bad'}", "g1");
        _verifyPath(r, "{'a':1,'b':2,'c':3,'d':4,'e':5,'f':6,'g':'bad'}", "g");
    }

    // No aliases, no case-insensitivity: input name is not read
    @Test
    public void noInputNames() throws Exception {
        _verifyPath(MAPPER.readerFor(PlainBigBean.class), "{'b':'bad'}", "b");
    }

    @Test
    public void caseInsensitive() throws Exception {
        final ObjectMapper mapper = _mapperBuilder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .build();
        for (Class<?> type : BEANS) {
            _verifyPath(mapper.readerFor(type), "{'A':'bad'}", "A");
        }
    }

    @Test
    public void forUpdate() throws Exception {
        for (Object bean : List.of(new SmallBean(), new BigBean())) {
            ObjectReader r = MAPPER.readerForUpdating(bean);
            _verifyPath(r, "{'a1':'bad'}", "a1");
            _verifyPath(r, "{'a':1,'b1':'bad'}", "b1");
        }
    }

    // Parser already pointing to the first property name
    @Test
    public void forUpdateFromPropertyName() throws Exception {
        try (JsonParser p = MAPPER.createParser(a2q("{'a1':'bad'}"))) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            DatabindException e = assertThrows(DatabindException.class,
                    () -> MAPPER.readerForUpdating(new BigBean()).readValue(p));
            assertEquals(List.of("a1"), _path(e));
            assertTrue(_hasFrame(e, "_deserializeDisordered"));
        }
    }

    // Not an Object: nothing to update, bean is left as is, same as stock
    @Test
    public void updateFromNonObject() throws Exception {
        for (ObjectMapper mapper : List.of(newVanillaJSONMapper(), MAPPER)) {
            for (Object bean : List.of(new SmallBean(), new BigBean())) {
                assertSame(bean, mapper.readerForUpdating(bean).readValue("12"));
            }
            assertNotNull(mapper.readValue(a2q("{'big':12}"), MergeHolder.class).big);
        }
    }

    // Failure is reported once, not also for earlier property
    @Test
    public void unknownProperty() throws Exception {
        final ObjectMapper mapper = _mapperBuilder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        for (Class<?> type : BEANS) {
            _verifyPath(mapper.readerFor(type), "{'zzz':1}", "zzz");
            _verifyPath(mapper.readerFor(type), "{'a':1,'zzz':1}", "zzz");
        }
    }

    // Exception that a problem handler throws is not wrapped, same as stock
    @Test
    public void unknownPropertyHandlerFailure() throws Exception {
        final DeserializationProblemHandler handler = new DeserializationProblemHandler() {
            @Override
            public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser p,
                    ValueDeserializer<?> deser, Object beanOrClass, String propName) {
                throw new IllegalStateException("handler failure");
            }
        };
        final ObjectMapper afterburner = _mapperBuilder().addHandler(handler).build();
        final ObjectMapper stock = newVanillaJSONMapper().rebuild().addHandler(handler).build();
        final String json = a2q("{'a':1,'zzz':1}");
        for (Class<?> type : BEANS) {
            assertThrows(IllegalStateException.class,
                    () -> stock.readerFor(type).readValue(json));
            assertTrue(_hasFrame(assertThrows(IllegalStateException.class,
                    () -> afterburner.readerFor(type).readValue(json)),
                    "_deserializeDisordered"));
        }
    }

    // Parser failures are not failures of a property: no path, same as stock
    @Test
    public void parserFailures() throws Exception {
        final ObjectMapper stock = newVanillaJSONMapper();
        for (String json : List.of("{ bad", "{'a':bad", "{'a':1, bad", "{'a':1,'b':bad", "{'b':1, bad")) {
            for (ObjectReader r : List.of(
                    stock.readerFor(SmallBean.class), stock.readerFor(BigBean.class),
                    MAPPER.readerFor(SmallBean.class), MAPPER.readerFor(BigBean.class),
                    MAPPER.readerForUpdating(new SmallBean()), MAPPER.readerForUpdating(new BigBean()))) {
                assertEquals(List.of(), _path(assertThrows(StreamReadException.class,
                        () -> r.readValue(a2q(json)))), json);
            }
        }
    }

    private static JsonMapper.Builder _mapperBuilder() {
        return afterburnerMapperBuilder()
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION);
    }

    private static void _verifyPath(ObjectReader r, String json, String expName) {
        DatabindException e = assertThrows(DatabindException.class,
                () -> r.readValue(a2q(json)));
        assertEquals(List.of(expName), _path(e));
        assertTrue(_hasFrame(e, "deserialize"), "should go through Afterburner's deserializer");
    }

    private static List<String> _path(JacksonException e) {
        return e.getPath().stream()
                .map(ref -> ref.getPropertyName())
                .toList();
    }

    private static boolean _hasFrame(Throwable e, String method) {
        return Arrays.stream(e.getStackTrace()).anyMatch(
                f -> f.getClassName().contains("SuperSonic") && f.getMethodName().equals(method));
    }
}
