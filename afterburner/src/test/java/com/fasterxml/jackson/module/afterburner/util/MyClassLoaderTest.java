package com.fasterxml.jackson.module.afterburner.util;

import com.fasterxml.jackson.module.afterburner.AfterburnerTestBase;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;

import static org.objectweb.asm.Opcodes.*;

public class MyClassLoaderTest extends AfterburnerTestBase
{
    public void testNameReplacement() throws Exception
    {
        byte[] input = "Something with FOO in it (but not just FO!): FOOFOO".getBytes("UTF-8");
        int count = MyClassLoader.replaceName(input, "FOO", "BAR");
        assertEquals(3, count);
        assertEquals("Something with BAR in it (but not just FO!): BARBAR", new String(input, "UTF-8"));
    }


    public void testLoadAndResolveTryParentSameClassTwice() {
        ClassName className = ClassName.constructFor(TestClass.class, "_TryParent_Twice");
        byte[] stubTestClassByteCode = generateTestClassByteCode(className, TestClass.class);
        className.assignChecksum(stubTestClassByteCode);

        ClassLoader parentClassLoader = MyClassLoaderTest.class.getClassLoader();
        MyClassLoader myClassLoader = new MyClassLoader(parentClassLoader, true);

        Class<?> clazz0 = myClassLoader.loadAndResolve(className, stubTestClassByteCode);
        Class<?> clazz1 = myClassLoader.loadAndResolve(className, stubTestClassByteCode);
        assertNotNull("first loaded class should not be null", clazz0);
        assertNotNull("second loaded class should not be null", clazz1);
        assertEquals(
                "first class should be loaded with parent class loader",
                parentClassLoader,
                clazz0.getClassLoader());
        assertEquals(
                "second class should be loaded with parent class loader",
                parentClassLoader,
                clazz1.getClassLoader());
        assertEquals("the two loaded class instances should be equal", clazz0, clazz1);
    }

    public void testLoadAndResolvePrivateSuperclassTryParentSameClassTwice() {
        ClassName className = ClassName.constructFor(PrivateTestClass.class, "_TryParent_Twice");
        byte[] stubTestClassByteCode = generateTestClassByteCode(className, PrivateTestClass.class);
        className.assignChecksum(stubTestClassByteCode);

        ClassLoader parentClassLoader = MyClassLoaderTest.class.getClassLoader();
        MyClassLoader myClassLoader = new MyClassLoader(parentClassLoader, true);

        Class<?> clazz0 = myClassLoader.loadAndResolve(className, stubTestClassByteCode);
        Class<?> clazz1 = myClassLoader.loadAndResolve(className, stubTestClassByteCode);
        assertNotNull("first loaded class should not be null", clazz0);
        assertNotNull("second loaded class should not be null", clazz1);
        assertEquals(
                "first class should be loaded with parent class loader",
                parentClassLoader,
                clazz0.getClassLoader());
        assertEquals(
                "second class should be loaded with parent class loader",
                parentClassLoader,
                clazz1.getClassLoader());
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

}
