package com.fasterxml.jackson.module.afterburner.ser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class SerializeWithViewTest extends AfterburnerTestBase
{
    @JsonPropertyOrder({ "a", "b" })
    static class Bean {
        @JsonView({ String.class })
        public int a;

        @JsonView({ Integer.class, Character.class })
        public int b;
    }
    
    @Test
    public void testWriterWithView() throws Exception
    {
        ObjectMapper mapper = newObjectMapper();

        String json = mapper.writeValueAsString(new Bean());
        // by default: both fields serialized
        assertEquals("{\"a\":0,\"b\":0}", json);

        // but with view enabled, just one
        json = mapper.writerWithView(Integer.class).writeValueAsString(new Bean());
        assertEquals("{\"b\":0}", json);

    }
}
