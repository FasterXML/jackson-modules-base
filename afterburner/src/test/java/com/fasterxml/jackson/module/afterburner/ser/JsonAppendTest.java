package com.fasterxml.jackson.module.afterburner.ser;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.annotation.JsonAppend;
import com.fasterxml.jackson.databind.cfg.MapperConfig;
import com.fasterxml.jackson.databind.introspect.AnnotatedClass;
import com.fasterxml.jackson.databind.introspect.BeanPropertyDefinition;
import com.fasterxml.jackson.databind.ser.VirtualBeanPropertyWriter;
import com.fasterxml.jackson.databind.util.Annotations;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

// most for [module-afterburner#57]
public class JsonAppendTest extends AfterburnerTestBase
{
    @JsonAppend(props = @JsonAppend.Prop(name = "virtual", value = MyVirtualPropertyWriter.class))
    public static class Pojo {
        public final String name;
        public Pojo(String name) {
            this.name = name;
        }
    }

    @SuppressWarnings("serial")
    public static class MyVirtualPropertyWriter extends VirtualBeanPropertyWriter {
        protected MyVirtualPropertyWriter() { }

        protected MyVirtualPropertyWriter(BeanPropertyDefinition propDef, Annotations contextAnnotations,
                JavaType declaredType) {
            super(propDef, contextAnnotations, declaredType);
        }
        @Override
        protected Object value(Object bean, JsonGenerator g, SerializerProvider prov) throws Exception {
            return "bar";
        }
        @Override
        public VirtualBeanPropertyWriter withConfig(MapperConfig<?> config, AnnotatedClass declaringClass,
                BeanPropertyDefinition propDef, JavaType type) {
            return new MyVirtualPropertyWriter(propDef, declaringClass.getAnnotations(), type);
        }
    }

    @JsonAppend(prepend=true, props = @JsonAppend.Prop(name = "virtual", value = MyVirtualPropertyWriter.class))
    public static class Pojo6WithVirtual extends Pojo6 {
        public final String name;
        public Pojo6WithVirtual(String name) {
            super();
            this.name = name;
        }
    }

    /*
    /**********************************************************************
    /* Test methods
    /**********************************************************************
     */

    private final ObjectMapper MAPPER = newAfterburnerMapper();

    public void testSimpleAppend() throws Exception
    {
        String json = MAPPER.writeValueAsString(new Pojo("foo"));
        assertEquals("{\"name\":\"foo\",\"virtual\":\"bar\"}", json);
    }

    public void testBiggerAppend() throws Exception
    {
        String json = MAPPER.writeValueAsString(new Pojo6WithVirtual("Bubba"));
        assertEquals(aposToQuotes("{'virtual':'bar',"
                +"'a':13,'b':'foo','c':true,'d':-13117,'e':0.25,'f':[1,2,3],"
                +"'name':'Bubba'}"),
                json);
    }
}
