package com.fasterxml.jackson.module.afterburner.deser.java8;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

// for [modules-base#30]
public class DefaultMethodsTest extends AfterburnerTestBase
{
    // NOTE: can only be enabled for Jackson 3.x
    /*
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
    */

    // for Jackson 2.x:
    public interface Typed {
        public String getType();
        public void setType(String t);
    }

    static class Model implements Typed {
        String x;

        @Override
        public String getType() { return "bogus"; }
        @Override
        public void setType(String t) { x = t; }
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    public void testSerializeViaDefault() throws Exception
    {
        assertEquals(aposToQuotes("{'type':'bogus'}"),
                MAPPER.writeValueAsString(new Model()));
    }

    public void testDeserializeViaDefault() throws Exception
    {
        // Would throws `java.lang.IncompatibleClassChangeError`
        Model m = MAPPER.readValue(aposToQuotes("{'type':'stuff'}"),
                Model.class);
        assertNotNull(m);
        assertEquals("stuff", m.x);
    }
}
