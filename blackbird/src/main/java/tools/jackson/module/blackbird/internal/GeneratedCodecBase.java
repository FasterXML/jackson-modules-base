package tools.jackson.module.blackbird.internal;

import java.lang.constant.ConstantDescs;
import java.lang.invoke.MethodHandles;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.util.AccessPattern;
import tools.jackson.databind.util.ClassUtil;
import tools.jackson.databind.util.NameTransformer;

/**
 * Superclass for generated codecs: everything except the plain-object
 * deserialize loop forwards to the stock deserializer the codec replaced, so
 * typed (polymorphic) entry, null handling, and update-value reads keep stock
 * semantics.
 */
public abstract class GeneratedCodecBase extends ValueDeserializer<Object>
{
    protected final BeanDeserializerBase _fallback;

    protected GeneratedCodecBase(BeanDeserializerBase fallback) {
        _fallback = fallback;
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer) {
        return _fallback.deserializeWithType(p, ctxt, typeDeserializer);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt, Object intoValue) {
        return _fallback.deserialize(p, ctxt, intoValue);
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        return _fallback.getNullValue(ctxt);
    }

    @Override
    public Object getEmptyValue(DeserializationContext ctxt) {
        return _fallback.getEmptyValue(ctxt);
    }

    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return _fallback.getAbsentValue(ctxt);
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return _fallback.getNullAccessPattern();
    }

    @Override
    public AccessPattern getEmptyAccessPattern() {
        return _fallback.getEmptyAccessPattern();
    }

    @Override
    public Collection<Object> getKnownPropertyNames() {
        return _fallback.getKnownPropertyNames();
    }

    @Override
    public SettableBeanProperty findBackReference(String refName) {
        return _fallback.findBackReference(refName);
    }

    @Override
    public Boolean supportsUpdate(DeserializationConfig config) {
        return _fallback.supportsUpdate(config);
    }

    @Override
    public Class<?> handledType() {
        return _fallback.handledType();
    }

    @Override
    public LogicalType logicalType() {
        return _fallback.logicalType();
    }

    @Override
    public boolean isCachable() {
        return _fallback.isCachable();
    }

    // Unwrapping must produce the stock unwrapping variant: the generated loop
    // reads one JSON object, which is not the shape an unwrapped value has.
    @Override
    public ValueDeserializer<Object> unwrappingDeserializer(DeserializationContext ctxt,
            NameTransformer unwrapper) {
        return _fallback.unwrappingDeserializer(ctxt, unwrapper);
    }

    // Called by generated code when a property name was expected but the
    // stream holds something else; reports through the context the way the
    // stock deserializer does.
    protected final Object _unexpectedToken(JsonParser p, DeserializationContext ctxt) {
        return ctxt.handleUnexpectedToken(_fallback.handledType(), p);
    }

    // Called from the generated exception handler that covers every property
    // arm: mirrors the stock loop's wrapAndThrow so property errors carry the
    // reference path. Always throws; the return type only satisfies the
    // generated athrow.
    protected final RuntimeException _propertyException(Throwable t, Object beanOrNull,
            SettableBeanProperty prop, DeserializationContext ctxt) {
        Object ref = (beanOrNull == null) ? _fallback.handledType() : beanOrNull;
        throw _fallback.wrapAndThrow(t, ref, prop.getName(), ctxt);
    }

    // Called by generated code for a name the matcher does not know: runs the
    // configured problem handlers and honors FAIL_ON_UNKNOWN_PROPERTIES, like
    // the stock loop. Beans with ignored or included property sets never
    // generate a codec, so plain unknown handling is the whole contract here.
    // beanOrBuilder is null in record mode, where no instance exists yet.
    protected final void _handleUnknown(JsonParser p, DeserializationContext ctxt,
            Object beanOrBuilder) {
        String name = p.currentName();
        p.nextToken();
        ctxt.handleUnknownProperty(p, _fallback,
                (beanOrBuilder == null) ? _fallback.handledType() : beanOrBuilder, name);
    }

    // Called by generated record codecs when the document ended with unseen
    // components; mirrors PropertyValueBuffer's required and
    // FAIL_ON_MISSING_CREATOR_PROPERTIES reporting.
    protected final void _checkRecordSeen(DeserializationContext ctxt, long seen, int count) {
        for (int i = 0; i < count; i++) {
            if ((seen & (1L << i)) != 0) {
                continue;
            }
            SettableBeanProperty prop = _creatorPropByIndex(i);
            if (prop == null) {
                continue;
            }
            if (prop.isRequired()) {
                ctxt.reportInputMismatch(prop,
                        "Missing required creator property '%s' (index %d)",
                        prop.getName(), i);
            }
            if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)) {
                ctxt.reportInputMismatch(prop,
                        "Missing creator property '%s' (index %d); `DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES` enabled",
                        prop.getName(), i);
            }
        }
    }

    private SettableBeanProperty _creatorPropByIndex(int index) {
        for (Iterator<SettableBeanProperty> it = _fallback.properties(); it.hasNext(); ) {
            SettableBeanProperty prop = it.next();
            if (prop.getCreatorIndex() == index) {
                return prop;
            }
        }
        return null;
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

    // Copy-on-write cache of view -> arm-visibility bitmask. View sets are
    // small and stable per application, so lookups are a reference scan.
    // Instance state only: the cached view classes unload with the codec and
    // its mapper.
    private volatile Class<?>[] _maskViews = NO_VIEWS;
    private volatile long[] _masks = NO_MASKS;

    // Called by generated code once per view-active call.
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

    // Overridden by generated codecs that filter per view: bit i reports
    // whether property arm i is visible in the given view. Codecs that
    // delegate view-active calls whole (more than 64 properties) never call
    // the mask machinery.
    protected long _computeViewMask(Class<?> view) {
        throw new UnsupportedOperationException("codec has no view mask");
    }

    // Called by generated code for an arm hidden in the active view, with the
    // parser advanced to the value token; mirrors the stock loop's
    // handleUnexpectedView (message included), then consumes the value.
    protected final void _hiddenView(JsonParser p, DeserializationContext ctxt,
            SettableBeanProperty prop) {
        if (ctxt.isEnabled(DeserializationFeature.FAIL_ON_UNEXPECTED_VIEW_PROPERTIES)) {
            ctxt.reportInputMismatch(_fallback.handledType(),
                    "Input mismatch while deserializing %s. Property '%s' is not part of current active view '%s'"
                            + " (disable 'DeserializationFeature.FAIL_ON_UNEXPECTED_VIEW_PROPERTIES' to allow)",
                    ClassUtil.nameOf(_fallback.handledType()), prop.getName(),
                    ctxt.getActiveView().getName());
        }
        p.skipChildren();
    }
}
