package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TestAbstractClassesWithOverrides
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

        public abstract String roast(int temperature);

        public String getFoo() { return "Foo!"; }
        public void setY(int value) { y = value; }
    }

    public abstract static class CoffeeBean extends Bean {
        @Override public String roast(int temperature) {
            return "The coffee beans are roasting at " + temperature + " degrees now, yummy";
        }
    }

    public abstract static class PeruvianCoffeeBean extends CoffeeBean {}


    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    public void testOverrides() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();
        Bean bean = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13 }", CoffeeBean.class);
        verifyBean(bean);
        Bean bean2 = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13 }", PeruvianCoffeeBean.class);
        verifyBean(bean2);
    }

    private void verifyBean(Bean bean) {
        assertNotNull(bean);
        assertEquals("abc", bean.getX());
        assertEquals(13, bean.y);
        assertEquals("Foo!", bean.getFoo());
        assertEquals("The coffee beans are roasting at 123 degrees now, yummy", bean.roast(123));
    }
}
