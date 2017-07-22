package com.fasterxml.jackson.module.jaxb.misc;

import javax.xml.bind.annotation.XmlEnum;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

public class TestEnums extends BaseJaxbTest
{
    enum Plain { A, B; }

    @XmlEnum(Integer.class)
    enum Numeric { A, B; }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    // [JACKSON-436]
    public void testWrapperWithCollection() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("\"B\"", mapper.writeValueAsString(Plain.B));
        assertEquals("1", mapper.writeValueAsString(Numeric.B));
    }
}
