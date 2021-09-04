package com.fasterxml.jackson.module.jakarta.xmlbind.misc;

import jakarta.xml.bind.annotation.XmlRootElement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.fasterxml.jackson.module.jakarta.xmlbind.ModuleTestBase;

public class TestRootName extends ModuleTestBase
{
    @XmlRootElement(name="rooty")
    static class MyType
    {
        public int value = 37;
    }
    
    /*
    /**********************************************************************
    /* Unit tests
    /**********************************************************************
     */
    
    public void testRootName() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        assertEquals("{\"rooty\":{\"value\":37}}",
                mapper.writer()
                    .with(SerializationFeature.WRAP_ROOT_VALUE)
                    .writeValueAsString(new MyType()));
    }
}
