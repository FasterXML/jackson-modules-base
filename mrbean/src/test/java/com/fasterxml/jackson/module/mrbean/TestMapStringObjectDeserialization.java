package com.fasterxml.jackson.module.mrbean;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestMapStringObjectDeserialization
    extends BaseTest
{

    /**
     * Test simple Map deserialization works.
     */
    public void testMapWithMrbean() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();
        runTest(mapper);
    }

    /**
     * Test simple Map deserialization works.
     */
    public void testMapWithoutMrbean() throws Exception
    {
        ObjectMapper mapper = new ObjectMapper();
        runTest(mapper);
    }

    void runTest(ObjectMapper mapper) throws IOException, JsonParseException, JsonMappingException
    {
        Map<String, Object> map = mapper.readValue("{\"test\":3 }", new TypeReference<Map<String, Object>>() {});
        assertEquals(Collections.singletonMap("test", 3), map);
    }
}
