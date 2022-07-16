package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.annotation.JsonFormat;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.annotation.JsonSerialize;
import tools.jackson.databind.ser.std.ToStringSerializer;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class JDKScalarsSerTest extends AfterburnerTestBase
{
    // [modules-base#117]
    static class Bean117UsingJsonSerialize {
        @JsonSerialize(using = ToStringSerializer.class)
        public int getValue() {
            return 42;
        }
    }

    // [modules-base#118]
    static class Bean118IntUsingJsonFormat {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public int value = 42;
    }

    static class Bean118LongUsingJsonFormat {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public long value = -137L;
    }

    private final ObjectMapper MAPPER = newAfterburnerMapper();
    private final ObjectMapper VANILLA_MAPPER = newVanillaJSONMapper();

    // [modules-base#117]
    public void testIntAsStringWithJsonSerialize() throws Exception
    {
        final String EXP_JSON = "{\"value\":\"42\"}";
        final Object input = new Bean117UsingJsonSerialize();
        assertEquals(EXP_JSON, VANILLA_MAPPER.writeValueAsString(input));
        assertEquals(EXP_JSON, MAPPER.writeValueAsString(input));
    }

    // [modules-base#118]
    public void testIntAsStringWithJsonFormat() throws Exception
    {
        final String EXP_JSON = "{\"value\":\"42\"}";
        final Object input = new Bean118IntUsingJsonFormat();
        assertEquals(EXP_JSON, VANILLA_MAPPER.writeValueAsString(input));
        assertEquals(EXP_JSON, MAPPER.writeValueAsString(input));
    }

    // [modules-base#118]
    public void testLongAsStringWithJsonFormat() throws Exception
    {
        final String EXP_JSON = "{\"value\":\"-137\"}";
        final Object input = new Bean118LongUsingJsonFormat();
        assertEquals(EXP_JSON, VANILLA_MAPPER.writeValueAsString(input));
        assertEquals(EXP_JSON, MAPPER.writeValueAsString(input));
    }
}
