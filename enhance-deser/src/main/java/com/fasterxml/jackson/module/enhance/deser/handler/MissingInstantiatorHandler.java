package com.fasterxml.jackson.module.enhance.deser.handler;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.databind.deser.ValueInstantiator;
import com.fasterxml.jackson.module.enhance.deser.util.ReflectionUtil;

import java.io.IOException;

public class MissingInstantiatorHandler extends DeserializationProblemHandler {

    private static volatile MissingInstantiatorHandler INSTANCE;

    private MissingInstantiatorHandler() {
    }

    public static MissingInstantiatorHandler getInstance() {
        if (INSTANCE == null) {
            synchronized (MissingInstantiatorHandler.class) {
                if (INSTANCE == null) {
                    INSTANCE = new MissingInstantiatorHandler();
                }
            }
        }
        return INSTANCE;
    }

    @Override
    public Object handleMissingInstantiator(DeserializationContext ctxt, Class<?> instClass, ValueInstantiator valueInsta,
                                            JsonParser jsonParser, String msg) throws IOException, JacksonException {
        Object instance = ReflectionUtil.newConstructorAndCreateInstance(instClass);
        if (instance == null) {
            return NOT_HANDLED;
        }

        JsonDeserializer<Object> deserializer = ctxt.findRootValueDeserializer(ctxt.constructType(instClass));
        if (deserializer != null) {
            return deserializer.deserialize(jsonParser, ctxt, instance);
        }
        return NOT_HANDLED;
    }
}