package com.fasterxml.jackson.module.blackbird.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class DoubleBoxedArrayConstructorDeser141Test extends BlackbirdTestBase
{
    static class Foo141 {
        @JsonProperty("bar")
        Double[] bar;

        @JsonCreator
        public Foo141(@JsonProperty("bar") Double[] bar) {
          this.bar = bar;
        }
    }

    private final ObjectMapper MAPPER = newObjectMapper();

    @Test
    public void testBoxedDoubleArrayCreator() throws Exception
    {
        Foo141 foo = new Foo141(new Double[] { 2.0, 0.25 });
        String serialized = MAPPER.writeValueAsString(foo);
        Foo141 foo2 = MAPPER.readValue(serialized, Foo141.class);

        assertEquals(2, foo2.bar.length);
        assertEquals(0.25, foo2.bar[1]);
    }
}
