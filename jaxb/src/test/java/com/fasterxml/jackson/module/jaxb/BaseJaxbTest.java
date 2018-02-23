package com.fasterxml.jackson.module.jaxb;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;

public abstract class BaseJaxbTest
    extends junit.framework.TestCase
{
    protected BaseJaxbTest() { }
    
    /*
    /**********************************************************************
    /* Factory methods
    /**********************************************************************
     */

    protected ObjectMapper newObjectMapper()
    {
        return getJaxbAndJacksonMapper();
    }

    protected MapperBuilder<?,?> getJaxbMapperBuilder()
    {
        AnnotationIntrospector intr = new JaxbAnnotationIntrospector();
        return ObjectMapper.builder()
                .annotationIntrospector(intr);
    }

    protected MapperBuilder<?,?> getJaxbAndJacksonMapperBuilder()
    {
        AnnotationIntrospector intr = new AnnotationIntrospectorPair(
                new JaxbAnnotationIntrospector(),
                new JacksonAnnotationIntrospector());
        return ObjectMapper.builder()
                .annotationIntrospector(intr);
    }

    protected MapperBuilder<?,?> getJacksonAndJaxbMapperBuilder()
    {
        AnnotationIntrospector intr = new AnnotationIntrospectorPair(new JacksonAnnotationIntrospector(),
                new JaxbAnnotationIntrospector());
        return ObjectMapper.builder()
                .annotationIntrospector(intr);
    }

    protected ObjectMapper getJaxbMapper() {
        return getJaxbMapperBuilder().build();
    }

    protected ObjectMapper getJaxbAndJacksonMapper()
    {
        return getJaxbAndJacksonMapperBuilder().build();
    }
    
    /*
    /**********************************************************************
    /* Helper methods
    /**********************************************************************
     */

    @SuppressWarnings("unchecked")
    protected Map<String,Object> writeAndMap(ObjectMapper m, Object value)
        throws IOException
    {
        String str = m.writeValueAsString(value);
        return (Map<String,Object>) m.readValue(str, Map.class);
    }

    protected Map<String,Object> writeAndMap(Object value)
        throws IOException
    {
        return writeAndMap(new ObjectMapper(), value);
    }

    protected String serializeAsString(ObjectMapper m, Object value) throws IOException
    {
        return m.writeValueAsString(value);
    }

    protected String serializeAsString(Object value) throws IOException
    {
        return serializeAsString(new ObjectMapper(), value);
    }

    /*
    /**********************************************************
    /* Helper methods, other
    /**********************************************************
     */

    public String quote(String str) {
        return '"'+str+'"';
    }

    protected static String aposToQuotes(String json) {
        return json.replace("'", "\"");
    }
}
