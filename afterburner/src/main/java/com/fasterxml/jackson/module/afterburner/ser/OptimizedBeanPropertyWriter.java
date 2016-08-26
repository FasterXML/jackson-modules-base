/*
* Copyright (c) FasterXML, LLC.
* Licensed to the Apache Software Foundation (ASF)
* under one or more contributor license agreements;
* and to You under the Apache License, Version 2.0.
*/

package com.fasterxml.jackson.module.afterburner.ser;

import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.BeanPropertyWriter;
import com.fasterxml.jackson.databind.util.ClassUtil;

/**
 * Intermediate base class that is used for concrete
 * per-type implementations
 */
@SuppressWarnings("serial")
abstract class OptimizedBeanPropertyWriter<T extends OptimizedBeanPropertyWriter<T>>
    extends BeanPropertyWriter
{
    protected final BeanPropertyAccessor _propertyAccessor;

    /**
     * Locally stored version of efficiently serializable name.
     * Used to work around earlier problems with typing between
     * interface, implementation
     * 
     * @since 2.5
     */
    protected final SerializableString _fastName;
    protected final int _propertyIndex;

    protected final BeanPropertyWriter fallbackWriter;
    // Not volatile to prevent overhead, worst case is we trip the exception a few extra times
    protected boolean broken = false;

    protected OptimizedBeanPropertyWriter(BeanPropertyWriter src,
            BeanPropertyAccessor propertyAccessor, int propertyIndex,
            JsonSerializer<Object> ser)
    {
        super(src);
        this.fallbackWriter = unwrapFallbackWriter(src);
        // either use the passed on serializer or the original one
        _serializer = (ser != null) ? ser : src.getSerializer();
        _propertyAccessor = propertyAccessor;
        _propertyIndex = propertyIndex;
        _fastName = src.getSerializedName();
    }

    private BeanPropertyWriter unwrapFallbackWriter(BeanPropertyWriter srcIn)
    {
        while (srcIn instanceof OptimizedBeanPropertyWriter) {
            srcIn = ((OptimizedBeanPropertyWriter<?>)srcIn).fallbackWriter;
        }
        return srcIn;
    }

    // Overridden since 2.6.3
    @Override
    public void assignTypeSerializer(TypeSerializer typeSer) {
        super.assignTypeSerializer(typeSer);
        if (fallbackWriter != null) {
            fallbackWriter.assignTypeSerializer(typeSer);
        }
        // 04-Oct-2015, tatu: Should we handle this wrt [module-afterburner#59]?
        //    Seems unlikely, as String/long/int/boolean are final types; and for
        //    basic 'Object' we delegate to deserializer as expected
    }

    // Overridden since 2.6.3
    @Override
    public void assignSerializer(JsonSerializer<Object> ser) {
        super.assignSerializer(ser);
        if (fallbackWriter != null) {
            fallbackWriter.assignSerializer(ser);
        }
        // 04-Oct-2015, tatu: To fix [module-afterburner#59], need to disable use of
        //    fully optimized variant
        if (!isDefaultSerializer(ser)) {
            broken = true;
        }
    }

    @Override // since 2.7.6
    public void assignNullSerializer(JsonSerializer<Object> nullSer) {
        super.assignNullSerializer(nullSer);
        if (fallbackWriter != null) {
            fallbackWriter.assignNullSerializer(nullSer);
        }
    }

    public abstract T withAccessor(BeanPropertyAccessor acc);

    public abstract BeanPropertyWriter withSerializer(JsonSerializer<Object> ser);

    @Override
    public abstract void serializeAsField(Object bean, JsonGenerator jgen, SerializerProvider prov) throws Exception;

    // since 2.4.3
    @Override
    public abstract void serializeAsElement(Object bean, JsonGenerator jgen, SerializerProvider prov) throws Exception;

    // note: synchronized used to try to minimize race conditions; also, should NOT
    // be a performance problem
    protected synchronized void _handleProblem(Object bean, JsonGenerator gen, SerializerProvider prov,
            Throwable t, boolean element) throws Exception
    {
        if ((t instanceof IllegalAccessError)
                || (t instanceof SecurityException)) {
            _reportProblem(bean, t);
            if (element) {
                fallbackWriter.serializeAsElement(bean, gen, prov);
            } else {
                fallbackWriter.serializeAsField(bean, gen, prov);
            }
            return;
        }
        if (t instanceof Error) {
            throw (Error) t;
        }
        throw (Exception) t;
    }

    protected void _reportProblem(Object bean, Throwable e)
    {
        broken = true;
        String msg = String.format("Disabling Afterburner serialization for %s (field #%d; muator %s), due to access error (type %s, message=%s)%n",
                bean.getClass(), _propertyIndex, getClass().getName(),
                e.getClass().getName(), e.getMessage());
        Logger.getLogger(OptimizedBeanPropertyWriter.class.getName()).log(Level.WARNING, msg, e);
    }

    /**
     * Helper method used to check whether given serializer is the default
     * serializer implementation: this is necessary to avoid overriding other
     * kinds of deserializers.
     */
    protected boolean isDefaultSerializer(JsonSerializer<?> ser)
    {
        return (ser == null) || ClassUtil.isJacksonStdImpl(ser);
    }
}
