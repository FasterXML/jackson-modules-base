package com.fasterxml.jackson.module.blackbird.failing;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

// Failing test from "BasicDeserializeTest", see [modules-base#123]
public class BasicDeserialize123Test extends BlackbirdTestBase
{
    // [modules-base#123]: fluent method(s)
    static class Model123 {
        int value = 10;

        protected Model123() { }
        public Model123(int v) { value = v; }

        public int getValue() {
            return value;
        }

        public Model123 setValue(int value) {
            this.value = value;
            return this;
        }
    }

    private final ObjectMapper MAPPER = newObjectMapper();

    // [modules-base#123]
    public void testFluentMethod() throws Exception
    {
        String json = MAPPER.writeValueAsString(new Model123(28));

        Model123 result = MAPPER.readValue(json, Model123.class);
        assertNotNull(result);
        assertEquals(28, result.value);
    }
}
