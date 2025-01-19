package com.fasterxml.jackson.module.blackbird.ser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestRawValues extends BlackbirdTestBase
{
    static class SerializableObject
    {
        public SerializableObject(String v) { value = v; }
        
        @JsonRawValue
        public String value;
    }    

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    private final ObjectMapper MAPPER = newObjectMapper();

    @Test
    public void testAfterBurner() throws Exception
    {
        SerializableObject so = new SerializableObject("[123]");

        assertEquals("{\"value\":[123]}", MAPPER.writeValueAsString(so));
    }    
}
