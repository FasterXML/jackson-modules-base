package com.fasterxml.jackson.module.noctordeser.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.util.ClassUtil;
import com.fasterxml.jackson.databind.util.SimpleLookupCache;

import sun.reflect.ReflectionFactory;

@SuppressWarnings("restriction")
public class ReflectionUtil
{
    // Limit max number of generated Constructors cached
    private final SimpleLookupCache<Class<?>, Constructor<?>> constructorCache = new SimpleLookupCache<>(20, 100);

    public Object newConstructorAndCreateInstance(DeserializationContext ctxt,
            Class<?> classToInstantiate)
        throws JacksonException
    {
        if (classToInstantiate.isInterface() || Modifier.isAbstract(classToInstantiate.getModifiers())) {
            return null;
        }
        Constructor<?> constructor = constructorCache.get(classToInstantiate);
        try {
            if (constructor == null) {
                constructor = ReflectionFactory.getReflectionFactory()
                        .newConstructorForSerialization(classToInstantiate, Object.class.getDeclaredConstructor());
                constructor.setAccessible(true);
                constructorCache.put(classToInstantiate, constructor);
            }
        } catch (Exception e) {
            ctxt.reportBadDefinition(classToInstantiate,
"No-Constructor-Deserialization module failed to force generation of virtual constructor: "
                    +e.getMessage());
        }
 
        try {
            return constructor.newInstance();
        } catch (Exception e) {
            ctxt.reportBadDefinition(classToInstantiate,
"No-Constructor-Deserialization module failed to forcibly instantiate "
+ClassUtil.nameOf(classToInstantiate)+": " +e.getMessage());
        }
        // never gets here
        return null;
    }
}
