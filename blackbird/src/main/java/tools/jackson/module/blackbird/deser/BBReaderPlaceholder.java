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

    // Set once resolution generated no codec
    private volatile boolean _demoted;

    // A plain BeanDeserializer with this placeholder's resolved and
    // contextualized state, made on first demoted contextualization
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
        _demoted = (codec == null);
    }

    // Contextualization is the stock one; when it leaves this deserializer
    // as it is, the codec (or, when demoted, the stock copy) takes its place.
    // It runs on this resolved instance, never on the copy: copies do not
    // carry _propertiesContextualized, so contextualizing one would redo
    // the property contextualization that resolution already did. The copy
    // caches the name matcher when constructed, and contextualization
    // initializes the matcher, so the copy is made after that (a racing
    // duplicate copy is harmless).
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
        _propNameMatcher = _beanProperties.getNameMatcher();
        _propsByIndex = _beanProperties.getNameMatcherProperties();
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        ValueDeserializer<Object> codec = _codec;
        return (codec != null) ? codec.deserialize(p, ctxt) : super.deserialize(p, ctxt);
    }

    // BeanDeserializer unwraps only for its exact class (subclasses "MUST
    // override"); an exact-class copy of this state does what it would. The
    // field guards against recursive unwrapping, as in stock. The copy is
    // fresh on purpose, not the shared demoted _stock: stock unwrapping uses
    // the instance's own _currentlyTransforming as its guard, which a shared
    // instance would race on. Unwrapping runs once per unwrapped property,
    // while the outer bean resolves.
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
