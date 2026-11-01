package tools.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [modules-base#343]: SuperSonicBeanDeserializer.deserializeFromObject threw on a
// trailing unknown property after all known ordered properties were consumed, even
// with FAIL_ON_UNKNOWN_PROPERTIES disabled. That override no longer exists: this
// stays as a regression test for the same input.
public class UnknownProperty343Test extends AfterburnerTestBase
{
    // Marker class used purely to force the bean deserializer to operate in
    // non-vanilla mode (view processing active), which routes through
    // BeanDeserializer.deserializeFromObject rather than the vanilla fast path.
    static class DefaultView { }

    // 8 properties -> SuperSonicBeanDeserializer (threshold is > 6)
    @JsonView(DefaultView.class)
    public static class POJO343 {
        @JsonProperty("a") public String a;
        @JsonProperty("b") public String b;
        @JsonProperty("c") public String c;
        @JsonProperty("d") public String d;
        @JsonProperty("e") public String e;
        @JsonProperty("f") public String f;
        @JsonProperty("g") public String g;
        @JsonProperty("h") public String h;
    }

    // No views: Afterburner's own ordered fast paths handle this one
    public static class Vanilla343 {
        public String a, b, c, d, e, f, g, h;
    }

    private final static String JSON = "{"
            + "\"a\":\"a\",\"b\":\"b\",\"c\":\"c\",\"d\":\"d\","
            + "\"e\":\"e\",\"f\":\"f\",\"g\":\"g\",\"h\":\"h\","
            + "\"i\":\"i\"}";

    private final ObjectMapper MAPPER = afterburnerMapperBuilder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    // Afterburner's optimized deserializers need "vanilla" processing
    private final ObjectMapper VANILLA_MAPPER = afterburnerMapperBuilder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .build();

    @Test
    public void testTrailingUnknownProperty() throws Exception
    {
        POJO343 result = MAPPER.readerWithView(DefaultView.class)
                .forType(POJO343.class)
                .readValue(JSON);
        assertEquals("a", result.a);
        assertEquals("h", result.h);
    }

    @Test
    public void testTrailingUnknownPropertyVanilla() throws Exception
    {
        Vanilla343 result = VANILLA_MAPPER.readValue(JSON, Vanilla343.class);
        assertEquals("a", result.a);
        assertEquals("h", result.h);
    }

    @Test
    public void testTrailingUnknownPropertyVanillaForUpdate() throws Exception
    {
        Vanilla343 bean = new Vanilla343();
        assertSame(bean, VANILLA_MAPPER.readerForUpdating(bean).readValue(JSON));
        assertEquals("a", bean.a);
        assertEquals("h", bean.h);
    }
}
