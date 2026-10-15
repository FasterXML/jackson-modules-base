package tools.jackson.module.blackbird.internal;

import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.util.Map;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.BeanSerializer;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;

/**
 * Superclass for generated serializers: a copy of the resolved stock bean
 * serializer (as a {@link BeanSerializer}) whose {@code serialize} method
 * (and, for most beans, {@code serializeWithType}) the generated class
 * overrides. Everything else, and every call the generated code does not
 * model, is the stock implementation, and databind sees a bean serializer
 * wherever it checks for one (the self-reference check of
 * BeanPropertyWriter among them). Not API.
 */
public abstract class GeneratedWriterBase extends BeanSerializer
{
    // One "name=KIND" entry per property, for diagnostics and tests.
    private final String _layout;

    private final ViewMasks _viewMasks = new ViewMasks(this::_computeViewMask);

    protected GeneratedWriterBase(BeanSerializerBase src, String layout) {
        super(src);
        _layout = layout;
    }

    /** The generated properties, as "name=KIND" entries in write order. */
    public final String codecLayout() {
        return _layout;
    }

    @Override
    public String toString() {
        return "BBWriter[" + handledType().getName() + ": " + _layout + "]";
    }

    // A copy of a resolved serializer: nothing left to resolve.
    @Override
    public void resolve(SerializationContext ctxt) { }

    /*
    /**********************************************************************
    /* Support for generated code
    /**********************************************************************
     */

    // The stock implementations, for calls the generated code hands back.
    protected final void _stockSerialize(Object bean, JsonGenerator g, SerializationContext ctxt) {
        super.serialize(bean, g, ctxt);
    }

    protected final void _stockSerializeWithType(Object bean, JsonGenerator g,
            SerializationContext ctxt, TypeSerializer typeSer) {
        super.serializeWithType(bean, g, ctxt, typeSer);
    }

    // Called from the generated handler around the property section: what
    // stock _serializePropertiesNoView does with an exception from the
    // property at index (the generated properties are the stock ones, in
    // stock order). Always throws; the return type only satisfies the
    // generated athrow.
    protected final RuntimeException _propertyFailure(Throwable t, Object bean, int index,
            JsonGenerator g, SerializationContext ctxt) {
        String name = _props[index].getName();
        if (t instanceof StackOverflowError soe) {
            throw DatabindException.from(g, "Infinite recursion (StackOverflowError)", soe)
                .prependPath(bean, name);
        }
        wrapAndThrow(ctxt, t, bean, name);
        throw new IllegalStateException("wrapAndThrow returned", t);
    }

    // Constant bootstrap for generated code; see
    // GeneratedReaderBase.classDataEntry.
    public static Object classDataEntry(MethodHandles.Lookup lookup, String name, Class<?> type)
            throws IllegalAccessException {
        Map<?, ?> data = MethodHandles.classData(lookup, ConstantDescs.DEFAULT_NAME, Map.class);
        Object value = data.get(name);
        if (value == null) {
            throw new IllegalStateException("no class data entry named " + name);
        }
        return type.cast(value);
    }

    protected final long _viewMask(Class<?> view) {
        return _viewMasks.maskFor(view);
    }

    // Overridden by generated writers that filter per view: bit i reports
    // whether property i is visible in the given view.
    protected long _computeViewMask(Class<?> view) {
        throw new UnsupportedOperationException("writer has no view mask");
    }

    // Visibility of one property in a view, matching the rule the stock
    // factory uses to build the filtered writer array: no view annotations
    // means DEFAULT_VIEW_INCLUSION decides (captured at generation time),
    // otherwise any declared view assignable from the active view.
    protected static boolean _propVisible(PropertyWriter w, Class<?> view,
            boolean includeByDefault) {
        Class<?>[] views = (w instanceof BeanPropertyWriter bpw) ? bpw.getViews() : null;
        if (views == null || views.length == 0) {
            return includeByDefault;
        }
        for (Class<?> v : views) {
            if (v.isAssignableFrom(view)) {
                return true;
            }
        }
        return false;
    }
}
