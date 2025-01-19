package com.fasterxml.jackson.module.jakarta.xmlbind.misc;

import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.module.jakarta.xmlbind.JakartaXmlBindAnnotationModule;

import com.fasterxml.jackson.module.jakarta.xmlbind.ModuleTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestSerializationInclusion extends ModuleTestBase
{
    static class Data {
        private final List<Object> stuff = new java.util.ArrayList<Object>();

        @XmlElement
        public List<Object> getStuff() {
            return stuff;
        }
    }    

    @Test
    public void testIssue39() throws Exception
    {
        // First: use plain JAXB introspector:
        _testInclusion(getJaxbMapper());
        // and then combination ones
        _testInclusion(getJaxbAndJacksonMapper());
        _testInclusion(getJacksonAndJaxbMapper());

        // finally: verify using actual module
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JakartaXmlBindAnnotationModule());
        _testInclusion(mapper);
    }
        
    private void _testInclusion(ObjectMapper mapper) throws Exception
    {
        mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
        String json = mapper.writeValueAsString(new Data());
        assertEquals("{}", json);
    }
}
