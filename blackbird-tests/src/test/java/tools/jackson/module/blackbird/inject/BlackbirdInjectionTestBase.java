package tools.jackson.module.blackbird.inject;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.deser.ValueDeserializerModifier;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.module.blackbird.BlackbirdModule;

import static org.junit.jupiter.api.Assertions.assertNotNull;

// Test utilities for verifying that Blackbird's codec generation engaged for a
// given POJO loaded from the unnamed module (classpath). Blackbird installs a
// per-bean placeholder (BBCodecPlaceholder / BBSerCodecPlaceholder) that
// resolves to a generated hidden-class codec; the placeholder types are
// package-private inside the blackbird module, so these checks match by simple
// name and package rather than compile-time type references. Mirrors the
// afterburner-tests harness; see that module's README for the broader
// rationale.
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
            // Modifiers run in reverse registration order, so the capture
            // module registers first to observe what Blackbird installed.
            this.mapper = JsonMapper.builder()
                    .addModule(capture)
                    .addModule(new BlackbirdModule())
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

    /** True if Blackbird installed its deserializer codec for the captured value. */
    protected static boolean isBlackbirdDeserCodec(ValueDeserializer<?> deser) {
        return blackbirdClassChainIncludes(deser.getClass(), "BBCodecPlaceholder");
    }

    /** True if Blackbird installed its serializer codec for the captured value. */
    protected static boolean isBlackbirdSerCodec(ValueSerializer<?> ser) {
        return blackbirdClassChainIncludes(ser.getClass(), "BBSerCodecPlaceholder");
    }

    /** Walks the superclass chain of {@code cls} looking for a class whose
     *  simple name is {@code simpleName} and that lives inside a blackbird
     *  package. Recognizes Blackbird's package-private types without importing
     *  them, and guards against unrelated classes sharing a simple name. */
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
