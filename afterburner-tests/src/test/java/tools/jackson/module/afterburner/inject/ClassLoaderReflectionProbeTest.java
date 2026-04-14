package tools.jackson.module.afterburner.inject;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Verifies the detection hook for issue #348: afterburner now probes at static
// init time whether ClassLoader#findLoadedClass / ClassLoader#defineClass are
// reflectively accessible. The result is exposed via a static method
// MyClassLoader.isParentClassLoaderReflectionAvailable(). If the probe fails,
// afterburner logs a WARNING once and short-circuits the parent-classloader
// cache path.
//
// This test has two jobs:
//
//  1. Confirm the public accessor exists and is reachable — i.e. a future
//     refactor can't silently remove it without breaking this test.
//
//  2. Confirm it returns `true` in this test environment. The afterburner-tests
//     pom passes `--add-opens java.base/java.lang=ALL-UNNAMED` on the surefire
//     argLine precisely so the probe succeeds. If this assertion ever fails,
//     either the argLine was dropped (fix the pom) or afterburner's probe logic
//     broke (fix the probe). Either way, the GeneratedClassCachingTest's
//     `testSameBeanAcrossMappersReusesSameMutatorClass` assertion will also
//     fail — but this test gives a more direct failure message pointing at
//     the probe, not the consequence.
public class ClassLoaderReflectionProbeTest
{
    private static final String MY_CL =
            "tools.jackson.module.afterburner.util.MyClassLoader";

    @Test
    public void testProbeAccessorExistsAndIsPublic() throws Exception
    {
        // MyClassLoader is in a non-exported package of the afterburner JPMS
        // module, but we're in the unnamed module (classpath), so we can
        // reflectively reach it. `isParentClassLoaderReflectionAvailable` is
        // a public static method so the probe result is observable without
        // poking at private fields.
        Class<?> myClassLoader = Class.forName(MY_CL);
        Method m = myClassLoader.getMethod("isParentClassLoaderReflectionAvailable");
        assertTrue(java.lang.reflect.Modifier.isPublic(m.getModifiers()),
                "isParentClassLoaderReflectionAvailable() should be public");
        assertTrue(java.lang.reflect.Modifier.isStatic(m.getModifiers()),
                "isParentClassLoaderReflectionAvailable() should be static");
        assertEquals(boolean.class, m.getReturnType(),
                "isParentClassLoaderReflectionAvailable() should return boolean");
    }

    @Test
    public void testProbeReturnsTrueInTestEnvironment() throws Exception
    {
        // The afterburner-tests pom sets
        // `--add-opens java.base/java.lang=ALL-UNNAMED` on the surefire
        // argLine (see the pom for rationale + issue #348). With that flag
        // in place, the probe must succeed — otherwise either the pom was
        // tampered with, or afterburner's probe logic is broken.
        Class<?> myClassLoader = Class.forName(MY_CL);
        Method m = myClassLoader.getMethod("isParentClassLoaderReflectionAvailable");
        Object result = m.invoke(null);
        assertEquals(Boolean.TRUE, result,
                "Expected parent-classloader reflection to be available in"
                        + " the afterburner-tests environment, because this"
                        + " module's surefire argLine sets"
                        + " `--add-opens java.base/java.lang=ALL-UNNAMED`. If"
                        + " the probe is returning false, either the argLine"
                        + " was dropped from the pom or the probe logic in"
                        + " MyClassLoader's static initializer is broken."
                        + " See issue #348 for context.");
    }
}
