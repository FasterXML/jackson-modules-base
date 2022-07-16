package com.fasterxml.jackson.module.afterburner.ser;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonAppend;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.ser.VirtualBeanPropertyWriter;
import tools.jackson.databind.util.Annotations;
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
