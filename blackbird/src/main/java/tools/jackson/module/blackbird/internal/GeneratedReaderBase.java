package tools.jackson.module.blackbird.internal;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.ReadableObjectId;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.UnresolvedForwardReference;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.bean.BeanPropertyMap;
import tools.jackson.databind.deser.bean.PropertyValueBuffer;
import tools.jackson.databind.deser.std.StdValueInstantiator;
import tools.jackson.databind.util.IgnorePropertiesUtil;
import tools.jackson.databind.util.NameTransformer;
import tools.jackson.databind.util.TokenBuffer;

/**
 * Superclass for generated POJO and record codecs (builder codecs extend
 * {@link GeneratedBuilderBase}). A codec is a copy of the resolved stock
 * {@link BeanDeserializer} whose {@code deserialize} method the generated
 * class overrides with the per-bean loop: everything else, and every input
 * the loop does not model, is the stock implementation, and databind sees a
 * {@code BeanDeserializer} wherever it checks for one. The helpers below are
 * the cold paths of the generated loop; each one calls the stock code for
 * the same situation. Not API.
 */
public abstract class GeneratedReaderBase extends BeanDeserializer
{
    // One "name=KIND" entry per arm, for diagnostics and tests.
    private final String _layout;

    private final ViewMasks _viewMasks = new ViewMasks(this::_computeViewMask);

    protected GeneratedReaderBase(BeanDeserializer src, BeanPropertyMap props, String layout) {
        // Built from the resolved deserializer, with the bean property map
        // whose name matcher is initialized.
        super(src, props);
        _layout = layout;
        _propertiesContextualized = true;
    }

    /** The generated arms, as "name=KIND" entries in arm order. */
    public final String codecLayout() {
        return _layout;
    }

    @Override
    public String toString() {
        return "BBReader[" + handledType().getName() + ": " + _layout + "]";
    }

    // A copy of a resolved deserializer: nothing left to resolve.
    @Override
    public void resolve(DeserializationContext ctxt) { }

    // The generated deserialize hands inputs it does not model here.
    protected final Object _stockDeserialize(JsonParser p, DeserializationContext ctxt) {
        return super.deserialize(p, ctxt);
    }

    // BeanDeserializer unwraps only for its exact class (subclasses "MUST
    // override"); an exact-class copy of this state does what it would. The
    // field guards against recursive unwrapping, as in stock.
    @Override
    public ValueDeserializer<Object> unwrappingDeserializer(DeserializationContext ctxt,
            NameTransformer transformer) {
        if (_currentlyTransforming == transformer) {
            return this;
        }
        _currentlyTransforming = transformer;
        try {
            return withBeanProperties(_beanProperties).unwrappingDeserializer(ctxt, transformer);
        } finally {
            _currentlyTransforming = null;
        }
    }

    /*
    /**********************************************************************
    /* Failures
    /**********************************************************************
     */

    // The no-arg constructor handle threw: what StdValueInstantiator
    // .createUsingDefault does (the factory uses the handle only with exactly
    // that instantiator). Returns a problem handler's replacement value.
    protected final Object _instantiationFailure(DeserializationContext ctxt, Throwable t) {
        return ReaderSupport.creatorProblem((StdValueInstantiator) _valueInstantiator, ctxt,
                null, t);
    }

    // Called from the generated exception handler that covers every property
    // arm: the stock loop's wrapAndThrow, so property errors carry the
    // reference path. Always throws; the return type only satisfies the
    // generated athrow.
    protected final RuntimeException _propertyException(Throwable t, Object beanOrNull,
            SettableBeanProperty prop, DeserializationContext ctxt) {
        Object ref = (beanOrNull == null) ? handledType() : beanOrNull;
        throw wrapAndThrow(t, ref, prop.getName(), ctxt);
    }

    // A generated store (setter call or field write) threw: what the stock
    // property throws for it, through the stock loop's wrapAndThrow.
    protected final RuntimeException _storeFailure(Throwable t, Object bean,
            Object childValue, SettableBeanProperty prop, JsonParser p,
            DeserializationContext ctxt) {
        throw wrapAndThrow(ReaderSupport.storeException(t, childValue, prop, p), bean,
                prop.getName(), ctxt);
    }

    /*
    /**********************************************************************
    /* POJO loop helpers
    /**********************************************************************
     */

    // Right after construction, before the property loop: the placement
    // stock deserializeFromObject uses, so values from the document override
    // injected values exactly like stock.
    protected final void _injectValues(DeserializationContext ctxt, Object bean) {
        injectValues(ctxt, bean);
    }

    // A name the matcher does not know: stock handleUnknownVanilla (ignored
    // names, then the any-setter, then unknown-property handling).
    protected final void _handleUnknown(JsonParser p, DeserializationContext ctxt,
            Object bean) {
        String name = p.currentName();
        p.nextToken();
        handleUnknownVanilla(p, ctxt, bean, name);
    }

    // Neither a name nor the end of the object where a name was expected.
    protected final Object _unexpectedToken(JsonParser p, DeserializationContext ctxt,
            Object bean) {
        return _handleUnexpectedWithin(p, ctxt, bean);
    }

    /*
    /**********************************************************************
    /* Record loop helpers
    /**********************************************************************
     */

    // A name no component takes: the order of the stock property-based
    // loop. Ignored names go through ignored-property handling at once;
    // other names are skipped when stock would skip them, and otherwise
    // buffered, to be reported against the record once it exists (see
    // _recordReplayUnknown). Returns the buffer, created on first use.
    protected final Object _recordUnknown(JsonParser p, DeserializationContext ctxt,
            Object buffered) {
        String name = p.currentName();
        p.nextToken();
        if (IgnorePropertiesUtil.shouldIgnore(name, _ignorableProps, _includableProps)) {
            handleIgnoredProperty(p, ctxt, handledType(), name);
            return buffered;
        }
        if (_shouldSkipUnknowns(ctxt)) {
            p.skipChildren();
            return buffered;
        }
        TokenBuffer tb = (buffered == null) ? ctxt.bufferForInputBuffering(p)
                : (TokenBuffer) buffered;
        tb.writeName(name);
        tb.copyCurrentStructure(p);
        return tb;
    }

    protected final Object _recordReplayUnknown(DeserializationContext ctxt, Object bean,
            Object buffered) {
        return handleUnknownProperties(ctxt, bean, (TokenBuffer) buffered);
    }

    // A component value that is a forward Object Id reference: what the
    // stock property-based loop does ([databind#3030]). The component gets
    // null, and the reference resolves against the record once it exists
    // (see _recordResolveForwardRefs). Returns the referrings, a list
    // created on first use.
    protected final Object _recordForwardRef(DeserializationContext ctxt,
            SettableBeanProperty prop, UnresolvedForwardReference ref, Object referrings) {
        RecordReferring referring = new RecordReferring(ctxt, ref, prop);
        ref.getRoid().appendReferring(referring);
        @SuppressWarnings("unchecked")
        List<RecordReferring> list = (referrings == null) ? new ArrayList<>()
                : (List<RecordReferring>) referrings;
        list.add(referring);
        return list;
    }

    @SuppressWarnings("unchecked")
    protected final void _recordResolveForwardRefs(Object bean, Object referrings) {
        if (bean != null) {
            for (RecordReferring referring : (List<RecordReferring>) referrings) {
                referring.setBean(bean);
            }
        }
    }

    // The stock BeanDeserializer.BeanReferring, which is not accessible.
    private static final class RecordReferring extends ReadableObjectId.Referring {
        private final DeserializationContext _context;
        private final SettableBeanProperty _prop;
        private Object _bean;
        private Object _resolvedValue;

        RecordReferring(DeserializationContext ctxt, UnresolvedForwardReference ref,
                SettableBeanProperty prop) {
            super(ref, prop.getType());
            _context = ctxt;
            _prop = prop;
        }

        void setBean(Object bean) {
            _bean = bean;
            if (_resolvedValue != null) {
                _prop.set(_context, _bean, _resolvedValue);
                _resolvedValue = null;
            }
        }

        @Override
        public void handleResolvedForwardReference(DeserializationContext ctxt,
                Object id, Object value) {
            if (_bean == null) {
                _resolvedValue = value;
            } else {
                _prop.set(ctxt, _bean, value);
            }
        }
    }

    // Record codecs call the canonical constructor directly only when the
    // document supplied every component, every primitive component received
    // its exact wrapper (not null or another type from a custom
    // deserializer), and FAIL_ON_NULL_CREATOR_PROPERTIES is off. Everything
    // else ends here: values holds the components (boxed, or the raw stock
    // value, in creator-index order) and seen marks the ones the document
    // supplied. Without a constructor failure, the stock creator completes the
    // arguments (absent values, required and FAIL_ON_MISSING /
    // FAIL_ON_NULL_CREATOR_PROPERTIES checks) and constructs; a failure from
    // the fast path goes where StdValueInstantiator sends it. Either way a
    // failure ends in wrapInstantiationProblem, as in the stock loop.
    protected final Object _recordBuild(JsonParser p, DeserializationContext ctxt, long seen,
            Object[] values, Throwable ctorFailure) {
        if (ctorFailure != null) {
            try {
                return ReaderSupport.creatorProblem((StdValueInstantiator) _valueInstantiator,
                        ctxt, values, ctorFailure);
            } catch (Exception e) {
                return wrapInstantiationProblem(ctxt, e);
            }
        }
        SettableBeanProperty[] props = _propertyBasedCreator.allPropertiesInOrder();
        PropertyValueBuffer buffer = _propertyBasedCreator.startBuilding(p, ctxt, null, false);
        for (int i = 0; i < props.length; i++) {
            if ((seen & (1L << i)) != 0) {
                buffer.assignParameter(props[i], values[i]);
            }
        }
        try {
            return _propertyBasedCreator.build(ctxt, buffer);
        } catch (Exception e) {
            return wrapInstantiationProblem(ctxt, e);
        }
    }

    /*
    /**********************************************************************
    /* Views and class data
    /**********************************************************************
     */

    // Called by generated code once per view-active call.
    protected final long _viewMask(Class<?> view) {
        return _viewMasks.maskFor(view);
    }

    // Overridden by generated codecs that filter per view: bit i reports
    // whether property arm i is visible in the given view. Codecs that
    // delegate view-active calls whole (more than 64 properties) never call
    // the mask machinery.
    protected long _computeViewMask(Class<?> view) {
        throw new UnsupportedOperationException("codec has no view mask");
    }

    // An arm hidden in the active view, the parser on the value token.
    protected final void _hiddenView(JsonParser p, DeserializationContext ctxt,
            SettableBeanProperty prop) {
        handleUnexpectedView(p, ctxt, prop, ctxt.getActiveView());
    }
}
