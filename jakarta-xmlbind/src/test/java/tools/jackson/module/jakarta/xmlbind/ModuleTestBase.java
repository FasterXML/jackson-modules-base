package tools.jackson.module.jakarta.xmlbind;

import java.util.Map;

import tools.jackson.databind.*;
import tools.jackson.databind.cfg.MapperBuilder;
import tools.jackson.databind.introspect.AnnotationIntrospectorPair;
import tools.jackson.databind.introspect.JacksonAnnotationIntrospector;
import tools.jackson.databind.json.JsonMapper;

public abstract class ModuleTestBase
{
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

    protected MapperBuilder<?,?> objectMapperBuilder()
    {
        return JsonMapper.builder();
    }

    protected MapperBuilder<?,?> getJaxbMapperBuilder()
    {
        return JsonMapper.builder()
                .annotationIntrospector(new JakartaXmlBindAnnotationIntrospector());
    }

    protected MapperBuilder<?,?> getJaxbAndJacksonMapperBuilder()
    {
        return JsonMapper.builder()
                .annotationIntrospector(new AnnotationIntrospectorPair(
                        new JakartaXmlBindAnnotationIntrospector(),
                        new JacksonAnnotationIntrospector()));
    }

    protected MapperBuilder<?,?> getJacksonAndJaxbMapperBuilder()
    {
        return JsonMapper.builder()
                .annotationIntrospector(new AnnotationIntrospectorPair(new JacksonAnnotationIntrospector(),
                        new JakartaXmlBindAnnotationIntrospector()));
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
    {
        String str = m.writeValueAsString(value);
        return (Map<String,Object>) m.readValue(str, Map.class);
    }

    protected Map<String,Object> writeAndMap(Object value)
    {
        return writeAndMap(new ObjectMapper(), value);
    }

    protected String serializeAsString(ObjectMapper m, Object value)
    {
        return m.writeValueAsString(value);
    }

    protected String serializeAsString(Object value)
    {
        return serializeAsString(new ObjectMapper(), value);
    }

    /*
    /**********************************************************
    /* Helper methods, other
    /**********************************************************
     */

    public String q(String str) {
        return '"'+str+'"';
    }

    protected static String a2q(String json) {
        return json.replace("'", "\"");
    }
}
