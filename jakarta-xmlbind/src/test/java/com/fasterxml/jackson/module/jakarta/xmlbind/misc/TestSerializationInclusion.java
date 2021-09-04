package com.fasterxml.jackson.module.jakarta.xmlbind.misc;

import java.util.List;

import jakarta.xml.bind.annotation.XmlElement;

import com.fasterxml.jackson.annotation.JsonInclude;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;

import com.fasterxml.jackson.module.jakarta.xmlbind.ModuleTestBase;

public class TestSerializationInclusion extends ModuleTestBase
{
    static class Data {
        private final List<Object> stuff = new java.util.ArrayList<Object>();

        @XmlElement
        public List<Object> getStuff() {
            return stuff;
        }
    }    

    public void testIssue39() throws Exception
    {
        // First: use plain JAXB introspector:
        _testInclusion(getJaxbMapperBuilder());
        // and then combination ones
        _testInclusion(getJaxbAndJacksonMapperBuilder());
        _testInclusion(getJacksonAndJaxbMapperBuilder());
    }

    private void _testInclusion(MapperBuilder<?,?> builder) throws Exception
    {
        ObjectMapper mapper = builder.changeDefaultPropertyInclusion(
                incl -> incl.withValueInclusion(JsonInclude.Include.NON_EMPTY))
                .build();
        String json = mapper.writeValueAsString(new Data());
        assertEquals("{}", json);
    }
}
