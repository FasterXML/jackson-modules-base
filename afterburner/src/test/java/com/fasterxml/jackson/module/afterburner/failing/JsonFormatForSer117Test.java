package com.fasterxml.jackson.module.afterburner.failing;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class JsonFormatForSer117Test extends AfterburnerTestBase
{
    static class Bean117UsingJsonFormat {
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public int value = 42;
    }

    private final ObjectMapper MAPPER = newObjectMapper();
    private final ObjectMapper VANILLA_MAPPER = newVanillaJSONMapper();

    public void testIntAsStringWithJsonFormat() throws Exception
    {
        final String EXP_JSON = "{\"value\":\"42\"}";
        final Object input = new Bean117UsingJsonFormat();
        assertEquals(EXP_JSON, VANILLA_MAPPER.writeValueAsString(input));
        assertEquals(EXP_JSON, MAPPER.writeValueAsString(input));
    }
}
