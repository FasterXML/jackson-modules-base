package com.fasterxml.jackson.module.mrbean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.mrbean.AbstractTypeMaterializer.MyClassLoader;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 *
 */
public class TestBridgeMethods extends BaseTest {

    public interface Drink {
        String getType();
    }

    public interface Coffee extends Drink {
        String getFlavor();
    }

    public interface DrinkHolder {
        Drink getDrink();
    }

    public interface CoffeeHolder extends DrinkHolder {
        Coffee getDrink();
    }

    public void testCovariantProperty() throws Exception
    {
        ObjectMapper mapper = newMrBeanMapper();

        Class<? extends CoffeeHolder> aClass = reorderBridgeMethodFirst(CoffeeHolder.class, "getDrink");
        CoffeeHolder result = mapper.readValue("{\"drink\":{\"type\":\"coffee\",\"flavor\":\"pumpkin spice\"}}", aClass);
        assertNotNull(result);
        assertNotNull(result.getDrink());
        assertEquals("coffee", result.getDrink().getType());
        assertEquals("pumpkin spice", result.getDrink().getFlavor());
    }

    /**
     * Rewrites the specified class so that the bridge version of the specified method appears *before* the non-bridge version.
     *
     * This is generally necessary (although maybe not sufficient) for reproducing the issue because:
     * <ul>
     *     <li>{@link Class#getDeclaredMethods()} <a href="https://bugs.java.com/bugdatabase/view_bug.do?bug_id=7023180">does not have a predictable order</a>.</li>
     *     <li>The Java compiler tends to place bridge methods after the actual non-bridge method.</li>
     *     <li>The order of methods in the class definition typically influences the order seen in reflection.</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    private static <T> Class<T> reorderBridgeMethodFirst(Class<T> clazz, final String methodName) throws IOException {
        ClassReader reader = new ClassReader(clazz.getName());
        ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM7, writer) {
            private BufferingMethodVisitor _nonBridgeMethod;
            private boolean _wroteBridgeMethod;

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                if (methodName.equals(name)) {
                    if (Modifier.isVolatile(access)) { // Modifier for BRIDGE Method matches VOLATILE Field
                        // Write the bridge method immediately, followed by the non-bridge method if already buffered
                        return new MethodVisitor(Opcodes.ASM7, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                            @Override
                            public void visitEnd() {
                                super.visitEnd();
                                _wroteBridgeMethod = true;
                                if (_nonBridgeMethod != null) {
                                    _nonBridgeMethod.visitNow();
                                    _nonBridgeMethod = null;
                                }
                            }
                        };
                    } else if (!_wroteBridgeMethod) {
                        // Non-bridge method appeared first... buffer it until we've encountered the bridge version
                        return _nonBridgeMethod = new BufferingMethodVisitor(writer, access, name, descriptor, signature, exceptions);
                    }
                }
                return super.visitMethod(access, name, descriptor, signature, exceptions);
            }
        }, ClassReader.EXPAND_FRAMES);
        byte[] byteArray = writer.toByteArray();
//        System.out.println(Base64.getEncoder().encodeToString(byteArray));
        return (Class<T>) new MyClassLoader(TestBridgeMethods.class.getClassLoader()).loadAndResolve(clazz.getName(), byteArray, clazz);
    }

    /**
     * Buffers the visits for a single {@link MethodVisitor}, enabling the method to be visited at a later point by invoking {@link #visitNow()}.
     */
    private static class BufferingMethodVisitor extends MethodVisitor {
        private final Supplier<MethodVisitor> _visitor;
        private final List<Consumer<MethodVisitor>> _visits = new ArrayList<>();

        public BufferingMethodVisitor(ClassWriter target, int access, String name, String descriptor, String signature, String[] exceptions) {
            super(Opcodes.ASM7);
            _visitor = () -> target.visitMethod(access, name, descriptor, signature, exceptions);
        }

        public void visitNow() {
            MethodVisitor visitor = _visitor.get();
            _visits.forEach(visit -> visit.accept(visitor));
        }

        @Override
        public void visitParameter(String name, int access) {
            _visits.add(visitor -> visitor.visitParameter(name, access));
        }

        @Override
        public AnnotationVisitor visitAnnotationDefault() {
            return null;
        }

        @Override
        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
            return null;
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            return null;
        }

        @Override
        public void visitAnnotableParameterCount(int parameterCount, boolean visible) {
            _visits.add(visitor -> visitor.visitAnnotableParameterCount(parameterCount, visible));
        }

        @Override
        public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
            return null;
        }

        @Override
        public void visitAttribute(Attribute attribute) {
            _visits.add(visitor -> visitor.visitAttribute(attribute));
        }

        @Override
        public void visitCode() {
            _visits.add(MethodVisitor::visitCode);
        }

        @Override
        public void visitFrame(int type, int numLocal, Object[] local, int numStack, Object[] stack) {
            _visits.add(visitor -> visitor.visitFrame(type, numLocal, local, numStack, stack));
        }

        @Override
        public void visitInsn(int opcode) {
            _visits.add(visitor -> visitor.visitInsn(opcode));
        }

        @Override
        public void visitIntInsn(int opcode, int operand) {
            _visits.add(visitor -> visitor.visitIntInsn(opcode, operand));
        }

        @Override
        public void visitVarInsn(int opcode, int var) {
            _visits.add(visitor -> visitor.visitVarInsn(opcode, var));
        }

        @Override
        public void visitTypeInsn(int opcode, String type) {
            _visits.add(visitor -> visitor.visitTypeInsn(opcode, type));
        }

        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            _visits.add(visitor -> visitor.visitFieldInsn(opcode, owner, name, descriptor));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor) {
            _visits.add(visitor -> visitor.visitMethodInsn(opcode, owner, name, descriptor));
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
            _visits.add(visitor -> visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface));
        }

        @Override
        public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
            _visits.add(visitor -> visitor.visitInvokeDynamicInsn(name, descriptor, bootstrapMethodHandle, bootstrapMethodArguments));
        }

        @Override
        public void visitJumpInsn(int opcode, Label label) {
            _visits.add(visitor -> visitor.visitJumpInsn(opcode, label));
        }

        @Override
        public void visitLabel(Label label) {
            _visits.add(visitor -> visitor.visitLabel(label));
        }

        @Override
        public void visitLdcInsn(Object value) {
            _visits.add(visitor -> visitor.visitLdcInsn(value));
        }

        @Override
        public void visitIincInsn(int var, int increment) {
            _visits.add(visitor -> visitor.visitIincInsn(var, increment));
        }

        @Override
        public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
            _visits.add(visitor -> visitor.visitTableSwitchInsn(min, max, dflt, labels));
        }

        @Override
        public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
            _visits.add(visitor -> visitor.visitLookupSwitchInsn(dflt, keys, labels));
        }

        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            _visits.add(visitor -> visitor.visitMultiANewArrayInsn(descriptor, numDimensions));
        }

        @Override
        public AnnotationVisitor visitInsnAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            return null;
        }

        @Override
        public void visitTryCatchBlock(Label start, Label end, Label handler, String type) {
            _visits.add(visitor -> visitor.visitTryCatchBlock(start, end, handler, type));
        }

        @Override
        public AnnotationVisitor visitTryCatchAnnotation(int typeRef, TypePath typePath, String descriptor, boolean visible) {
            return null;
        }

        @Override
        public void visitLocalVariable(String name, String descriptor, String signature, Label start, Label end, int index) {
            _visits.add(visitor -> visitor.visitLocalVariable(name, descriptor, signature, start, end, index));
        }

        @Override
        public AnnotationVisitor visitLocalVariableAnnotation(int typeRef, TypePath typePath, Label[] start, Label[] end, int[] index, String descriptor, boolean visible) {
            return null;
        }

        @Override
        public void visitLineNumber(int line, Label start) {
            _visits.add(visitor -> visitor.visitLineNumber(line, start));
        }

        @Override
        public void visitMaxs(int maxStack, int maxLocals) {
            _visits.add(visitor -> visitor.visitMaxs(maxStack, maxLocals));
        }

        @Override
        public void visitEnd() {
            _visits.add(MethodVisitor::visitEnd);
        }
    }
}
