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

        // also verify non-public methods
        protected abstract String getZ();
        private Object customMethod() { return protectedAbstractMethod(); }
        protected abstract Object protectedAbstractMethod();
    }

    public abstract static class CoffeeBean extends Bean {
        @Override public String roast(int temperature) {
            return "The coffee beans are roasting at " + temperature + " degrees now, yummy";
        }

        @Override protected Object protectedAbstractMethod() {
            return "Private methods invoking protected abstract methods is the bomb!";
        }
    }

    public abstract static class PeruvianCoffeeBean extends CoffeeBean {}

    public abstract static class StringlessCoffeeBean extends CoffeeBean
    {
        @Override public abstract String toString();
    }

    public abstract static class CoffeeBeanWithVariableFoo extends CoffeeBean
    {
        @Override public abstract String getFoo();
    }


    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    public void testOverrides() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();

        Bean bean = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", CoffeeBean.class);
        verifyBean(bean);

        Bean bean2 = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", PeruvianCoffeeBean.class);
        verifyBean(bean2);
    }

    public void testReAbstractedMethods() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();

        Bean bean = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", StringlessCoffeeBean.class);
        verifyBean(bean);
        try {
            assertNotNull(bean.toString());
        } catch (UnsupportedOperationException e) {
            verifyException(e, "Unimplemented method 'toString'");
        }

        // Ensure that the re-abstracted method will read "foo" from the JSON
        Bean beanWithNoFoo = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", CoffeeBeanWithVariableFoo.class);
        assertNull(beanWithNoFoo.getFoo());
        Bean beanWithOtherFoo = mapper.readValue("{ \"foo\": \"Another Foo!\", \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", CoffeeBeanWithVariableFoo.class);
        assertEquals("Another Foo!", beanWithOtherFoo.getFoo());
    }

    private void verifyBean(Bean bean) {
        assertNotNull(bean);
        assertEquals("abc", bean.getX());
        assertEquals(13, bean.y);
        assertEquals("Foo!", bean.getFoo());
        assertEquals("def", bean.getZ());
        assertEquals("The coffee beans are roasting at 123 degrees now, yummy", bean.roast(123));
        assertEquals("Private methods invoking protected abstract methods is the bomb!", bean.customMethod());
    }
}
