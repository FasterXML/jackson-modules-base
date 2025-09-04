package com.fasterxml.jackson.module.jakarta.xmlbind.misc;

import jakarta.xml.bind.annotation.XmlEnum;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestEnums extends ModuleTestBase
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
    @Test
    public void testWrapperWithCollection() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("\"B\"", mapper.writeValueAsString(Plain.B));
        assertEquals("1", mapper.writeValueAsString(Numeric.B));
    }
}
