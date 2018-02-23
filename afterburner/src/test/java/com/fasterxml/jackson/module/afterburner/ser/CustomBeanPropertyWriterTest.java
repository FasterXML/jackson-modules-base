package com.fasterxml.jackson.module.afterburner.ser;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.ser.BeanSerializerModifier;
import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;

// for [afterburner#52]
public class CustomBeanPropertyWriterTest extends AfterburnerTestBase
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

    static class Only2BeanSerializerModifier extends BeanSerializerModifier {
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
        public void serializeAsField(Object bean, JsonGenerator jgen, SerializerProvider prov) throws Exception {
          Object val = get(bean);
          if((val == null || !val.equals(2)) && _nullSerializer == null) {
            return;
          }
          super.serializeAsField(bean, jgen, prov);
        }
    }
    
    public void testCustomPropertyWriter() throws Exception
    {
        SimpleModule simpleModule = new SimpleModule()
                .setSerializerModifier(new Only2BeanSerializerModifier());
        ObjectMapper objectMapper = afterburnerMapperBuilder()
                .addModule(simpleModule)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
        assertEquals("{\"field2\":2}",
                objectMapper.writeValueAsString(new SampleObject(null, 2, 3)));
    }
}
