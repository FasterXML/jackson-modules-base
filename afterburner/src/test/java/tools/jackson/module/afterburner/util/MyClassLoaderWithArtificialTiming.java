package tools.jackson.module.afterburner.util;

import java.util.concurrent.Semaphore;

/**
 * An extension of MyClassLoader with controllable blocking behavior of the
 * {@code defineClassOnParent(ClassLoader, String, byte[], int, int)} method,
 * allowing the interleaving of threads
 * through {@link #loadAndResolve(ClassName, byte[])} to be controlled by an external test harness.
 */
public class MyClassLoaderWithArtificialTiming extends MyClassLoader
{
    private Semaphore semaphore;

    public MyClassLoaderWithArtificialTiming(ClassLoader parent,
                                             boolean tryToUseParent,
                                             Semaphore semaphore) {
        super(parent, tryToUseParent);
        this.semaphore = semaphore;
    }

    @Override
    Class<?> defineClassOnParent(ClassLoader parentClassLoader, String className, byte[] byteCode, int offset, int length) {
        semaphore.acquireUninterruptibly();
        return super.defineClassOnParent(parentClassLoader, className, byteCode, offset, length);
    }
}
