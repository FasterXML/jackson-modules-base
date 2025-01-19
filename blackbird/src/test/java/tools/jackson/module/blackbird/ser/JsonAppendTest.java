package tools.jackson.module.blackbird.ser;

import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonGenerator;

import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonAppend;
import tools.jackson.databind.cfg.MapperConfig;
import tools.jackson.databind.introspect.AnnotatedClass;
import tools.jackson.databind.introspect.BeanPropertyDefinition;
import tools.jackson.databind.ser.VirtualBeanPropertyWriter;
import tools.jackson.databind.util.Annotations;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// most for [module-afterburner#57]
public class JsonAppendTest extends BlackbirdTestBase
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
        protected Object value(Object bean, JsonGenerator g, SerializationContext ctxt) throws Exception {
            return "bar";
        }
        @Override
        public VirtualBeanPropertyWriter withConfig(MapperConfig<?> config, AnnotatedClass declaringClass,
                BeanPropertyDefinition propDef, JavaType type) {
            return new MyVirtualPropertyWriter(propDef, declaringClass.getAnnotations(), type);
        }
    }

    @Test
    public void testSimpleAppend() throws Exception
    {
        ObjectMapper mapper = newBlackbirdMapper();
        String json = mapper.writeValueAsString(new Pojo("foo"));
        assertEquals("{\"name\":\"foo\",\"virtual\":\"bar\"}", json);
    }
}
