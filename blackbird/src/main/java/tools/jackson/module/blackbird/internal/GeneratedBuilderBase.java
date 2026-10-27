package tools.jackson.module.blackbird.internal;

import tools.jackson.core.JsonParser;
import tools.jackson.core.TokenStreamContext;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanPropertyMap;
import tools.jackson.databind.deser.bean.BuilderBasedDeserializer;

/**
 * Superclass for generated builder codecs: a copy of the resolved stock
 * {@link BuilderBasedDeserializer} whose {@code deserialize} method the
 * generated class overrides; the counterpart of {@link GeneratedReaderBase},
 * whose comments apply. Not API.
 */
public abstract class GeneratedBuilderBase extends BuilderBasedDeserializer
{
    private final String _layout;

    private final ViewMasks _viewMasks = new ViewMasks(this::_computeViewMask);

    protected GeneratedBuilderBase(BuilderBasedDeserializer src, BeanPropertyMap props,
            String layout) {
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

    @Override
    public void resolve(DeserializationContext ctxt) { }

    protected final Object _stockDeserialize(JsonParser p, DeserializationContext ctxt) {
        return super.deserialize(p, ctxt);
    }

    // The build method threw: what BuilderBasedDeserializer.finishBuild does.
    protected final Object _buildFailure(DeserializationContext ctxt, Throwable t) {
        return wrapInstantiationProblem(ctxt, t);
    }

    // The generated code calls the build method itself only on an instance
    // of the builder class; anything else a with-method or a problem handler
    // produced (the built target, null, another type) goes to the stock
    // finishBuild, which returns the target as-is and reports the rest.
    protected final boolean _isBuilder(Object builder) {
        return handledType().isInstance(builder);
    }

    protected final Object _finishBuild(DeserializationContext ctxt, Object builder) {
        return finishBuild(ctxt, builder);
    }

    // The property name that stock reports for a failing property: its
    // general loop (deserializeFromObject) reports the name as written in
    // the input (an alias, or another case), its vanilla loop and view loop
    // the property's own name. Called once at entry, on the entry token:
    // null selects the property's own name, otherwise the object's parse
    // context, whose current name at failure time is the input name.
    protected final TokenStreamContext _nameContext(JsonParser p, DeserializationContext ctxt) {
        if (_vanillaProcessing && p.isExpectedStartObjectToken()) {
            return null;
        }
        if (_needViewProcesing && ctxt.getActiveView() != null) {
            return null;
        }
        return p.streamReadContext();
    }

    private static String _failedName(SettableBeanProperty prop, TokenStreamContext nameContext) {
        String name = (nameContext == null) ? null : nameContext.currentName();
        return (name == null) ? prop.getName() : name;
    }

    protected final RuntimeException _propertyException(Throwable t, Object builder,
            SettableBeanProperty prop, TokenStreamContext nameContext,
            DeserializationContext ctxt) {
        throw wrapAndThrow(t, builder, _failedName(prop, nameContext), ctxt);
    }

    protected final RuntimeException _storeFailure(Throwable t, Object builder,
            Object childValue, SettableBeanProperty prop, JsonParser p,
            TokenStreamContext nameContext, DeserializationContext ctxt) {
        throw wrapAndThrow(ReaderSupport.storeException(t, childValue, prop, p), builder,
                _failedName(prop, nameContext), ctxt);
    }

    protected final void _injectValues(DeserializationContext ctxt, Object builder) {
        injectValues(ctxt, builder);
    }

    protected final void _handleUnknown(JsonParser p, DeserializationContext ctxt,
            Object builder) {
        String name = p.currentName();
        p.nextToken();
        handleUnknownVanilla(p, ctxt, builder, name);
    }

    // As in the stock vanilla loop, whatever the unexpected-token handling
    // returns is then built.
    protected final Object _unexpectedToken(JsonParser p, DeserializationContext ctxt,
            Object builder) {
        return finishBuild(ctxt, _handleUnexpectedWithin(p, ctxt, builder));
    }

    protected final long _viewMask(Class<?> view) {
        return _viewMasks.maskFor(view);
    }

    protected long _computeViewMask(Class<?> view) {
        throw new UnsupportedOperationException("codec has no view mask");
    }

    protected final void _hiddenView(JsonParser p, DeserializationContext ctxt,
            SettableBeanProperty prop) {
        handleUnexpectedView(p, ctxt, prop, ctxt.getActiveView());
    }
}
