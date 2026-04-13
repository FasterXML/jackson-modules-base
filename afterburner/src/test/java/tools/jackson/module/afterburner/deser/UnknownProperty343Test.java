package tools.jackson.module.afterburner.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

// [modules-base#343]: SuperSonicBeanDeserializer.deserializeFromObject threw on a
// trailing unknown property after all known ordered properties were consumed, even
// with FAIL_ON_UNKNOWN_PROPERTIES disabled.
public class UnknownProperty343Test extends AfterburnerTestBase
{
    // Marker class used purely to force the bean deserializer to operate in
    // non-vanilla mode (view processing active), which routes through
    // BeanDeserializer.deserializeFromObject rather than the vanilla fast path —
    // and thus through SuperSonicBeanDeserializer.deserializeFromObject, which is
    // where issue #343 lives.
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

    private final ObjectMapper MAPPER = afterburnerMapperBuilder()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    @Test
    public void testTrailingUnknownProperty() throws Exception
    {
        String json = "{"
                + "\"a\":\"a\",\"b\":\"b\",\"c\":\"c\",\"d\":\"d\","
                + "\"e\":\"e\",\"f\":\"f\",\"g\":\"g\",\"h\":\"h\","
                + "\"i\":\"i\"}";
        POJO343 result = MAPPER.readerWithView(DefaultView.class)
                .forType(POJO343.class)
                .readValue(json);
        assertEquals("a", result.a);
        assertEquals("h", result.h);
    }
}
