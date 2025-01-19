package com.fasterxml.jackson.module.jakarta.xmlbind;

import java.io.IOException;
import java.util.Map;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.cfg.MapperBuilder;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;

public abstract class ModuleTestBase
{
    public static class NoCheckSubTypeValidator
        extends PolymorphicTypeValidator.Base
    {
        private static final long serialVersionUID = 1L;
    
        @Override
        public Validity validateBaseType(MapperConfig<?> config, JavaType baseType) {
            return Validity.ALLOWED;
        }
    }

    protected ModuleTestBase() { }

    /*
    /**********************************************************************
    /* Factory methods
    /**********************************************************************
     */

    protected ObjectMapper newObjectMapper()
    {
        return getJaxbAndJacksonMapper();
    }

    protected ObjectMapper getJaxbMapper()
    {
        ObjectMapper mapper = new ObjectMapper();
        AnnotationIntrospector intr = new JakartaXmlBindAnnotationIntrospector(mapper.getTypeFactory());
        mapper.setAnnotationIntrospector(intr);
        return mapper;
    }

    @SuppressWarnings("deprecation")
    protected MapperBuilder<?,?> getJaxbMapperBuilder()
    {
        return JsonMapper.builder()
                .annotationIntrospector(new JakartaXmlBindAnnotationIntrospector());
    }

    protected ObjectMapper getJaxbAndJacksonMapper()
    {
        ObjectMapper mapper = new ObjectMapper();
        AnnotationIntrospector intr = new AnnotationIntrospectorPair(new JakartaXmlBindAnnotationIntrospector(
                mapper.getTypeFactory()), new JacksonAnnotationIntrospector());
        mapper.setAnnotationIntrospector(intr);
        return mapper;
    }

    protected ObjectMapper getJacksonAndJaxbMapper()
    {
        ObjectMapper mapper = new ObjectMapper();
        AnnotationIntrospector intr = new AnnotationIntrospectorPair(new JacksonAnnotationIntrospector(),
                new JakartaXmlBindAnnotationIntrospector(mapper.getTypeFactory()) );
        mapper.setAnnotationIntrospector(intr);
        return mapper;
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

    protected String serializeAsString(ObjectMapper m, Object value)
        throws IOException
    {
        return m.writeValueAsString(value);
    }

    protected String serializeAsString(Object value)
        throws IOException
    {
        return serializeAsString(new ObjectMapper(), value);
    }

    /*
    /**********************************************************************
    /* Helper methods, other
    /**********************************************************************
     */

    public String q(String str) {
        return '"'+str+'"';
    }

    protected static String a2q(String json) {
        return json.replace("'", "\"");
    }
}
