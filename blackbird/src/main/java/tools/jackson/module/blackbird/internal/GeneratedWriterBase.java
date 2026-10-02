package tools.jackson.module.blackbird.internal;

import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.util.Map;

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.jsonFormatVisitors.JsonFormatVisitorWrapper;
import tools.jackson.databind.jsontype.TypeSerializer;
import tools.jackson.databind.ser.BeanPropertyWriter;
import tools.jackson.databind.ser.PropertyWriter;
import tools.jackson.databind.ser.bean.BeanSerializerBase;
import tools.jackson.databind.util.NameTransformer;

/**
 * Superclass for generated serializers: everything except the plain-object
 * serialize path forwards to the stock serializer the codec replaced.
 */
public abstract class GeneratedWriterBase extends ValueSerializer<Object>
{
    protected final BeanSerializerBase _fallback;

    protected GeneratedWriterBase(BeanSerializerBase fallback) {
        _fallback = fallback;
    }

    // Generated writers override this with a native WritableTypeId flow;
    // this forwarding remains for beans with a @JsonTypeId property, whose
    // value feeds the type id through the stock path.
    @Override
    public void serializeWithType(Object value, JsonGenerator g, SerializationContext ctxt,
            TypeSerializer typeSer) {
        _fallback.serializeWithType(value, g, ctxt, typeSer);
    }

    @Override
    public boolean isEmpty(SerializationContext ctxt, Object value) {
        return _fallback.isEmpty(ctxt, value);
    }

    @Override
    public boolean usesObjectId() {
        return _fallback.usesObjectId();
    }

    @Override
    public Class<?> handledType() {
        return _fallback.handledType();
    }

    // Unwrapping must produce the stock unwrapping variant: the generated
    // writer emits one JSON object, which is not the shape an unwrapped value
    // has.
    @Override
    public ValueSerializer<Object> unwrappingSerializer(NameTransformer unwrapper) {
        return _fallback.unwrappingSerializer(unwrapper);
    }

    @Override
    public void acceptJsonFormatVisitor(JsonFormatVisitorWrapper visitor, JavaType type) {
        _fallback.acceptJsonFormatVisitor(visitor, type);
    }

    /*
    /**********************************************************************
    /* Support for generated code
    /**********************************************************************
     */

    // Constant bootstrap for generated code: resolves one named entry of the
    // hidden class's class data, a Map built at generation time. The JDK's
    // classDataAt bootstrap requires the condy name to be "_", so named
    // entries need this owner, which generated classes can always resolve
    // (the package is exported exactly for their supertype needs). A condy
    // links once and then constant-folds the same as classDataAt; the names
    // exist for dump readability and to remove positional-index bookkeeping
    // from the generators.
    public static Object classDataEntry(MethodHandles.Lookup lookup, String name, Class<?> type)
            throws IllegalAccessException {
        Map<?, ?> data = MethodHandles.classData(lookup, ConstantDescs.DEFAULT_NAME, Map.class);
        Object value = data.get(name);
        if (value == null) {
            throw new IllegalStateException("no class data entry named " + name);
        }
        return type.cast(value);
    }

    /*
    /**********************************************************************
    /* View support: per-view visibility masks
    /**********************************************************************
     */

    private static final Class<?>[] NO_VIEWS = new Class<?>[0];
    private static final long[] NO_MASKS = new long[0];

    // Copy-on-write cache of view -> property-visibility bitmask; same shape
    // and rationale as GeneratedReaderBase. Instance state only, so cached view
    // classes unload with the writer and its mapper.
    private volatile Class<?>[] _maskViews = NO_VIEWS;
    private volatile long[] _masks = NO_MASKS;

    protected final long _viewMask(Class<?> view) {
        Class<?>[] views = _maskViews;
        for (int i = 0; i < views.length; i++) {
            if (views[i] == view) {
                return _masks[i];
            }
        }
        return _addViewMask(view);
    }

    private synchronized long _addViewMask(Class<?> view) {
        Class<?>[] views = _maskViews;
        for (int i = 0; i < views.length; i++) {
            if (views[i] == view) {
                return _masks[i];
            }
        }
        long mask = _computeViewMask(view);
        Class<?>[] newViews = new Class<?>[views.length + 1];
        long[] newMasks = new long[views.length + 1];
        System.arraycopy(views, 0, newViews, 0, views.length);
        System.arraycopy(_masks, 0, newMasks, 0, views.length);
        newViews[views.length] = view;
        newMasks[views.length] = mask;
        _masks = newMasks;
        _maskViews = newViews;
        return mask;
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
