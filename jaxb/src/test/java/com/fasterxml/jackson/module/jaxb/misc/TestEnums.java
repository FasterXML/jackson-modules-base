package com.fasterxml.jackson.module.jaxb.misc;

import javax.xml.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

public class TestEnums extends BaseJaxbTest
{
    enum Plain { A, B; }

    @XmlEnum(Integer.class)
    enum Numeric { A, B; }

    /*
    /**********************************************************
    /* Test methods
    /**********************************************************
     */

    private final ObjectMapper MAPPER = getJaxbMapper();

    // [JACKSON-436]
    public void testWrapperWithCollection() throws Exception
    {
        assertEquals("\"B\"", MAPPER.writeValueAsString(Plain.B));
        assertEquals("1", MAPPER.writeValueAsString(Numeric.B));
    }
}
