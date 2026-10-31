package tools.jackson.module.blackbird.ser;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
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
import tools.jackson.databind.ser.impl.FilteredBeanPropertyWriter;
import tools.jackson.databind.ser.jdk.BooleanSerializer;
import tools.jackson.databind.ser.jdk.NumberSerializers;
import tools.jackson.databind.ser.jdk.StringSerializer;
import tools.jackson.databind.ser.std.NullSerializer;
import tools.jackson.databind.MapperFeature;
import tools.jackson.module.blackbird.codegen.ReaderCodegen.ViewStrategy;
import tools.jackson.module.blackbird.codegen.WriterCodegen;
import tools.jackson.module.blackbird.codegen.WriterCodegen.GenWProp;
import tools.jackson.module.blackbird.codegen.WriterCodegen.WKind;
import tools.jackson.module.blackbird.codegen.MemberHandles;
import tools.jackson.module.blackbird.internal.GeneratedWriterBase;
import tools.jackson.module.blackbird.codegen.CodegenDebug;
import tools.jackson.module.blackbird.codegen.CodegenFallbacks;

/**
 * Builds a generated serializer from a resolved stock bean serializer, or
 * returns null when the bean does not qualify. Same philosophy as the
 * deserializer side: anything not cheaply verifiable rides the stock
 * PropertyWriter from generated code, and member access goes through
 * {@link MemberHandles} - databind ran fixAccess on every accessor before
 * this factory sees it, so an unreflect failure means the stock path could
 * not read the property either, and the property demotes.
 */
final class BBWriterFactory
{
    // Exact classes: a subclass may write differently
    private static final Set<Class<?>> STOCK_SCALAR_SERS = Set.of(
            StringSerializer.class,
            NumberSerializers.IntegerSerializer.class,
            NumberSerializers.LongSerializer.class,
            BooleanSerializer.class);

    private BBWriterFactory() {}

    // props and filtered: the resolved _props and _filteredProps of delegate,
    // which are protected. asArray: the bean's own shape is ARRAY.
    static ValueSerializer<Object> tryGenerate(BeanSerializerBase delegate,
            BeanPropertyWriter[] props, BeanPropertyWriter[] filtered, boolean hasTypeId,
            boolean asArray, SerializationContext ctxt) {
        ValueSerializer<Object> codec;
        try {
            codec = generate(delegate, props, filtered, hasTypeId,
                    asArray, ctxt);
        } catch (StackOverflowError e) {
            // Generation ran out of stack deep in a recursive resolution: not
            // a generator bug, and the stock path, which needs no generation,
            // can still complete the call. Nothing more (not even logging)
            // runs on the nearly exhausted stack. In strict mode (the test
            // suites) it more likely is unbounded recursion in the
            // generator, so it fails the test; the property name is a
            // constant, so the check initializes no Blackbird class.
            if (Boolean.getBoolean(CodegenFallbacks.FAIL_ON_ERROR_PROPERTY)) {
                throw e;
            }
            return null;
        } catch (VirtualMachineError e) {
            // Out of memory or a broken JVM, not a generator bug: never demote
            throw e;
        } catch (Throwable t) {
            if (CodegenDebug.ENABLED) {
                CodegenDebug.log(() -> "writer tryGenerate " + delegate.handledType().getName() + " threw:");
                t.printStackTrace();
            }
            CodegenFallbacks.generationFailure(delegate.handledType(), t);
            return null;
        }
        // Outside the try: tracing must not turn into a demotion of a codec
        // that generated fine.
        CodegenDebug.log(() -> "writer tryGenerate " + delegate.handledType().getName()
                + " -> " + (codec == null ? "null (gated)" : codec.getClass().getName()));
        return codec;
    }

    private static ValueSerializer<Object> generate(BeanSerializerBase delegate,
            BeanPropertyWriter[] beanProps, BeanPropertyWriter[] filtered, boolean hasTypeId,
            boolean asArray, SerializationContext ctxt)
            throws ReflectiveOperationException {
        // Stock contextualizes an array-shaped bean to its as-array
        // serializer, which writes with its own loop and calls back into the
        // placeholder only on stock paths (unwrapping, copies), never into a
        // writer codec.
        if (asArray) {
            CodegenDebug.log("writer gate: array shape");
            return null;
        }
        if (delegate.usesObjectId() || delegate.getFilterId() != null) {
            CodegenDebug.log("writer gate: object id or filter id");
            return null;
        }
        Class<?> beanClass = delegate.handledType();
        List<GenWProp> props = new ArrayList<>();
        for (Iterator<PropertyWriter> it = delegate.properties(); it.hasNext(); ) {
            PropertyWriter writer = it.next();
            props.add(classify(writer));
        }
        if (props.isEmpty()) {
            CodegenDebug.log("writer gate: no props");
            return null;
        }
        boolean includeByDefault = ctxt.isEnabled(MapperFeature.DEFAULT_VIEW_INCLUSION);
        // A @JsonTypeId property replaces the resolver-computed type id, which
        // the generated serializeWithType does not model; such beans keep the
        // base class's whole-call forwarding for typed writes.
        boolean nativePolyWrites = !hasTypeId;
        // null when the writer would be too large to JIT-compile.
        return WriterCodegen.generate(beanClass, props, delegate,
                viewStrategy(beanProps, filtered, props, includeByDefault), includeByDefault,
                nativePolyWrites);
    }

    // Under an active view stock writes the resolved filtered-writer array
    // (_filteredProps, or every property when there is none). The generated
    // code derives visibility from each property's views instead, which is
    // only the same as long as the array is the one processViews builds:
    // with no array, views cannot affect the output at all (NONE); an array
    // anything else built (a filtered writer replaced, nulled or added by
    // updateBuilder, a processViews or constructFilteredBeanWriter override)
    // DELEGATEs the view-active call to stock. Otherwise, as processViews:
    // no property declares a view, so with default inclusion off an active
    // view hides every property and the rare view-active call DELEGATEs to
    // stock's write-nothing path, keeping the no-view hot path free of
    // per-property tests. Properties with views take MASK (fast path under
    // views) up to 64 properties, DELEGATE beyond. Visibility is read per
    // property from BeanPropertyWriter.getViews.
    private static ViewStrategy viewStrategy(BeanPropertyWriter[] beanProps,
            BeanPropertyWriter[] filtered, List<GenWProp> props, boolean includeByDefault) {
        if (filtered == null) {
            return ViewStrategy.NONE;
        }
        if (!isStockViewArray(beanProps, filtered, includeByDefault)) {
            CodegenDebug.log("writer views: non-stock filtered writers, delegating");
            return ViewStrategy.DELEGATE;
        }
        boolean viewsFound = false;
        for (GenWProp p : props) {
            if (p.stock() instanceof BeanPropertyWriter bpw
                    && bpw.getViews() != null && bpw.getViews().length > 0) {
                viewsFound = true;
                break;
            }
        }
        if (!viewsFound) {
            return includeByDefault ? ViewStrategy.NONE : ViewStrategy.DELEGATE;
        }
        return (props.size() > 64) ? ViewStrategy.DELEGATE : ViewStrategy.MASK;
    }

    // Whether filtered is what BeanSerializerFactory.processViews builds
    // from props: an unannotated property as itself (default inclusion on)
    // or left out (off), one with views as a stock FilteredBeanPropertyWriter
    // with the same name and views. Which writer a stock filtered writer
    // wraps is not visible (its delegate field is in a private class of a
    // package that databind does not open), so one built by
    // constructViewBased around a different writer for the same property
    // (say, a wrapper that changes the value) is taken as stock: under an
    // active view the generated writer then writes props[i], not the wrapper.
    private static boolean isStockViewArray(BeanPropertyWriter[] props,
            BeanPropertyWriter[] filtered, boolean includeByDefault) {
        if (filtered.length != props.length) {
            return false;
        }
        for (int i = 0; i < props.length; i++) {
            BeanPropertyWriter prop = props[i];
            BeanPropertyWriter f = filtered[i];
            Class<?>[] views = prop.getViews();
            if (views == null || views.length == 0) {
                if (f != (includeByDefault ? prop : null)) {
                    return false;
                }
            } else if (f == null
                    || f.getClass().getEnclosingClass() != FilteredBeanPropertyWriter.class
                    || !f.getName().equals(prop.getName())
                    || !Arrays.equals(f.getViews(), views)) {
                return false;
            }
        }
        return true;
    }

    // A generated arm writes the value directly, so every per-property
    // feature of BeanPropertyWriter.serializeAsProperty that it skips must be
    // absent: a value type serializer (polymorphic values, default typing),
    // an applied view, suppression of nulls or values, and, for values that
    // can be null, a null serializer other than the default one (the arm
    // writes JSON null itself).
    private static GenWProp classify(PropertyWriter writer) {
        if (writer.getClass() != BeanPropertyWriter.class) {
            return stock(writer);
        }
        BeanPropertyWriter bpw = (BeanPropertyWriter) writer;
        WriterProbe.State state = WriterProbe.stateOf(bpw);
        if (bpw.willSuppressNulls() || state.suppressableValue() != null
                || bpw.getTypeSerializer() != null || state.applyView() != null) {
            return stock(writer);
        }
        Class<?> raw;
        MethodHandle handle;
        try {
            if (bpw.getMember() instanceof AnnotatedMethod am
                    && am.getAnnotated() != null
                    && am.getAnnotated().getParameterCount() == 0) {
                Method getter = am.getAnnotated();
                raw = getter.getReturnType();
                handle = MemberHandles.getter(getter);
            } else if (bpw.getMember() instanceof AnnotatedField af
                    && af.getAnnotated() != null
                    && !Modifier.isStatic(af.getAnnotated().getModifiers())) {
                Field field = af.getAnnotated();
                raw = field.getType();
                handle = MemberHandles.fieldGetter(field);
            } else {
                return stock(writer);
            }
        } catch (IllegalAccessException | RuntimeException e) {
            return stock(writer);
        }
        if (!raw.isPrimitive() && (state.nullSerializer() == null
                || state.nullSerializer().getClass() != NullSerializer.class)) {
            return stock(writer);
        }
        ValueSerializer<Object> valueSer = bpw.getSerializer();
        if (valueSer instanceof GeneratedWriterBase child && !raw.isPrimitive()) {
            return new GenWProp(WKind.CHILD, writer, bpw.getSerializedName(), child, handle);
        }
        WKind kind = scalarKind(raw);
        if (kind == null || valueSer == null
                || !STOCK_SCALAR_SERS.contains(valueSer.getClass())) {
            return stock(writer);
        }
        return new GenWProp(kind, writer, bpw.getSerializedName(), null, handle);
    }

    // BeanPropertyWriter keeps these in protected fields with no accessor;
    // the copy constructor carries them into this probe, whose own inherited
    // fields are readable.
    private static final class WriterProbe extends BeanPropertyWriter {
        record State(Object suppressableValue, ValueSerializer<Object> nullSerializer,
                Class<?> applyView) {}

        private WriterProbe(BeanPropertyWriter base) {
            super(base);
        }

        static State stateOf(BeanPropertyWriter base) {
            WriterProbe p = new WriterProbe(base);
            return new State(p._suppressableValue, p._nullSerializer, p._applyView);
        }
    }

    private static GenWProp stock(PropertyWriter writer) {
        CodegenDebug.log(() -> "writer prop stock: " + writer.getName());
        return new GenWProp(WKind.STOCK, writer, null, null, null);
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
