package tools.jackson.module.blackbird.ser;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Modifier;
import java.util.function.Function;

import tools.jackson.databind.BeanDescription;
import tools.jackson.databind.SerializationConfig;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.ser.BeanSerializer;
import tools.jackson.databind.ser.UnrolledBeanSerializer;
import tools.jackson.databind.ser.ValueSerializerModifier;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.module.blackbird.codegen.CodegenDebug;
import tools.jackson.module.blackbird.codegen.CodegenFallbacks;

/**
 * Wraps eligible stock bean serializers in a placeholder that generates a
 * per-bean writer at resolve time. Every gate that fails leaves the stock
 * serializer in place.
 */
public class BBSerializerModifier extends ValueSerializerModifier
{
    private static final long serialVersionUID = 1L;

    // Kept for the released BlackbirdModule(Function) contract; the codec
    // no longer needs it. Member access rides databind's own fixAccess (see
    // MemberHandles), so a user lookup is not required for acceleration.
    private final Function<Class<?>, MethodHandles.Lookup> _lookups;

    public BBSerializerModifier(Function<Class<?>, MethodHandles.Lookup> lookups) {
        _lookups = lookups;
    }

    @Override
    public ValueSerializer<?> modifySerializer(SerializationConfig config,
            BeanDescription.Supplier beanDescRef, ValueSerializer<?> serializer)
    {
        // Gate failures of any kind leave the stock serializer in place. The
        // reflective gates can throw for exotic classes (a bean from a foreign
        // classloader with inconsistent InnerClasses metadata raises
        // IncompatibleClassChangeError from getEnclosingClass), and an
        // acceleration modifier must never break a bean stock databind handles.
        try {
            return doModify(beanDescRef, serializer);
        } catch (RuntimeException | LinkageError e) {
            CodegenFallbacks.gateFailure(beanDescRef.getBeanClass(), e);
            return serializer;
        }
    }

    private ValueSerializer<?> doModify(BeanDescription.Supplier beanDescRef,
            ValueSerializer<?> serializer)
    {
        if (serializer.getClass() != BeanSerializer.class
                && serializer.getClass() != UnrolledBeanSerializer.class) {
            return skip(beanDescRef, serializer, "not a stock bean serializer");
        }
        // Non-static inner classes stay stock for symmetry with the reader
        // side; no other class-shape gate remains, since generated writers
        // never name the bean class. The static check runs first: for a
        // static member class redefined in a foreign classloader,
        // getEnclosingClass raises IncompatibleClassChangeError (its
        // InnerClasses metadata resolves to the parent-loaded owner), and
        // such beans accelerate now.
        Class<?> beanClass = beanDescRef.getBeanClass();
        if (!Modifier.isStatic(beanClass.getModifiers())
                && beanClass.getEnclosingClass() != null) {
            return skip(beanDescRef, serializer, "non-static inner class");
        }
        return new BBWriterPlaceholder((BeanSerializerBase) serializer);
    }

    private static ValueSerializer<?> skip(BeanDescription.Supplier beanDescRef,
            ValueSerializer<?> serializer, String reason) {
        CodegenDebug.logSkip("writer", beanDescRef.getBeanClass(), reason);
        return serializer;
    }

}
