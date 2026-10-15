package tools.jackson.module.blackbird.deser;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.bean.BuilderBasedDeserializer;
import tools.jackson.module.blackbird.codegen.CodegenDebug;

/**
 * The builder-based counterpart of {@link BBReaderPlaceholder}: a copy of
 * the stock {@link BuilderBasedDeserializer} that generates the builder
 * codec when resolved.
 */
final class BBBuilderPlaceholder extends BuilderBasedDeserializer
{
    private final boolean _declaresViews;

    private volatile ValueDeserializer<Object> _codec;

    BBBuilderPlaceholder(BuilderBasedDeserializer src, boolean declaresViews) {
        super(src);
        _declaresViews = declaresViews;
    }

    @Override
    public void resolve(DeserializationContext ctxt) {
        CodegenDebug.log(() -> "reader resolve " + handledType().getName());
        super.resolve(ctxt);
        // A builder without a build method (the builder is the value) keeps
        // the stock path.
        if (_buildMethod == null) {
            return;
        }
        _codec = BBReaderFactory.tryGenerate(new BBReaderFactory.Source(this, _anySetter,
                _injectables != null, _beanProperties, _propertyBasedCreator,
                _nonStandardCreation,
                (_delegateDeserializer != null) || (_arrayDelegateDeserializer != null),
                _externalTypeIdHandler != null, _unwrappedPropertyHandler != null,
                _buildMethod),
                ctxt, _declaresViews);
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        ValueDeserializer<?> contextual = super.createContextual(ctxt, property);
        ValueDeserializer<Object> codec = _codec;
        return (contextual == this && codec != null) ? codec : contextual;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        ValueDeserializer<Object> codec = _codec;
        return (codec != null) ? codec.deserialize(p, ctxt) : super.deserialize(p, ctxt);
    }
}
