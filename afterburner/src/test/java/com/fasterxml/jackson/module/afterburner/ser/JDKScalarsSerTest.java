package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

// [modules-base#117]
public class JDKScalarsSerTest extends AfterburnerTestBase
{
    static class Bean117UsingJsonSerialize {
        @JsonSerialize(using = ToStringSerializer.class)
        public int getValue() {
            return 42;
        }
    }

    private final ObjectMapper MAPPER = newObjectMapper();
    private final ObjectMapper VANILLA_MAPPER = newVanillaJSONMapper();

    // [modules-base#117]
    public void testIntAsStringWithJsonSerialize() throws Exception
    {
        final String EXP_JSON = "{\"value\":\"42\"}";
        final Object input = new Bean117UsingJsonSerialize();
        assertEquals(EXP_JSON, VANILLA_MAPPER.writeValueAsString(input));
        assertEquals(EXP_JSON, MAPPER.writeValueAsString(input));
    }
}
