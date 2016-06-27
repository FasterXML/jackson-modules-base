package com.fasterxml.jackson.module.afterburner.ser;

import java.io.IOException;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.ContextualSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

/**
 * Reproduction of issue [modules-base#14]; accidental removal of null
 * values, due to lack of delegation of "null serializer" (for 2.7)
 */
public class TestInclusionNull extends AfterburnerTestBase
{
    @JsonInclude(JsonInclude.Include.ALWAYS)
    static class MyPOJOBeanWrapper {
        public int id = 3;

        public MyPOJOBean wrapped;

        public MyPOJOBeanWrapper() { wrapped = new MyPOJOBean(); }
        public MyPOJOBeanWrapper(int x) { wrapped = new MyPOJOBean(x); }
    }

    @JsonInclude(JsonInclude.Include.ALWAYS)
    static final class MyPOJOBean {
        public MyPOJO value;

        protected MyPOJOBean() { }
        public MyPOJOBean(int x) {
            value = new MyPOJO();
            value.x = x;
        }
    }

    static class MyPOJO {
        public int x = 3;
    }

    @SuppressWarnings("serial")
    static class MyPOJOSerializer extends StdSerializer<MyPOJO>
        implements ContextualSerializer
    {
        private final String message;

        public MyPOJOSerializer() { this("Foo"); }
        public MyPOJOSerializer(String str) {
            super(MyPOJO.class);
System.err.println("DEBUG: MyPOJOSerializer with '"+str+"'");
            message = str;
        }

        @Override
        public void serialize(MyPOJO value, JsonGenerator gen,
                SerializerProvider provider) throws IOException {
System.err.println("DEBUG: serialize MyPOJO");

            gen.writeString(message);
        }

        @Override
        public JsonSerializer<?> createContextual(SerializerProvider prov,
                BeanProperty property) throws JsonMappingException {
System.err.println("DEBUG: create contextual");            
            return new MyPOJOSerializer(message + "bar");
        }
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    // for [#14]
    public void testNonStdSerializerAndNull() throws Exception
    {
        SimpleModule mod = new SimpleModule("test");
        mod.addSerializer(MyPOJO.class, new MyPOJOSerializer());
        final ObjectMapper mapper = mapperWithModule()
                .registerModule(mod);

        assertEquals(aposToQuotes("{'id':3,'wrapped':{'value':null}}"),
                mapper.writeValueAsString(new MyPOJOBeanWrapper()));
    }

}
