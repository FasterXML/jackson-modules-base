package tools.jackson.module.blackbird.deser;

import com.fasterxml.jackson.annotation.JsonFormat;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.deser.bean.BuilderBasedDeserializer;
import tools.jackson.module.blackbird.codegen.CodegenDebug;

/**
 * The builder-based counterpart of {@link BBReaderPlaceholder}: a copy of
 * the stock {@link BuilderBasedDeserializer} that generates the builder
 * codec when resolved, and hands out an exact-class copy of the resolved
 * stock state when no codec is generated.
 */
final class BBBuilderPlaceholder extends BuilderBasedDeserializer
{
    private final boolean _declaresViews;

    private volatile ValueDeserializer<Object> _codec;

    // Set once resolution generated no codec
    private volatile boolean _demoted;

    // A plain BuilderBasedDeserializer with this placeholder's resolved and
    // contextualized state, made on first demoted contextualization
    private volatile BeanDeserializerBase _stock;

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
        ValueDeserializer<Object> codec = null;
        if (_buildMethod == null) {
            CodegenDebug.log("reader gate: builder without build method");
        } else {
            codec = BBReaderFactory.tryGenerate(new BBReaderFactory.Source(this, _anySetter,
                    _injectables != null, _vanillaProcessing, _beanProperties, _propertyBasedCreator,
                    _nonStandardCreation,
                    (_delegateDeserializer != null) || (_arrayDelegateDeserializer != null),
                    _externalTypeIdHandler != null, _unwrappedPropertyHandler != null,
                    _serializationShape == JsonFormat.Shape.ARRAY, _buildMethod),
                    ctxt, _declaresViews);
        }
        _codec = codec;
        _demoted = (codec == null);
    }

    // As in BBReaderPlaceholder: contextualize this resolved instance, then
    // hand out the codec or the stock copy (made after contextualization)
    // when that leaves it as it is.
    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        ValueDeserializer<?> contextual = super.createContextual(ctxt, property);
        if (contextual != this) {
            return contextual;
        }
        ValueDeserializer<Object> codec = _codec;
        if (codec != null) {
            return codec;
        }
        if (!_demoted) {
            return this;
        }
        BeanDeserializerBase stock = _stock;
        if (stock == null) {
            stock = withBeanProperties(_beanProperties);
            _stock = stock;
        }
        return stock;
    }

    // Stock createContextual initializes the name matcher of the property
    // map on every contextualization; generation already did, for this map
    // and this mapper's stream factory (BBReaderFactory), so with a codec
    // the map is not rebuilt: only this instance's cached view of it is set.
    @Override
    protected void initNameMatcher(DeserializationContext ctxt) {
        if (_codec == null) {
            super.initNameMatcher(ctxt);
            return;
        }
        _propertyNameMatcher = _beanProperties.getNameMatcher();
        _propertiesByIndex = _beanProperties.getNameMatcherProperties();
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        ValueDeserializer<Object> codec = _codec;
        return (codec != null) ? codec.deserialize(p, ctxt) : super.deserialize(p, ctxt);
    }
}
