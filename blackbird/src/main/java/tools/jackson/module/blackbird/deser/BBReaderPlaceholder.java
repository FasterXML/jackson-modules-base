package tools.jackson.module.blackbird.deser;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationConfig;
import tools.jackson.databind.PropertyName;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.SettableBeanProperty;
import tools.jackson.databind.deser.bean.BeanDeserializerBase;
import tools.jackson.databind.introspect.AnnotatedMethod;
import tools.jackson.databind.jsontype.TypeDeserializer;
import tools.jackson.databind.type.LogicalType;
import tools.jackson.databind.util.AccessPattern;
import tools.jackson.databind.util.NameTransformer;
import tools.jackson.module.blackbird.codegen.BeanReaderGenerator;
import tools.jackson.module.blackbird.codegen.CodegenDebug;

/**
 * Stands in for a stock bean deserializer until resolution: the codec needs
 * the resolved property list and the context's TokenStreamFactory, both of
 * which exist only at resolve time. After resolve, contextualization hands out
 * the generated codec directly, so steady-state call sites reference the codec
 * with no extra hop; anything that keeps the placeholder still works through
 * delegation.
 */
final class BBReaderPlaceholder extends ValueDeserializer<Object>
{
    private final BeanDeserializerBase _delegate;

    private final AnnotatedMethod _buildMethod;

    // Whether the bean or any property declares @JsonView explicitly, read
    // from the property definitions at modify time (the resolved properties
    // cannot distinguish a declared view from the empty view set that
    // disabled DEFAULT_VIEW_INCLUSION forces onto unannotated properties).
    private final boolean _declaresViews;
    private final BeanReaderGenerator.Ignorals _ignorals;
    private final Map<String, List<PropertyName>> _aliases;
    private final boolean _caseInsensitive;

    private volatile ValueDeserializer<Object> _codec;

    BBReaderPlaceholder(BeanDeserializerBase delegate,
            AnnotatedMethod buildMethod, boolean declaresViews,
            BeanReaderGenerator.Ignorals ignorals, Map<String, List<PropertyName>> aliases,
            boolean caseInsensitive) {
        _delegate = delegate;
        _buildMethod = buildMethod;
        _declaresViews = declaresViews;
        _ignorals = ignorals;
        _aliases = aliases;
        _caseInsensitive = caseInsensitive;
    }

    @Override
    public void resolve(DeserializationContext ctxt) {
        CodegenDebug.log("resolve " + _delegate.handledType().getName());
        _delegate.resolve(ctxt);
        _codec = BBReaderFactory.tryGenerate(_delegate, ctxt, _buildMethod,
                _declaresViews, _ignorals, _aliases, _caseInsensitive);
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        ValueDeserializer<?> contextual = _delegate.createContextual(ctxt, property);
        if (contextual != _delegate) {
            return contextual;
        }
        // A gated bean hands back the raw stock deserializer: databind
        // special-cases `instanceof BeanDeserializerBase` (the Nulls.AS_EMPTY
        // no-creator sanity check, for one), and a lingering wrapper would
        // change those decisions for beans that are fully stock anyway.
        ValueDeserializer<Object> codec = _codec;
        return (codec != null) ? codec : _delegate;
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt) {
        ValueDeserializer<Object> codec = _codec;
        return (codec != null) ? codec.deserialize(p, ctxt) : _delegate.deserialize(p, ctxt);
    }

    @Override
    public Object deserialize(JsonParser p, DeserializationContext ctxt, Object intoValue) {
        return _delegate.deserialize(p, ctxt, intoValue);
    }

    @Override
    public Object deserializeWithType(JsonParser p, DeserializationContext ctxt,
            TypeDeserializer typeDeserializer) {
        return _delegate.deserializeWithType(p, ctxt, typeDeserializer);
    }

    @Override
    public Object getNullValue(DeserializationContext ctxt) {
        return _delegate.getNullValue(ctxt);
    }

    @Override
    public Object getEmptyValue(DeserializationContext ctxt) {
        return _delegate.getEmptyValue(ctxt);
    }

    @Override
    public Object getAbsentValue(DeserializationContext ctxt) {
        return _delegate.getAbsentValue(ctxt);
    }

    @Override
    public AccessPattern getNullAccessPattern() {
        return _delegate.getNullAccessPattern();
    }

    @Override
    public AccessPattern getEmptyAccessPattern() {
        return _delegate.getEmptyAccessPattern();
    }

    @Override
    public Collection<Object> getKnownPropertyNames() {
        return _delegate.getKnownPropertyNames();
    }

    @Override
    public SettableBeanProperty findBackReference(String refName) {
        return _delegate.findBackReference(refName);
    }

    @Override
    public Boolean supportsUpdate(DeserializationConfig config) {
        return _delegate.supportsUpdate(config);
    }

    @Override
    public Class<?> handledType() {
        return _delegate.handledType();
    }

    @Override
    public LogicalType logicalType() {
        return _delegate.logicalType();
    }

    @Override
    public boolean isCachable() {
        return _delegate.isCachable();
    }

    // Unwrapping must produce the stock unwrapping variant, never the codec
    // (rationale in GeneratedReaderBase.unwrappingDeserializer).
    @Override
    public ValueDeserializer<Object> unwrappingDeserializer(DeserializationContext ctxt,
            NameTransformer unwrapper) {
        return _delegate.unwrappingDeserializer(ctxt, unwrapper);
    }
}
