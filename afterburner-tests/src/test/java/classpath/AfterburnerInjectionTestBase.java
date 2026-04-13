package classpath;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.module.afterburner.AfterburnerModule;

import static org.junit.jupiter.api.Assertions.assertNotNull;

// Test utilities for verifying that Afterburner's bytecode injection pipeline actually
// ran on a given POJO. Because OptimizedSettableBeanProperty and its serializer-side
// counterpart are package-private inside afterburner, these checks rely on reflection
// and simple-name matching rather than compile-time type references.
abstract class AfterburnerInjectionTestBase
{
    /** Builds a mapper with AfterburnerModule + a capture hook that remembers
     *  the built deserializer and serializer for each bean class we touch. */
    protected static Harness newHarness() {
        return new Harness();
    }

    protected static final class Harness {
        private final ConcurrentMap<Class<?>, ValueDeserializer<?>> desers = new ConcurrentHashMap<>();
        private final ConcurrentMap<Class<?>, ValueSerializer<?>> sers = new ConcurrentHashMap<>();
        final JsonMapper mapper;

        Harness() {
            SimpleModule capture = new SimpleModule("capture") {
                private static final long serialVersionUID = 1L;
                @Override
                public void setupModule(SetupContext ctxt) {
                    super.setupModule(ctxt);
                    ctxt.addDeserializerModifier(new ValueDeserializerModifier() {
                        private static final long serialVersionUID = 1L;
                        @Override
                        public ValueDeserializer<?> modifyDeserializer(
                                DeserializationConfig cfg, BeanDescription.Supplier ref,
                                ValueDeserializer<?> d) {
                            desers.put(ref.getBeanClass(), d);
                            return d;
                        }
                    });
                    ctxt.addSerializerModifier(new ValueSerializerModifier() {
                        private static final long serialVersionUID = 1L;
                        @Override
                        public ValueSerializer<?> modifySerializer(
                                SerializationConfig cfg, BeanDescription.Supplier ref,
                                ValueSerializer<?> s) {
                            sers.put(ref.getBeanClass(), s);
                            return s;
                        }
                    });
                }
            };
            this.mapper = JsonMapper.builder()
                    .addModule(new AfterburnerModule())
                    .addModule(capture)
                    .build();
        }

        ValueDeserializer<?> deserFor(Class<?> cls) {
            ValueDeserializer<?> d = desers.get(cls);
            assertNotNull(d, "no deserializer captured for " + cls.getName());
            return d;
        }

        ValueSerializer<?> serFor(Class<?> cls) {
            ValueSerializer<?> s = sers.get(cls);
            assertNotNull(s, "no serializer captured for " + cls.getName());
            return s;
        }
    }

    /** Returns the `_propsByIndex` array from a bean deserializer, walking up the
     *  class hierarchy because the field is declared on a base class. */
    protected static SettableBeanProperty[] propsOf(ValueDeserializer<?> deser) {
        if (!(deser instanceof BeanDeserializer)) {
            throw new AssertionError("not a BeanDeserializer: " + deser.getClass().getName());
        }
        Class<?> c = deser.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("_propsByIndex");
                f.setAccessible(true);
                return (SettableBeanProperty[]) f.get(deser);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("_propsByIndex not found on " + deser.getClass());
    }

    /** Returns the BeanPropertyWriter[] from a bean serializer, walking up. */
    protected static List<BeanPropertyWriter> writersOf(ValueSerializer<?> ser) {
        Class<?> c = ser.getClass();
        while (c != null) {
            try {
                Field f = c.getDeclaredField("_props");
                f.setAccessible(true);
                BeanPropertyWriter[] arr = (BeanPropertyWriter[]) f.get(ser);
                List<BeanPropertyWriter> out = new ArrayList<>(arr.length);
                for (BeanPropertyWriter w : arr) out.add(w);
                return out;
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError(e);
            }
        }
        throw new AssertionError("_props not found on " + ser.getClass());
    }

    /** True if `prop`'s class chain contains Afterburner's OptimizedSettableBeanProperty. */
    protected static boolean isOptimizedProperty(SettableBeanProperty prop) {
        Class<?> c = prop.getClass();
        while (c != null) {
            if ("OptimizedSettableBeanProperty".equals(c.getSimpleName())
                    && c.getPackageName().startsWith("tools.jackson.module.afterburner")) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    /** True if `writer`'s class chain contains Afterburner's OptimizedBeanPropertyWriter. */
    protected static boolean isOptimizedWriter(BeanPropertyWriter writer) {
        Class<?> c = writer.getClass();
        while (c != null) {
            if ("OptimizedBeanPropertyWriter".equals(c.getSimpleName())
                    && c.getPackageName().startsWith("tools.jackson.module.afterburner")) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }
}
