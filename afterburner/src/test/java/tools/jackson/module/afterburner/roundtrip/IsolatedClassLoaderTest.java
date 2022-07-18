package tools.jackson.module.afterburner.roundtrip;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.afterburner.AfterburnerModule;
import junit.framework.TestCase;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Made for a bug found when trying to serialize an Object loaded from an
 * parent classloader (or, more generally, an isolated classloader).<br/><br/>
 *<p>
 * What happens is that MyClassLoader defaults the parent cl to the bean's
 * classloader, which then extends BeanPropertyAccessor. However, the
 * bean's classloader doesn't know what BeanPropertyAccessor is and blows
 * up.<br/><br/>
 *
 * The Bean.class (in the resources dir) is simply defined as:<br/><br/>
 *
 * <pre>
 *     // NOTE: defined in "default" package so NO explicit package!!!
 * 
 *     public class Bean {
 *         private String value = "some string";
 *         public String getValue() { return value; }
 *     }
 * </pre>
 *
 * It's important that Bean.class doesn't have a setter; otherwise the
 * exception doesn't occur.<br/><br/>
 * Also important to note that the class is NOT defined as being in same
 * package but simply located in same subdirectory under {@code src/test/resource/}.
 * It would be nice to improve test to avoid storing pre-compiled class but
 * for now it'll have to do.
 */
public class IsolatedClassLoaderTest extends TestCase {

    public void testBeanWithSeparateClassLoader() throws IOException {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new AfterburnerModule())
                .build();
        Object bean = makeObjectFromIsolatedClassloader();
        String result = mapper.writeValueAsString(bean);
        assertEquals("{\"value\":\"some string\"}", result);
    }

    private Object makeObjectFromIsolatedClassloader() {
        try {
            URL[] resourcesDir = {getClass().getResource("")};
            // Parent classloader is null so Afterburner is inaccessible.
            @SuppressWarnings("resource")
            ClassLoader isolated = new URLClassLoader(resourcesDir, null);
            Class<?> beanClz = isolated.loadClass("Bean");
            return beanClz.newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
