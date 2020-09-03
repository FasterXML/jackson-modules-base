package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestAbstractClasses
    extends BaseTest
{
    /*
    /**********************************************************
    /* Test classes, enums
    /**********************************************************
     */

    public abstract static class Bean
    {
        int y;

        protected Bean() { }

        public abstract String getX();

        public String getFoo() { return "Foo!"; }
        public void setY(int value) { y = value; }

        // also verify non-public methods
        protected abstract String getZ();
        private Object customMethod() { return new Object(); }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    public void testSimpleInteface() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();
        Bean bean = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", Bean.class);
        assertNotNull(bean);
        assertEquals("abc", bean.getX());
        assertEquals(13, bean.y);
        assertEquals("Foo!", bean.getFoo());
        assertEquals("def", bean.getZ());
        assertNotNull(bean.customMethod());
    }
}
