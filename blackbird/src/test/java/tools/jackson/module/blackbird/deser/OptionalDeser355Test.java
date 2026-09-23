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

public class OptionalDeser355Test extends BlackbirdTestBase
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
        private Optional<Integer> number;

        public Optional<String> getValue() {
            return value;
        }

        @JsonDeserialize(using = CustomOptionalDeserializer.class)
        public void setValue(Optional<String> value) {
            this.value = value;
        }

        public Optional<Integer> getNumber() {
            return number;
        }

        @JsonDeserialize(using = CustomOptionalDeserializer.class)
        public void setNumber(Optional<Integer> number) {
            this.number = number;
        }
    }

    static class CustomOptionalDeserializer extends Jdk8OptionalDeserializer {
        /**
         * Placeholder used by the no-arguments constructor that
         * {@code @JsonDeserialize(using=...)} calls: the actual declared type is
         * only known once {@link #createContextual} gets called.
         */
        private static final JavaType UNRESOLVED_TYPE = TypeFactory.createDefaultInstance()
                .constructType(Optional.class);

        public CustomOptionalDeserializer() {
            this(UNRESOLVED_TYPE, null, null);
        }

        private CustomOptionalDeserializer(JavaType fullType,
                TypeDeserializer typeDeser, ValueDeserializer<?> valueDeser) {
            super(fullType, null, typeDeser, valueDeser);
        }

        @Override
        public ValueDeserializer<?> createContextual(DeserializationContext ctxt,
                BeanProperty property) {
            // Resolve against whatever `Optional<T>` the property actually declares,
            // instead of assuming one specific value type
            JavaType declaredType = (property == null) ? _fullType : property.getType();
            if (!declaredType.equals(_fullType)) {
                return new CustomOptionalDeserializer(declaredType, null, null)
                        .createContextual(ctxt, property);
            }
            return super.createContextual(ctxt, property);
        }

        @Override
        public CustomOptionalDeserializer withResolved(TypeDeserializer typeDeser,
                ValueDeserializer<?> valueDeser) {
            return new CustomOptionalDeserializer(_fullType, typeDeser, valueDeser);
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

        public ValueDeserializer<?> deserializerFor(Class<?> beanType) {
            ValueDeserializer<?> deserializer = deserializers.get(beanType);
            assertNotNull(deserializer, "No deserializer captured for " + beanType.getName());
            return deserializer;
        }
    }

    // The new engine never swaps individual properties: eligible beans -
    // these package-private ones included - get a whole generated codec, and
    // every Optional-typed property rides its stock SettableBeanProperty
    // inside it (the issue-355 revert cannot recur by construction). The
    // original per-property assertions named the old engine's classes; their
    // equivalent here is codec engagement plus behavior.
    @Test
    public void testOptionalPropertyWithBuiltInDeserializer() throws Exception {
        DeserializerCapture capture = new DeserializerCapture();
        ObjectMapper mapper = mapperWithCapture(capture);

        OptionalBean bean = mapper.readValue("{\"value\":\"test\"}", OptionalBean.class);

        assertEquals(Optional.of("test"), bean.getValue());
        assertEquals("BBReaderPlaceholder",
                capture.deserializerFor(OptionalBean.class).getClass().getSimpleName());
        assertEquals(Optional.empty(),
                mapper.readValue("{\"value\":null}", OptionalBean.class).getValue());
    }

    @Test
    public void testOptionalPropertyWithCustomDeserializer() throws Exception {
        DeserializerCapture capture = new DeserializerCapture();
        ObjectMapper mapper = mapperWithCapture(capture);

        CustomOptionalBean bean = mapper.readValue(
                "{\"value\":\"test\", \"number\":42}", CustomOptionalBean.class);

        assertEquals(Optional.of("test"), bean.getValue());
        assertEquals(Optional.of(42), bean.getNumber());
        // The custom-deserializer properties ride the codec's stock arms.
        assertEquals("BBReaderPlaceholder",
                capture.deserializerFor(CustomOptionalBean.class).getClass().getSimpleName());
    }

    private ObjectMapper mapperWithCapture(DeserializerCapture capture) {
        SimpleModule captureModule = new SimpleModule()
                .setDeserializerModifier(capture);
        // Modifiers run in reverse registration order: the capture module
        // registers first so it observes what Blackbird installed.
        return JsonMapper.builder()
                .addModule(captureModule)
                .addModule(new BlackbirdModule())
                .build();
    }
}
