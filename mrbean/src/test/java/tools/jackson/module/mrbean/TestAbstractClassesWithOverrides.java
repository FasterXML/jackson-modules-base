package tools.jackson.module.mrbean;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.exc.InvalidDefinitionException;
import tools.jackson.databind.json.JsonMapper;

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

    /*
     * Test classes where some concrete method has been re-"abstract"-ed
     */

    public abstract static class CoffeeBeanWithVariableFoo extends CoffeeBean
    {
        @Override public abstract String getFoo();
    }

    public abstract static class StringlessCoffeeBean extends CoffeeBean
    {
        @Override public abstract String toString();
    }

    public abstract static class UnroastableCoffeeBean extends CoffeeBean
    {
        @Override public abstract String roast(int temperature);
    }

    public abstract static class CoffeeBeanLackingPublicMethod extends CoffeeBean
    {
        @Override protected abstract Object protectedAbstractMethod();
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

    @SuppressWarnings("synthetic-access")
    public void testReAbstractedMethods() throws Exception
    {
        AbstractTypeMaterializer mat = new AbstractTypeMaterializer();
        // ensure that we will only get deferred error methods
        mat.disable(AbstractTypeMaterializer.Feature.FAIL_ON_UNMATERIALIZED_METHOD);
        final ObjectMapper mapper = JsonMapper.builder()
                .addModule(new MrBeanModule(mat))
                .build();

        verifyReAbstractedProperty(mapper);

        Bean bean = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", StringlessCoffeeBean.class);
        verifyBean(bean);
        try {
            assertNotNull(bean.toString());
            fail("Should not pass");
        } catch (UnsupportedOperationException e) {
            verifyException(e, "Unimplemented method 'toString'");
        }

        Bean unroastableBean = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", UnroastableCoffeeBean.class);
        try {
            unroastableBean.roast(123);
            fail("Should not pass");
        } catch (AbstractMethodError e) { // happens in 3.0?
            ; // anything to verify?
        } catch (UnsupportedOperationException e) {
            verifyException(e, "Unimplemented method 'roast'");
        }

        Bean beanLackingNonPublicMethod = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", CoffeeBeanLackingPublicMethod.class);
        try {
            beanLackingNonPublicMethod.customMethod();
            fail("Should not pass");
        } catch (UnsupportedOperationException e) {
            verifyException(e, "Unimplemented method 'protectedAbstractMethod'");
        }
    }

    // Ensures that the re-abstracted method will read "foo" from the JSON, regardless of the FAIL_ON_UNMATERIALIZED_METHOD setting
    private void verifyReAbstractedProperty(ObjectMapper mapper) throws Exception {
        Bean beanWithNoFoo = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", CoffeeBeanWithVariableFoo.class);
        assertNull(beanWithNoFoo.getFoo());
        Bean beanWithOtherFoo = mapper.readValue("{ \"foo\": \"Another Foo!\", \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", CoffeeBeanWithVariableFoo.class);
        assertEquals("Another Foo!", beanWithOtherFoo.getFoo());
    }

    public void testEagerFailureOnReAbstractedMethods() throws Exception
    {
        AbstractTypeMaterializer mat = new AbstractTypeMaterializer();
        // ensure that we will get eager failure on abstract methods
        mat.enable(AbstractTypeMaterializer.Feature.FAIL_ON_UNMATERIALIZED_METHOD);
        final ObjectMapper mapper = JsonMapper.builder()
                .addModule(new MrBeanModule(mat))
                .build();

        verifyReAbstractedProperty(mapper);

        try {
            mapper.readValue("{}", StringlessCoffeeBean.class);
            fail("Should not pass");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "Unrecognized abstract method 'toString'");
        }

        try {
            mapper.readValue("{}", UnroastableCoffeeBean.class);
            fail("Should not pass");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "Unrecognized abstract method 'roast'");
        }

        try {
            mapper.readValue("{}", CoffeeBeanLackingPublicMethod.class);
            fail("Should not pass");
        } catch (InvalidDefinitionException e) {
            verifyException(e, "Unrecognized abstract method 'protectedAbstractMethod'");
        }
    }

    @SuppressWarnings("synthetic-access")
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
