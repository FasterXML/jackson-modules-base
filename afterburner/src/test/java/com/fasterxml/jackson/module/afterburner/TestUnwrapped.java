package com.fasterxml.jackson.module.afterburner;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TestUnwrapped extends AfterburnerTestBase
{
    @JsonPropertyOrder({ "a", "b" })
    static class Wrapper
    {
        public int a;
        
        @JsonUnwrapped(prefix="foo.")
        public Unwrapped b;

        public Wrapper() { }
        public Wrapper(int a, int value) {
            this.a = a;
            b = new Unwrapped(value);
        }
    }

    static class Unwrapped {
        public int value;

        public Unwrapped() { }
        public Unwrapped(int v) { value = v; }
    }
    
    /*
    /**********************************************************
    /* Actual tests
    /**********************************************************
     */

    public void testSimpleSerialize() throws Exception
    {
        final ObjectMapper VANILLA = new ObjectMapper();
        final ObjectMapper BURNER = newAfterburnerMapper();
        Wrapper input = new Wrapper(1, 3);
        String json = VANILLA.writeValueAsString(input);
        assertEquals(json, BURNER.writeValueAsString(input));
    }
    
    public void testUnwrappedDeserialize() throws Exception
    {
        final ObjectMapper VANILLA = new ObjectMapper();
        final ObjectMapper BURNER = newAfterburnerMapper();
        String json = VANILLA.writeValueAsString(new Wrapper(2, 9));
        Wrapper out = BURNER.readValue(json, Wrapper.class);
        assertEquals(2, out.a);
        assertNotNull(out.b);
        assertEquals(9, out.b.value);
    }

}
