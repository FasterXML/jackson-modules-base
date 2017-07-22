package com.fasterxml.jackson.module.jaxb;

import javax.xml.bind.annotation.adapters.XmlAdapter;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.fasterxml.jackson.databind.util.StdConverter;

public class AdapterConverter
    extends StdConverter<Object,Object>
{
    protected final JavaType _inputType, _targetType;

    protected final XmlAdapter<Object,Object> _adapter;
    
    protected final boolean _forSerialization;
    
    @SuppressWarnings("unchecked")
    public AdapterConverter(XmlAdapter<?,?> adapter,
            JavaType inType, JavaType outType, boolean ser)
    {
        _adapter = (XmlAdapter<Object,Object>) adapter;
        _inputType = inType;
        _targetType = outType;
        _forSerialization = ser;
    }
    
    @Override
    public Object convert(Object value)
    {
        try {
            if (_forSerialization) {
                return _adapter.marshal(value);
            }
            return _adapter.unmarshal(value);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public JavaType getInputType(TypeFactory typeFactory) {
        return _inputType;
    }

    @Override
    public JavaType getOutputType(TypeFactory typeFactory) {
        return _targetType;
    }

}
