package tools.jackson.module.blackbird.ser;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.introspect.AnnotatedField;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.module.blackbird.codegen.BeanWriterGenerator;
import tools.jackson.module.blackbird.codegen.BeanWriterGenerator.GenWProp;
import tools.jackson.module.blackbird.codegen.BeanWriterGenerator.WKind;
import tools.jackson.module.blackbird.codegen.GeneratedWriterBase;

/**
 * Builds a generated serializer from a resolved stock bean serializer, or
 * returns null when the bean does not qualify. Same philosophy as the
 * deserializer side: anything not cheaply verifiable rides the stock
 * PropertyWriter from generated code.
 */
final class BBSerCodecFactory
{
    private static final Set<String> STOCK_SCALAR_SERS = Set.of(
            "tools.jackson.databind.ser.jdk.StringSerializer",
            "tools.jackson.databind.ser.jdk.NumberSerializers$IntegerSerializer",
            "tools.jackson.databind.ser.jdk.NumberSerializers$LongSerializer",
            "tools.jackson.databind.ser.jdk.BooleanSerializer");

    private BBSerCodecFactory() {}

    static ValueSerializer<Object> tryGenerate(BeanSerializerBase delegate,
            SerializationContext ctxt) {
        try {
            return generate(delegate, ctxt);
        } catch (Throwable t) {
            return null;
        }
    }

    private static ValueSerializer<Object> generate(BeanSerializerBase delegate,
            SerializationContext ctxt) throws ReflectiveOperationException {
        if (delegate.usesObjectId() || delegate.getFilterId() != null) {
            return null;
        }
        Class<?> beanClass = delegate.handledType();
        if (!Modifier.isPublic(beanClass.getModifiers())) {
            return null;
        }
        if (!visibleToGenerator(beanClass)) {
            return null;
        }
        List<GenWProp> props = new ArrayList<>();
        for (Iterator<PropertyWriter> it = delegate.properties(); it.hasNext(); ) {
            PropertyWriter writer = it.next();
            props.add(classify(writer, beanClass));
        }
        if (props.isEmpty()) {
            return null;
        }
        return BeanWriterGenerator.generate(beanClass, props, delegate);
    }

    private static GenWProp classify(PropertyWriter writer, Class<?> beanClass) {
        if (writer.getClass() != BeanPropertyWriter.class) {
            return stock(writer);
        }
        BeanPropertyWriter bpw = (BeanPropertyWriter) writer;
        if (bpw.willSuppressNulls()) {
            return stock(writer);
        }
        ValueSerializer<Object> valueSer = bpw.getSerializer();
        if (bpw.getMember() instanceof AnnotatedMethod am) {
            return classifyGetter(writer, beanClass, bpw, am.getAnnotated(), valueSer);
        }
        if (bpw.getMember() instanceof AnnotatedField af) {
            return classifyField(writer, bpw, af.getAnnotated(), valueSer);
        }
        return stock(writer);
    }

    private static GenWProp classifyGetter(PropertyWriter writer, Class<?> beanClass,
            BeanPropertyWriter bpw, Method getter, ValueSerializer<Object> valueSer) {
        if (getter == null || getter.getParameterCount() != 0
                || !Modifier.isPublic(getter.getModifiers())
                || !Modifier.isPublic(getter.getDeclaringClass().getModifiers())
                || getter.getDeclaringClass() != beanClass) {
            return stock(writer);
        }
        if (valueSer instanceof GeneratedWriterBase child
                && !getter.getReturnType().isPrimitive()) {
            return new GenWProp(WKind.CHILD, getter, writer, bpw.getSerializedName(), child);
        }
        WKind kind = scalarKind(getter.getReturnType());
        if (kind == null || valueSer == null
                || !STOCK_SCALAR_SERS.contains(valueSer.getClass().getName())) {
            return stock(writer);
        }
        return new GenWProp(kind, getter, writer, bpw.getSerializedName(), null);
    }

    // Public fields (any finality - reads are unrestricted) load through a
    // generated getfield. Non-public fields have no generated read path on the
    // serializer side and stay on the stock writer, the same limitation
    // non-public getters have.
    private static GenWProp classifyField(PropertyWriter writer, BeanPropertyWriter bpw,
            Field field, ValueSerializer<Object> valueSer) {
        if (field == null || Modifier.isStatic(field.getModifiers())
                || !Modifier.isPublic(field.getModifiers())
                || !Modifier.isPublic(field.getDeclaringClass().getModifiers())) {
            return stock(writer);
        }
        Class<?> raw = field.getType();
        if (valueSer instanceof GeneratedWriterBase child && !raw.isPrimitive()) {
            return new GenWProp(WKind.CHILD, null, writer, bpw.getSerializedName(), child, field);
        }
        WKind kind = scalarKind(raw);
        if (kind == null || valueSer == null
                || !STOCK_SCALAR_SERS.contains(valueSer.getClass().getName())) {
            return stock(writer);
        }
        return new GenWProp(kind, null, writer, bpw.getSerializedName(), null, field);
    }

    private static GenWProp stock(PropertyWriter writer) {
        return new GenWProp(WKind.STOCK, null, writer, null, null);
    }

    // Rationale in BBCodecFactory.visibleToGenerator: generated code refers to
    // the bean class by name, resolved through this module's loader.
    private static boolean visibleToGenerator(Class<?> cls) {
        try {
            return Class.forName(cls.getName(), false,
                    GeneratedWriterBase.class.getClassLoader()) == cls;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static WKind scalarKind(Class<?> raw) {
        if (raw == String.class) {
            return WKind.STRING;
        }
        if (raw == int.class) {
            return WKind.INT;
        }
        if (raw == long.class) {
            return WKind.LONG;
        }
        if (raw == boolean.class) {
            return WKind.BOOLEAN;
        }
        return null;
    }
}
