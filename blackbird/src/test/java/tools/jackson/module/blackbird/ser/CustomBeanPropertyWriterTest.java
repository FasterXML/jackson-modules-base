package tools.jackson.module.blackbird.ser;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonInclude;

import tools.jackson.core.*;

import tools.jackson.databind.*;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// for [afterburner#52]
public class CustomBeanPropertyWriterTest extends BlackbirdTestBase
{
    static class SampleObject {
        public String field1;
        public Integer field2;
        public Integer field3;

        protected SampleObject() { }
        public SampleObject(String field1, Integer field2, Integer field3) {
            this.field1 = field1;
            this.field2 = field2;
            this.field3 = field3;
        }
    }

    static class Only2BeanSerializerModifier extends ValueSerializerModifier {
        private static final long serialVersionUID = 1L;

        @Override
        public List<BeanPropertyWriter> changeProperties(SerializationConfig config, BeanDescription beanDesc, List<BeanPropertyWriter> props)
        {
            for (int i = 0, len = props.size(); i < len; ++i) {
                BeanPropertyWriter w = props.get(i);
                if (Integer.class.isAssignableFrom(w.getType().getRawClass())) {
                    props.set(i, new Only2BeanPropertyWriter(w));
                }
            }
            return props;
        }
    }

    @SuppressWarnings("serial")
    static class Only2BeanPropertyWriter extends BeanPropertyWriter
    {
        protected Only2BeanPropertyWriter(BeanPropertyWriter base) {
            super(base);
        }

        @Override
        public void serializeAsProperty(Object bean, JsonGenerator g, SerializationContext ctxt)
            throws Exception
        {
          Object val = get(bean);
          if((val == null || !val.equals(2)) && _nullSerializer == null) {
              return;
          }
          super.serializeAsProperty(bean, g, ctxt);
        }
    }

    @Test
    public void testCustomPropertyWriter() throws Exception
    {
        SimpleModule simpleModule = new SimpleModule()
                .setSerializerModifier(new Only2BeanSerializerModifier());
        ObjectMapper objectMapper = mapperBuilder()
                .addModule(simpleModule)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        assertEquals("{\"field2\":2}",
                objectMapper.writeValueAsString(new SampleObject(null, 2, 3)));
    }
}
