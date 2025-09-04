package com.fasterxml.jackson.module.jakarta.xmlbind.misc;

import jakarta.xml.bind.annotation.XmlRootElement;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import com.fasterxml.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestRootName extends ModuleTestBase
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    @XmlRootElement(name="rooty")
    static class MyType
    {
        public int value = 37;
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */
    
    @Test
    public void testRootName() throws Exception
    {
        ObjectMapper mapper = getJaxbMapper();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, true);
        assertEquals("{\"rooty\":{\"value\":37}}", mapper.writeValueAsString(new MyType()));
    }
}
