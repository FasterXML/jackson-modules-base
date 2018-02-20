package com.fasterxml.jackson.module.jaxb.misc;

import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.BeanDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;

import com.fasterxml.jackson.module.jaxb.BaseJaxbTest;

/**
 * Unit test(s) for [JACKSON-472]
 */
public class TestDeserializerCaching extends BaseJaxbTest
{
    /*
    /**********************************************************
    /* Helper beans
    /**********************************************************
     */

    static class MyBeanModule extends com.fasterxml.jackson.databind.Module {
        @Override public String getModuleName() {
            return "MyBeanModule";
        }

        @Override public Version version() {
            return Version.unknownVersion();
        }

        @Override public void setupModule(SetupContext context) {
            context.addDeserializerModifier(new MyBeanDeserializerModifier());
        }
    }

    @SuppressWarnings("serial")
    static class MyBeanDeserializer extends BeanDeserializer {
        public MyBeanDeserializer(BeanDeserializer src) {
            super(src);
        }
    }

    static class MyBean {
        public MyType value1;
        public MyType value2;
        public MyType value3;
    }

    static class MyType {
        public String name;
        public String value;
    }
    
    static class MyBeanDeserializerModifier extends BeanDeserializerModifier
    {
        static int count = 0;

        @Override
        public JsonDeserializer<?> modifyDeserializer(DeserializationConfig config,
                BeanDescription beanDesc, JsonDeserializer<?> deserializer)
        {
            if (MyType.class.isAssignableFrom(beanDesc.getBeanClass())) {
                count++;
                return new MyBeanDeserializer((BeanDeserializer)deserializer);
            }
            return super.modifyDeserializer(config, beanDesc, deserializer);
        }
    }

    /*
    /**********************************************************
    /* Unit tests
    /**********************************************************
     */

    public void testCaching() throws Exception
    {
        final String JSON = "{\"value1\" : {\"name\" : \"fruit\", \"value\" : \"apple\"},\n"
            +"\"value2\" : {\"name\" : \"color\", \"value\" : \"red\"},\n"
            +"\"value3\" : {\"name\" : \"size\", \"value\" : \"small\"}}"
            ;
        ObjectMapper mapper = getJaxbMapperBuilder()
            .addModule(new MyBeanModule())
            .build();
        mapper.readValue(JSON, MyBean.class);
        assertEquals(1, MyBeanDeserializerModifier.count);
    }
}
