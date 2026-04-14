package tools.jackson.module.blackbird.inject;

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
import tools.jackson.module.blackbird.BlackbirdModule;

import static org.junit.jupiter.api.Assertions.assertNotNull;

// Test utilities for verifying that Blackbird's lambda-based injection pipeline
// actually ran on a given POJO. Because Blackbird's optimized property and writer
// classes (SettableIntProperty, etc.; IntPropertyWriter, etc.) are package-private
// inside the blackbird module, these checks rely on reflection and simple-name
// matching rather than compile-time type references. Mirrors the afterburner-tests
// harness; see that module's README for the broader rationale.
abstract class BlackbirdInjectionTestBase
{
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
                    .addModule(new BlackbirdModule())
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

    /** Returns the `_propsByIndex` array from a bean deserializer. */
    protected static SettableBeanProperty[] propsOf(ValueDeserializer<?> deser) {
        if (!(deser instanceof BeanDeserializer)) {
            throw new AssertionError("not a BeanDeserializer: " + deser.getClass().getName());
        }
        return (SettableBeanProperty[]) reflectField(deser, "_propsByIndex");
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
     *  named {@code fieldName}. Picks the error message hypothesis based on the
     *  class's package — databind/blackbird fields usually mean a rename,
     *  anything else means the caller passed the wrong receiver. */
    protected static Object reflectField(Object instance, String fieldName) {
        Class<?> origClass = instance.getClass();
        Class<?> c = origClass;
        while (c != null) {
            try {
                Field f = c.getDeclaredField(fieldName);
                f.setAccessible(true);
                return f.get(instance);
            } catch (NoSuchFieldException ignore) {
                c = c.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError("cannot read field '" + fieldName + "' on "
                        + origClass.getName(), e);
            }
        }
        String pkg = origClass.getPackageName();
        String hint;
        if (pkg.startsWith("tools.jackson.databind")
                || pkg.startsWith("tools.jackson.module.blackbird")) {
            hint = "databind or blackbird may have renamed or removed it;"
                    + " update " + BlackbirdInjectionTestBase.class.getSimpleName()
                    + " to match.";
        } else {
            hint = "this looks like the wrong receiver type — '" + fieldName
                    + "' is an internal Jackson field and the caller passed an"
                    + " instance of " + origClass.getName() + ".";
        }
        throw new AssertionError("field '" + fieldName + "' not found on "
                + origClass.getName() + " (walked up full class chain) — " + hint);
    }

    /** True if `prop`'s class chain contains Blackbird's OptimizedSettableBeanProperty. */
    protected static boolean isOptimizedProperty(SettableBeanProperty prop) {
        return blackbirdClassChainIncludes(prop.getClass(), "OptimizedSettableBeanProperty");
    }

    /** True if `writer`'s class chain contains Blackbird's OptimizedBeanPropertyWriter. */
    protected static boolean isOptimizedWriter(BeanPropertyWriter writer) {
        return blackbirdClassChainIncludes(writer.getClass(), "OptimizedBeanPropertyWriter");
    }

    /** Walks the superclass chain of {@code cls} looking for a class whose simple
     *  name is {@code simpleName}. Used to recognize Blackbird's package-private
     *  optimized types without importing them. */
    protected static boolean classChainIncludes(Class<?> cls, String simpleName) {
        Class<?> c = cls;
        while (c != null) {
            if (simpleName.equals(c.getSimpleName())) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }

    /** Like {@link #classChainIncludes} but additionally requires the matched
     *  class to live inside a blackbird package. Guards against false positives
     *  from unrelated classes that happen to share a simple name. */
    protected static boolean blackbirdClassChainIncludes(Class<?> cls, String simpleName) {
        Class<?> c = cls;
        while (c != null) {
            if (simpleName.equals(c.getSimpleName())
                    && c.getPackageName().startsWith("tools.jackson.module.blackbird")) {
                return true;
            }
            c = c.getSuperclass();
        }
        return false;
    }
}
