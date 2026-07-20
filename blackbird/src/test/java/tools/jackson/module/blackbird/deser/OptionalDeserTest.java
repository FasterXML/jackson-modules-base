package tools.jackson.module.blackbird.deser;

import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.impl.MethodProperty;
import tools.jackson.databind.ext.jdk8.Jdk8OptionalDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.TypeFactory;

import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

public class OptionalDeserTest extends BlackbirdTestBase
{
    static class OptionalBean {
        private Optional<String> value;

        public Optional<String> getValue() {
            return value;
        }

        public void setValue(Optional<String> value) {
            this.value = value;
        }
    }

    static class CustomOptionalBean {
        private Optional<String> value;

        public Optional<String> getValue() {
            return value;
        }

        @JsonDeserialize(using = CustomOptionalDeserializer.class)
        public void setValue(Optional<String> value) {
            this.value = value;
        }
    }

    static class CustomOptionalDeserializer extends Jdk8OptionalDeserializer {
        public CustomOptionalDeserializer() {
            this(optionalStringType(), null, null);
        }

        private CustomOptionalDeserializer(JavaType fullType,
                TypeDeserializer typeDeser, ValueDeserializer<?> valueDeser) {
            super(fullType, null, typeDeser, valueDeser);
        }

        @Override
        public CustomOptionalDeserializer withResolved(TypeDeserializer typeDeser,
                ValueDeserializer<?> valueDeser) {
            return new CustomOptionalDeserializer(_fullType, typeDeser, valueDeser);
        }

        private static JavaType optionalStringType() {
            TypeFactory typeFactory = TypeFactory.createDefaultInstance();
            return typeFactory.constructReferenceType(Optional.class,
                    typeFactory.constructType(String.class));
        }
    }

    static class DeserializerCapture extends ValueDeserializerModifier {
        private static final long serialVersionUID = 1L;

        private final Map<Class<?>, ValueDeserializer<?>> deserializers =
                new ConcurrentHashMap<>();

        @Override
        public ValueDeserializer<?> modifyDeserializer(DeserializationConfig config,
                BeanDescription.Supplier beanDescRef, ValueDeserializer<?> deserializer) {
            deserializers.put(beanDescRef.getBeanClass(), deserializer);
            return deserializer;
        }

        public SettableBeanProperty propertyFor(Class<?> beanType, String propertyName) {
            ValueDeserializer<?> deserializer = deserializers.get(beanType);
            BeanDeserializerBase beanDeserializer = assertInstanceOf(
                    BeanDeserializerBase.class, deserializer);
            Iterator<SettableBeanProperty> properties = beanDeserializer.properties();
            while (properties.hasNext()) {
                SettableBeanProperty property = properties.next();
                if (property.getName().equals(propertyName)) {
                    return property;
                }
            }
            return fail("No property '"+propertyName+"' found for "+beanType.getName());
        }
    }

    @Test
    public void keepsOptimizedPropertyForBuiltInOptionalDeserializer() throws Exception {
        DeserializerCapture capture = new DeserializerCapture();
        ObjectMapper mapper = mapperWithCapture(capture);

        OptionalBean bean = mapper.readValue("{\"value\":\"test\"}", OptionalBean.class);

        assertEquals(Optional.of("test"), bean.getValue());
        assertInstanceOf(SettableObjectProperty.class,
                capture.propertyFor(OptionalBean.class, "value"));
    }

    @Test
    public void fallsBackForCustomOptionalDeserializer() throws Exception {
        DeserializerCapture capture = new DeserializerCapture();
        ObjectMapper mapper = mapperWithCapture(capture);

        CustomOptionalBean bean = mapper.readValue(
                "{\"value\":\"test\"}", CustomOptionalBean.class);

        assertEquals(Optional.of("test"), bean.getValue());
        assertInstanceOf(MethodProperty.class,
                capture.propertyFor(CustomOptionalBean.class, "value"));
    }

    private ObjectMapper mapperWithCapture(DeserializerCapture capture) {
        SimpleModule captureModule = new SimpleModule()
                .setDeserializerModifier(capture);
        return JsonMapper.builder()
                .addModule(new BlackbirdModule())
                .addModule(captureModule)
                .build();
    }
}
