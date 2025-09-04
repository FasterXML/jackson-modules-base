package com.fasterxml.jackson.module.blackbird.deser;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// [modules-base#141]
public class ObjectVarArgsDeser141Test extends BlackbirdTestBase
{
    static class Bar {
        @JsonProperty("baz")
        String baz;

        @JsonCreator
        public Bar(@JsonProperty("baz") String baz) {
            this.baz = baz;
        }
    }
    // [modules-base#141]
    static class Foo141 {
        @JsonProperty("bar")
        List<Bar> bar;

        @JsonCreator
        public Foo141(@JsonProperty("bar") Bar... bar) {
          this.bar = Arrays.asList(bar);
        }
    }

    private final ObjectMapper MAPPER = newObjectMapper();

    // [modules-base#141]
    @Test
    public void testObjectVarargsCreator() throws Exception
    {
        Foo141 foo = new Foo141(new Bar("a"), new Bar("b"));
        String serialized = MAPPER.writeValueAsString(foo);
        Foo141 foo2 = MAPPER.readValue(serialized, Foo141.class);

        assertEquals(2, foo2.bar.size());
        assertEquals("a", foo2.bar.get(0).baz);
        assertEquals("b", foo2.bar.get(1).baz);
    }
}
