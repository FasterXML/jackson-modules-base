package tools.jackson.module.afterburner.util;

import java.util.concurrent.*;

import tools.jackson.module.afterburner.AfterburnerTestBase;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

import static org.junit.jupiter.api.Assertions.*;

@Disabled("Fails on JVM 17 + JPMS")
public class MyClassLoaderTest extends AfterburnerTestBase
{
    @Test
    public void testNameReplacement() throws Exception
    {
        byte[] input = "Something with FOO in it (but not just FO!): FOOFOO".getBytes("UTF-8");
        int count = MyClassLoader.replaceName(input, "FOO", "BAR");
        assertEquals(3, count);
        assertEquals("Something with BAR in it (but not just FO!): BARBAR", new String(input, "UTF-8"));
    }

    @Test
    public void testLoadAndResolveTryParentSameClassTwice() {
        ClassName className = ClassName.constructFor(TestClass.class, "_TryParent_Twice");
        byte[] stubTestClassByteCode = generateTestClassByteCode(className, TestClass.class);
        className.assignChecksum(stubTestClassByteCode);

        ClassLoader parentClassLoader = MyClassLoaderTest.class.getClassLoader();
        MyClassLoader myClassLoader = new MyClassLoader(parentClassLoader, true);

        Class<?> clazz0 = myClassLoader.loadAndResolve(className, stubTestClassByteCode);
        Class<?> clazz1 = myClassLoader.loadAndResolve(className, stubTestClassByteCode);
        assertNotNull(clazz0, "first loaded class should not be null");
        assertNotNull(clazz1, "second loaded class should not be null");
        assertEquals(
                parentClassLoader,
                clazz0.getClassLoader(),
                "first class should be loaded with parent class loader");
        assertEquals(
                parentClassLoader,
                clazz1.getClassLoader(),
                "second class should be loaded with parent class loader");
        assertEquals(clazz0, clazz1,
                "the two loaded class instances should be equal");
    }

    @Test
    public void testLoadAndResolvePrivateSuperclassTryParentSameClassTwice() {
        ClassName className = ClassName.constructFor(PrivateTestClass.class, "_TryParent_Twice");
        byte[] stubTestClassByteCode = generateTestClassByteCode(className, PrivateTestClass.class);
        className.assignChecksum(stubTestClassByteCode);

        ClassLoader parentClassLoader = MyClassLoaderTest.class.getClassLoader();
        MyClassLoader myClassLoader = new MyClassLoader(parentClassLoader, true);

        Class<?> clazz0 = myClassLoader.loadAndResolve(className, stubTestClassByteCode);
        Class<?> clazz1 = myClassLoader.loadAndResolve(className, stubTestClassByteCode);
        assertNotNull(clazz0, "first loaded class should not be null");
        assertNotNull(clazz1, "second loaded class should not be null");
        assertEquals(
                parentClassLoader,
                clazz0.getClassLoader(),
                "first class should be loaded with parent class loader");
        assertEquals(
                parentClassLoader,
                clazz1.getClassLoader(),
                "second class should be loaded with parent class loader");
    }

    @Test
    public void testLoadAndResolveTryParentSameClassTwiceTwoThreads() {
        Class<?>[] loadedClasses = loadSameClassOnTwoThreads(TestClass.class, "_TryParent_TwoThreads", true);

        assertNotNull(loadedClasses[0], "first loaded class should not be null");
        assertNotNull(loadedClasses[1], "second loaded class should not be null");
        assertEquals(
                getParentClassLoader(),
                loadedClasses[0].getClassLoader(),
                "first class should be loaded with parent class loader");
        assertEquals(
                getParentClassLoader(),
                loadedClasses[1].getClassLoader(),
                "second class should be loaded with parent class loader");
        assertEquals(loadedClasses[0], loadedClasses[1],
                "the two loaded class instances should be equal");
    }

    @Test
    public void testLoadAndResolvePrivateSuperclassTryParentSameClassTwiceTwoThreads() {
        Class<?>[] loadedClasses = loadSameClassOnTwoThreads(PrivateTestClass.class, "_TryParent_TwoThreads", true);

        assertNotNull(loadedClasses[0], "first loaded class should not be null");
        assertNotNull(loadedClasses[1], "second loaded class should not be null");
        assertEquals(
                getParentClassLoader(),
                loadedClasses[0].getClassLoader(),
                "first class should be loaded with parent class loader");
        assertEquals(
                getParentClassLoader(),
                loadedClasses[1].getClassLoader(),
                "second class should be loaded with parent class loader");
        assertEquals(loadedClasses[0], loadedClasses[1],
                "the two loaded class instances should be equal");
    }

    /**
     * Simple public class to use for testing the MyClassLoader.
     */
    public static class TestClass {

    }

    /**
     * A private inner class to use for testing the MyClassLoader.
     */
    private static class PrivateTestClass {

    }

    /**
     * Create simple stub bytecode for a class with only a no-arg constructor.
     *
     * @param baseName      the base class name for the new class
     * @param superClass    the superclass from which the new class should extend
     * @return              the bytecode for a new class
     */
    private static byte[] generateTestClassByteCode(ClassName baseName, Class<?> superClass) {
        final String tmpClassName = baseName.getSlashedTemplate();
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        String superClassName = superClass.getName().replace(".", "/");

        cw.visit(V1_5, ACC_PUBLIC + ACC_SUPER + ACC_FINAL, tmpClassName, null, superClassName, null);
        cw.visitSource(baseName.getSourceFilename(), null);

        // default (no-arg) constructor:
        MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(ALOAD, 0);
        mv.visitMethodInsn(INVOKESPECIAL, superClassName, "<init>", "()V", false);
        mv.visitInsn(RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static ClassLoader getParentClassLoader() {
        return MyClassLoaderTest.class.getClassLoader();
    }

    private static Class<?>[] loadSameClassOnTwoThreads(Class<?> superclassOfClassToLoad,
                                                       String suffix,
                                                       boolean tryToUseParent) {
        final ClassName className = ClassName.constructFor(superclassOfClassToLoad, suffix);
        final byte[] stubTestClassByteCode = generateTestClassByteCode(className, superclassOfClassToLoad);
        className.assignChecksum(stubTestClassByteCode);

        ClassLoader parentClassLoader = getParentClassLoader();

        // The "normal" MyClassLoader will execute loadAndResolve without any artificial timing
        final MyClassLoader normalMyClassLoader = new MyClassLoader(parentClassLoader, tryToUseParent);

        // The "slow" MyClassLoader will block in the middle of its invocation of loadAndResolve,
        // until a permit is made available on the semaphore.
        final Semaphore semaphore = new Semaphore(0);
        final MyClassLoader slowMyClassLoader = new MyClassLoaderWithArtificialTiming(
                parentClassLoader, true, semaphore);
        ExecutorService exec = new ThreadPoolExecutor(
                2, 2, 0, TimeUnit.SECONDS, new SynchronousQueue<Runnable>());

        // First, we will start loading a class via slowMyClassLoader and wait a short while to allow it to
        // reach the point at which it will block. Then we will attempt the same load via normalMyClassLoader,
        // and wait another interval to allow it the chance to run to completion.
        // At that point, a permit will be released into the semaphore allowing the slowMyClassLoader to complete
        // the second half of its invocation.
        try {
            // Start loading via the slow loader on one thread
            Future<Class<?>> slowFutureClass = exec.submit(new Callable<Class<?>>() {
                @Override
                public Class<?> call() {
                    return slowMyClassLoader.loadAndResolve(className, stubTestClassByteCode);
                }
            });
            // We will wait here for a little while to allow slowMyClassLoader to complete
            // the first half of loadAndResolve() and reach the point at which it should block.
            Thread.sleep(500L);

            // Start loading the same class-to-load via the normal loader on a second thread
            Future<Class<?>> normalFutureClass = exec.submit(new Callable<Class<?>>() {
                @Override
                public Class<?> call() {
                    return normalMyClassLoader.loadAndResolve(className, stubTestClassByteCode);
                }
            });

            // Wait another interval to allow normalMyClassLoader a chance to complete
            Thread.sleep(500L);

            // Release a permit to allow the slowMyClassLoader to proceed from its block
            semaphore.release(1);

            // Return the result of both loads. This call will block until both loads have completed.
            return new Class<?>[]{normalFutureClass.get(), slowFutureClass.get()};
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
