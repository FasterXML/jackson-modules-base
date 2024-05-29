package com.fasterxml.jackson.module.paranamer;

import com.fasterxml.jackson.annotation.JsonCreator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.json.JsonMapper;

public class SimpleTest extends ModuleTestBase
{
    static class CreatorBean
    {
        protected String name;
        protected int age;

        @JsonCreator
        public CreatorBean(int age, String name)
        {
            this.name = name;
            this.age = age;
        }
    }
    
    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    public void testSimple() throws Exception
    {
        final String JSON = "{\"name\":\"Bob\", \"age\":40}";
        // First, try without module
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.readValue(JSON, CreatorBean.class);
            fail("should fail");
        } catch (InvalidDefinitionException e) {
            // pre-2.18:
//            verifyException(e, "has no property name");
            // 2.18 changes to:
            verifyException(e, "More than one argument");
        }

        // then with two available modules:
        mapper = JsonMapper.builder()
                .addModule(new ParanamerModule())
                .build();
        CreatorBean bean = mapper.readValue(JSON, CreatorBean.class);
        assertEquals("Bob", bean.name);
        assertEquals(40, bean.age);

        mapper = JsonMapper.builder()
                .annotationIntrospector(new ParanamerOnJacksonAnnotationIntrospector())
                .build();
        bean = mapper.readValue(JSON, CreatorBean.class);
        assertEquals("Bob", bean.name);
        assertEquals(40, bean.age);
    }

    // Let's test handling of case where parameter names are not found; for example when
    // trying to access things for JDK types
    public void testWrapper() throws Exception
    {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new ParanamerModule())
                .build();
        String json = mapper.writeValueAsString(Integer.valueOf(1));
        assertEquals("1", json);
    }
}
