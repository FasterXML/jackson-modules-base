package com.fasterxml.jackson.module.blackbird.roundtrip;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.introspect.Annotated;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// NOTE: copied almost verbatim from jackson-databind tests
public class POJOAsArrayTest extends BlackbirdTestBase
{
    static class Pojo
    {
        @JsonFormat(shape=JsonFormat.Shape.ARRAY)
        public PojoValue value;

        public Pojo() { }
        public Pojo(String name, int x, int y, boolean c) {
            value = new PojoValue(name, x, y, c);
        }
    }

    // note: must be serialized/deserialized alphabetically; fields NOT declared in that order
    @JsonPropertyOrder(alphabetic=true)
    static class PojoValue
    {
        public int x, y;
        public String name;
        public boolean complete;

        public PojoValue() { }
        public PojoValue(String name, int x, int y, boolean c) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.complete = c;
        }
    }

    @JsonPropertyOrder(alphabetic=true)
    @JsonFormat(shape=JsonFormat.Shape.ARRAY)
    static class FlatPojo
    {
        public int x, y;
        public String name;
        public boolean complete;

        public FlatPojo() { }
        public FlatPojo(String name, int x, int y, boolean c) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.complete = c;
        }
    }

    static class ForceArraysIntrospector extends JacksonAnnotationIntrospector
    {
        private static final long serialVersionUID = 1L;

        @Override
        public JsonFormat.Value findFormat(Annotated a) {
            return new JsonFormat.Value().withShape(JsonFormat.Shape.ARRAY);
        }
    }

    static class A {
        public B value = new B();
    }

    @JsonPropertyOrder(alphabetic=true)
    static class B {
        public int x = 1;
        public int y = 2;
    }

    // for [JACKSON-805]
    @JsonFormat(shape=Shape.ARRAY)
    static class SingleBean {
        public String name = "foo";
    }

    @JsonPropertyOrder(alphabetic=true)
    @JsonFormat(shape=Shape.ARRAY)
    static class TwoStringsBean {
        public String bar = null;
        public String foo = "bar";
    }
    
    /*
    /*****************************************************
    /* Basic tests
    /*****************************************************
     */

    final ObjectMapper MAPPER = newObjectMapper();
    
    /**
     * Test that verifies that property annotation works
     */
    @Test
    public void testReadSimplePropertyValue() throws Exception
    {
        String json = "{\"value\":[true,\"Foobar\",42,13]}";
        Pojo p = MAPPER.readValue(json, Pojo.class);
        assertNotNull(p.value);
        assertTrue(p.value.complete);
        assertEquals("Foobar", p.value.name);
        assertEquals(42, p.value.x);
        assertEquals(13, p.value.y);
    }

    /**
     * Test that verifies that Class annotation works
     */
    @Test
    public void testReadSimpleRootValue() throws Exception
    {
        String json = "[false,\"Bubba\",1,2]";
        FlatPojo p = MAPPER.readValue(json, FlatPojo.class);
        assertFalse(p.complete);
        assertEquals("Bubba", p.name);
        assertEquals(1, p.x);
        assertEquals(2, p.y);
    }
    
    /**
     * Test that verifies that property annotation works
     */
    @Test
    public void testWriteSimplePropertyValue() throws Exception
    {
        String json = MAPPER.writeValueAsString(new Pojo("Foobar", 42, 13, true));
        // will have wrapper POJO, then POJO-as-array..
        assertEquals("{\"value\":[true,\"Foobar\",42,13]}", json);
    }

    /**
     * Test that verifies that Class annotation works
     */
    @Test
    public void testWriteSimpleRootValue() throws Exception
    {
        String json = MAPPER.writeValueAsString(new FlatPojo("Bubba", 1, 2, false));
        // will have wrapper POJO, then POJO-as-array..
        assertEquals("[false,\"Bubba\",1,2]", json);
    }

    // [Issue#223]
    @Test
    public void testNullColumn() throws Exception
    {
        assertEquals("[null,\"bar\"]", MAPPER.writeValueAsString(new TwoStringsBean()));
    }

    /*
    /*****************************************************
    /* Compatibility with "single-elem as array" feature
    /*****************************************************
     */
    
    @Test
    public void testSerializeAsArrayWithSingleProperty() throws Exception {
        String json = MAPPER.writer()
                .with(SerializationFeature.WRITE_SINGLE_ELEM_ARRAYS_UNWRAPPED)
                .writeValueAsString(new SingleBean());
        assertEquals("\"foo\"", json);
    }
}
