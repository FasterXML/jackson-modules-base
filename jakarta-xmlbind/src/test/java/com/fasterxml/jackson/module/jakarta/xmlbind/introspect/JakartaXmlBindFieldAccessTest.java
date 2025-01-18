package com.fasterxml.jackson.module.jakarta.xmlbind.introspect;

import java.io.IOException;

import jakarta.xml.bind.annotation.*;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.*;

import com.fasterxml.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class JakartaXmlBindFieldAccessTest extends ModuleTestBase
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
    @Test
    public void testFieldSerialization() throws IOException
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("{\"x\":3}", serializeAsString(mapper, new Fields(3)));
    }

    // Verify deserialization wrt [JACKSON-202]
    @Test
    public void testFieldDeserialization() throws IOException
    {
        ObjectMapper mapper = getJaxbMapper();
        Fields result = mapper.readValue("{ \"x\":3 }", Fields.class);
        assertEquals(3, result.x);
    }
}
