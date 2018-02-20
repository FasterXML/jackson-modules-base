package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestConstructors extends AfterburnerTestBase
{
    // [Issue#34]
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    private static class Row
    {
        private String id;

        public String _id() { return id; }
    }
    
    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    // For [Issue#34]
    public void testPrivateConstructor() throws Exception
    {
        ObjectMapper mapper = newAfterburnerMapper();
        Row row = mapper.readValue("{\"id\":\"x\"}", Row.class);
        assertNotNull(row);
        assertEquals("x", row._id());
        
    }
}
