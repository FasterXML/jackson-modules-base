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

/**
 * Wraps eligible stock bean serializers in a placeholder that generates a
 * per-bean writer at resolve time. Every gate that fails leaves the stock
 * serializer in place.
 */
public class BBSerializerModifier extends ValueSerializerModifier
{
    private static final long serialVersionUID = 1L;

    // Reserved for member access beyond public API; the v1 writer generator
    // only touches public getters.
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
            return serializer;
        }
    }

    private ValueSerializer<?> doModify(BeanDescription.Supplier beanDescRef,
            ValueSerializer<?> serializer)
    {
        if (serializer.getClass() != BeanSerializer.class
                && serializer.getClass() != UnrolledBeanSerializer.class) {
            return serializer;
        }
        Class<?> beanClass = beanDescRef.getBeanClass();
        if (!Modifier.isPublic(beanClass.getModifiers())
                || (beanClass.getEnclosingClass() != null
                        && !Modifier.isStatic(beanClass.getModifiers()))) {
            return serializer;
        }
        return new BBSerCodecPlaceholder((BeanSerializerBase) serializer);
    }
}
