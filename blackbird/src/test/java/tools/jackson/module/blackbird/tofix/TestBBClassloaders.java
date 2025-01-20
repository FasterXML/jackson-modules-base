package tools.jackson.module.blackbird.tofix;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;
import tools.jackson.module.blackbird.testutil.failure.JacksonTestFailureExpected;

import static org.junit.jupiter.api.Assertions.*;

public class TestBBClassloaders extends BlackbirdTestBase
{
    protected final String resourceName =
            (TestBBClassloaders.class.getName() + "$Data")
                .replace('.', '/').concat(".class");

    // Note: looks this test: passes on JDKs OTHER than 11 for Jackson 2.x,
    // but fails for JDK 17+ for Jackson 3.x.
    @Test
    @JacksonTestFailureExpected
    public void testLoadInChildClassloader() throws Exception
    {
        // 19-Jan-2025, tatu: only fails on JDK 11 specifically, not on JDK 17 or later
        //
        // So just skip on that
        if (System.getProperty("java.version").startsWith("11.")) {
             System.out.println("Skipping `testLoadInChildClassloader()` on JDK 11");
             return;
        }
        TestLoader loader = new TestLoader(getClass().getClassLoader());
        Class<?> clazz = Class.forName(Data.class.getName(), true, loader);
        ObjectMapper mapper = newObjectMapper();
        Constructor<?> constructor = clazz.getConstructor(int.class);
        Object data = constructor.newInstance(42);
        assertEquals("{\"field\":42}", mapper.writeValueAsString(data));
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
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        InputStream in = getResource(resourceName).openStream();
                        int i;
                        while ((i = in.read()) != -1) {
                            baos.write(i);
                        }
                        byte[] bytes = baos.toByteArray();
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
