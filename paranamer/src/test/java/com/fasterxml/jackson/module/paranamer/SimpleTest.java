package com.fasterxml.jackson.module.paranamer;

import com.fasterxml.jackson.annotation.JsonCreator;

import com.fasterxml.jackson.databind.ObjectMapper;

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

        // 26-Sep-2017, tatu: Without module may or may not work -- with Java 8 may
        //   well work... so do not assume failure
        
        // then with two available modules:
        ObjectMapper mapper = newObjectMapper();
        CreatorBean bean = mapper.readValue(JSON, CreatorBean.class);
        assertEquals("Bob", bean.name);
        assertEquals(40, bean.age);
    }

    // Let's test handling of case where parameter names are not found; for example when
    // trying to access things for JDK types
    public void testWrapper() throws Exception
    {
        ObjectMapper mapper = newObjectMapper();
        String json = mapper.writeValueAsString(Integer.valueOf(1));
        assertEquals("1", json);
    }
}
