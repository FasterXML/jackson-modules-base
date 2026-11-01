package tools.jackson.module.afterburner.deser;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAlias;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.*;
import tools.jackson.databind.exc.InvalidFormatException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.afterburner.AfterburnerModule;
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

    // Afterburner's optimized deserializers need "vanilla" processing
    private final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new AfterburnerModule())
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .build();

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

    // Parser already pointing to the first property name
    @Test
    public void orderedFromPropertyName() throws Exception {
        try (JsonParser p = MAPPER.createParser("""
                {"a1":"bad"}
                """)) {
            assertToken(JsonToken.START_OBJECT, p.nextToken());
            assertToken(JsonToken.PROPERTY_NAME, p.nextToken());
            InvalidFormatException e = assertThrows(InvalidFormatException.class,
                    () -> MAPPER.readerFor(BigBean.class).readValue(p));
            _verifyException(e, "a1");
        }
    }

    private void _verifyPath(Class<?> type, String json, String expName) {
        _verifyPath(MAPPER.readerFor(type), json, expName);
    }

    private void _verifyPath(ObjectReader r, String json, String expName) {
        _verifyException(assertThrows(InvalidFormatException.class, () -> r.readValue(json)),
                expName);
    }

    private void _verifyException(InvalidFormatException e, String expName) {
        assertEquals(List.of(expName), e.getPath().stream()
                .map(ref -> ref.getPropertyName())
                .toList());
        assertTrue(Arrays.stream(e.getStackTrace()).anyMatch(
                f -> f.getClassName().contains("SuperSonic")),
                "should go through Afterburner's deserializer");
    }
}
