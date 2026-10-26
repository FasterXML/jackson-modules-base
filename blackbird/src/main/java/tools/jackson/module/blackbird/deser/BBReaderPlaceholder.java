package tools.jackson.module.blackbird.deser;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializer;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.util.NameTransformer;
import tools.jackson.module.blackbird.codegen.CodegenDebug;

/**
 * Takes the place of a stock {@link BeanDeserializer} (POJOs and records)
 * from the modifier on: it is a copy of that deserializer, so other
 * modifiers and databind's own checks see a {@code BeanDeserializer}, and
 * it behaves as stock until resolution. Resolving it resolves the stock
 * state and generates the codec, which needs the resolved properties and
 * the context's TokenStreamFactory. Contextualization then hands out the
 * codec, so steady-state call sites reference it with no extra hop. When
 * no codec is generated, contextualization hands out an exact-class copy of
 * the resolved stock state instead, so a demoted bean costs nothing over
 * stock.
 */
final class BBReaderPlaceholder extends BeanDeserializer
{
    // Whether the bean or any property declares @JsonView explicitly, read
    // from the property definitions at modify time (the resolved properties
    // cannot distinguish a declared view from the empty view set that
    // disabled DEFAULT_VIEW_INCLUSION forces onto unannotated properties).
    private final boolean _declaresViews;

    private volatile ValueDeserializer<Object> _codec;

    // Set once resolution generated no codec: a plain BeanDeserializer with
    // this placeholder's resolved state
    private volatile BeanDeserializerBase _stock;

    BBReaderPlaceholder(BeanDeserializer src, boolean declaresViews) {
        super(src);
        _declaresViews = declaresViews;
    }

    @Override
    public void resolve(DeserializationContext ctxt) {
        CodegenDebug.log(() -> "reader resolve " + handledType().getName());
        super.resolve(ctxt);
        ValueDeserializer<Object> codec = BBReaderFactory.tryGenerate(new BBReaderFactory.Source(this, _anySetter,
                _injectables != null, _beanProperties, _propertyBasedCreator,
                _nonStandardCreation,
                (_delegateDeserializer != null) || (_arrayDelegateDeserializer != null),
                _externalTypeIdHandler != null, _unwrappedPropertyHandler != null, null),
                ctxt, _declaresViews);
        _codec = codec;
        if (codec == null) {
            _stock = withBeanProperties(_beanProperties);
        }
    }

    // Contextualization is the stock one; when it leaves this deserializer
    // as it is, the codec takes its place.
    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        BeanDeserializerBase stock = _stock;
        if (stock != null) {
            return stock.createContextual(ctxt, property);
        }
        ValueDeserializer<?> contextual = super.createContextual(ctxt, property);
        ValueDeserializer<Object> codec = _codec;
        return (contextual == this && codec != null) ? codec : contextual;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        ValueDeserializer<Object> codec = _codec;
        return (codec != null) ? codec.deserialize(p, ctxt) : super.deserialize(p, ctxt);
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
}
