package com.fasterxml.jackson.module.blackbird.misc;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class PreventJDKTypeAccessTest extends BlackbirdTestBase
{
    private final ObjectMapper MAPPER = newObjectMapper();
    private final ObjectMapper VANILLA_MAPPER = newVanillaJSONMapper();

    @Test
    public void testJDKThreadroundTrip() throws Exception
    {
        final Object input = Thread.currentThread();
        final String json1 = VANILLA_MAPPER.writeValueAsString(input);
        final String json2 = MAPPER.writeValueAsString(input);

        Map<?,?> map1 = VANILLA_MAPPER.readValue(json1, Map.class);
        Map<?,?> map2 = MAPPER.readValue(json2, Map.class);

        assertEquals(map1.keySet(), map2.keySet());
    }
}
