package tools.jackson.module.mrbean;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

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
        private String customMethod() { return "Private methods rock!"; }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    @SuppressWarnings("synthetic-access")
    @Test
    public void testSimpleInteface() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();
        Bean bean = mapper.readValue("{ \"x\" : \"abc\", \"y\" : 13, \"z\" : \"def\" }", Bean.class);
        assertNotNull(bean);
        assertEquals("abc", bean.getX());
        assertEquals(13, bean.y);
        assertEquals("Foo!", bean.getFoo());
        assertEquals("def", bean.getZ());
        assertEquals("Private methods rock!", bean.customMethod());
    }
}
