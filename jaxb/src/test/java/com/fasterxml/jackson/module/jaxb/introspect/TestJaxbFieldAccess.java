package com.fasterxml.jackson.module.jaxb.introspect;

import java.io.IOException;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.databind.*;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

public class TestJaxbFieldAccess extends BaseJaxbTest
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    @XmlAccessorType(XmlAccessType.FIELD)
    static class Fields {
        protected int x;

        public Fields() { }
        Fields(int x) { this.x = x; }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    // Verify serialization wrt [JACKSON-202]
    public void testFieldSerialization() throws IOException
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("{\"x\":3}", serializeAsString(mapper, new Fields(3)));
    }

    // Verify deserialization wrt [JACKSON-202]
    public void testFieldDeserialization() throws IOException
    {
        ObjectMapper mapper = getJaxbMapper();
        Fields result = mapper.readValue("{ \"x\":3 }", Fields.class);
        assertEquals(3, result.x);
    }
}
