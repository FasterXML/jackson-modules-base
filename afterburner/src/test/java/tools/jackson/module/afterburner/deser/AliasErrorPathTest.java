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
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.exc.MismatchedInputException;
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

    // Afterburner's optimized deserializers need "vanilla" processing
    private final ObjectMapper MAPPER = _mapperBuilder().build();

    @Test
    public void unrolledAlias() throws Exception {
        _verifyPath(SmallBean.class, """
                {"a1":"bad"}
                """, "a1");
        _verifyPath(SmallBean.class, """
                {"a":1,"b1":"bad"}
                """, "b1");
    }

    @Test
    public void unrolledCanonical() throws Exception {
        _verifyPath(SmallBean.class, """
                {"a":"bad"}
                """, "a");
    }

    @Test
    public void orderedAlias() throws Exception {
        _verifyPath(BigBean.class, """
                {"a1":"bad"}
                """, "a1");
        _verifyPath(BigBean.class, """
                {"a":1,"b1":"bad"}
                """, "b1");
        _verifyPath(BigBean.class, """
                {"a":1,"b":2,"c":3,"d":4,"e":5,"f":6,"g1":"bad"}
                """, "g1");
    }

    @Test
    public void orderedCanonical() throws Exception {
        _verifyPath(BigBean.class, """
                {"a":"bad"}
                """, "a");
        _verifyPath(BigBean.class, """
                {"a":1,"b":2,"c":3,"d":4,"e":5,"f":6,"g":"bad"}
                """, "g");
    }

    @Test
    public void orderedForUpdate() throws Exception {
        _verifyPath(MAPPER.readerForUpdating(new BigBean()), """
                {"a1":"bad"}
                """, "a1");
        _verifyPath(MAPPER.readerForUpdating(new BigBean()), """
                {"a":1,"b1":"bad"}
                """, "b1");
    }

    // Parser already pointing to the first property name. Handled by databind's
    // `BeanDeserializer`, since `_deserializeOther()` calls its own vanilla method
    @Test
    public void orderedFromPropertyName() throws Exception {
        try (JsonParser p = _parserAtFirstName("""
                {"a1":"bad"}
                """)) {
            InvalidFormatException e = assertThrows(InvalidFormatException.class,
                    () -> MAPPER.readerFor(BigBean.class).readValue(p));
            _verifyException(e, "a1");
        }
    }

    // As above, but for update: handled by `SuperSonicBeanDeserializer`
    @Test
    public void orderedForUpdateFromPropertyName() throws Exception {
        try (JsonParser p = _parserAtFirstName("""
                {"a1":"bad"}
                """)) {
            InvalidFormatException e = assertThrows(InvalidFormatException.class,
                    () -> MAPPER.readerForUpdating(new BigBean()).readValue(p));
            _verifyException(e, "a1");
            assertTrue(_hasFrame(e, "_deserializeDisordered"));
        }
    }

    // Not an Object: nothing to update, bean is left as is, same as stock
    @Test
    public void updateFromNonObject() throws Exception {
        final ObjectMapper stock = newVanillaJSONMapper();
        for (ObjectMapper mapper : List.of(stock, MAPPER)) {
            for (String json : List.of("12", "\"x\"", "true")) {
                for (Object bean : List.of(new SmallBean(), new BigBean())) {
                    assertSame(bean, mapper.readerForUpdating(bean).readValue(json), json);
                }
            }
            MergeHolder holder = mapper.readValue("""
                    {"big":12}
                    """, MergeHolder.class);
            assertNotNull(holder.big);
        }
    }

    // Other case: input name differs from logical one without aliases
    @Test
    public void caseInsensitive() throws Exception {
        final ObjectMapper mapper = _mapperBuilder()
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES)
                .build();
        for (Class<?> type : List.of(SmallBean.class, BigBean.class)) {
            _verifyPath(mapper.readerFor(type), """
                    {"A":"bad"}
                    """, "A");
        }
    }

    // No aliases, no case-insensitivity: input name is not read
    @Test
    public void noInputNames() throws Exception {
        _verifyPath(PlainBigBean.class, """
                {"b":"bad"}
                """, "b");
    }

    // Name must be found also when the value is not scalar
    @Test
    public void aliasWithStructuredValue() throws Exception {
        for (Class<?> type : List.of(SmallBean.class, BigBean.class)) {
            for (String json : List.of("""
                    {"a1":{}}
                    """, """
                    {"a1":[]}
                    """)) {
                MismatchedInputException e = assertThrows(MismatchedInputException.class,
                        () -> MAPPER.readerFor(type).readValue(json));
                _verifyException(e, "a1");
            }
        }
    }

    // Failure from property handling is reported once, not also for earlier property
    @Test
    public void unknownProperty() throws Exception {
        final ObjectMapper mapper = _mapperBuilder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
        for (Class<?> type : List.of(SmallBean.class, BigBean.class)) {
            for (String json : List.of("""
                    {"zzz":1}
                    """, """
                    {"a":1,"zzz":1}
                    """)) {
                DatabindException e = assertThrows(DatabindException.class,
                        () -> mapper.readerFor(type).readValue(json));
                assertEquals(List.of("zzz"), _path(e));
            }
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
        for (Class<?> type : List.of(SmallBean.class, BigBean.class)) {
            for (String json : List.of("""
                    {"zzz":1}
                    """, """
                    {"a":1,"zzz":1}
                    """)) {
                assertThrows(IllegalStateException.class,
                        () -> stock.readerFor(type).readValue(json));
                IllegalStateException e = assertThrows(IllegalStateException.class,
                        () -> afterburner.readerFor(type).readValue(json));
                assertTrue(_hasFrame(e, "_deserializeDisordered"));
            }
        }
    }

    // Parser failures are not failures of a property: no path, same as stock
    @Test
    public void parserFailures() throws Exception {
        final ObjectMapper stock = newVanillaJSONMapper();
        for (Class<?> type : List.of(SmallBean.class, BigBean.class)) {
            for (String json : List.of(
                    "{ bad",
                    "{\"a\":bad",
                    "{\"a\":1, bad",
                    "{\"a\":1,\"b\":bad",
                    "{\"b\":1, bad")) {
                for (ObjectReader r : List.of(stock.readerFor(type),
                        MAPPER.readerFor(type))) {
                    assertEquals(List.of(), _path(
                            assertThrows(StreamReadException.class, () -> r.readValue(json))),
                            json);
                }
                assertEquals(List.of(), _path(assertThrows(StreamReadException.class,
                        () -> MAPPER.readerForUpdating(type.getDeclaredConstructor().newInstance())
                                .readValue(json))), json);
            }
        }
    }

    private static JsonMapper.Builder _mapperBuilder() {
        return afterburnerMapperBuilder()
                .enable(MapperFeature.DEFAULT_VIEW_INCLUSION);
    }

    private JsonParser _parserAtFirstName(String json) {
        JsonParser p = MAPPER.createParser(json);
        assertToken(JsonToken.START_OBJECT, p.nextToken());
        assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
        return p;
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

    private void _verifyPath(Class<?> type, String json, String expName) {
        _verifyPath(MAPPER.readerFor(type), json, expName);
    }

    private void _verifyPath(ObjectReader r, String json, String expName) {
        _verifyException(assertThrows(InvalidFormatException.class, () -> r.readValue(json)),
                expName);
    }

    private void _verifyException(DatabindException e, String expName) {
        assertEquals(List.of(expName), _path(e));
        assertTrue(Arrays.stream(e.getStackTrace()).anyMatch(
                f -> f.getClassName().contains("SuperSonic")),
                "should go through Afterburner's deserializer");
    }
}
