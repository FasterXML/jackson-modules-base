package tools.jackson.module.blackbird.inject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.internal.GeneratedBuilderBase;
import tools.jackson.module.blackbird.internal.GeneratedReaderBase;

// Test utilities for verifying that Blackbird's codec generation engaged for a
// given POJO loaded from the unnamed module (classpath). The checks look at
// the (de)serializer the mapper uses for the type: a generated codec, or the
// stock one when a gate demoted the bean. (A modifier would only see the
// placeholder that stands in for either until resolution.) Serializer checks
// look for the optimized property writers of the LambdaMetafactory engine,
// which are package-private, by simple name.
abstract class BlackbirdInjectionTestBase
{
    static {
        // Strict mode: a codec-generation failure fails the test instead of
        // demoting to the stock path (see CodegenFallbacks).
        System.setProperty("tools.jackson.module.blackbird.failOnCodegenError", "true");
    }

    protected static Harness newHarness() {
        return new Harness();
    }

    protected static final class Harness {
        final JsonMapper mapper = JsonMapper.builder()
                .addModule(new BlackbirdModule())
                .build();

        ValueDeserializer<?> deserFor(Class<?> cls) {
            DeserializationContext ctxt = mapper._deserializationContext();
            return ctxt.findContextualValueDeserializer(ctxt.constructType(cls), null);
        }

        ValueSerializer<?> serFor(Class<?> cls) {
            SerializationContext ctxt = mapper._serializationContext();
            return ctxt.handleRootContextualization(ctxt.findValueSerializer(cls));
        }
    }

    /** True if the deserializer is a generated Blackbird codec. */
    protected static boolean isBlackbirdReader(ValueDeserializer<?> deser) {
        return (deser instanceof GeneratedReaderBase) || (deser instanceof GeneratedBuilderBase);
    }

    /** Returns the BeanPropertyWriter[] from a bean serializer, as a list. */
    protected static List<BeanPropertyWriter> writersOf(ValueSerializer<?> ser) {
        BeanPropertyWriter[] arr = (BeanPropertyWriter[]) reflectField(ser, "_props");
        List<BeanPropertyWriter> out = new ArrayList<>(arr.length);
        for (BeanPropertyWriter w : arr) {
            out.add(w);
        }
        return out;
    }

    /** Walks the class chain of {@code instance} looking for a declared field
     *  named {@code fieldName}. */
    protected static Object reflectField(Object instance, String fieldName) {
        Class<?> c = instance.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(instance);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError("cannot read field '" + fieldName + "' on "
                        + instance.getClass().getName(), e);
            }
        }
        throw new AssertionError("field '" + fieldName + "' not found on "
                + instance.getClass().getName());
    }

    /** True if `writer`'s class chain contains Blackbird's OptimizedBeanPropertyWriter. */
    protected static boolean isOptimizedWriter(BeanPropertyWriter writer) {
        Class<?> c = writer.getClass();
        while (c != null) {
            if ("OptimizedBeanPropertyWriter".equals(c.getSimpleName())
                    && c.getPackageName().startsWith("tools.jackson.module.blackbird")) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }
}
