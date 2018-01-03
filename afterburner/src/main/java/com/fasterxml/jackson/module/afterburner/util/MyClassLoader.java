package com.fasterxml.jackson.module.afterburner.util;

import java.lang.reflect.Method;
import java.nio.charset.Charset;

/**
 * Class loader that is needed to load generated classes.
 */
public class MyClassLoader extends ClassLoader
{
    private final static Charset UTF8 = Charset.forName("UTF-8");

    /**
     * Flag that determines if we should first try to load new class
     * using parent class loader or not; this may be done to try to
     * force access to protected/package-access properties.
     */
    protected final boolean _cfgUseParentLoader;
    
    public MyClassLoader(ClassLoader parent, boolean tryToUseParent)
    {
        super(parent);
        _cfgUseParentLoader = tryToUseParent;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        try {
            return super.loadClass(name);
        } catch (ClassNotFoundException e) {
            return getClass().getClassLoader().loadClass(name);
        }
    }

    /**
     * Helper method called to check whether it is acceptable to create a new
     * class in package that given class is part of.
     * This is used to prevent certain class of failures, related to access
     * limitations: for example, we can not add classes in sealed packages,
     * or core Java packages (java.*).
     * 
     * @since 2.2.1
     */
    public static boolean canAddClassInPackageOf(Class<?> cls)
    {
        final Package beanPackage = cls.getPackage();
        if (beanPackage != null) {
            if (beanPackage.isSealed()) {
                return false;
            }
            String pname = beanPackage.getName();
            /* 14-Aug-2014, tatu: java.* we do not want to touch, but
             *    javax is bit trickier. For now let's 
             */
            if (pname.startsWith("java.")
                    || pname.startsWith("javax.security.")) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * @param className Interface or abstract class that class to load should extend or 
     *   implement
     */
    public Class<?> loadAndResolve(ClassName className, byte[] byteCode)
        throws IllegalArgumentException
    {
        // First things first: just to be sure; maybe we have already loaded it?
        Class<?> old = findLoadedClass(className.getDottedName());
        if (old != null) {
            return old;
        }

        // Important: bytecode is generated with a template name (since bytecode itself
        // is used for checksum calculation) -- must be replaced now, however
        replaceName(byteCode, className.getSlashedTemplate(), className.getSlashedName());
        
        // First: let's try calling it directly on parent, to be able to access protected/package-access stuff:
        if (_cfgUseParentLoader && getParent() != null) {
            try {
                Method method = ClassLoader.class.getDeclaredMethod("defineClass",
                        new Class[] {String.class, byte[].class, int.class,
                        int.class});
                method.setAccessible(true);
                return (Class<?>)method.invoke(getParent(),
                        className.getDottedName(), byteCode, 0, byteCode.length);
            } catch (Exception e) {
                // Should we handle this somehow?
            }
        }

        // but if that doesn't fly, try to do it from our own class loader
        return resolveFromThisClassLoader(className, byteCode);
    }

    private Class<?> resolveFromThisClassLoader(ClassName className, byte[] byteCode) {
        try {
            Class<?> impl = defineClass(className.getDottedName(), byteCode, 0, byteCode.length);
            // important: must also resolve the class...
            resolveClass(impl);
            return impl;
        } catch (LinkageError e) {
            Throwable t = e;
            while (t.getCause() != null) {
                t = t.getCause();
            }
            throw new IllegalArgumentException("Failed to load class '"+className+"': "+t.getMessage(), t);
        }
    }

    public static int replaceName(byte[] byteCode,
            String from, String to)
    {
        byte[] fromB = from.getBytes(UTF8);
        byte[] toB = to.getBytes(UTF8);

        final int matchLength = fromB.length;

        // sanity check
        if (matchLength != toB.length) {
            throw new IllegalArgumentException("From String '"+from
                    +"' has different length than To String '"+to+"'");
        }

        int i = 0;
        int count = 0;

        // naive; for now has to do
        main_loop:
        for (int end = byteCode.length - matchLength; i <= end; ) {
            if (byteCode[i++] == fromB[0]) {
                for (int j = 1; j < matchLength; ++j) {
                    if (fromB[j] != byteCode[i+j-1]) {
                        continue main_loop;
                    }
                }
                ++count;
                System.arraycopy(toB, 0, byteCode, i-1, matchLength);
                i += (matchLength-1);
            }
        }
        return count;
    }
}
