package com.fasterxml.jackson.module.blackbird.deser.java8;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// for [modules-base#30]
public class DefaultMethodsTest extends BlackbirdTestBase
{
    public interface Typed {
        default String getType() {
            return "bogus";
        }
        default void setType(String type) { internalSet(type); }

        void internalSet(String s);
    }

    static class Model implements Typed {
        String x;

        @Override
        public void internalSet(String value) {
            x = value;
        }
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newObjectMapper();

    @Test
    public void testSerializeViaDefault() throws Exception
    {
        assertEquals(aposToQuotes("{'type':'bogus'}"),
                MAPPER.writeValueAsString(new Model()));
    }

    @Test
    public void testDeserializeViaDefault() throws Exception
    {
        // Would throws `java.lang.IncompatibleClassChangeError`
        Model m = MAPPER.readValue(aposToQuotes("{'type':'stuff'}"),
                Model.class);
        assertNotNull(m);
        assertEquals("stuff", m.x);
    }
}
