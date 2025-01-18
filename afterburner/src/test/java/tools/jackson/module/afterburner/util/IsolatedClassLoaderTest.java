package tools.jackson.module.afterburner.util;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import tools.jackson.module.afterburner.AfterburnerModule;
import tools.jackson.module.afterburner.AfterburnerTestBase;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
@Disabled("Fails on JVM 17 + JPMS")
public class IsolatedClassLoaderTest extends AfterburnerTestBase
{
    @Test
    public void testBeanWithSeparateClassLoader() throws IOException {
        // 11-Jul-2023, tatu: No idea why/how, but Github CI barfs on
        // trying to load `Bean.class` so... let's just skip if so
        String str = System.getenv("GITHUB_ACTIONS");
        if ((str != null) && !str.isEmpty()) {
            System.err.println("Running as Github Action: will skip!");
            return;
        }

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
            return beanClz.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
