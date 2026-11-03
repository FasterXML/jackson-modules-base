package com.fasterxml.jackson.module.blackbird.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.module.blackbird.deser.otherpackage.ModeBeanBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestReflectionHack
{
    static class SamePackage { }

    static class Base {
        public Object get() { return null; }
    }

    // Declares `String get()` and, for overriding, a bridge `Object get()`
    static class Covariant extends Base {
        @Override
        public String get() { return "x"; }
    }

    private static final Lookup LOOKUP = MethodHandles.lookup();

    @Test
    public void testCanAccessClass() throws Exception
    {
        Class<?> mode = _packagePrivateFromOtherPackage();
        assertTrue(ReflectionHack.canAccessClass(LOOKUP, int.class));
        assertTrue(ReflectionHack.canAccessClass(LOOKUP, int[].class));
        assertTrue(ReflectionHack.canAccessClass(LOOKUP, String.class));
        assertTrue(ReflectionHack.canAccessClass(LOOKUP, ModeBeanBase.class));
        assertTrue(ReflectionHack.canAccessClass(LOOKUP, SamePackage[].class));
        assertFalse(ReflectionHack.canAccessClass(LOOKUP, mode));
        assertFalse(ReflectionHack.canAccessClass(LOOKUP, java.lang.reflect.Array.newInstance(mode, 0).getClass()));
    }

    // what Java 8 uses, which can't be picked on newer JDKs
    @Test
    public void testCanAccessClassWithoutModules() throws Exception
    {
        Class<?> self = getClass();
        assertTrue(ReflectionHack.canAccessClassWithoutModules(self, String.class));
        assertTrue(ReflectionHack.canAccessClassWithoutModules(self, ModeBeanBase.class));
        assertTrue(ReflectionHack.canAccessClassWithoutModules(self, SamePackage.class));
        assertFalse(ReflectionHack.canAccessClassWithoutModules(self, _packagePrivateFromOtherPackage()));
    }

    @SuppressWarnings("deprecation")
    @Test
    public void testWithoutAccessOverride() throws Exception
    {
        Method plain = Covariant.class.getDeclaredMethod("get");
        assertSame(plain, ReflectionHack.withoutAccessOverride(plain));

        // the bridge and the method it bridges to have the same name and parameters
        for (Method method : Covariant.class.getDeclaredMethods()) {
            if (!method.getName().equals("get")) {
                continue;
            }
            method.setAccessible(true);
            Method copy = ReflectionHack.withoutAccessOverride(method);
            assertNotSame(method, copy);
            assertEquals(method, copy);
            assertEquals(method.getReturnType(), copy.getReturnType());
            assertFalse(copy.isAccessible());
        }
    }

    private static Class<?> _packagePrivateFromOtherPackage() throws Exception {
        return Class.forName("com.fasterxml.jackson.module.blackbird.deser.otherpackage.Mode");
    }
}
