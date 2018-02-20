package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

public class SerializeWithViewTest extends AfterburnerTestBase
{
    @JsonPropertyOrder({ "a", "b" })
    static class Bean {
        @JsonView({ String.class })
        public int a;

        @JsonView({ Integer.class, Character.class })
        public int b;
    }

    @JsonPropertyOrder({ "a", "b", "c", "d", "e", "f" })
    static class Bean6 {
        @JsonView({ String.class })
        public int a = 1;

        @JsonView({ Integer.class, Character.class })
        public int b = 2;

        public int c = 3;

        @JsonView({ String.class })
        public int d = 4;

        public int e = 5;

        @JsonView({ Integer.class })
        public int f = 6;
    }
    
    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    final private ObjectMapper MAPPER = newAfterburnerMapper();

    public void testWriterWithView() throws Exception
    {
        String json = MAPPER.writeValueAsString(new Bean());
        // by default: both fields serialized
        assertEquals(aposToQuotes("{'a':0,'b':0}"), json);

        // but with view enabled, just one
        json = MAPPER.writerWithView(Integer.class).writeValueAsString(new Bean());
        assertEquals("{\"b\":0}", json);
    }

    public void testBiggerWithView() throws Exception
    {
        String json = MAPPER.writerWithView(Integer.class)
                .writeValueAsString(new Bean6());
        assertEquals(aposToQuotes("{'b':2,'c':3,'e':5,'f':6}"), json);
    }
}
