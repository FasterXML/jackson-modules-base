package tools.jackson.module.blackbird.ser;

import java.io.InputStream;
import java.lang.reflect.Constructor;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.module.blackbird.BlackbirdTestBase;

import static org.junit.jupiter.api.Assertions.*;

// Cross-loader regression test (formerly tofix/TestBBClassloaders): a bean
// class redefined in a child classloader serializes correctly with the module
// registered. The engine takes the stock path for such a bean: the modifier
// gates demote it (its InnerClasses metadata raises
// IncompatibleClassChangeError from getEnclosingClass, and generated code
// refers to the bean class by name, which this module's loader would resolve
// to the parent-loaded class), and stock databind handles foreign-loader
// classes reflectively. The observable contract is correct output.
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
