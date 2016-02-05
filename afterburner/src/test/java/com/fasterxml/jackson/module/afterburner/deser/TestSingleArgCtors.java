package com.fasterxml.jackson.module.afterburner.deser;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class TestSingleArgCtors extends AfterburnerTestBase
{
    static class Node {
        public String name;
        
        public int value;

        public Node() { }

        @JsonCreator
        public Node(String n) {
            name = n;
            value = -1;
        }
    }
    
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = mapperWithModule();
    
    public void testSingleStringArgCtor() throws Exception
    {
        Node bean = MAPPER.readValue(quote("Foobar"), Node.class);
        assertNotNull(bean);
        assertEquals(-1, bean.value);
        assertEquals("Foobar", bean.name);
    }

}
