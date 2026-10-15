package tools.jackson.module.blackbird.internal;

import tools.jackson.core.JsonParser;
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

    protected final RuntimeException _propertyException(Throwable t, Object builder,
            SettableBeanProperty prop, DeserializationContext ctxt) {
        throw wrapAndThrow(t, builder, prop.getName(), ctxt);
    }

    protected final RuntimeException _storeFailure(Throwable t, Object builder,
            Object childValue, SettableBeanProperty prop, JsonParser p,
            DeserializationContext ctxt) {
        throw wrapAndThrow(ReaderSupport.storeException(t, childValue, prop, p), builder,
                prop.getName(), ctxt);
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

    protected final Object _unexpectedToken(JsonParser p, DeserializationContext ctxt,
            Object builder) {
        return _handleUnexpectedWithin(p, ctxt, builder);
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
