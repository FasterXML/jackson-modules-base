package com.fasterxml.jackson.module.noctordeser;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.module.noctordeser.util.ReflectionUtil;

import java.io.IOException;

public class MissingInstantiatorHandler extends DeserializationProblemHandler
{
    private final ReflectionUtil _util = new ReflectionUtil();

    @Override
    public Object handleMissingInstantiator(DeserializationContext ctxt, Class<?> instClass, ValueInstantiator valueInsta,
            JsonParser jsonParser, String msg) throws IOException, JacksonException
    {
        Object instance = _util.newConstructorAndCreateInstance(ctxt, instClass);
        if (instance != null) {
            JsonDeserializer<Object> deserializer = ctxt.findRootValueDeserializer(ctxt.constructType(instClass));
            if (deserializer != null) {
                return deserializer.deserialize(jsonParser, ctxt, instance);
            }
        }
        return NOT_HANDLED;
    }
}