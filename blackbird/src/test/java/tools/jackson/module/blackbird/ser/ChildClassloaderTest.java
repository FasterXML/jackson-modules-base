package tools.jackson.module.blackbird.ser;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.module.blackbird.BlackbirdModule;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// Cross-loader regression test (formerly tofix/TestBBClassloaders): a bean
// class redefined in a child classloader serializes correctly - and now
// ACCELERATES - with the module registered. Generated code never names the
// bean class (values load through unreflected constant handles bound to the
// child-loaded Class), so the foreign loader no longer gates codec
// generation; this widened pin holds that capability.
//
// Old Blackbird failed this on the module path only because the test read the
// class bytes through classloader getResource, which JPMS encapsulation nulls;
// Class#getResourceAsStream resolves inside this module and works in both
// modes.
public class ChildClassloaderTest extends BlackbirdTestBase
{
    @Test
    public void testLoadInChildClassloader() throws Exception
    {
        TestLoader loader = new TestLoader(getClass().getClassLoader());
        Class<?> clazz = Class.forName(Data.class.getName(), true, loader);
        assertNotSame(Data.class, clazz);
        Map<Class<?>, ValueSerializer<?>> seen = new ConcurrentHashMap<>();
        ObjectMapper mapper = capturingMapper(seen);
        Constructor<?> constructor = clazz.getConstructor(int.class);
        Object data = constructor.newInstance(42);
        assertEquals("{\"field\":42}", mapper.writeValueAsString(data));
        ValueSerializer<?> captured = seen.get(clazz);
        assertNotNull(captured, "no serializer captured for the child-loaded class");
        assertEquals("BBWriterPlaceholder", captured.getClass().getSimpleName(),
                "child-loaded bean did not engage a codec");
        Field codec = captured.getClass().getDeclaredField("_codec");
        codec.setAccessible(true);
        assertNotNull(codec.get(captured),
                "child-loaded bean engaged but no writer generated");
    }

    private static ObjectMapper capturingMapper(Map<Class<?>, ValueSerializer<?>> seen) {
        SimpleModule capture = new SimpleModule("capture") {
            private static final long serialVersionUID = 1L;
            @Override
            public void setupModule(SetupContext ctxt) {
                super.setupModule(ctxt);
                ctxt.addSerializerModifier(new ValueSerializerModifier() {
                    private static final long serialVersionUID = 1L;
                    @Override
                    public ValueSerializer<?> modifySerializer(SerializationConfig cfg,
                            BeanDescription.Supplier ref, ValueSerializer<?> s) {
                        seen.put(ref.getBeanClass(), s);
                        return s;
                    }
                });
            }
        };
        return JsonMapper.builder()
                .addModule(capture)
                .addModule(new BlackbirdModule())
                .build();
    }

    public static class Data {
        private int field;

        public Data(int field) {
            this.field = field;
        }

        public int getField() {
            return field;
        }

        public void setField(int field) {
            this.field = field;
        }
    }

    public class TestLoader extends ClassLoader {
        public TestLoader(final ClassLoader parent) {
            super(parent);
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                try {
                    Class<?> clazz;
                    if (Data.class.getName().equals(name)) {
                        byte[] bytes;
                        try (InputStream in = ChildClassloaderTest.class
                                .getResourceAsStream("ChildClassloaderTest$Data.class")) {
                            bytes = in.readAllBytes();
                        }
                        clazz = defineClass(name, bytes, 0, bytes.length);
                    } else {
                        clazz = super.loadClass(name, resolve);
                    }
                    if (resolve) {
                        resolveClass(clazz);
                    }
                    return clazz;
                } catch (Exception e) {
                    throw new ClassNotFoundException("Unable to load class", e);
                }
            }
        }
    }
}
