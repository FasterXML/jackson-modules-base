package com.fasterxml.jackson.module.blackbird.util;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.module.blackbird.deser.otherpackage.ModeBuilderBase;

import static org.junit.jupiter.api.Assertions.*;

public class TestReflectionHack
{
    static class SamePackage { }

    private static final Lookup LOOKUP = MethodHandles.lookup();

    @Test
    public void testCanAccessClass() throws Exception
    {
        Class<?> mode = _packagePrivateFromOtherPackage();
        assertTrue(ReflectionHack.canAccessClass(LOOKUP, int.class));
        assertTrue(ReflectionHack.canAccessClass(LOOKUP, int[].class));
        assertTrue(ReflectionHack.canAccessClass(LOOKUP, String.class));
        assertTrue(ReflectionHack.canAccessClass(LOOKUP, ModeBuilderBase.class));
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
        assertTrue(ReflectionHack.canAccessClassWithoutModules(self, ModeBuilderBase.class));
        assertTrue(ReflectionHack.canAccessClassWithoutModules(self, SamePackage.class));
        assertFalse(ReflectionHack.canAccessClassWithoutModules(self, _packagePrivateFromOtherPackage()));
    }

    private static Class<?> _packagePrivateFromOtherPackage() throws Exception {
        return Class.forName("com.fasterxml.jackson.module.blackbird.deser.otherpackage.Mode");
    }
}
